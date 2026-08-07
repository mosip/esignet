package keymanager_test

import (
	"crypto/sha256"
	"encoding/pem"
	"testing"

	"github.com/stretchr/testify/require"
	"golang.org/x/crypto/cryptobyte"
	cryptobyte_asn1 "golang.org/x/crypto/cryptobyte/asn1"

	"github.com/mosip/esignet/internal/keymanager"
)

// negativeSerialCertPEM is a real partner-uploaded certificate whose ASN.1
// serial number is encoded without the leading 0x00 padding byte DER
// requires when the magnitude's top bit is set — making it a negative
// two's-complement INTEGER per the spec. OpenSSL and Java parse it (and
// print/return a negative serial); Go's crypto/x509 rejects it outright with
// "x509: negative serial number". parseCertPEM/ParseCertPEM must tolerate
// this instead of failing the upload.
const negativeSerialCertPEM = `-----BEGIN CERTIFICATE-----
MIIDwDCCAqigAwIBAgII3/XjDTHdq18wDQYJKoZIhvcNAQELBQAwdjELMAkGA1UE
BhMCSU4xCzAJBgNVBAgMAktBMRIwEAYDVQQHDAlCQU5HQUxPUkUxDTALBgNVBAoM
BElJVEIxIDAeBgNVBAsMF01PU0lQLVRFQ0gtQ0VOVEVSIChQTVMpMRUwEwYDVQQD
DAx3d3cubW9zaXAuaW8wHhcNMjYwODA3MTQyMjAyWhcNMjcwODA3MTQyMjAyWjCB
hTELMAkGA1UEBhMCSU4xCzAJBgNVBAgMAktBMRIwEAYDVQQHDAlCYW5nYWxvcmUx
DjAMBgNVBAoMBUlJSVRCMRYwFAYDVQQLDA1tb3NpcC1lc2lnbmV0MS0wKwYDVQQD
DCR3d3cubW9zaXAuaW8gKE9JRENfUEFSVE5FUl9SU0FfMjA0OCkwggEiMA0GCSqG
SIb3DQEBAQUAA4IBDwAwggEKAoIBAQC7GhEiTWZljEfny+EEx3JqLNQcVKyf7G+Z
wVhq5a++gPBNpGQS072Z6SEIpC8jmYeeulT2DVZhvc8qKr+Uftq5DJPCZ5ybr5XR
zV9a7oPYB6qKKq21F/W4yhlIuwxxxXhRkJ+DZ8bAsjOgxxbcQfOdQIG5RYIQcH2q
xs0+tgwYsZZOTzSk41N6DLs5iOzM6qtbcDaLXlpSEsc6G3/e+MUQUbH3QFpfOCO4
Nx6DcQLcrQC7lOHFfoucTDkpZraEbWkNipTyfOq55xMW6k8UYMgWZcmfK1zlNKDw
AcTm32wMzp4rScRHok4s7zQAid3b6ZaW0h47V2D5EKCeQVtiHI4jAgMBAAGjQjBA
MA8GA1UdEwEB/wQFMAMBAf8wHQYDVR0OBBYEFIFAz7DgRrwgwfcAmNlMr9q02WO7
MA4GA1UdDwEB/wQEAwIChDANBgkqhkiG9w0BAQsFAAOCAQEAM0+3n5/7sW/pnur9
cNZjUij2NQspCZEkv5Xq5kxbeOKQsKt7EFaludxyNkvXsmpuJ2p18RdLYmu2mkkF
i7zDrtuDpdjpbsGLsrsokwj0Wt7lmIuSqGro4QD3xfST01bBF2h5Hh/DZl0UI4qG
mZAWNgmZndxDTnKdlTqb28phXuOPL6vSTGzveDmwEXpPKcS1JbrQOEseZTOHMYuV
lLva3ufHTkOLCkU1YJTr9rex+R+h9+2b0H2JDAgRvgKZ55smUhJ3nUPueX73A88T
wBGpbi7Lh3H9PoowJ+5/S2WUJ5X88psNl3xXJNoZDAoWCUu+0ZvcJpbylfLz0ISQ
aMPW9g==
-----END CERTIFICATE-----`

func TestParseCertPEM_ToleratesNegativeSerialNumber(t *testing.T) {
	block, _ := pem.Decode([]byte(negativeSerialCertPEM))
	require.NotNil(t, block)
	wantSum := sha256.Sum256(block.Bytes)

	cert, err := keymanager.ParseCertPEM(negativeSerialCertPEM)
	require.NoError(t, err)
	require.Equal(t, "www.mosip.io (OIDC_PARTNER_RSA_2048)", cert.Subject.CommonName)

	// Raw must be exactly the original uploaded bytes — thumbprinting
	// (sha256 of Raw) and stored certificate_data both depend on this, and
	// must match what a Java peer computes from the same bytes.
	gotSum := sha256.Sum256(cert.Raw)
	require.Equal(t, wantSum, gotSum, "cert.Raw must equal the original DER, not a re-encoded copy")

	// RawTBSCertificate must be the original signed bytes (with the
	// unpadded serial), not the re-encoded ones — CheckSignatureFrom hashes
	// RawTBSCertificate, and it must match what the CA actually signed.
	wantTBS := extractTBSCertificateForTest(t, block.Bytes)
	require.Equal(t, wantTBS, cert.RawTBSCertificate, "RawTBSCertificate must equal the original TBSCertificate DER, not a re-encoded copy")
}

// extractTBSCertificateForTest independently extracts the tbsCertificate
// SEQUENCE's raw DER bytes from a certificate, mirroring what a correct
// implementation must preserve after re-encoding the serial number.
func extractTBSCertificateForTest(t *testing.T, der []byte) []byte {
	t.Helper()
	input := cryptobyte.String(der)
	var certSeq cryptobyte.String
	require.True(t, input.ReadASN1(&certSeq, cryptobyte_asn1.SEQUENCE))
	var tbs cryptobyte.String
	require.True(t, certSeq.ReadASN1Element(&tbs, cryptobyte_asn1.SEQUENCE))
	return tbs
}
