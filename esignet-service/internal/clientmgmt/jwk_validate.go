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
	"fmt"
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
