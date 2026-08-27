// Package e2e drives a full end-to-end OAuth/OIDC flow against eSignet as a real client.
package e2e

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"maps"
	"math"
	"math/big"
	"net/http"
	"strings"
	"time"

	"github.com/mosip/esignet/api-test/internal/httpx"
)

// b64 is base64url without padding, the JOSE/JWT encoding.
func b64(b []byte) string { return base64.RawURLEncoding.EncodeToString(b) }

// generateRSA makes a fresh 2048-bit RSA key for the throwaway test client.
func generateRSA() (*rsa.PrivateKey, error) { return rsa.GenerateKey(rand.Reader, 2048) }

// publicJWK renders the public half as a JWK object (for client registration).
func publicJWK(priv *rsa.PrivateKey, kid string) map[string]any {
	pub := &priv.PublicKey
	return map[string]any{
		"kty": "RSA",
		"e":   b64(big.NewInt(int64(pub.E)).Bytes()),
		"n":   b64(pub.N.Bytes()),
		"use": "sig",
		"alg": "RS256",
		"kid": kid,
	}
}

// signJWS builds and signs a compact JWS with an explicit header. The caller
// owns every header member except "alg", which is set from alg so the header
// and the signature scheme can never disagree.
//
// Both supported algs sign with the same RSA key and differ only in padding:
// RS256 is PKCS#1 v1.5, PS256 is RSA-PSS. Which one a deployment accepts for a
// DPoP proof comes from its dpop_signing_alg_values_supported, so the harness
// has to be able to produce either.
func signJWS(priv *rsa.PrivateKey, alg string, header, claims map[string]any) (string, error) {
	hdr := maps.Clone(header)
	if hdr == nil {
		hdr = map[string]any{}
	}
	hdr["alg"] = alg
	h, err := json.Marshal(hdr)
	if err != nil {
		return "", err
	}
	p, err := json.Marshal(claims)
	if err != nil {
		return "", err
	}
	signingInput := b64(h) + "." + b64(p)
	sum := sha256.Sum256([]byte(signingInput))
	var sig []byte
	switch alg {
	case "RS256":
		sig, err = rsa.SignPKCS1v15(rand.Reader, priv, crypto.SHA256, sum[:])
	case "PS256":
		// RFC 7518 fixes the PS256 salt length at the hash length.
		sig, err = rsa.SignPSS(rand.Reader, priv, crypto.SHA256, sum[:],
			&rsa.PSSOptions{SaltLength: rsa.PSSSaltLengthEqualsHash, Hash: crypto.SHA256})
	default:
		return "", fmt.Errorf("unsupported signing alg %q (want RS256 or PS256)", alg)
	}
	if err != nil {
		return "", err
	}
	return signingInput + "." + b64(sig), nil
}

// signRS256 builds and signs a compact JWS (JWT) with the given claims.
func signRS256(priv *rsa.PrivateKey, kid string, claims map[string]any) (string, error) {
	header := map[string]any{"typ": "JWT"}
	if kid != "" {
		header["kid"] = kid
	}
	return signJWS(priv, "RS256", header, claims)
}

// clientAssertionTypeJWTBearer is the RFC 7523 client_assertion_type every
// private_key_jwt call carries — token, PAR and introspection alike.
const clientAssertionTypeJWTBearer = "urn:ietf:params:oauth:client-assertion-type:jwt-bearer"

// clientAssertion builds the private_key_jwt client assertion for the token call.
func clientAssertion(priv *rsa.PrivateKey, kid, clientID, aud string) (string, error) {
	now := time.Now()
	jti := make([]byte, 16)
	_, _ = rand.Read(jti)
	return signRS256(priv, kid, map[string]any{
		"iss": clientID,
		"sub": clientID,
		"aud": aud,
		"iat": now.Unix(),
		"exp": now.Add(5 * time.Minute).Unix(),
		"jti": b64(jti),
	})
}

// pkce returns a PKCE verifier and its S256 challenge.
func pkce() (verifier, challenge string) {
	raw := make([]byte, 32)
	_, _ = rand.Read(raw)
	verifier = b64(raw)
	sum := sha256.Sum256([]byte(verifier))
	challenge = b64(sum[:])
	return verifier, challenge
}

// decodeJWTPayload decodes (without verifying) the claims segment of a compact JWS.
func decodeJWTPayload(token string) (map[string]any, error) {
	parts := strings.Split(strings.TrimSpace(token), ".")
	if len(parts) < 2 {
		return nil, fmt.Errorf("not a compact JWT (%d segments)", len(parts))
	}
	pb, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return nil, fmt.Errorf("decode payload: %w", err)
	}
	var m map[string]any
	if err := json.Unmarshal(pb, &m); err != nil {
		return nil, fmt.Errorf("payload not JSON: %w", err)
	}
	return m, nil
}

