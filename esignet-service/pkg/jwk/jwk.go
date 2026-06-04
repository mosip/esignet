// Package jwk parses JWK material and computes the public-key hash stored
// in the `client_detail.public_key_hash` column: hex-encoded SHA-256 of the
// JWK's defining material.
package jwk

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/lestrrat-go/jwx/v2/jwk"
)

// ErrUnsupportedKeyType is returned when the JWK's `kty` is neither RSA nor EC.
var ErrUnsupportedKeyType = errors.New("unsupported jwk key type")

// Parse parses raw JSON bytes into a JWK. The raw input must be a single JWK
// object (not a JWK Set). Returns a wrapped error when the bytes are not a
// well-formed JWK.
func Parse(raw json.RawMessage) (jwk.Key, error) {
	if len(raw) == 0 {
		return nil, fmt.Errorf("jwk: empty input")
	}
	key, err := jwk.ParseKey(raw)
	if err != nil {
		return nil, fmt.Errorf("jwk: parse: %w", err)
	}
	return key, nil
}

// ComputeHash returns the lowercase hex SHA-256 of the JWK's key material.
// Hash domain:
//
//   - RSA → SHA-256 of n (base64url string, no padding)
//   - EC  → SHA-256 of x||y (concatenated base64url strings)
//   - other kty → ErrUnsupportedKeyType
//
// Output is 64 lowercase hex characters (32-byte digest).
func ComputeHash(k jwk.Key) (string, error) {
	if k == nil {
		return "", fmt.Errorf("jwk: nil key")
	}

	raw, err := jwkRawFields(k)
	if err != nil {
		return "", err
	}

	switch raw.Kty {
	case "RSA":
		if raw.N == "" {
			return "", fmt.Errorf("jwk: RSA key missing n")
		}
		return sha256Hex(raw.N), nil
	case "EC":
		if raw.X == "" || raw.Y == "" {
			return "", fmt.Errorf("jwk: EC key missing x or y")
		}
		return sha256Hex(raw.X + raw.Y), nil
	default:
		return "", fmt.Errorf("jwk: %w: %q", ErrUnsupportedKeyType, raw.Kty)
	}
}

// rawJWK is the minimal projection used for hashing. We round-trip through
// JSON so we read the original base64url-no-pad strings — avoids re-encoding
// drift from jwk.Key's typed (decoded byte) accessors.
type rawJWK struct {
	Kty string `json:"kty"`
	N   string `json:"n,omitempty"`
	X   string `json:"x,omitempty"`
	Y   string `json:"y,omitempty"`
}

func jwkRawFields(k jwk.Key) (rawJWK, error) {
	buf, err := json.Marshal(k)
	if err != nil {
		return rawJWK{}, fmt.Errorf("jwk: marshal: %w", err)
	}
	var r rawJWK
	if err := json.Unmarshal(buf, &r); err != nil {
		return rawJWK{}, fmt.Errorf("jwk: unmarshal: %w", err)
	}
	if r.Kty == "" {
		return rawJWK{}, fmt.Errorf("jwk: missing kty")
	}
	return r, nil
}

func sha256Hex(s string) string {
	sum := sha256.Sum256([]byte(s))
	return hex.EncodeToString(sum[:])
}
