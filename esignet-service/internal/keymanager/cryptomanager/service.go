package cryptomanager

import (
	"bytes"
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/db"
	applog "github.com/mosip/esignet/internal/log"
)

// Sentinel errors for validation/lookup failures (branch via errors.Is),
// following the same convention as internal/keymanager's own error vars.
var (
	// ErrBlankApplicationID is returned by Encrypt, JWTEncrypt, JWTDecrypt,
	// and Decrypt when ApplicationID is blank — checked first, before
	// ReferenceID or Data, mirroring Java's CryptomanagerRequestDto/
	// JWTEncryptRequestDto/JWTDecryptRequestDto, which all declare
	// applicationId @NotBlank (unlike referenceId, which is only required
	// by business logic for encrypt-direction calls — see
	// ErrBlankReferenceID).
	ErrBlankApplicationID = errors.New("application id is required")

	// ErrBlankReferenceID is returned by Encrypt, JWTEncrypt, and JWTDecrypt
	// when ReferenceID is empty — a target key always needs one. Checked
	// after ErrBlankApplicationID.
	ErrBlankReferenceID = errors.New("reference id is required")

	// ErrEncryptionAgainstReservedKey is returned by validateKeyIdentifierIDs
	// when (ApplicationID, ReferenceID) resolves to a keystore-resident key
	// (ROOT, a Component Master Key, or an EC/sign key) — encrypting data
	// "to" one of these identity keys is never valid; only Component
	// Encryption Keys are legitimate Encrypt/JWTEncrypt targets. Broader
	// than the Java reference's KERNEL/SIGN-specific guard by deliberate
	// choice — see the design doc's Context section.
	ErrEncryptionAgainstReservedKey = errors.New("not allowed to use Component Master Key/Root for encryption purpose")

	// ErrInvalidData is returned when a caller-supplied data field isn't
	// valid base64 (neither unpadded URL-safe nor padded standard).
	ErrInvalidData = errors.New("data is not valid base64")

	// ErrInvalidRequest is returned when a request's data field (Encrypt/
	// JWTEncrypt's Data, Decrypt's Data, JWTDecrypt's EncData) is blank —
	// "", whitespace-only, or decodes to nothing but whitespace.
	ErrInvalidRequest = errors.New("data field is required or should not be blank")

	// ErrInvalidJSON is returned by JWTEncrypt when the decoded payload
	// isn't valid JSON and isn't already-signed JWS data (3 dot-separated
	// segments, used as-is without a JSON check).
	ErrInvalidJSON = errors.New("data is not valid JSON")

	// ErrEnvelopeMalformed is returned when Decrypt's wire-format envelope
	// can't be base64url-decoded, or the configured splitter isn't found in it.
	ErrEnvelopeMalformed = errors.New("encrypted data envelope is malformed")

	// ErrLegacyFormatUnsupported is returned when the envelope's key
	// material doesn't match the VER_R2 thumbprint-prefixed shape this port
	// implements — see the design doc's non-goals (legacy no-key-identifier
	// format is out of scope).
	ErrLegacyFormatUnsupported = errors.New("encrypted key material is not in the VER_R2 format supported by this implementation")

	// ErrKeyNotFoundForThumbprint is returned when no key_alias row matches
	// the certificate thumbprint embedded in the encrypted key material (or,
	// for JWTDecrypt, the JWE's kid header).
	ErrKeyNotFoundForThumbprint = errors.New("no key found for the certificate thumbprint in the encrypted data")

	// ErrKeyIdentifierMismatch is returned when the key_alias row resolved
	// by certificate thumbprint does not belong to the request's
	// (ApplicationID, ReferenceID). Deliberately does not disclose what the
	// thumbprint actually resolved to — see resolveDecryptionKey.
	ErrKeyIdentifierMismatch = errors.New("mismatch of application id and reference id")

	// ErrDecryptionNotAllowed is returned when the resolved key is itself a
	// keystore-resident master/root/sign key — rejected unconditionally for
	// both Decrypt and JWTDecrypt (no fetchMasterKey-style flag, unlike the
	// Java reference; see the design doc's Context section for why: Encrypt
	// already refuses to ever target one, so this should be unreachable in
	// practice and exists as defense in depth).
	ErrDecryptionNotAllowed = errors.New("decryption using a master or root key is not allowed")

	// ErrForeignDomainKeyNotDecryptable is returned when the resolved key
	// belongs to a foreign-domain, cert-only key_store row (private_key == "NA").
	ErrForeignDomainKeyNotDecryptable = errors.New("resolved key has no decryptable private key (foreign-domain certificate-only entry)")

	// ErrJWECertificateKeyLengthInvalid is returned by JWTEncrypt when the
	// resolved or caller-supplied certificate's RSA public key is not 2048 bits.
	ErrJWECertificateKeyLengthInvalid = errors.New("certificate RSA public key must be 2048 bits for JWT encryption")

	// ErrJWEEncryptionFailed wraps a JWE library failure during JWTEncrypt.
	ErrJWEEncryptionFailed = errors.New("JWE encryption failed")

	// ErrJWEInvalidCompactSerialization is returned by JWTDecrypt when the
	// input isn't a valid compact-serialized JWE.
	ErrJWEInvalidCompactSerialization = errors.New("data is not a valid compact-serialized JWE")

	// ErrJWEDecryptFailed is returned by JWTDecrypt when JWE decryption
	// itself fails (wrong key, tampered data, auth-tag mismatch).
	ErrJWEDecryptFailed = errors.New("JWE decryption failed")
)