// jwksKey is one RSA key from a JWKS document.
type jwksKey struct {
	Kty string `json:"kty"`
	Kid string `json:"kid"`
	N   string `json:"n"`
	E   string `json:"e"`
}

// verifyJWS verifies a compact JWS against the keys at jwksURL, matching by kid
// when present. Both RSA signature families are accepted, mirroring signJWS:
// eSignet's discovery advertises whichever it signs with, and a deployment
// publishing PS256 only (RSA-PSS) is as valid as one on RS256 (PKCS#1 v1.5).
func verifyJWS(ctx context.Context, token, jwksURL string, tlsVerify bool) error {
	parts := strings.Split(strings.TrimSpace(token), ".")
	if len(parts) != 3 {
		return fmt.Errorf("not a 3-part JWS")
	}
	// header kid
	hb, err := base64.RawURLEncoding.DecodeString(parts[0])
	if err != nil {
		return fmt.Errorf("decode header: %w", err)
	}
	var hdr struct {
		Kid string `json:"kid"`
		Alg string `json:"alg"`
	}
	_ = json.Unmarshal(hb, &hdr)
	if hdr.Alg != "RS256" && hdr.Alg != "PS256" {
		return fmt.Errorf("unexpected alg %q (want RS256 or PS256)", hdr.Alg)
	}

	keys, err := fetchJWKS(ctx, jwksURL, tlsVerify)
	if err != nil {
		return err
	}
	sig, err := base64.RawURLEncoding.DecodeString(parts[2])
	if err != nil {
		return fmt.Errorf("decode signature: %w", err)
	}
	sum := sha256.Sum256([]byte(parts[0] + "." + parts[1]))

	for _, k := range keys {
		if k.Kty != "RSA" {
			continue
		}
		if hdr.Kid != "" && k.Kid != "" && k.Kid != hdr.Kid {
			continue
		}
		pub, err := rsaPubFromJWK(k)
		if err != nil {
			continue
		}
		// The header alg picked above decides the scheme; a key that does not
		// verify falls through to the next one exactly as before.
		var verr error
		if hdr.Alg == "PS256" {
			verr = rsa.VerifyPSS(pub, crypto.SHA256, sum[:], sig,
				&rsa.PSSOptions{SaltLength: rsa.PSSSaltLengthAuto, Hash: crypto.SHA256})
		} else {
			verr = rsa.VerifyPKCS1v15(pub, crypto.SHA256, sum[:], sig)
		}
		if verr == nil {
			return nil
		}
	}
	return fmt.Errorf("no JWKS key verified the signature")
}

func fetchJWKS(ctx context.Context, url string, tlsVerify bool) ([]jwksKey, error) {
	// Shared client so the TLS-verify policy and floor live in one place.
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, fmt.Errorf("fetch jwks: %w", err)
	}
	resp, err := httpx.NewClient(tlsVerify, 15*time.Second).Do(req)
	if err != nil {
		return nil, fmt.Errorf("fetch jwks: %w", err)
	}
	defer func() { _ = resp.Body.Close() }()
	// An error page decodes into an empty key set, so distinguish it from a real verification failure.
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		return nil, fmt.Errorf("fetch jwks %s: HTTP %d", url, resp.StatusCode)
	}
	var doc struct {
		Keys []jwksKey `json:"keys"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&doc); err != nil {
		return nil, fmt.Errorf("parse jwks: %w", err)
	}
	return doc.Keys, nil
}

// b64urlDecode decodes a JWS/JWK base64url value, tolerating the padded form.
func b64urlDecode(s string) ([]byte, error) {
	if strings.ContainsRune(s, base64.StdPadding) {
		return base64.URLEncoding.DecodeString(s)
	}
	return base64.RawURLEncoding.DecodeString(s)
}

func rsaPubFromJWK(k jwksKey) (*rsa.PublicKey, error) {
	nb, err := b64urlDecode(k.N)
	if err != nil {
		return nil, err
	}
	eb, err := b64urlDecode(k.E)
	if err != nil {
		return nil, err
	}
	if len(nb) == 0 || len(eb) == 0 {
		return nil, fmt.Errorf("jwks key %q: empty modulus or exponent", k.Kid)
	}
	// Reject rather than silently truncate: an oversized exponent would build a
	// bogus key that fails verification for the wrong reason.
	e := new(big.Int).SetBytes(eb)
	if !e.IsInt64() || e.Int64() < 3 || e.Int64() > math.MaxInt32 {
		return nil, fmt.Errorf("jwks key %q: exponent out of range", k.Kid)
	}
	return &rsa.PublicKey{
		N: new(big.Int).SetBytes(nb),
		E: int(e.Int64()),
	}, nil
}
