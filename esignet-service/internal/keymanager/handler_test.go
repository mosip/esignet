/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package keymanager_test

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/x509"
	"encoding/json"
	"encoding/pem"
	"errors"
	"math/big"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/db"
	applog "github.com/mosip/esignet/internal/log"
)

type errorEnvelope struct {
	Errors []struct {
		ErrorCode    string `json:"errorCode"`
		ErrorMessage string `json:"errorMessage"`
	} `json:"errors"`
}

// newTestMux wires a Handler over svc (nil is allowed for paths that fail
// before reaching the service) behind a passthrough middleware.
func newTestMux(svc *keymanager.Service) *http.ServeMux {
	mux := http.NewServeMux()
	keymanager.NewHandler(svc, applog.GetLogger()).RegisterRoutes(mux, func(next http.Handler) http.Handler { return next })
	return mux
}

func do(mux *http.ServeMux, method, target, body string) *httptest.ResponseRecorder {
	req := httptest.NewRequest(method, target, strings.NewReader(body))
	req.Header.Set("Content-Type", "application/json")
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)
	return rec
}

func requireError(t *testing.T, rec *httptest.ResponseRecorder, status int, code, msg string) {
	t.Helper()
	require.Equal(t, status, rec.Code)
	var resp errorEnvelope
	require.NoError(t, json.Unmarshal(rec.Body.Bytes(), &resp))
	require.Len(t, resp.Errors, 1)
	require.Equal(t, code, resp.Errors[0].ErrorCode)
	require.Equal(t, msg, resp.Errors[0].ErrorMessage)
}

func uploadBody(t *testing.T, appID, refID, cert string) string {
	t.Helper()
	b, err := json.Marshal(map[string]any{"request": map[string]string{
		"applicationId": appID, "referenceId": refID, "certificateData": cert,
	}})
	require.NoError(t, err)
	return string(b)
}

func TestRegisterRoutes_NilMiddlewarePanics(t *testing.T) {
	h := keymanager.NewHandler(nil, applog.GetLogger())
	require.Panics(t, func() { h.RegisterRoutes(http.NewServeMux(), nil) })
}

func TestRegisterRoutes_AppliesMiddleware(t *testing.T) {
	called := 0
	mux := http.NewServeMux()
	keymanager.NewHandler(nil, applog.GetLogger()).RegisterRoutes(mux, func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			called++
			w.WriteHeader(http.StatusForbidden)
		})
	})
	require.Equal(t, http.StatusForbidden, do(mux, http.MethodGet, "/system-info/certificate", "").Code)
	require.Equal(t, http.StatusForbidden, do(mux, http.MethodPost, "/system-info/uploadCertificate", "{}").Code)
	require.Equal(t, 2, called)
}

func TestGetCertificate_Validation(t *testing.T) {
	svc := keymanager.NewServiceWithQuerier(&fakeQuerier{}, newFakeKeyStore(), testConfig())
	mux := newTestMux(svc)

	tests := []struct {
		name, query, msg string
	}{
		{"missing applicationId", "", "applicationId is required"},
		{"blank applicationId", "?applicationId=%20%20", "applicationId is required"},
		{"referenceId too long", "?applicationId=ROOT&referenceId=" + strings.Repeat("a", 129), "referenceId is too long"},
		{"referenceId not allowed", "?applicationId=ESIGNET&referenceId=" + url.QueryEscape("not-allowed"), "referenceId is not permitted"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			rec := do(mux, http.MethodGet, "/system-info/certificate"+tc.query, "")
			requireError(t, rec, http.StatusOK, "invalid_request", tc.msg)
		})
	}
}

func TestGetCertificate_Success(t *testing.T) {
	ks := newFakeKeyStore()
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{validAliasRow("root-alias")}, nil
		},
	}
	mux := newTestMux(keymanager.NewServiceWithQuerier(q, ks, testConfig()))

	rec := do(mux, http.MethodGet, "/system-info/certificate?applicationId=ROOT", "")
	require.Equal(t, http.StatusOK, rec.Code)
	var resp struct {
		ResponseTime string `json:"responseTime"`
		Response     struct {
			Certificate string `json:"certificate"`
			IssuedAt    string `json:"issuedAt"`
			ExpiryAt    string `json:"expiryAt"`
			Timestamp   string `json:"timestamp"`
		} `json:"response"`
	}
	require.NoError(t, json.Unmarshal(rec.Body.Bytes(), &resp))
	require.NotEmpty(t, resp.ResponseTime)
	require.Contains(t, resp.Response.Certificate, "BEGIN CERTIFICATE")
	require.NotEmpty(t, resp.Response.IssuedAt)
	require.NotEmpty(t, resp.Response.ExpiryAt)
	require.NotEmpty(t, resp.Response.Timestamp)
}

func TestGetCertificate_ServiceErrors(t *testing.T) {
	// Blank referenceId is only meaningful for ROOT, so another app hits ErrBlankReferenceID.
	t.Run("blank referenceId maps to invalid_request", func(t *testing.T) {
		q := &fakeQuerier{getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil }}
		mux := newTestMux(keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig()))
		rec := do(mux, http.MethodGet, "/system-info/certificate?applicationId=ESIGNET", "")
		requireError(t, rec, http.StatusOK, "invalid_request", keymanager.ErrBlankReferenceID.Error())
	})

	t.Run("unexpected error maps to 500 without leaking detail", func(t *testing.T) {
		q := &fakeQuerier{
			getKeyPolicyFn:          func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
			getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) { return nil, errors.New("db down: secret") },
		}
		mux := newTestMux(keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig()))
		rec := do(mux, http.MethodGet, "/system-info/certificate?applicationId=ROOT", "")
		requireError(t, rec, http.StatusInternalServerError, "server_error", "an unexpected error occurred")
	})
}