// Service implements the cryptomanager business logic: Encrypt/Decrypt
// (hybrid envelope encryption of arbitrary payloads) and
// JWTEncrypt/JWTDecrypt (RSA-OAEP-256 + A256GCM JWE), resolving key
// material through km rather than duplicating any key-lifecycle logic.
type Service struct {
	q      db.Querier
	km     *keymanager.Service
	cfg    Config
	logger *applog.Logger
}

// NewService constructs a Service. q and km would typically share the same
// underlying DB connection/schema that km itself was constructed with —
// cryptomanager needs direct Querier access only for the
// thumbprint-based key resolution keymanager.Service doesn't expose (see
// keyresolve.go).
func NewService(q db.Querier, km *keymanager.Service, cfg Config) *Service {
	return &Service{q: q, km: km, cfg: cfg, logger: applog.GetLogger().Named("cryptomanager")}
}

// validateKeyIdentifierIDs is the shared applicationId/referenceId guard for
// Encrypt, JWTEncrypt, and JWTDecrypt — mirrors Java's
// CryptomanagerUtils.validateKeyIdentifierIds, broadened per the design
// doc's Context section: any keystore-resident target is refused, not just
// KERNEL's own SIGN/identity-cache key.
func (s *Service) validateKeyIdentifierIDs(appID, refID string) error {
	if !isDataValid(appID) {
		return ErrBlankApplicationID
	}
	if !isDataValid(refID) {
		return ErrBlankReferenceID
	}
	if keymanager.IsKeystoreResident(appID, refID) {
		return ErrEncryptionAgainstReservedKey
	}
	return nil
}

