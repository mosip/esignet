package signature

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"crypto/x509/pkix"
	"encoding/base64"
	"encoding/hex"
	"encoding/json"
	"math/big"
	"testing"
	"time"
)

func testLeafCert(t *testing.T) *x509.Certificate {
	t.Helper()
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	tmpl := &x509.Certificate{
		SerialNumber: big.NewInt(1),
		Subject:      pkix.Name{CommonName: "test"},
		NotBefore:    time.Now().Add(-time.Hour),
		NotAfter:     time.Now().Add(time.Hour),
	}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, &priv.PublicKey, priv)
	if err != nil {
		t.Fatalf("create certificate: %v", err)
	}
	cert, err := x509.ParseCertificate(der)
	if err != nil {
		t.Fatalf("parse certificate: %v", err)
	}
	return cert
}

func decodeHeader(t *testing.T, headerB64 string) jwsHeader {
	t.Helper()
	raw, err := base64.RawURLEncoding.DecodeString(headerB64)
	if err != nil {
		t.Fatalf("decode header base64url: %v", err)
	}
	var h jwsHeader
	if err := json.Unmarshal(raw, &h); err != nil {
		t.Fatalf("unmarshal header JSON: %v", err)
	}
	return h
}

func TestBuildHeader_MinimalFlags(t *testing.T) {
	headerB64, err := buildHeader(headerParams{signAlgorithm: algPS256, b64: true})
	if err != nil {
		t.Fatalf("buildHeader: %v", err)
	}
	h := decodeHeader(t, headerB64)
	if h.Alg != algPS256 {
		t.Errorf("alg = %q, want %q", h.Alg, algPS256)
	}
	if h.B64 != nil || h.Crit != nil || h.X5C != nil || h.X5TS256 != "" || h.X5U != "" || h.Kid != "" {
		t.Errorf("expected only alg to be set, got %+v", h)
	}
}

func TestBuildHeader_B64False_SetsCriticalParam(t *testing.T) {
	headerB64, err := buildHeader(headerParams{signAlgorithm: algES256, b64: false})
	if err != nil {
		t.Fatalf("buildHeader: %v", err)
	}
	h := decodeHeader(t, headerB64)
	if h.B64 == nil || *h.B64 != false {
		t.Fatalf("expected b64=false to be present and false, got %v", h.B64)
	}
	if len(h.Crit) != 1 || h.Crit[0] != "b64" {
		t.Errorf("expected crit=[b64], got %v", h.Crit)
	}
}

func TestBuildHeader_IncludesCertAndHash(t *testing.T) {
	cert := testLeafCert(t)
	headerB64, err := buildHeader(headerParams{
		signAlgorithm: algRS256, b64: true,
		includeCertificate: true, includeCertHash: true,
		leafCert: cert,
	})
	if err != nil {
		t.Fatalf("buildHeader: %v", err)
	}
	h := decodeHeader(t, headerB64)
	if len(h.X5C) != 1 {
		t.Fatalf("expected exactly one x5c entry (V1: leaf only), got %d", len(h.X5C))
	}
	gotDER, err := base64.StdEncoding.DecodeString(h.X5C[0])
	if err != nil {
		t.Fatalf("x5c is not valid base64: %v", err)
	}
	if string(gotDER) != string(cert.Raw) {
		t.Error("x5c entry does not match the leaf certificate's DER bytes")
	}
	if h.X5TS256 == "" {
		t.Error("expected x5t#S256 to be set")
	}
}

func TestBuildHeader_CertificateURL(t *testing.T) {
	headerB64, err := buildHeader(headerParams{signAlgorithm: algRS256, b64: true, certificateURL: "https://example.test/cert"})
	if err != nil {
		t.Fatalf("buildHeader: %v", err)
	}
	h := decodeHeader(t, headerB64)
	if h.X5U != "https://example.test/cert" {
		t.Errorf("x5u = %q, want the configured URL", h.X5U)
	}
}

