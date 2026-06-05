package client

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/lestrrat-go/jwx/v2/jwk"
)

// errUnsupportedKeyType is returned when the JWK's `kty` is neither RSA nor EC.
var errUnsupportedKeyType = errors.New("unsupported jwk key type")

// parseJWK parses raw JSON bytes into a JWK. The input must be a single JWK
// object (not a JWK Set). Returns a wrapped error when the bytes are not a
// well-formed JWK.
func parseJWK(raw json.RawMessage) (jwk.Key, error) {
	if len(raw) == 0 {
		return nil, fmt.Errorf("jwk: empty input")
	}
	key, err := jwk.ParseKey(raw)
	if err != nil {
		return nil, fmt.Errorf("jwk: parse: %w", err)
	}
	return key, nil
}

// computeJWKHash returns the lowercase hex SHA-256 of the JWK's key material,
// the value stored in `client_detail.public_key_hash`. Hash domain:
//
//   - RSA → SHA-256 of n (base64url string, no padding)
//   - EC  → SHA-256 of x||y (concatenated base64url strings)
//   - other kty → errUnsupportedKeyType
//
// Output is 64 lowercase hex characters (32-byte digest).
func computeJWKHash(k jwk.Key) (string, error) {
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
		return "", fmt.Errorf("jwk: %w: %q", errUnsupportedKeyType, raw.Kty)
	}
}

// rawJWK is the minimal projection used for hashing. Round-tripping through
// JSON lets us read the original base64url-no-pad strings — avoids re-encoding
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
