package cryptomanager

import "time"

// EncryptRequest mirrors the relevant fields of Java's
// CryptomanagerRequestDto for the encrypt call. Unlike Java, there is no
// Salt/AAD input — Encrypt always generates its own 32-byte AAD (the
// VER_R2 format, see envelope.go); see the design doc's Context section for
// why caller-supplied salt/AAD was dropped.
type EncryptRequest struct {
	ApplicationID string
	ReferenceID   string

	// Data is base64-encoded plaintext. Rejected with ErrInvalidRequest if
	// blank as a string ("", whitespace-only) OR if it decodes to nothing
	// but whitespace (e.g. base64-encoded "   ") — either way, there's
	// nothing meaningful to encrypt.
	Data string
}

// EncryptResponse mirrors CryptomanagerResponseDto.
type EncryptResponse struct {
	Data string // base64url wire-format envelope, see envelope.go
}

// DecryptRequest mirrors the relevant fields of CryptomanagerRequestDto for
// the decrypt call. No CallerID/identity field — decrypt's
// key_policy_def.access_allowed authorization check is intentionally
// omitted in this port (see design doc §8, item 1): no "authenticated
// principal" concept exists anywhere in esignet-service today.
type DecryptRequest struct {
	ApplicationID string
	ReferenceID   string
	Data          string // base64url wire-format envelope, as produced by Encrypt
}

// DecryptResponse mirrors CryptomanagerResponseDto.
type DecryptResponse struct {
	Data string // base64url-encoded plaintext
}

// JWTEncryptRequest mirrors JWTEncryptRequestDto.
type JWTEncryptRequest struct {
	ApplicationID string
	ReferenceID   string
	Data          string // base64url-encoded JSON, or an already-signed JWS compact string (3 dot-separated segments)

	// EnableDeflateCompression enables RFC 1951 DEFLATE compression of the
	// JWE payload (Java's enableDefCompression, default true). A *bool, not
	// bool, because the Java default is true and a plain Go bool's zero
	// value can't express "unset, use default" — nil means "use the
	// default (true)"; explicit false disables compression.
	//
	// SECURITY: compressing before encrypting makes ciphertext length a
	// function of plaintext redundancy (the CRIME/BREACH class of attack,
	// see RFC 8725 §3.4). If a caller ever encrypts a payload that mixes
	// attacker-influenced and secret data through this path, leave this
	// explicitly false. The default stays true only to match the Java
	// service's wire behavior for existing MOSIP consumers — this is a
	// deliberately accepted risk, not an oversight; callers that control
	// their own payload shape should default to disabling it.
	EnableDeflateCompression *bool

	// IncludeCertificate embeds the signing certificate chain (x5c header).
	IncludeCertificate bool

	// IncludeCertHash embeds the certificate's SHA-256 thumbprint (x5t#S256 header).
	IncludeCertHash bool

	// JWKSetURL, if set, is embedded as the jku header.
	JWKSetURL string

	// X509Certificate, if set, is a caller-supplied PEM certificate used
	// directly for encryption — bypasses ApplicationID/ReferenceID
	// resolution entirely (no keymanager/DB interaction).
	X509Certificate string

	// ValidateJSON controls whether Data (once base64url-decoded, for the
	// non-JWS case — see isJWSData) must itself be valid JSON before it's
	// JWE-encrypted. true: decode, then validate, then encrypt (rejecting
	// non-JSON payloads with ErrInvalidJSON). false (the zero value):
	// decode, then encrypt the decoded bytes directly, without a JSON
	// check — the caller is responsible for knowing what it's encrypting.
	ValidateJSON bool
}

// JWTCipherResponse mirrors JWTCipherResponseDto, used by both JWTEncrypt
// and JWTDecrypt.
type JWTCipherResponse struct {
	Data      string
	Timestamp time.Time
}

// JWTDecryptRequest mirrors JWTDecryptRequestDto. No CallerID — jwtDecrypt
// performs no authorization check in Java either.
type JWTDecryptRequest struct {
	ApplicationID string
	ReferenceID   string
	EncData       string // compact-serialized JWE
}