func TestUploadCertificate_BadRequests(t *testing.T) {
	mux := newTestMux(nil)

	tests := []struct {
		name, body, msg string
	}{
		{"malformed JSON", "{not json", "malformed JSON body"},
		{"missing applicationId", uploadBody(t, "", "", "cert"), "applicationId is required"},
		{"blank applicationId", uploadBody(t, "   ", "", "cert"), "applicationId is required"},
		{"missing certificateData", uploadBody(t, "ESIGNET", "RSA_2048", ""), "certificateData is required"},
		{"blank certificateData", uploadBody(t, "ESIGNET", "RSA_2048", "  "), "certificateData is required"},
	}
	for _, tc := range tests {
		t.Run(tc.name, func(t *testing.T) {
			rec := do(mux, http.MethodPost, "/system-info/uploadCertificate", tc.body)
			requireError(t, rec, http.StatusOK, "invalid_request", tc.msg)
		})
	}
}

func TestUploadCertificate_BodyTooLarge(t *testing.T) {
	rec := do(newTestMux(nil), http.MethodPost, "/system-info/uploadCertificate", strings.Repeat("a", (1<<20)+1))
	requireError(t, rec, http.StatusOK, "invalid_request", "malformed request body")
}

func TestUploadCertificate_ThumbprintMismatch(t *testing.T) {
	ks := newFakeKeyStore()
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("root-alias", "root-alias", testCertTemplateParams(), "RSA", ""))
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{validAliasRow("root-alias")}, nil
		},
	}
	mux := newTestMux(keymanager.NewServiceWithQuerier(q, ks, testConfig()))

	// ROOT is reserved at the HTTP layer, so use a component app id.
	rec := do(mux, http.MethodPost, "/system-info/uploadCertificate",
		uploadBody(t, "ESIGNET", "RSA_2048", generateUnrelatedSelfSignedCertPEM(t)))
	requireError(t, rec, http.StatusOK, "invalid_certificate", keymanager.ErrThumbprintMismatch.Error())
}

func TestUploadCertificate_Success(t *testing.T) {
	ks := newFakeKeyStore()
	require.NoError(t, ks.GenerateAndStoreAsymmetricKey("alias-1", "alias-1", testCertTemplateParams(), "RSA", ""))
	existing, err := ks.GetCertificate("alias-1")
	require.NoError(t, err)
	priv, err := ks.GetPrivateKey("alias-1")
	require.NoError(t, err)
	renewed := renewedCertPEM(t, existing, priv)

	updated := false
	q := &fakeQuerier{
		getKeyPolicyFn: func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) {
			return []db.KeyAlias{validAliasRow("alias-1")}, nil
		},
		updateKeyAliasFn: func(_ context.Context, _ db.KeyAlias) error { updated = true; return nil },
	}
	mux := newTestMux(keymanager.NewServiceWithQuerier(q, ks, testConfig()))

	rec := do(mux, http.MethodPost, "/system-info/uploadCertificate", uploadBody(t, "ESIGNET", "RSA_2048", renewed))
	require.Equal(t, http.StatusOK, rec.Code, rec.Body.String())
	var resp struct {
		Response struct {
			Status    string `json:"status"`
			Timestamp string `json:"timestamp"`
		} `json:"response"`
	}
	require.NoError(t, json.Unmarshal(rec.Body.Bytes(), &resp))
	require.NotEmpty(t, resp.Response.Status)
	require.NotEmpty(t, resp.Response.Timestamp)
	require.True(t, updated)
}

func TestUploadCertificate_UnexpectedError(t *testing.T) {
	q := &fakeQuerier{
		getKeyPolicyFn:          func(_ context.Context, _ string) (db.KeyPolicy, error) { return alwaysActivePolicy(), nil },
		getKeyAliasesByAppRefFn: func(_ context.Context, _, _ string) ([]db.KeyAlias, error) { return nil, errors.New("db down") },
	}
	mux := newTestMux(keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), testConfig()))
	rec := do(mux, http.MethodPost, "/system-info/uploadCertificate", uploadBody(t, "ESIGNET", "RSA_2048", "cert"))
	requireError(t, rec, http.StatusInternalServerError, "server_error", "an unexpected error occurred")
}

// TestUploadCertificate_ReservedApplicationID verifies that ROOT and
// OIDC_SERVICE are rejected before the service is ever invoked (the handler
// is built with a nil Service, so reaching it would panic).
func TestUploadCertificate_ReservedApplicationID(t *testing.T) {
	mux := newTestMux(nil)

	for _, appID := range []string{keymanager.AppIDRoot, keymanager.AppIDService, "  " + keymanager.AppIDRoot + "  ", " " + keymanager.AppIDService} {
		t.Run(appID, func(t *testing.T) {
			rec := do(mux, http.MethodPost, "/system-info/uploadCertificate", uploadBody(t, appID, "", "dummy-cert"))
			requireError(t, rec, http.StatusOK, "invalid_request", "applicationId is reserved and cannot be used")
		})
	}
}

func renewedCertPEM(t *testing.T, existing *x509.Certificate, priv crypto.PrivateKey) string {
	t.Helper()
	nb := time.Now().UTC().Truncate(time.Second).AddDate(0, 0, -1)
	tmpl := &x509.Certificate{SerialNumber: big.NewInt(2), Subject: existing.Subject, NotBefore: nb, NotAfter: nb.AddDate(5, 0, 0)}
	der, err := x509.CreateCertificate(rand.Reader, tmpl, tmpl, existing.PublicKey, priv)
	require.NoError(t, err)
	return string(pem.EncodeToMemory(&pem.Block{Type: "CERTIFICATE", Bytes: der}))
}
