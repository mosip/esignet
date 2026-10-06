// handler_upload_certificate_security_test.go — HTTP integration tests for UploadCertificate security guards.
//
// Spins up a real httptest.Server backed by the actual Handler → Service →
// fakeKeyStore pipeline (no Docker, no Postgres, no Redis required).  Every
// HTTP wire-layer concern — request parsing, JSON envelope, error code mapping
// — is exercised end-to-end.
//
// Scenarios (mirrors the "What to check" table from the finding write-up):
//   1. Provenance gate:  cert with correct pubkey, throwaway signer   → invalid_certificate
//   2. Expiry gate:      cert with NotAfter in the past               → invalid_certificate
//   3. Not-yet-valid:    cert with NotBefore in the future            → invalid_certificate
//   4. Duplicate guard:  same cert already on file                    → invalid_certificate (pre-existing)
//   5. Legitimate path:  cert signed by actual ROOT key, valid window → status:success

package keymanager_test

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"io"
	"math/big"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/db"
	applog "github.com/mosip/esignet/internal/log"
)

// ── suite wiring ─────────────────────────────────────────────────────────────

type UploadCertificateSecuritySuite struct {
	suite.Suite
	server      *httptest.Server
	ks          *fakeKeyStore
	rootAlias   string
	rootPriv    *rsa.PrivateKey
	rootCert    *x509.Certificate
	rootCertPEM string
	thumbprint  string
}

func TestUploadCertificateSecuritySuite(t *testing.T) {
	suite.Run(t, new(UploadCertificateSecuritySuite))
}

func (ts *UploadCertificateSecuritySuite) SetupTest() {
	ts.ks = newFakeKeyStore()
	ts.rootAlias = "root-alias"

	// Provision a ROOT key directly into the keystore.
	ts.Require().NoError(ts.ks.GenerateAndStoreAsymmetricKey(
		ts.rootAlias, ts.rootAlias, testCertTemplateParams(), "RSA", "",
	))
	privRaw, err := ts.ks.GetPrivateKey(ts.rootAlias)
	ts.Require().NoError(err)
	ts.rootPriv = privRaw.(*rsa.PrivateKey)
	ts.rootCert, err = ts.ks.GetCertificate(ts.rootAlias)
	ts.Require().NoError(err)
	ts.rootCertPEM = string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: ts.rootCert.Raw}))
	tp := keymanager.ThumbprintForCert(ts.rootCert)
	ts.thumbprint = tp

	// Wire querier: returns the ROOT alias for any (appID, refID) lookup.
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) {
			return alwaysActivePolicy(), nil
		},
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			row := validAliasRow(ts.rootAlias)
			row.CertThumbprint = &ts.thumbprint
			return []db.KeyAlias{row}, nil
		},
		updateKeyAliasFn: func(_ context.Context, _ db.KeyAlias) error { return nil },
	}

	svc := keymanager.NewServiceWithQuerier(q, ts.ks, testConfig())
	handler := keymanager.NewHandler(svc, applog.GetLogger().Named("test"))

	mux := http.NewServeMux()
	handler.RegisterRoutes(mux, func(h http.Handler) http.Handler { return h }) // no-op middleware
	ts.server = httptest.NewServer(mux)
}

func (ts *UploadCertificateSecuritySuite) TearDownTest() {
	ts.server.Close()
}

// ── helpers ───────────────────────────────────────────────────────────────────

type uploadResponse struct {
	Response *struct {
		Status string `json:"status"`
	} `json:"response"`
	Errors []struct {
		ErrorCode    string `json:"errorCode"`
		ErrorMessage string `json:"errorMessage"`
	} `json:"errors"`
}

