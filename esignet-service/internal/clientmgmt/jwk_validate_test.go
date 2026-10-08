/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt

import (
	"encoding/base64"
	"fmt"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/stretchr/testify/assert"
)

// rsaModulus returns a base64url RSA modulus of exactly bits bits (top bit set).
func rsaModulus(bits int) string {
	b := make([]byte, bits/8)
	b[0] = 0x80
	return base64.RawURLEncoding.EncodeToString(b)
}

var testRSAN = rsaModulus(minRSAKeyBits)

func (ts *JwkValidateTestSuite) TestValidateJWK() {
	t := ts.T()
	t.Run("empty key", func(t *testing.T) {
		assert.Equal(t, "invalid_public_key", errCode(t, validateJWK(nil)))
	})

	t.Run("unsupported kty", func(t *testing.T) {
		assert.Equal(t, "invalid_public_key", errCode(t, validateJWK(map[string]string{"kty": "oct"})))
	})

	t.Run("rsa missing fields", func(t *testing.T) {
		assert.Equal(t, "invalid_public_key", errCode(t, validateJWK(map[string]string{"kty": "RSA"})))
	})

	t.Run("rsa invalid base64", func(t *testing.T) {
		err := validateJWK(map[string]string{"kty": "RSA", "n": "not base64!", "e": "AQAB"})
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	t.Run("rsa valid", func(t *testing.T) {
		assert.NoError(t, validateJWK(map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "kid": "key-1"}))
	})

	t.Run("ec missing fields", func(t *testing.T) {
		assert.Equal(t, "invalid_public_key", errCode(t, validateJWK(map[string]string{"kty": "EC"})))
	})

	t.Run("ec invalid base64 x", func(t *testing.T) {
		err := validateJWK(map[string]string{"kty": "EC", "crv": "P-256", "x": "not base64!", "y": "abc"})
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	t.Run("ec invalid base64 y", func(t *testing.T) {
		err := validateJWK(map[string]string{"kty": "EC", "crv": "P-256", "x": "abc", "y": "not base64!"})
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	t.Run("ec unsupported curve", func(t *testing.T) {
		err := validateJWK(map[string]string{"kty": "EC", "crv": "P-999", "x": "abc", "y": "abc"})
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	for _, curve := range []string{"P-256", "P-384", "P-521"} {
		t.Run("ec valid "+curve, func(t *testing.T) {
			assert.NoError(t, validateJWK(map[string]string{"kty": "EC", "crv": curve, "x": "abc", "y": "abc", "kid": "key-1"}))
		})
	}

	t.Run("missing kid rejected", func(t *testing.T) {
		err := validateJWK(map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB"})
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	t.Run("blank kid rejected", func(t *testing.T) {
		err := validateJWK(map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "kid": ""})
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	t.Run("present kid accepted", func(t *testing.T) {
		assert.NoError(t, validateJWK(map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "kid": "key-1"}))
	})
}

func (ts *JwkValidateTestSuite) TestValidateEncJWK() {
	t := ts.T()
	key := func(alg string) map[string]string {
		return map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "alg": alg, "kid": "enc-1"}
	}

	t.Run("missing alg rejected regardless of supported list", func(t *testing.T) {
		err := validateEncJWK(map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "kid": "enc-1"}, nil)
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	t.Run("missing kid rejected", func(t *testing.T) {
		err := validateEncJWK(map[string]string{"kty": "RSA", "n": testRSAN, "e": "AQAB", "alg": "RSA-OAEP-256"}, nil)
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})

	t.Run("nil supported list leaves alg unrestricted", func(t *testing.T) {
		assert.NoError(t, validateEncJWK(key("RSA-OAEP"), nil))
	})

	t.Run("alg in supported list accepted", func(t *testing.T) {
		assert.NoError(t, validateEncJWK(key("RSA-OAEP-256"), []string{"AES-GCM", "RSA-OAEP-256"}))
	})

	t.Run("alg not in supported list rejected", func(t *testing.T) {
		err := validateEncJWK(key("RSA-OAEP"), []string{"AES-GCM", "RSA-OAEP-256"})
		assert.Equal(t, "invalid_public_key", errCode(t, err))
	})
}

func (ts *JwkValidateTestSuite) TestMarshalJWK() {
	t := ts.T()
	empty, err := marshalJWK(nil)
	assert.NoError(t, err)
	assert.Empty(t, empty)

	got, err := marshalJWK(map[string]string{"kty": "RSA"})
	assert.NoError(t, err)
	assert.JSONEq(t, `{"kty":"RSA"}`, got)
}

func (ts *JwkValidateTestSuite) TestHashJWK() {
	t := ts.T()
	h1 := hashJWK(map[string]string{"kty": "RSA", "n": "abc"})
	h2 := hashJWK(map[string]string{"kty": "RSA", "n": "abc"})
	h3 := hashJWK(map[string]string{"kty": "RSA", "n": "xyz"})
	assert.NotEmpty(t, h1)
	assert.Equal(t, h1, h2)
	assert.NotEqual(t, h1, h3)
}

func (ts *JwkValidateTestSuite) TestRSAKeyBits() {
	t := ts.T()
	for _, bits := range []int{1024, 2048, 4096} {
		t.Run(fmt.Sprintf("rsa %d-bit", bits), func(t *testing.T) {
			got, ok := rsaKeyBits(`{"kty":"RSA","n":"` + rsaModulus(bits) + `","e":"AQAB"}`)
			assert.True(t, ok)
			assert.Equal(t, bits, got)
		})
	}

	for name, jwk := range map[string]string{
		"invalid json":    `not json`,
		"ec key":          `{"kty":"EC","crv":"P-256","x":"AA","y":"AA"}`,
		"missing modulus": `{"kty":"RSA","e":"AQAB"}`,
		"invalid modulus": `{"kty":"RSA","n":"not base64!","e":"AQAB"}`,
		"non-string n":    `{"kty":"RSA","n":123,"e":"AQAB"}`,
	} {
		t.Run(name, func(t *testing.T) {
			_, ok := rsaKeyBits(jwk)
			assert.False(t, ok)
		})
	}
}

type JwkValidateTestSuite struct {
	suite.Suite
}

func TestJwkValidateTestSuite(t *testing.T) {
	suite.Run(t, new(JwkValidateTestSuite))
}
