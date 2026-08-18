/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package keystore

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/x509"
	"crypto/x509/pkix"
	"math/big"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"golang.org/x/crypto/cryptobyte"
	cryptobyte_asn1 "golang.org/x/crypto/cryptobyte/asn1"
)

// selfSignedDER builds a minimal self-signed certificate DER-encoded with
// the given serial number, using Go's own (correct) ASN.1 encoder.
func selfSignedDER(t *testing.T, serial *big.Int) []byte {
	t.Helper()

	priv, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	require.NoError(t, err)

	tmpl := &x509.Certificate{
		SerialNumber: serial,
		Subject:      pkix.Name{CommonName: "test"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(time.Hour),
		KeyUsage:     x509.KeyUsageDigitalSignature,
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &priv.PublicKey, priv)
	require.NoError(t, err)
	return der
}

// stripSerialPadding rewrites a valid certificate's DER so the
// TBSCertificate serialNumber INTEGER drops its leading 0x00 padding byte —
// reproducing the malformed (but real-world) encoding some non-Go tooling
// emits for a serial whose top content bit is set. This is the exact
// inverse of certparse.go's reencodeNonNegativeSerial.
func stripSerialPadding(t *testing.T, der []byte) []byte {
	t.Helper()

	input := cryptobyte.String(der)
	var certSeq cryptobyte.String
	require.True(t, input.ReadASN1(&certSeq, cryptobyte_asn1.SEQUENCE))

	var tbs cryptobyte.String
	require.True(t, certSeq.ReadASN1(&tbs, cryptobyte_asn1.SEQUENCE))
	// certSeq now holds signatureAlgorithm + signatureValue, copied through
	// unchanged; ParseCertificateTolerant doesn't verify the signature.

	versionTag := cryptobyte_asn1.Tag(0).Constructed().ContextSpecific()
	var versionContent cryptobyte.String
	var hasVersion bool
	require.True(t, tbs.ReadOptionalASN1(&versionContent, &hasVersion, versionTag))

	var serialContent cryptobyte.String
	require.True(t, tbs.ReadASN1(&serialContent, cryptobyte_asn1.INTEGER))
	require.Len(t, serialContent, 2, "test fixture must produce a 1-byte serial padded to 2 bytes")
	require.Equal(t, byte(0x00), serialContent[0])
	require.NotZero(t, serialContent[1]&0x80, "second byte must have its top bit set to trigger the negative-serial path")
	stripped := serialContent[1:]

	var out cryptobyte.Builder
	out.AddASN1(cryptobyte_asn1.SEQUENCE, func(b *cryptobyte.Builder) {
		b.AddASN1(cryptobyte_asn1.SEQUENCE, func(b *cryptobyte.Builder) {
			if hasVersion {
				b.AddASN1(versionTag, func(b *cryptobyte.Builder) {
					b.AddBytes(versionContent)
				})
			}
			b.AddASN1(cryptobyte_asn1.INTEGER, func(b *cryptobyte.Builder) {
				b.AddBytes(stripped)
			})
			b.AddBytes(tbs)
		})
		b.AddBytes(certSeq)
	})
	malformed, err := out.Bytes()
	require.NoError(t, err)
	return malformed
}

type CertParseTestSuite struct {
	suite.Suite
}

func TestCertParseTestSuite(t *testing.T) {
	suite.Run(t, new(CertParseTestSuite))
}

func (ts *CertParseTestSuite) TestParseCertificateTolerant_WellFormedCertificate() {
	der := selfSignedDER(ts.T(), big.NewInt(42))

	got, err := ParseCertificateTolerant(der)
	require.NoError(ts.T(), err)

	want, err := x509.ParseCertificate(der)
	require.NoError(ts.T(), err)

	assert.Equal(ts.T(), want.SerialNumber, got.SerialNumber)
	assert.Equal(ts.T(), want.Raw, got.Raw)
	assert.Equal(ts.T(), want.RawTBSCertificate, got.RawTBSCertificate)
}

func (ts *CertParseTestSuite) TestParseCertificateTolerant_NegativeSerialNumber() {
	// Serial 0x80 (128) is DER-encoded by Go as [0x00, 0x80] (padded, since
	// the top bit of 0x80 is set); stripping the padding reproduces the
	// malformed two's-complement encoding this function tolerates.
	valid := selfSignedDER(ts.T(), big.NewInt(0x80))
	malformed := stripSerialPadding(ts.T(), valid)

	_, err := x509.ParseCertificate(malformed)
	require.EqualError(ts.T(), err, "x509: negative serial number", "test fixture must reproduce stdlib's rejection")

	got, err := ParseCertificateTolerant(malformed)
	require.NoError(ts.T(), err)

	assert.Equal(ts.T(), big.NewInt(0x80), got.SerialNumber, "original serial magnitude must be recovered")
	assert.Equal(ts.T(), malformed, got.Raw, "Raw must be the original malformed input, unmodified")

	wantTBS, err := extractTBSCertificate(malformed)
	require.NoError(ts.T(), err)
	assert.Equal(ts.T(), wantTBS, got.RawTBSCertificate)
}

func (ts *CertParseTestSuite) TestParseCertificateTolerant_OtherErrorsPassThrough() {
	input := []byte("not a certificate")
	_, wantErr := x509.ParseCertificate(input)
	require.Error(ts.T(), wantErr)

	_, err := ParseCertificateTolerant(input)
	require.EqualError(ts.T(), err, wantErr.Error())
}