// Encrypt hybrid-encrypts data: a fresh AES session key encrypts the
// payload (AES-GCM, VER_R2 format — see envelope.go), and the current
// Component Encryption Key's certificate RSA-OAEP-wraps the session key.
// Mirrors Java's CryptomanagerServiceImpl.encrypt.
func (s *Service) Encrypt(ctx context.Context, req EncryptRequest) (EncryptResponse, error) {
	// Validation order is deliberate: ApplicationID, then ReferenceID
	// (both via validateKeyIdentifierIDs), then Data — not the reverse.
	if err := s.validateKeyIdentifierIDs(req.ApplicationID, req.ReferenceID); err != nil {
		return EncryptResponse{}, err
	}
	if !isDataValid(req.Data) {
		return EncryptResponse{}, ErrInvalidRequest
	}
	plaintext, err := decodeBase64(req.Data)
	if err != nil {
		return EncryptResponse{}, err
	}
	// isDataValid(req.Data) above only rules out a blank *base64 string* —
	// it can't catch e.g. "   " (three spaces), which base64-encodes to a
	// perfectly well-formed, non-blank string that then decodes back to
	// nothing but whitespace. Check the decoded content too.
	if len(bytes.TrimSpace(plaintext)) == 0 {
		return EncryptResponse{}, ErrInvalidRequest
	}

	sessionKey := make([]byte, s.cfg.SessionKeyLength)
	if _, err := rand.Read(sessionKey); err != nil {
		return EncryptResponse{}, fmt.Errorf("generate session key: %w", err)
	}

	encryptedData, err := symmetricEncrypt(sessionKey, plaintext)
	if err != nil {
		return EncryptResponse{}, fmt.Errorf("symmetric encrypt: %w", err)
	}

	cert, _, _, err := s.km.ResolveCurrentKey(ctx, req.ApplicationID, req.ReferenceID)
	if err != nil {
		return EncryptResponse{}, fmt.Errorf("resolve encryption certificate: %w", err)
	}
	rsaPub, ok := cert.PublicKey.(*rsa.PublicKey)
	if !ok {
		return EncryptResponse{}, fmt.Errorf("certificate for %q/%q does not have an RSA public key", req.ApplicationID, req.ReferenceID)
	}
	encryptedSessionKey, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, rsaPub, sessionKey, nil)
	if err != nil {
		return EncryptResponse{}, fmt.Errorf("wrap session key: %w", err)
	}

	envelope := buildEnvelope(s.cfg.DataKeySplitter, thumbprintRaw(cert), encryptedSessionKey, encryptedData)
	s.logger.Debug(ctx, "data encrypted",
		applog.String("applicationId", req.ApplicationID), applog.String("referenceId", req.ReferenceID))
	return EncryptResponse{Data: envelope}, nil
}

// Decrypt reverses Encrypt: the envelope's embedded certificate thumbprint
// resolves the private key needed to unwrap the session key, which then
// AES-GCM-decrypts the payload. Mirrors Java's
// CryptomanagerServiceImpl.decrypt — except decrypt's key_policy_def
// authorization check (hasKeyAccess) is intentionally omitted (see the
// design doc's Context section and §8, item 1).
func (s *Service) Decrypt(ctx context.Context, req DecryptRequest) (DecryptResponse, error) {
	// ApplicationID is required (mirrors Java's CryptomanagerRequestDto
	// @NotBlank applicationId) — checked first, before Data. Unlike
	// Encrypt/JWTEncrypt/JWTDecrypt, ReferenceID isn't required up front
	// for Decrypt (Java doesn't annotate it @NotBlank there either): a
	// blank ReferenceID is handled by resolveDecryptionKey's own
	// (appID, refID) match against the resolved key, same as any other
	// mismatch.
	if !isDataValid(req.ApplicationID) {
		return DecryptResponse{}, ErrBlankApplicationID
	}
	// A blank Data ("" — e.g. -data simply not given) previously fell
	// straight through to parseEnvelope, which base64-decodes it to zero
	// bytes, fails to find the splitter in that, and surfaces a generic
	// "envelope is malformed: splitter not found" — technically accurate,
	// but not the actual problem (there was no envelope at all). Checked
	// explicitly, same as Encrypt/JWTEncrypt/JWTDecrypt already do for
	// their own data fields.
	if !isDataValid(req.Data) {
		return DecryptResponse{}, ErrInvalidRequest
	}
	thumbprintHex, encryptedSessionKey, encryptedData, err := parseEnvelope(s.cfg.DataKeySplitter, req.Data)
	if err != nil {
		return DecryptResponse{}, err
	}

	priv, _, err := s.resolveDecryptionKey(ctx, req.ApplicationID, req.ReferenceID, thumbprintHex)
	if err != nil {
		return DecryptResponse{}, err
	}
	decrypter, ok := priv.(crypto.Decrypter)
	if !ok {
		return DecryptResponse{}, fmt.Errorf("resolved private key does not support decryption")
	}
	sessionKey, err := decrypter.Decrypt(rand.Reader, encryptedSessionKey, &rsa.OAEPOptions{Hash: crypto.SHA256})
	if err != nil {
		return DecryptResponse{}, fmt.Errorf("unwrap session key: %w", err)
	}

	plaintext, err := symmetricDecrypt(sessionKey, encryptedData)
	if err != nil {
		return DecryptResponse{}, fmt.Errorf("symmetric decrypt: %w", err)
	}
	s.logger.Debug(ctx, "data decrypted",
		applog.String("applicationId", req.ApplicationID), applog.String("referenceId", req.ReferenceID))
	return DecryptResponse{Data: base64.RawURLEncoding.EncodeToString(plaintext)}, nil
}

