package clientmgmt

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"strings"
)

func validateJWK(key map[string]string) error {
	if len(key) == 0 {
		return validationErr("invalid_public_key")
	}
	kty := key["kty"]
	switch kty {
	case "RSA":
		if key["n"] == "" || key["e"] == "" {
			return validationErr("invalid_public_key")
		}
		if _, err := decodeBase64URL(key["n"]); err != nil {
			return validationErr("invalid_public_key")
		}
		if _, err := decodeBase64URL(key["e"]); err != nil {
			return validationErr("invalid_public_key")
		}
	case "EC":
		if key["crv"] == "" || key["x"] == "" || key["y"] == "" {
			return validationErr("invalid_public_key")
		}
		if _, err := decodeBase64URL(key["x"]); err != nil {
			return validationErr("invalid_public_key")
		}
		if _, err := decodeBase64URL(key["y"]); err != nil {
			return validationErr("invalid_public_key")
		}
		switch key["crv"] {
		case "P-256", "P-384", "P-521":
		default:
			return validationErr("invalid_public_key")
		}
	default:
		return validationErr("invalid_public_key")
	}
	return nil
}

func decodeBase64URL(s string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(s)
}

func marshalJWK(m map[string]string) (string, error) {
	if len(m) == 0 {
		return "", nil
	}
	b, err := json.Marshal(m)
	if err != nil {
		return "", err
	}
	return string(b), nil
}

func hashJWK(key map[string]string) string {
	b, err := json.Marshal(key)
	if err != nil {
		return ""
	}
	sum := sha256.Sum256(b)
	return fmt.Sprintf("%x", sum)
}

func isValidURI(uri string) bool {
	if uri == "" {
		return false
	}
	if strings.HasPrefix(uri, "http://") || strings.HasPrefix(uri, "https://") {
		return len(uri) <= 1024
	}
	if strings.Contains(uri, "://") {
		return len(uri) <= 1024
	}
	return false
}

func hasUniqueStrings(items []string) bool {
	seen := make(map[string]struct{}, len(items))
	for _, item := range items {
		if _, ok := seen[item]; ok {
			return false
		}
		seen[item] = struct{}{}
	}
	return true
}

func containsAll(items []string, allowed map[string]struct{}) bool {
	for _, item := range items {
		if _, ok := allowed[item]; !ok {
			return false
		}
	}
	return true
}
