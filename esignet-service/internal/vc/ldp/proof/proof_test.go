/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package proof

import (
	"encoding/base64"
	"encoding/json"
	"slices"
	"testing"

	"github.com/mr-tron/base58"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/vc/ldp/sig"
)

type ProofTestSuite struct {
	suite.Suite
}

func TestProofTestSuite(t *testing.T) {
	suite.Run(t, new(ProofTestSuite))
}

func jwsHeader(h map[string]any) string {
	b, _ := json.Marshal(h)
	return base64.RawURLEncoding.EncodeToString(b)
}

func (ts *ProofTestSuite) TestDetachedJWS() {
	sig64 := base64.RawURLEncoding.EncodeToString(make([]byte, 64))
	good := jwsHeader(map[string]any{"alg": "EdDSA", "b64": false, "crit": []string{"b64"}})

	j, err := parseDetachedJWS(good + ".." + sig64)
	ts.Require().NoError(err)
	ts.Equal("EdDSA", j.alg)
	ts.Equal([]byte(good+".payload"), j.signingInput([]byte("payload")))

	for name, jws := range map[string]string{
		"b64 absent":   jwsHeader(map[string]any{"alg": "EdDSA", "crit": []string{"b64"}}) + ".." + sig64,
		"b64 true":     jwsHeader(map[string]any{"alg": "EdDSA", "b64": true, "crit": []string{"b64"}}) + ".." + sig64,
		"crit absent":  jwsHeader(map[string]any{"alg": "EdDSA", "b64": false}) + ".." + sig64,
		"crit extra":   jwsHeader(map[string]any{"alg": "EdDSA", "b64": false, "crit": []string{"b64", "exp"}}) + ".." + sig64,
		"two segments": good + "." + sig64,
		"payload":      good + ".hello." + sig64,
		"bad header":   "!!.." + sig64,
		"bad sig":      good + "..!!",
	} {
		_, err := parseDetachedJWS(jws)
		ts.ErrorIs(err, ErrMalformed, name)
	}
}

func (ts *ProofTestSuite) TestProofValue() {
	raw := make([]byte, 64)
	b, err := decodeProofValue("z" + base58.Encode(raw))
	ts.Require().NoError(err)
	ts.Len(b, 64)

	_, err = decodeProofValue("u" + base64.RawURLEncoding.EncodeToString(raw))
	ts.NoError(err, "base64url multibase is accepted")
	_, err = decodeProofValue("")
	ts.ErrorIs(err, ErrMalformed)
	_, err = decodeProofValue("m" + base64.StdEncoding.EncodeToString(raw))
	ts.ErrorIs(err, ErrMalformed)
}

func (ts *ProofTestSuite) TestParse() {
	pv := "z" + base58.Encode(make([]byte, 64))
	jws := jwsHeader(map[string]any{"alg": "ES256", "b64": false, "crit": []string{"b64"}}) + ".." +
		base64.RawURLEncoding.EncodeToString(make([]byte, 64))
	all := slices.Concat(DefaultVCSuites(), []string{JSONWebSignature2020})
	vm := "did:key:z6MkpiJgQdNWUzyojaFuCzQ1MWvSSaxUfL1tvbcRfqWFoJRK#z6MkpiJgQdNWUzyojaFuCzQ1MWvSSaxUfL1tvbcRfqWFoJRK"

	p, err := Parse(map[string]any{"type": EcdsaSecp256r1Signature2019, "verificationMethod": vm, "proofValue": pv}, all)
	ts.Require().NoError(err)
	ts.Equal(EcdsaSecp256r1Signature2019, p.Suite)
	ts.Equal(sig.ES256, p.Alg)

	p, err = Parse(map[string]any{"type": JSONWebSignature2020, "verificationMethod": vm, "jws": jws}, all)
	ts.Require().NoError(err)
	ts.Equal(sig.ES256, p.Alg)

	for name, c := range map[string]struct {
		proof   map[string]any
		allowed []string
		err     error
	}{
		"no type":                {map[string]any{"verificationMethod": vm, "proofValue": pv}, all, ErrMalformed},
		"unknown type":           {map[string]any{"type": "BbsBlsSignature2020", "verificationMethod": vm, "proofValue": pv}, all, ErrUnsupportedSuite},
		"data integrity":         {map[string]any{"type": "DataIntegrityProof", "cryptosuite": "eddsa-rdfc-2022", "verificationMethod": vm, "proofValue": pv}, all, ErrUnsupportedSuite},
		"not allowed":            {map[string]any{"type": RsaSignature2018, "verificationMethod": vm, "proofValue": pv}, DefaultVPSuites(), ErrUnsupportedSuite},
		"no verificationMethod":  {map[string]any{"type": Ed25519Signature2020, "proofValue": pv}, all, ErrMalformed},
		"jws and proofValue":     {map[string]any{"type": EcdsaSecp256r1Signature2019, "verificationMethod": vm, "proofValue": pv, "jws": jws}, all, ErrMalformed},
		"no signature":           {map[string]any{"type": Ed25519Signature2020, "verificationMethod": vm}, all, ErrMalformed},
		"jws on proofValue-only": {map[string]any{"type": Ed25519Signature2020, "verificationMethod": vm, "jws": jws}, all, ErrMalformed},
		"proofValue on jws-only": {map[string]any{"type": Ed25519Signature2018, "verificationMethod": vm, "proofValue": pv}, all, ErrMalformed},
		"alg not in suite":       {map[string]any{"type": Ed25519Signature2018, "verificationMethod": vm, "jws": jws}, all, ErrUnsupportedAlg},
	} {
		_, err := Parse(c.proof, c.allowed)
		ts.ErrorIs(err, c.err, name)
	}
}

func (ts *ProofTestSuite) TestDefaultSuites() {
	ts.NotContains(DefaultVPSuites(), RsaSignature2018)
	ts.Contains(DefaultVPSuites(), JSONWebSignature2020)
	ts.NotContains(DefaultVCSuites(), JSONWebSignature2020)
	ts.Contains(DefaultVCSuites(), RsaSignature2018)
	ts.True(slices.IsSorted(DefaultVCSuites()))

	vc := DefaultVCSuites()
	vc[0] = "changed"
	ts.NotEqual("changed", DefaultVCSuites()[0], "callers get a copy")

	ts.True(Supported(JSONWebSignature2020))
	ts.False(Supported("DataIntegrityProof"))
	ts.False(Supported("eddsa-rdfc-2022"))
}
