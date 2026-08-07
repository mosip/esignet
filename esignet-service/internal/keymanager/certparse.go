package keymanager

import (
	"crypto/x509"
	"errors"
	"math/big"

	"golang.org/x/crypto/cryptobyte"
	cryptobyte_asn1 "golang.org/x/crypto/cryptobyte/asn1"
)

// parseCertificateTolerant parses a DER-encoded certificate like
// x509.ParseCertificate, but also accepts certificates whose serial number
// is DER-encoded as a negative two's-complement INTEGER (top bit of the
// first content byte set, no leading 0x00 padding). RFC 5280 §4.1.2.2
// requires a non-negative serial, and Go's stdlib rejects the malformed
// encoding outright ("x509: negative serial number"), but OpenSSL and Java
// both parse it leniently, and some CA tooling (including at least one
// MOSIP partner cert we've seen in the wild) still produces it. The
// returned certificate's Raw is always the original, unmodified input, so
// callers hashing/storing Raw (cert_thumbprint, key_store.certificate_data)
// see exactly the bytes that were uploaded.
func parseCertificateTolerant(der []byte) (*x509.Certificate, error) {
	cert, err := x509.ParseCertificate(der)
	if err == nil || err.Error() != "x509: negative serial number" {
		return cert, err
	}
	fixed, fixErr := reencodeNonNegativeSerial(der)
	if fixErr != nil {
		return nil, err // fix-up failed; surface the original stdlib error
	}
	cert, err = x509.ParseCertificate(fixed)
	if err != nil {
		return nil, err
	}
	cert.Raw = der
	return cert, nil
}

// reencodeNonNegativeSerial rebuilds a certificate's DER with its
// TBSCertificate serialNumber re-encoded as a non-negative INTEGER, treating
// the original (malformed) content bytes as an unsigned magnitude — i.e.
// adding the DER-mandated 0x00 padding byte a correct encoder would have
// written. Every other field is copied through byte-for-byte.
func reencodeNonNegativeSerial(der []byte) ([]byte, error) {
	input := cryptobyte.String(der)
	var certSeq cryptobyte.String
	if !input.ReadASN1(&certSeq, cryptobyte_asn1.SEQUENCE) {
		return nil, errors.New("malformed certificate")
	}

	var tbs cryptobyte.String
	if !certSeq.ReadASN1(&tbs, cryptobyte_asn1.SEQUENCE) {
		return nil, errors.New("malformed tbs certificate")
	}
	// certSeq now holds exactly signatureAlgorithm + signatureValue.

	versionTag := cryptobyte_asn1.Tag(0).Constructed().ContextSpecific()
	var versionContent cryptobyte.String
	var hasVersion bool
	if !tbs.ReadOptionalASN1(&versionContent, &hasVersion, versionTag) {
		return nil, errors.New("malformed version")
	}

	var serialContent cryptobyte.String
	if !tbs.ReadASN1(&serialContent, cryptobyte_asn1.INTEGER) {
		return nil, errors.New("malformed serial number")
	}
	serial := new(big.Int).SetBytes(serialContent)
	// tbs now holds every TBSCertificate field after serialNumber.

	var out cryptobyte.Builder
	out.AddASN1(cryptobyte_asn1.SEQUENCE, func(b *cryptobyte.Builder) {
		b.AddASN1(cryptobyte_asn1.SEQUENCE, func(b *cryptobyte.Builder) {
			if hasVersion {
				b.AddASN1(versionTag, func(b *cryptobyte.Builder) {
					b.AddBytes(versionContent)
				})
			}
			b.AddASN1BigInt(serial)
			b.AddBytes(tbs)
		})
		b.AddBytes(certSeq)
	})
	return out.Bytes()
}
