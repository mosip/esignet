// Package signature ports two operations from Java's SignatureServiceImpl to
// Go: jwsSign (V1: single leaf certificate, no additionalHeaders) and
// jwtVerify (the counterpart verify path — JWS compact output is verified
// the same way regardless of which Java method produced it). See
// docs/design/SignatureService-Go-Port-Plan.md in the Java reference repo
// for the full design.
//
// Deliberately not ported (non-goals, resolved with the requester):
//   - RBAC (hasKeyAccess) — no caller-identity concept exists in this Go
//     library; access control is left to a future caller.
//   - Trust-chain validation (validateTrust) — PartnerCertificateManagerService
//     has no Go equivalent; JWSVerify proves the signature and certificate
//     date validity only, nothing about trust.
//   - jwtSign/jwtSignV2 (jose4j path), jwsSignV2/jwtVerifyV2 (full-chain +
//     additionalHeaders variants), legacy sign/validate, signPDF, signv2/rawSign.
//
// All key material is resolved through keymanager.Service.GetSigningCertificate
// / GetCertificate — this package never duplicates key-lifecycle or rotation
// logic, and adds no caching (every call resolves fresh, respecting lazy
// rotation), mirroring the cryptomanager package's relationship to keymanager.
package signature

import (
	"context"
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/rsa"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/mosip/esignet/internal/keymanager"
)

// Sentinel errors for validation/lookup failures (branch via errors.Is),
// following the same convention as keymanager's and cryptomanager's own
// error vars.
var (
	// ErrBlankApplicationID is returned by JWSSign when ApplicationID is
	// blank — checked first, before ReferenceID or DataToSign.
	ErrBlankApplicationID = errors.New("application id is required")

	// ErrBlankReferenceID is returned by JWSSign when ReferenceID is blank
	// for any ApplicationID other than ROOT (ROOT's reference id is always
	// blank, mirroring keymanager.Service's own ensureCurrentKey
	// convention). Checked after ErrBlankApplicationID, before DataToSign.
	ErrBlankReferenceID = errors.New("reference id is required")

	// ErrBlankSignatureData is returned by JWSSign when DataToSign is blank,
	// and by JWSVerify when JWTSignatureData is blank.
	ErrBlankSignatureData = errors.New("signature data is required")

	// ErrAESNotAllowedForSigning is returned by JWSSign when ReferenceID is
	// configured as a symmetric (AES) key reference id
	// (keymanager.Config.SymmetricKeyAllowedRefIDs) — an AES key has no
	// signing capability and can never produce a JWS signature.
	ErrAESNotAllowedForSigning = errors.New("Not allowed to use AES for JWS Signing.")

	// ErrEncryptionKeyNotAllowedForSigning is returned by JWSSign when
	// (ApplicationID, ReferenceID) resolves to a DB-resident Component
	// Encryption Key. JWS signing is restricted to keystore-resident
	// (PKCS#11/PKCS#12) identity keys — ROOT, a Component Master Key
	// (RSA_2048), or an EC/EdDSA sign key (keymanager.IsKeystoreResident) —
	// since only those keys are backed by a crypto.Signer suited to
	// producing signatures; Component Encryption Keys exist only to be
	// encrypted/decrypted against.
	ErrEncryptionKeyNotAllowedForSigning = errors.New("not allowed to use a component encryption key for JWS signing")

	// ErrInvalidBase64 is returned by JWSSign when DataToSign isn't valid
	// base64url (or padded base64url).
	ErrInvalidBase64 = errors.New("data to sign is not valid base64url")

	// ErrInvalidJSON is returned by JWSSign when ValidateJSON resolves true
	// and DataToSign's decoded bytes aren't valid JSON.
	ErrInvalidJSON = errors.New("data to sign is not valid JSON")

	// ErrCertificateNotValid is returned when the signing or verification
	// certificate is outside its NotBefore/NotAfter window.
	ErrCertificateNotValid = errors.New("certificate is not valid at the given time")

	// ErrUnsupportedAlgorithm is returned when the resolved JWS algorithm
	// has no signing/verification handler.
	ErrUnsupportedAlgorithm = errors.New("unsupported or unrecognized signature algorithm")

	// ErrSignFailed wraps any crypto.Signer.Sign failure.
	ErrSignFailed = errors.New("signing operation failed")

	// ErrMalformedJWS is returned by JWSVerify when JWTSignatureData isn't a
	// well-formed compact JWS (not 3 dot-separated segments, header isn't
	// valid base64url/JSON, or has no "alg").
	ErrMalformedJWS = errors.New("data is not a valid compact JWS")

	// ErrVerifyCertificateNotFound is returned when none of header/request/
	// keymanager certificate resolution succeeds.
	ErrVerifyCertificateNotFound = errors.New("no certificate available to verify the signature against")

	// ErrVerifyFailed is returned when cryptographic verification itself
	// fails (wrong key, tampered data, unsupported public key type).
	ErrVerifyFailed = errors.New("signature verification failed")
)

