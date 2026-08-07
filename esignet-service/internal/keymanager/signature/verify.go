package signature

import (
	"context"
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"math/big"

	"github.com/mosip/esignet/internal/keymanager"
)

// certFromHeader extracts the leaf certificate embedded in a JWS header's
// x5c claim (V1: single leaf entry only), if present. Ports
// certificateExistsInHeader, minus its cert cache — an in-process
// performance cache is a non-goal here (see the design doc's Caching
// section: it doesn't sit on jwsSign/jwtVerify's correctness-critical path
// in the Java reference either).
func certFromHeader(headerB64 string) (*x509.Certificate, bool) {
	raw, err := base64.RawURLEncoding.DecodeString(headerB64)
	if err != nil {
		return nil, false
	}
	var h struct {
		X5C []string `json:"x5c"`
	}
	if err := json.Unmarshal(raw, &h); err != nil || len(h.X5C) == 0 {
		return nil, false
	}
	der, err := base64.StdEncoding.DecodeString(h.X5C[0])
	if err != nil {
		return nil, false
	}
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		return nil, false
	}
	return cert, true
}

// resolveVerifyCert picks the certificate to verify against. Caller-pinned
// identity always wins over the JWS header's own claim about who signed it:
// CertificatePEM, then a keymanager lookup by ApplicationID/ReferenceID, and
// only when the caller supplies neither pinning field does this fall back to
// the header-embedded x5c — otherwise a token carrying an attacker-controlled
// self-signed x5c would validate even when the caller expects a specific
// registered certificate, defeating signer-identity pinning entirely.
func resolveVerifyCert(ctx context.Context, km *keymanager.Service, headerB64 string, req JWSVerifyRequest) (*x509.Certificate, error) {
	if req.CertificatePEM != "" {
		cert, err := keymanager.ParseCertPEM(req.CertificatePEM)
		if err != nil {
			return nil, fmt.Errorf("%w: parse supplied certificate: %w", ErrVerifyCertificateNotFound, err)
		}
		return cert, nil
	}
	if req.ApplicationID == "" || req.ReferenceID == "" {
		if !req.AllowHeaderCertificate {
			return nil, fmt.Errorf("%w: no pinned certificate supplied and header x5c is not permitted", ErrVerifyCertificateNotFound)
		}
		if cert, ok := certFromHeader(headerB64); ok {
			return cert, nil
		}
		return nil, ErrVerifyCertificateNotFound
	}
	resp, err := km.GetCertificate(ctx, req.ApplicationID, req.ReferenceID)
	if err != nil {
		return nil, fmt.Errorf("%w: %w", ErrVerifyCertificateNotFound, err)
	}
	cert, err := keymanager.ParseCertPEM(resp.Certificate)
	if err != nil {
		return nil, fmt.Errorf("%w: parse resolved certificate: %w", ErrVerifyCertificateNotFound, err)
	}
	return cert, nil
}

// verifySignature checks sig against signingInput for the given JWS alg and
// public key. alg comes from the JWS header being verified (not derived
// from a reference id, since a verifier doesn't necessarily know which key
// tier produced the token) — the algorithm identifiers are the same PS256/
// RS256/ES256/ES256K/EdDSA strings used on the signing side.
func verifySignature(alg string, pub crypto.PublicKey, signingInput, sig []byte) error {
	switch alg {
	case algPS256:
		rsaPub, ok := pub.(*rsa.PublicKey)
		if !ok {
			return fmt.Errorf("%w: certificate public key is not RSA", ErrVerifyFailed)
		}
		digest := sha256.Sum256(signingInput)
		opts := &rsa.PSSOptions{SaltLength: rsa.PSSSaltLengthAuto, Hash: crypto.SHA256}
		if err := rsa.VerifyPSS(rsaPub, crypto.SHA256, digest[:], sig, opts); err != nil {
			return fmt.Errorf("%w: %w", ErrVerifyFailed, err)
		}
		return nil

	case algRS256:
		rsaPub, ok := pub.(*rsa.PublicKey)
		if !ok {
			return fmt.Errorf("%w: certificate public key is not RSA", ErrVerifyFailed)
		}
		digest := sha256.Sum256(signingInput)
		if err := rsa.VerifyPKCS1v15(rsaPub, crypto.SHA256, digest[:], sig); err != nil {
			return fmt.Errorf("%w: %w", ErrVerifyFailed, err)
		}
		return nil

	case algES256, algES256K:
		ecPub, ok := pub.(*ecdsa.PublicKey)
		if !ok {
			return fmt.Errorf("%w: certificate public key is not ECDSA", ErrVerifyFailed)
		}
		if len(sig) != ecSignatureLength {
			return fmt.Errorf("%w: ECDSA signature is not %d bytes", ErrVerifyFailed, ecSignatureLength)
		}
		half := ecSignatureLength / 2
		r := new(big.Int).SetBytes(sig[:half])
		s := new(big.Int).SetBytes(sig[half:])
		digest := sha256.Sum256(signingInput)
		if !ecdsa.Verify(ecPub, digest[:], r, s) {
			return fmt.Errorf("%w: ECDSA signature mismatch", ErrVerifyFailed)
		}
		return nil

	case algEdDSA:
		edPub, ok := pub.(ed25519.PublicKey)
		if !ok {
			return fmt.Errorf("%w: certificate public key is not Ed25519", ErrVerifyFailed)
		}
		if !ed25519.Verify(edPub, signingInput, sig) {
			return fmt.Errorf("%w: Ed25519 signature mismatch", ErrVerifyFailed)
		}
		return nil

	default:
		return fmt.Errorf("%w: %q", ErrUnsupportedAlgorithm, alg)
	}
}
