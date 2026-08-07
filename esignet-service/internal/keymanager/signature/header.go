package signature

import (
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"fmt"
	"time"

	"github.com/mosip/esignet/internal/keymanager"
)

// JWS algorithm identifiers, matching Java's SignatureConstant.JWS_*_SIGN_ALGO_CONST.
const (
	algPS256  = "PS256"
	algRS256  = "RS256"
	algES256  = "ES256"
	algES256K = "ES256K"
	algEdDSA  = "EdDSA"
)

// payloadIssuerSentinel is the KeyIDPrepend value that triggers deriving the
// kid prefix from the payload's "iss" claim instead of using a literal
// string — mirrors Java's kidPrepend == "PAYLOAD_ISSUER" config semantics.
const payloadIssuerSentinel = "PAYLOAD_ISSUER"

// algorithmForRefID maps a reference id to its JWS algorithm, matching
// Java's SignatureUtil.getSignAlgorithm. The refID string constants are
// shared verbatim between the Java and Go key hierarchies
// (keymanager.RefIDECSECP256R1Sign etc.), confirmed against hierarchy.go.
func algorithmForRefID(refID string) string {
	switch refID {
	case keymanager.RefIDECSECP256R1Sign:
		return algES256
	case keymanager.RefIDECSECP256K1Sign:
		return algES256K
	case keymanager.RefIDED25519Sign:
		return algEdDSA
	default:
		return algPS256
	}
}

// AlgorithmForRefID is the exported form of algorithmForRefID, for callers
// (e.g. a RuntimeCryptoProvider adapter's GetPublicKeys) that need to report
// a resolved key's JWS algorithm without going through JWSSign/SignRaw.
func AlgorithmForRefID(refID string) string {
	return algorithmForRefID(refID)
}

// jwsHeader is the on-wire JWS protected header shape this port produces —
// V1 semantics: a single leaf certificate (x5c has at most one entry), no
// additionalHeaders/custom params. B64 uses *bool so the "b64" member is
// only ever emitted when explicitly false (RFC 7797); omitting it entirely
// means the default (true) applies, per spec.
type jwsHeader struct {
	Alg     string   `json:"alg"`
	B64     *bool    `json:"b64,omitempty"`
	Crit    []string `json:"crit,omitempty"`
	X5C     []string `json:"x5c,omitempty"`
	X5TS256 string   `json:"x5t#S256,omitempty"`
	X5U     string   `json:"x5u,omitempty"`
	Kid     string   `json:"kid,omitempty"`
}

// headerParams carries the already-default-resolved flags buildHeader needs.
type headerParams struct {
	signAlgorithm      string
	b64                bool
	includeCertificate bool
	includeCertHash    bool
	certificateURL     string
	leafCert           *x509.Certificate
	uniqueIdentifier   string
	includeKeyID       bool
	kidPrepend         string
}

// buildHeader constructs the JWS protected header and returns its
// base64url-encoded form — ports SignatureUtil.getJWSHeader (V1: single
// leaf certificate only, no additionalHeaders/full chain).
func buildHeader(p headerParams) (string, error) {
	h := jwsHeader{Alg: p.signAlgorithm}
	if !p.b64 {
		f := false
		h.B64 = &f
		h.Crit = []string{"b64"}
	}
	if p.includeCertificate && p.leafCert != nil {
		h.X5C = []string{base64.StdEncoding.EncodeToString(p.leafCert.Raw)}
	}
	if p.includeCertHash && p.leafCert != nil {
		sum := sha256.Sum256(p.leafCert.Raw)
		h.X5TS256 = base64.RawURLEncoding.EncodeToString(sum[:])
	}
	if p.certificateURL != "" {
		h.X5U = p.certificateURL
	}
	if p.includeKeyID {
		if kid, err := kidFromUniqueIdentifier(p.uniqueIdentifier); err == nil && kid != "" {
			h.Kid = p.kidPrepend + kid
		}
		// A kid-derivation failure is treated as best-effort omission (the
		// kid header is simply left blank), matching Java's
		// convertHexToBase64, which logs a warning and returns null rather
		// than failing the whole sign operation.
	}
	raw, err := json.Marshal(h)
	if err != nil {
		return "", fmt.Errorf("marshal JWS header: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(raw), nil
}

// buildSigningInput assembles the exact bytes signed for a JWS: the
// base64url-encoded header, a literal ".", and the payload bytes — ports
// SignatureUtil.buildSignData.
func buildSigningInput(headerB64 string, payload []byte) []byte {
	out := make([]byte, 0, len(headerB64)+1+len(payload))
	out = append(out, headerB64...)
	out = append(out, '.')
	out = append(out, payload...)
	return out
}

// kidFromUniqueIdentifier re-hashes a hex-encoded unique identifier (as
// stored in key_alias.uni_ident / SigningCertificate.UniqueIdentifier) with
// SHA-256 and base64url-encodes it — ports SignatureUtil.convertHexToBase64,
// confirmed to use SHA-256 (HMACUtils2.generateHash in the Java reference,
// kernel-core's HMACUtils2.HASH_ALGORITHM_NAME == "SHA-256").
func kidFromUniqueIdentifier(hexID string) (string, error) {
	raw, err := hex.DecodeString(hexID)
	if err != nil {
		return "", fmt.Errorf("decode unique identifier hex: %w", err)
	}
	sum := sha256.Sum256(raw)
	return base64.RawURLEncoding.EncodeToString(sum[:]), nil
}

// checkCertValidity ports keymanagerUtil.isCertificateValid: the leaf
// certificate must be within its NotBefore/NotAfter window at t.
func checkCertValidity(cert *x509.Certificate, t time.Time) error {
	if t.Before(cert.NotBefore) || t.After(cert.NotAfter) {
		return fmt.Errorf("%w: valid %s to %s, checked at %s", ErrCertificateNotValid, cert.NotBefore, cert.NotAfter, t)
	}
	return nil
}

// issuerFromPayload extracts the "iss" claim from a JSON payload — used when
// KeyIDPrepend == payloadIssuerSentinel. Ports
// SignatureUtil.getIssuerFromPayload: returns "" (not an error) if the
// payload isn't valid JSON or has no "iss" claim, matching Java's
// best-effort behavior.
func issuerFromPayload(jsonPayload []byte) string {
	var claims struct {
		Issuer string `json:"iss"`
	}
	if err := json.Unmarshal(jsonPayload, &claims); err != nil {
		return ""
	}
	return claims.Issuer
}