// Service implements JWSSign/JWSVerify. Resolves all key material through
// km; see the package doc comment for exactly what is and isn't ported from
// Java's SignatureServiceImpl.
type Service struct {
	km *keymanager.Service
}

// NewService constructs a Service. km would typically be the same
// keymanager.Service instance the rest of the application already uses —
// mirrors cryptomanager.NewService's dependency-injection pattern.
func NewService(km *keymanager.Service) *Service {
	return &Service{km: km}
}

func boolOrDefault(b *bool, def bool) bool {
	if b == nil {
		return def
	}
	return *b
}

// decodeBase64URL only accepts unpadded base64url. Unlike
// cryptomanager.decodeBase64 (which falls back to padded encodings for
// caller-supplied ciphertext that's never echoed back on the wire),
// req.DataToSign is used verbatim as the compact JWS payload segment when
// B64 defaults to true — accepting padded input here would let it leak into
// the wire output, violating RFC 7515's unpadded-base64url requirement for
// JOSE segments.
func decodeBase64URL(s string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(s)
}

// validateSigningKeyTier restricts JWSSign to keystore-resident (PKCS#11/
// PKCS#12) identity keys — ROOT, a Component Master Key (RSA_2048), or an
// EC/EdDSA sign key — rejecting both AES/symmetric reference ids (with a
// specific message) and DB-resident Component Encryption Keys (with a
// general one). Deliberately checked before any key material is resolved,
// so a rejected request never triggers GetSigningCertificate's lazy
// generation of a Component Encryption Key that was never a valid signing
// target in the first place.
func (s *Service) validateSigningKeyTier(appID, refID string) error {
	if keymanager.IsKeystoreResident(appID, refID) {
		return nil
	}
	if s.km.ValidateSymmetricKeyRefID(refID) == nil {
		return ErrAESNotAllowedForSigning
	}
	return ErrEncryptionKeyNotAllowedForSigning
}

// validateAlgorithmForKey fails closed when the (possibly caller-overridden)
// JWS algorithm doesn't match the resolved signing key's type, so a bad
// override is reported clearly here rather than surfacing as a confusing
// failure deep inside signDigest or at the verifier.
func validateAlgorithmForKey(alg string, pub crypto.PublicKey) error {
	switch alg {
	case algPS256, algRS256:
		if _, ok := pub.(*rsa.PublicKey); !ok {
			return fmt.Errorf("%w: %q requires an RSA key", ErrUnsupportedAlgorithm, alg)
		}
	case algES256, algES256K:
		if _, ok := pub.(*ecdsa.PublicKey); !ok {
			return fmt.Errorf("%w: %q requires an EC key", ErrUnsupportedAlgorithm, alg)
		}
	case algEdDSA:
		if _, ok := pub.(ed25519.PublicKey); !ok {
			return fmt.Errorf("%w: %q requires an Ed25519 key", ErrUnsupportedAlgorithm, alg)
		}
	}
	return nil
}

