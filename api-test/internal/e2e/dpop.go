package e2e

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"math/big"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// DPoP (RFC 9449) sender-constrained access tokens, from the relying party's side.
//
// The DPoP key is deliberately NOT the client's registration key. The
// registration key authenticates the client (private_key_jwt at token and PAR);
// the DPoP key proves possession per flow and is what the issued access token
// gets bound to via cnf.jkt. Keeping them separate is what makes a
// wrong-key proof a meaningful negative rather than an impossible one.

// defaultDPoPAlg is used when discovery advertises no
// dpop_signing_alg_values_supported at all. It is in the shipped
// deployment.yaml allow-list, so it is the safer guess than PS256.
const defaultDPoPAlg = "RS256"

// dpopAlgs are the algs this harness can produce, in preference order. Both
// sign with the same RSA key, so picking between them costs nothing.
var dpopAlgs = []string{"PS256", "RS256"}

// chooseDPoPAlg picks the first alg the harness can produce that the deployment
// also advertises. An empty or unrecognised advertisement falls back to
// defaultDPoPAlg rather than failing: a deployment that lists nothing may still
// accept a proof, and letting the request happen produces a real server error
// to read instead of a harness-side guess.
func chooseDPoPAlg(supported []string) string {
	for _, want := range dpopAlgs {
		for _, got := range supported {
			if strings.EqualFold(want, got) {
				return want
			}
		}
	}
	return defaultDPoPAlg
}

// dpopSigner mints the DPoP proofs for one scenario's flow. One signer is used
// across PAR, token and userinfo so the proofs share a thumbprint — that shared
// jkt is the binding the server enforces.
type dpopSigner struct {
	priv *rsa.PrivateKey
	alg  string
	jwk  map[string]any // exactly the RFC 7638 members, so it also serves as the proof header's jwk
	jkt  string         // base64url(SHA-256(canonical jwk)) — the cnf.jkt the token carries

	// nonce is the most recent DPoP-Nonce the server issued. A server may
	// demand one by rejecting the first proof; the next proof replays it.
	nonce string
}

// newDPoPSigner generates a fresh proof key and precomputes its thumbprint.
func newDPoPSigner(alg string) (*dpopSigner, error) {
	priv, err := generateRSA()
	if err != nil {
		return nil, fmt.Errorf("dpop keygen: %w", err)
	}
	// Only kty/n/e: the thumbprint is defined over exactly these members, so
	// carrying no others keeps the header jwk and the jkt trivially consistent.
	jwk := map[string]any{
		"kty": "RSA",
		"n":   b64(priv.PublicKey.N.Bytes()),
		"e":   b64(big.NewInt(int64(priv.PublicKey.E)).Bytes()),
	}
	jkt, err := jwkThumbprint(jwk)
	if err != nil {
		return nil, err
	}
	return &dpopSigner{priv: priv, alg: alg, jwk: jwk, jkt: jkt}, nil
}

// jwkThumbprint computes the RFC 7638 SHA-256 thumbprint of an RSA JWK: the
// required members only, lexicographically ordered, no whitespace.
func jwkThumbprint(jwk map[string]any) (string, error) {
	kty, _ := jwk["kty"].(string)
	n, _ := jwk["n"].(string)
	e, _ := jwk["e"].(string)
	if kty != "RSA" || n == "" || e == "" {
		return "", fmt.Errorf("thumbprint: not an RSA JWK with n and e")
	}
	// Assembled by hand rather than by marshalling a map: the canonical form
	// fixes both the member order and the absence of whitespace, neither of
	// which a generic encoder guarantees. Only the values go through the JSON
	// encoder, for escaping.
	eb, err := json.Marshal(e)
	if err != nil {
		return "", err
	}
	kb, err := json.Marshal(kty)
	if err != nil {
		return "", err
	}
	nb, err := json.Marshal(n)
	if err != nil {
		return "", err
	}
	sum := sha256.Sum256([]byte(`{"e":` + string(eb) + `,"kty":` + string(kb) + `,"n":` + string(nb) + `}`))
	return b64(sum[:]), nil
}

// proof returns a DPoP proof JWT for one request.
//
// accessToken is passed only at resource endpoints: it adds the ath claim that
// ties the proof to that specific token, without which a proof captured at the
// token endpoint could be replayed against userinfo.
func (d *dpopSigner) proof(method, uri, accessToken string) (string, error) {
	jti := make([]byte, 16)
	if _, err := rand.Read(jti); err != nil {
		return "", fmt.Errorf("dpop jti: %w", err)
	}
	claims := map[string]any{
		"jti": b64(jti),
		"htm": strings.ToUpper(method),
		"htu": htu(uri),
		"iat": time.Now().Unix(),
	}
	if accessToken != "" {
		sum := sha256.Sum256([]byte(accessToken))
		claims["ath"] = b64(sum[:])
	}
	if d.nonce != "" {
		claims["nonce"] = d.nonce
	}
	return signJWS(d.priv, d.alg, map[string]any{"typ": "dpop+jwt", "jwk": d.jwk}, claims)
}

// htu strips the query and fragment, which RFC 9449 excludes from the htu
// claim. The server canonicalises both sides before comparing, so anything
// beyond this (case, default ports) does not need normalising here.
func htu(raw string) string {
	u, err := url.Parse(raw)
	if err != nil {
		return raw
	}
	u.RawQuery = ""
	u.Fragment = ""
	u.RawFragment = ""
	return u.String()
}

// dpopNonceFrom records a server-issued DPoP nonce and reports whether it is new.
// A server that wants nonces answers the first proof with an error and the
// nonce to use; the caller retries once with it.
func (d *dpopSigner) dpopNonceFrom(h http.Header) bool {
	n := strings.TrimSpace(h.Get("DPoP-Nonce"))
	if n == "" || n == d.nonce {
		return false
	}
	d.nonce = n
	return true
}

// isDPoPNonceError reports whether a response body is the use_dpop_nonce error
// that asks the client to retry with the nonce from the DPoP-Nonce header.
func isDPoPNonceError(body []byte) bool {
	var e struct {
		Error string `json:"error"`
	}
	if json.Unmarshal(body, &e) != nil {
		return false
	}
	return e.Error == "use_dpop_nonce"
}