func TestBuildHeader_KeyID(t *testing.T) {
	headerB64, err := buildHeader(headerParams{
		signAlgorithm: algPS256, b64: true,
		includeKeyID: true, uniqueIdentifier: "ABCDEF0123456789", kidPrepend: "prefix:",
	})
	if err != nil {
		t.Fatalf("buildHeader: %v", err)
	}
	h := decodeHeader(t, headerB64)
	wantSuffix, err := kidFromUniqueIdentifier("ABCDEF0123456789")
	if err != nil {
		t.Fatalf("kidFromUniqueIdentifier: %v", err)
	}
	if h.Kid != "prefix:"+wantSuffix {
		t.Errorf("kid = %q, want %q", h.Kid, "prefix:"+wantSuffix)
	}
}

func TestBuildHeader_KeyID_InvalidUniqueIdentifier_OmitsKid(t *testing.T) {
	headerB64, err := buildHeader(headerParams{
		signAlgorithm: algPS256, b64: true,
		includeKeyID: true, uniqueIdentifier: "not-hex!!", kidPrepend: "prefix:",
	})
	if err != nil {
		t.Fatalf("buildHeader: %v", err)
	}
	h := decodeHeader(t, headerB64)
	if h.Kid != "" {
		t.Errorf("expected kid to be omitted on a derivation failure, got %q", h.Kid)
	}
}

func TestBuildSigningInput(t *testing.T) {
	got := buildSigningInput("HEADER", []byte("PAYLOAD"))
	want := "HEADER.PAYLOAD"
	if string(got) != want {
		t.Errorf("buildSigningInput = %q, want %q", got, want)
	}
}

func TestKidFromUniqueIdentifier_DeterministicSHA256(t *testing.T) {
	raw, _ := hex.DecodeString("ABCDEF0123456789")
	sum := sha256.Sum256(raw)
	want := base64.RawURLEncoding.EncodeToString(sum[:])

	got, err := kidFromUniqueIdentifier("ABCDEF0123456789")
	if err != nil {
		t.Fatalf("kidFromUniqueIdentifier: %v", err)
	}
	if got != want {
		t.Errorf("kidFromUniqueIdentifier = %q, want %q", got, want)
	}
}

func TestKidFromUniqueIdentifier_InvalidHex(t *testing.T) {
	if _, err := kidFromUniqueIdentifier("not hex"); err == nil {
		t.Fatal("expected an error for non-hex input")
	}
}

func TestCheckCertValidity(t *testing.T) {
	cert := &x509.Certificate{
		NotBefore: time.Date(2020, 1, 1, 0, 0, 0, 0, time.UTC),
		NotAfter:  time.Date(2020, 12, 31, 0, 0, 0, 0, time.UTC),
	}
	if err := checkCertValidity(cert, time.Date(2020, 6, 1, 0, 0, 0, 0, time.UTC)); err != nil {
		t.Errorf("expected valid at mid-year, got %v", err)
	}
	if err := checkCertValidity(cert, time.Date(2019, 1, 1, 0, 0, 0, 0, time.UTC)); err == nil {
		t.Error("expected an error before NotBefore")
	}
	if err := checkCertValidity(cert, time.Date(2021, 1, 1, 0, 0, 0, 0, time.UTC)); err == nil {
		t.Error("expected an error after NotAfter")
	}
}

func TestIssuerFromPayload(t *testing.T) {
	if got := issuerFromPayload([]byte(`{"iss":"https://issuer.example"}`)); got != "https://issuer.example" {
		t.Errorf("issuerFromPayload = %q, want the iss claim", got)
	}
	if got := issuerFromPayload([]byte(`{"sub":"no issuer here"}`)); got != "" {
		t.Errorf("issuerFromPayload = %q, want empty when no iss claim", got)
	}
	if got := issuerFromPayload([]byte(`not json`)); got != "" {
		t.Errorf("issuerFromPayload = %q, want empty for invalid JSON", got)
	}
}