// JWSSign signs req.DataToSign and returns a compact JWS — ports Java's
// jwsSign (V1 semantics; see the package doc comment for exactly which
// flags/fields this implements and which are omitted, e.g. RBAC).
func (s *Service) JWSSign(ctx context.Context, req JWSSignRequest) (JWSSignResponse, error) {
	if strings.TrimSpace(req.ApplicationID) == "" {
		return JWSSignResponse{}, ErrBlankApplicationID
	}
	if req.ApplicationID != keymanager.AppIDRoot && strings.TrimSpace(req.ReferenceID) == "" {
		return JWSSignResponse{}, ErrBlankReferenceID
	}
	if strings.TrimSpace(req.DataToSign) == "" {
		return JWSSignResponse{}, ErrBlankSignatureData
	}
	if err := s.validateSigningKeyTier(req.ApplicationID, req.ReferenceID); err != nil {
		return JWSSignResponse{}, err
	}
	decoded, err := decodeBase64URL(req.DataToSign)
	if err != nil {
		return JWSSignResponse{}, ErrInvalidBase64
	}
	// Java's jwsSign unboxes ValidateJSON directly (a raw Boolean, without
	// the null-safe SignatureUtil.isIncludeAttrsValid wrapper every other
	// flag uses) — a latent NPE risk on the Java side for an omitted value,
	// not a deliberate "no validation by default" design. There's no
	// coherent Java default to match here; this port applies the same
	// default-true convention as every other flag instead.
	if boolOrDefault(req.ValidateJSON, true) && !json.Valid(decoded) {
		return JWSSignResponse{}, ErrInvalidJSON
	}

	signAlg := req.SignAlgorithm
	if signAlg == "" {
		signAlg = algorithmForRefID(req.ReferenceID)
	}

	sc, err := s.km.GetSigningCertificate(ctx, req.ApplicationID, req.ReferenceID)
	if err != nil {
		return JWSSignResponse{}, fmt.Errorf("resolve signing certificate: %w", err)
	}
	if sc.KeyPairEntry == nil || sc.KeyPairEntry.Certificate == nil {
		return JWSSignResponse{}, fmt.Errorf("%w: signing key has no certificate", ErrCertificateNotValid)
	}
	now := time.Now().UTC()
	if err := checkCertValidity(sc.KeyPairEntry.Certificate, now); err != nil {
		return JWSSignResponse{}, err
	}
	signer, ok := sc.KeyPairEntry.PrivateKey.(crypto.Signer)
	if !ok {
		return JWSSignResponse{}, fmt.Errorf("%w: resolved private key does not support signing", ErrSignFailed)
	}
	// Only checked when the caller explicitly overrides SignAlgorithm — the
	// default, algorithmForRefID(req.ReferenceID), is always consistent with
	// the resolved key by construction. Without this, an incompatible
	// override (e.g. PS256 against an EC key) fails later inside
	// signDigest/verification with an error that doesn't point at the real
	// cause.
	if req.SignAlgorithm != "" {
		if err := validateAlgorithmForKey(signAlg, sc.KeyPairEntry.Certificate.PublicKey); err != nil {
			return JWSSignResponse{}, err
		}
	}

	includePayload := boolOrDefault(req.IncludePayload, true)
	// Certificate embedding is opt-in, not opt-out: unless the caller
	// explicitly sets IncludeCertificate/IncludeCertHash, the header
	// contains only "alg" (plus whatever else the caller explicitly
	// requested, e.g. a kid) — no x5c/x5t#S256 by default.
	includeCertificate := boolOrDefault(req.IncludeCertificate, false)
	includeCertHash := boolOrDefault(req.IncludeCertHash, false)
	b64 := boolOrDefault(req.B64, true)

	kidPrepend := req.KeyIDPrepend
	if kidPrepend == payloadIssuerSentinel {
		kidPrepend = issuerFromPayload(decoded)
	}

	headerB64, err := buildHeader(headerParams{
		signAlgorithm:      signAlg,
		b64:                b64,
		includeCertificate: includeCertificate,
		includeCertHash:    includeCertHash,
		certificateURL:     req.CertificateURL,
		leafCert:           sc.KeyPairEntry.Certificate,
		uniqueIdentifier:   sc.UniqueIdentifier,
		includeKeyID:       req.IncludeKeyID,
		kidPrepend:         kidPrepend,
	})
	if err != nil {
		return JWSSignResponse{}, err
	}

	// When b64 is true (the default), the JWS payload octets ARE the
	// caller's original base64url string used verbatim as the wire payload
	// segment — standard JWS behavior, since the caller already supplied
	// dataToSign pre-base64url-encoded. When false (RFC 7797), the raw
	// decoded bytes are signed/embedded directly instead.
	payloadForSigning := []byte(req.DataToSign)
	if !b64 {
		payloadForSigning = decoded
	}
	signingInput := buildSigningInput(headerB64, payloadForSigning)

	sigBytes, err := signDigest(signer, signAlg, signingInput)
	if err != nil {
		return JWSSignResponse{}, err
	}
	sigB64 := base64.RawURLEncoding.EncodeToString(sigBytes)

	var payloadSegment string
	if includePayload {
		payloadSegment = string(payloadForSigning)
	}

	jws := headerB64 + "." + payloadSegment + "." + sigB64
	return JWSSignResponse{JWTSignedData: jws, Timestamp: now}, nil
}

