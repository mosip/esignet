/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"slices"
)

// minRSAKeyBits is the smallest RSA modulus accepted for a client JWK.
const minRSAKeyBits = 2048

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
		bits, err := rsaModulusBits(key["n"])
		if err != nil || bits < minRSAKeyBits {
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
	if key["kid"] == "" {
		return validationErr("invalid_public_key")
	}
	return nil
}

// validateEncJWK validates an encryption key JWK, additionally requiring the
// alg field so the JWE key-management algorithm is always known, and — when
// supportedAlgs is non-empty — that alg is one of the configured supported
// encryption algorithms (config.AppConfig.SupportedEncAlgorithms). A non-empty
// "kid" is mandatory: when a client maintains multiple encryption keys, the
// "kid" in the JWE header tells it which key to decrypt the response with.
func validateEncJWK(key map[string]string, supportedAlgs []string) error {
	if err := validateJWK(key); err != nil {
		return err
	}
	alg := key["alg"]
	if alg == "" {
		return validationErr("invalid_public_key")
	}
	if len(supportedAlgs) > 0 && !slices.Contains(supportedAlgs, alg) {
		return validationErr("invalid_public_key")
	}
	return nil
}

func decodeBase64URL(s string) ([]byte, error) {
	return base64.RawURLEncoding.DecodeString(s)
}

// rsaModulusBits decodes a base64url RSA modulus ("n") and returns its bit
// length. Both registration (validateJWK) and the weak-key audit of stored
// clients (rsaKeyBits) size keys through it, so they agree on what counts as
// below minRSAKeyBits.
func rsaModulusBits(n string) (int, error) {
	b, err := decodeBase64URL(n)
	if err != nil {
		return 0, err
	}
	if len(b) == 0 {
		return 0, errors.New("empty RSA modulus")
	}
	return new(big.Int).SetBytes(b).BitLen(), nil
}

// rsaKeyBits returns the RSA modulus size of a stored JWK JSON string. ok is
// false when the value is not an RSA JWK or its modulus can't be decoded.
func rsaKeyBits(jwkJSON string) (bits int, ok bool) {
	var key map[string]any
	if err := json.Unmarshal([]byte(jwkJSON), &key); err != nil || key["kty"] != "RSA" {
		return 0, false
	}
	n, _ := key["n"].(string)
	bits, err := rsaModulusBits(n)
	if err != nil {
		return 0, false
	}
	return bits, true
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