func (ts *UploadCertificateSecuritySuite) upload(certPEM string) uploadResponse {
	payload := map[string]interface{}{
		"id":          "mosip.keymanager.uploadcertificate",
		"version":     "1.0",
		"requesttime": time.Now().UTC().Format(time.RFC3339),
		"request": map[string]string{
			"applicationId":   "ESIGNET",
			"referenceId":     "RSA_2048",
			"certificateData": certPEM,
		},
	}
	body, err := json.Marshal(payload)
	ts.Require().NoError(err)

	resp, err := http.Post(ts.server.URL+"/system-info/uploadCertificate", "application/json", bytes.NewReader(body))
	ts.Require().NoError(err)
	defer func() { _ = resp.Body.Close() }()
	raw, err := io.ReadAll(resp.Body)
	ts.Require().NoError(err)

	var ur uploadResponse
	ts.Require().NoError(json.Unmarshal(raw, &ur), "raw body: %s", string(raw))
	return ur
}

func (ts *UploadCertificateSecuritySuite) makeCertPEM(notBefore, notAfter time.Time, pubKey interface{}, signerPriv interface{}) string {
	template := &x509.Certificate{
		SerialNumber: big.NewInt(time.Now().UnixNano()),
		Subject:      ts.rootCert.Subject,
		NotBefore:    notBefore,
		NotAfter:     notAfter,
	}
	certDER, err := x509.CreateCertificate(rand.Reader, template, template, pubKey, signerPriv)
	ts.Require().NoError(err)
	return string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: certDER}))
}

func (ts *UploadCertificateSecuritySuite) assertRejectedWith(ur uploadResponse, wantCode string, label string) {
	ts.T().Helper()
	isSuccess := ur.Response != nil && ur.Response.Status == "success"
	hasError := len(ur.Errors) > 0 && ur.Errors[0].ErrorCode == wantCode

	ts.Assert().False(isSuccess, "[%s] upload must NOT succeed", label)
	ts.Require().True(hasError,
		"[%s] expected errorCode=%q, got response=%+v errors=%+v", label, wantCode, ur.Response, ur.Errors)

	if hasError {
		ts.T().Logf("✓  %-55s → %s: %s", label, ur.Errors[0].ErrorCode, ur.Errors[0].ErrorMessage)
	}
}

func (ts *UploadCertificateSecuritySuite) assertSuccess(ur uploadResponse, label string) {
	ts.T().Helper()
	isSuccess := ur.Response != nil && ur.Response.Status == "success"
	ts.Assert().True(isSuccess, "[%s] upload must succeed, got errors=%+v", label, ur.Errors)
	if isSuccess {
		ts.T().Logf("✓  %-55s → status:success", label)
	}
}

// ── Scenario 1 — provenance gate ─────────────────────────────────────────────

func (ts *UploadCertificateSecuritySuite) TestScenario1_ThrowawaySigner_Rejected() {
	ts.T().Log("\n── Scenario 1: cert embeds ROOT pubkey but signed by a throwaway key ──")
	throwaway, err := rsa.GenerateKey(rand.Reader, 2048)
	ts.Require().NoError(err)

	certPEM := ts.makeCertPEM(
		time.Now().AddDate(0, 0, -1),
		time.Now().AddDate(5, 0, 0),
		&ts.rootPriv.PublicKey, // correct public key
		throwaway,              // but signed by the wrong private key
	)
	ur := ts.upload(certPEM)
	ts.assertRejectedWith(ur, "invalid_certificate",
		"throwaway-signer cert (correct pubkey, wrong signer)")
	ts.Assert().True(
		containsAny(ur.Errors[0].ErrorMessage,
			"does not chain", "provenance", "signature"),
		"error message should mention provenance/signature, got: %s", ur.Errors[0].ErrorMessage,
	)
}

// ── Scenario 2 — expiry gate ─────────────────────────────────────────────────