// JWSVerify verifies a compact JWS — ports Java's jwtVerify (signature and
// certificate-date validity only; see the package doc comment for what's
// omitted, e.g. RBAC and trust-chain validation).
//
// Structural/input errors (blank input, malformed JWS) are returned as Go
// errors, matching Java's RequestException-on-bad-input behavior. A
// cryptographically well-formed but invalid signature is NOT a Go error —
// it's a normal JWSVerifyResponse{SignatureValid: false, Message: ...},
// matching Java's non-exception "verification failed" result.
func (s *Service) JWSVerify(ctx context.Context, req JWSVerifyRequest) (JWSVerifyResponse, error) {
	if strings.TrimSpace(req.JWTSignatureData) == "" {
		return JWSVerifyResponse{}, ErrBlankSignatureData
	}
	parts := strings.SplitN(req.JWTSignatureData, ".", 3)
	if len(parts) != 3 {
		return JWSVerifyResponse{}, ErrMalformedJWS
	}
	headerB64, payloadSegment, sigB64 := parts[0], parts[1], parts[2]

	rawHeader, err := base64.RawURLEncoding.DecodeString(headerB64)
	if err != nil {
		return JWSVerifyResponse{}, fmt.Errorf("%w: header is not valid base64url", ErrMalformedJWS)
	}
	var hdr struct {
		Alg string `json:"alg"`
	}
	if err := json.Unmarshal(rawHeader, &hdr); err != nil {
		return JWSVerifyResponse{}, fmt.Errorf("%w: header is not valid JSON", ErrMalformedJWS)
	}
	if hdr.Alg == "" {
		return JWSVerifyResponse{}, fmt.Errorf("%w: header has no alg", ErrMalformedJWS)
	}

	if req.ActualData != "" {
		payloadSegment = req.ActualData
	}

	cert, err := resolveVerifyCert(ctx, s.km, headerB64, req)
	if err != nil {
		return JWSVerifyResponse{SignatureValid: false, Message: err.Error()}, nil
	}
	if err := checkCertValidity(cert, time.Now().UTC()); err != nil {
		return JWSVerifyResponse{SignatureValid: false, Message: err.Error()}, nil
	}

	sigBytes, err := base64.RawURLEncoding.DecodeString(sigB64)
	if err != nil {
		return JWSVerifyResponse{SignatureValid: false, Message: "signature is not valid base64url"}, nil
	}

	signingInput := buildSigningInput(headerB64, []byte(payloadSegment))
	if err := verifySignature(hdr.Alg, cert.PublicKey, signingInput, sigBytes); err != nil {
		return JWSVerifyResponse{SignatureValid: false, Message: err.Error()}, nil
	}
	return JWSVerifyResponse{SignatureValid: true, Message: "signature valid"}, nil
}