// JWTEncrypt builds a compact-serialized JWE (RSA-OAEP-256 + A256GCM)
// encrypted to either a caller-supplied certificate (X509Certificate,
// bypassing key resolution entirely) or the current Component Encryption
// Key's certificate. Mirrors Java's CryptomanagerServiceImpl.jwtEncrypt.
func (s *Service) JWTEncrypt(ctx context.Context, req JWTEncryptRequest) (JWTCipherResponse, error) {
	// All input validation completes before anything touches the DB/
	// keystore (or even parses caller-supplied material) — ApplicationID,
	// then ReferenceID (both via validateKeyIdentifierIDs, skipped only
	// when a caller-supplied certificate bypasses key resolution
	// entirely), then Data. Previously this method resolved the
	// certificate — a DB call via km.ResolveCurrentKey — before Data had
	// been validated at all, so a request with valid identifiers but
	// invalid Data would fail with a resolution-layer error
	// ("resolve encryption certificate: ...") instead of the intended
	// input-validation error.
	usingSuppliedCert := isDataValid(req.X509Certificate)
	if !usingSuppliedCert {
		if err := s.validateKeyIdentifierIDs(req.ApplicationID, req.ReferenceID); err != nil {
			return JWTCipherResponse{}, err
		}
	}

	if !isDataValid(req.Data) {
		return JWTCipherResponse{}, ErrInvalidRequest
	}
	payload := []byte(req.Data)
	if !isJWSData(req.Data) {
		decoded, err := decodeBase64(req.Data)
		if err != nil {
			return JWTCipherResponse{}, err
		}
		// isDataValid(req.Data) above only rules out a blank *base64
		// string* — it can't catch e.g. "  " (two spaces), which
		// base64-encodes to a well-formed, non-blank string that then
		// decodes back to nothing but whitespace. Checked unconditionally,
		// regardless of ValidateJSON: even when the caller doesn't want
		// JSON structure enforced, there must still be actual content to
		// encrypt.
		if len(bytes.TrimSpace(decoded)) == 0 {
			return JWTCipherResponse{}, ErrInvalidRequest
		}
		if req.ValidateJSON && !isJSONValid(decoded) {
			return JWTCipherResponse{}, ErrInvalidJSON
		}
		payload = decoded
	}

	// Input validation is complete — only now do we resolve the
	// certificate, the one place this method touches the DB/keystore (or
	// parses caller-supplied material).
	cert, err := s.resolveJWTEncryptCertificate(ctx, req, usingSuppliedCert)
	if err != nil {
		return JWTCipherResponse{}, err
	}

	if s.cfg.EnforceJWTCertKeyLength {
		rsaPub, ok := cert.PublicKey.(*rsa.PublicKey)
		if !ok || rsaPub.N.BitLen() != 2048 {
			return JWTCipherResponse{}, ErrJWECertificateKeyLengthInvalid
		}
	}

	compact, err := buildJWECompact(cert, payload, jweBuildOptions{
		compress:           boolOrDefault(req.EnableDeflateCompression, true),
		includeCertificate: req.IncludeCertificate,
		includeCertHash:    req.IncludeCertHash,
		jwkSetURL:          req.JWKSetURL,
	})
	if err != nil {
		return JWTCipherResponse{}, err
	}
	s.logger.Debug(ctx, "JWT encrypted",
		applog.String("applicationId", req.ApplicationID), applog.String("referenceId", req.ReferenceID),
		applog.Bool("usingSuppliedCert", usingSuppliedCert))
	return JWTCipherResponse{Data: compact, Timestamp: time.Now().UTC()}, nil
}