func (ts *UploadCertificateSecuritySuite) TestScenario2_AlreadyExpired_Rejected() {
	ts.T().Log("\n── Scenario 2: cert has NotAfter in the past ──")
	certPEM := ts.makeCertPEM(
		time.Now().AddDate(-2, 0, 0),
		time.Now().Add(-1*time.Hour), // already expired
		&ts.rootPriv.PublicKey,
		ts.rootPriv,
	)
	ur := ts.upload(certPEM)
	ts.assertRejectedWith(ur, "invalid_certificate",
		"already-expired cert (NotAfter 1h ago)")
	ts.Assert().True(
		containsAny(ur.Errors[0].ErrorMessage, "expired", "NotAfter"),
		"error message should mention expiry, got: %s", ur.Errors[0].ErrorMessage,
	)
}

// ── Scenario 3 — not-yet-valid gate ──────────────────────────────────────────

func (ts *UploadCertificateSecuritySuite) TestScenario3_NotYetValid_Rejected() {
	ts.T().Log("\n── Scenario 3: cert has NotBefore in the future ──")
	certPEM := ts.makeCertPEM(
		time.Now().Add(24*time.Hour), // not yet valid
		time.Now().AddDate(5, 0, 0),
		&ts.rootPriv.PublicKey,
		ts.rootPriv,
	)
	ur := ts.upload(certPEM)
	ts.assertRejectedWith(ur, "invalid_certificate",
		"not-yet-valid cert (NotBefore 24h from now)")
	ts.Assert().True(
		containsAny(ur.Errors[0].ErrorMessage, "not yet valid", "NotBefore", "future"),
		"error message should mention validity, got: %s", ur.Errors[0].ErrorMessage,
	)
}

// ── Scenario 4 — duplicate guard (pre-existing, unchanged) ───────────────────

func (ts *UploadCertificateSecuritySuite) TestScenario4_DuplicateCert_Idempotent() {
	ts.T().Log("\n── Scenario 4: uploading the cert already on file is a no-op success ──")
	ur := ts.upload(ts.rootCertPEM) // identical cert, same thumbprint
	ts.assertSuccess(ur, "duplicate cert (same thumbprint — idempotent re-upload)")
}

// ── Scenario 5 — legitimate renewal (must still succeed) ─────────────────────

func (ts *UploadCertificateSecuritySuite) TestScenario5_LegitimateRenewal_Succeeds() {
	ts.T().Log("\n── Scenario 5: cert signed by ROOT key with valid window ──")
	// Wire updateKeyStoreRecord for the non-resident path (unused for ROOT,
	// but the querier needs to handle UpdateKeyAlias which is already wired).
	renewedPEM := ts.makeCertPEM(
		time.Now().AddDate(0, 0, -1), // NotBefore: yesterday
		time.Now().AddDate(5, 0, 0),  // NotAfter:  5 years
		&ts.rootPriv.PublicKey,       // same pubkey
		ts.rootPriv,                  // signed by the real ROOT private key
	)
	ur := ts.upload(renewedPEM)
	ts.assertSuccess(ur, "valid renewal (same key, new validity window, ROOT signer)")
}

// ── helper ────────────────────────────────────────────────────────────────────

func containsAny(s string, substrs ...string) bool {
	s = strings.ToLower(s)
	for _, sub := range substrs {
		if strings.Contains(s, strings.ToLower(sub)) {
			return true
		}
	}
	return false
}

// ── summary logger ────────────────────────────────────────────────────────────

func (ts *UploadCertificateSecuritySuite) TestSummary() {
	ts.T().Log(`
╔══════════════════════════════════════════════════════════════════════╗
║  UploadCertificate security-guard test matrix                       ║
╠══════════════════════════════════════════════════════════════════════╣
║  Sc  Description                              Expected  Guard       ║
║  1   Throwaway signer, correct pubkey         REJECT    provenance  ║
║  2   Correct signer, NotAfter in the past     REJECT    expiry      ║
║  3   Correct signer, NotBefore in the future  REJECT    not-valid   ║
║  4   Cert already on file (same thumbprint)   ACCEPT    idempotent  ║
║  5   Correct signer, valid window             ACCEPT    (none)      ║
╚══════════════════════════════════════════════════════════════════════╝`)
}
