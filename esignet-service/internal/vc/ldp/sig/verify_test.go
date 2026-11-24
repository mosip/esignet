/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package sig

import (
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"math/big"
	"testing"

	"github.com/btcsuite/btcd/btcec/v2"
	btcecdsa "github.com/btcsuite/btcd/btcec/v2/ecdsa"
	"github.com/stretchr/testify/suite"
)

type SigTestSuite struct {
	suite.Suite
	msg []byte
}

func TestSigTestSuite(t *testing.T) {
	suite.Run(t, new(SigTestSuite))
}

func (ts *SigTestSuite) SetupTest() {
	ts.msg = []byte("hash data")
}

func (ts *SigTestSuite) TestEdDSA() {
	pub, priv, err := ed25519.GenerateKey(rand.Reader)
	ts.Require().NoError(err)
	signature := ed25519.Sign(priv, ts.msg)
	ts.NoError(Verify(EdDSA, pub, ts.msg, signature))
	ts.ErrorIs(Verify(EdDSA, pub, []byte("other"), signature), ErrInvalidSignature)
}

func (ts *SigTestSuite) TestES256() {
	priv, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	ts.Require().NoError(err)
	h := sha256.Sum256(ts.msg)
	r, s, err := ecdsa.Sign(rand.Reader, priv, h[:])
	ts.Require().NoError(err)
	signature := make([]byte, 64)
	r.FillBytes(signature[:32])
	s.FillBytes(signature[32:])
	ts.NoError(Verify(ES256, &priv.PublicKey, ts.msg, signature))
	ts.ErrorIs(Verify(ES256, &priv.PublicKey, ts.msg, signature[:63]), ErrInvalidSignature)

	p384, err := ecdsa.GenerateKey(elliptic.P384(), rand.Reader)
	ts.Require().NoError(err)
	ts.ErrorIs(Verify(ES256, &p384.PublicKey, ts.msg, signature), ErrKeyMismatch)
}

// High-S secp256k1 signatures verify.
func (ts *SigTestSuite) TestES256K() {
	priv, err := btcec.NewPrivateKey()
	ts.Require().NoError(err)
	h := sha256.Sum256(ts.msg)
	sig := btcecdsa.Sign(priv, h[:])
	r, s := sig.R(), sig.S()
	signature := make([]byte, 64)
	r.PutBytesUnchecked(signature[:32])
	s.PutBytesUnchecked(signature[32:])
	ts.NoError(Verify(ES256K, priv.PubKey(), ts.msg, signature))

	highS := new(btcec.ModNScalar).NegateVal(&s)
	highS.PutBytesUnchecked(signature[32:])
	ts.NoError(Verify(ES256K, priv.PubKey(), ts.msg, signature))

	for i := range 32 {
		signature[i] = 0xff // r >= N
	}
	ts.ErrorIs(Verify(ES256K, priv.PubKey(), ts.msg, signature), ErrInvalidSignature)
}

func (ts *SigTestSuite) TestRSA() {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	ts.Require().NoError(err)
	h := sha256.Sum256(ts.msg)

	pkcs1, err := rsa.SignPKCS1v15(rand.Reader, priv, crypto.SHA256, h[:])
	ts.Require().NoError(err)
	ts.NoError(Verify(RS256, &priv.PublicKey, ts.msg, pkcs1))
	ts.ErrorIs(Verify(PS256, &priv.PublicKey, ts.msg, pkcs1), ErrInvalidSignature)

	pss, err := rsa.SignPSS(rand.Reader, priv, crypto.SHA256, h[:], &rsa.PSSOptions{SaltLength: 32})
	ts.Require().NoError(err)
	ts.NoError(Verify(PS256, &priv.PublicKey, ts.msg, pss))

	small, err := rsa.GenerateKey(rand.Reader, 1024)
	ts.Require().NoError(err)
	ts.ErrorIs(CheckKey(RS256, &small.PublicKey), ErrKeyMismatch)
	// 2047 bits still rounds up to 256 bytes.
	short := &rsa.PublicKey{N: new(big.Int).Lsh(big.NewInt(1), 2046), E: 65537}
	ts.Equal(256, short.Size())
	ts.ErrorIs(CheckKey(RS256, short), ErrKeyMismatch)
	ts.ErrorIs(CheckKey(PS256, short), ErrKeyMismatch)
}

func (ts *SigTestSuite) TestKeyMismatchAndUnknownAlg() {
	pub, _, err := ed25519.GenerateKey(rand.Reader)
	ts.Require().NoError(err)
	for _, alg := range []string{ES256, ES256K, RS256, PS256} {
		ts.ErrorIs(Verify(alg, pub, ts.msg, nil), ErrKeyMismatch, alg)
	}
	ts.ErrorIs(Verify("HS256", pub, ts.msg, nil), ErrUnsupportedAlg)
}
