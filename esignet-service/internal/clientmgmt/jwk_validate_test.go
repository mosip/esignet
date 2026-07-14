/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt

import (
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/stretchr/testify/assert"
)

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
		assert.NoError(t, validateJWK(map[string]string{"kty": "RSA", "n": "abc", "e": "AQAB"}))
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
			assert.NoError(t, validateJWK(map[string]string{"kty": "EC", "crv": curve, "x": "abc", "y": "abc"}))
		})
	}
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

type JwkValidateTestSuite struct {
	suite.Suite
}

func TestJwkValidateTestSuite(t *testing.T) {
	suite.Run(t, new(JwkValidateTestSuite))
}