// resolveJWTEncryptCertificate resolves the certificate to encrypt against
// — either parsing a caller-supplied PEM (usingSuppliedCert, no DB/keystore
// interaction) or looking up the current Component Encryption Key
// certificate via km (a DB/keystore call). Called only after JWTEncrypt has
// finished validating every input field.
func (s *Service) resolveJWTEncryptCertificate(ctx context.Context, req JWTEncryptRequest, usingSuppliedCert bool) (*x509.Certificate, error) {
	if usingSuppliedCert {
		c, err := keymanager.ParseCertPEM(req.X509Certificate)
		if err != nil {
			return nil, fmt.Errorf("parse supplied certificate: %w", err)
		}
		return c, nil
	}
	c, _, _, err := s.km.ResolveCurrentKey(ctx, req.ApplicationID, req.ReferenceID)
	if err != nil {
		return nil, fmt.Errorf("resolve encryption certificate: %w", err)
	}
	return c, nil
}

// JWTDecrypt reverses JWTEncrypt: the JWE's kid header (a base64url-encoded
// certificate thumbprint) resolves the private key needed to decrypt.
// Mirrors Java's CryptomanagerServiceImpl.jwtDecrypt — never falls back to
// a master/root/sign key (Java's fetchMasterKey=false), which in this port
// is simply resolveDecryptionKey's only behavior (see the design doc's
// Context section).
func (s *Service) JWTDecrypt(ctx context.Context, req JWTDecryptRequest) (JWTCipherResponse, error) {
	if err := s.validateKeyIdentifierIDs(req.ApplicationID, req.ReferenceID); err != nil {
		return JWTCipherResponse{}, err
	}
	if !isDataValid(req.EncData) {
		return JWTCipherResponse{}, ErrInvalidRequest
	}

	jwe, kidThumbprintHex, err := parseJWECompact(req.EncData)
	if err != nil {
		return JWTCipherResponse{}, err
	}

	priv, _, err := s.resolveDecryptionKey(ctx, req.ApplicationID, req.ReferenceID, kidThumbprintHex)
	if err != nil {
		return JWTCipherResponse{}, err
	}
	rsaPriv, ok := priv.(*rsa.PrivateKey)
	if !ok {
		return JWTCipherResponse{}, fmt.Errorf("resolved private key is not RSA")
	}

	plaintext, err := decryptJWE(jwe, rsaPriv)
	if err != nil {
		return JWTCipherResponse{}, err
	}
	s.logger.Debug(ctx, "JWT decrypted",
		applog.String("applicationId", req.ApplicationID), applog.String("referenceId", req.ReferenceID))
	return JWTCipherResponse{Data: base64.RawURLEncoding.EncodeToString(plaintext), Timestamp: time.Now().UTC()}, nil
}

// isDataValid reports whether s is non-blank after trimming — mirrors
// CryptomanagerUtils.isDataValid.
func isDataValid(s string) bool {
	return strings.TrimSpace(s) != ""
}

// isJWSData is a heuristic for "already a signed JWS compact string": 3
// dot-separated segments — mirrors CryptomanagerUtils.isJWSData.
func isJWSData(data string) bool {
	return strings.Count(data, ".") == 2 && len(strings.Split(data, ".")) == 3
}

// isJSONValid reports whether data parses as JSON — mirrors
// CryptomanagerUtils.isJsonValid (JWTEncrypt's checkForValidJsonData gate).
func isJSONValid(data []byte) bool {
	return json.Valid(data)
}

// boolOrDefault returns v if set, else fallback — used for
// JWTEncryptRequest.EnableDeflateCompression, whose Java counterpart
// defaults to true (a plain Go bool's zero value can't express that).
func boolOrDefault(v *bool, fallback bool) bool {
	if v == nil {
		return fallback
	}
	return *v
}
