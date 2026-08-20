/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt

import (
	"bytes"
	"context"
	"database/sql"
	"encoding/json"
	"errors"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/jackc/pgx/v5/pgconn"
	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/clientmgmt/db"
	applog "github.com/mosip/esignet/internal/log"
)

func newTestHandler(q db.Querier) *Handler {
	svc := NewServiceWithQuerier(q, nil, 0, nil)
	return NewHandler(svc, applog.GetLogger().Named("clientmgmt-test"))
}

func decodeEnvelope(t *testing.T, body []byte) map[string]any {
	t.Helper()
	var decoded map[string]any
	require.NoError(t, json.Unmarshal(body, &decoded))
	return decoded
}

func (ts *HandlerTestSuite) TestRegisterRoutes() {
	t := ts.T()
	h := newTestHandler(&fakeQuerier{createRow: existingClientRow()})
	mux := http.NewServeMux()
	h.RegisterRoutes(mux, nil)

	body := []byte(`{"requestTime":"2026-07-27T00:00:00.000Z","request":` + validCreateRequestJSON() + `}`)
	req := httptest.NewRequest(http.MethodPost, "/client-mgmt/oidc-client", bytes.NewReader(body))
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)
	require.Equal(t, http.StatusOK, rec.Code)
}

func (ts *HandlerTestSuite) TestRegisterRoutesWithMiddleware() {
	t := ts.T()
	h := newTestHandler(&fakeQuerier{getErr: sql.ErrNoRows})
	mux := http.NewServeMux()
	called := false
	middleware := func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			called = true
			next.ServeHTTP(w, r)
		})
	}
	h.RegisterRoutes(mux, middleware)

	req := httptest.NewRequest(http.MethodGet, "/client-mgmt/client/missing", nil)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)
	require.True(t, called)
	require.Equal(t, http.StatusOK, rec.Code)
}

func validCreateRequestJSON() string {
	b, _ := json.Marshal(validCreateRequest())
	return string(b)
}

func (ts *HandlerTestSuite) TestCreateClientHandler() {
	t := ts.T()

	t.Run("success", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{createRow: existingClientRow()})
		body := []byte(`{"requestTime":"2026-07-27T00:00:00.000Z","request":` + validCreateRequestJSON() + `}`)
		req := httptest.NewRequest(http.MethodPost, "/client-mgmt/oidc-client", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		h.createClient(ProfileOIDC).ServeHTTP(rec, req)

		require.Equal(t, http.StatusOK, rec.Code)
		decoded := decodeEnvelope(t, rec.Body.Bytes())
		require.Nil(t, decoded["errors"])
		response, ok := decoded["response"].(map[string]any)
		require.True(t, ok)
		require.Equal(t, "client-1", response["clientId"])
	})

	t.Run("malformed json body", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		req := httptest.NewRequest(http.MethodPost, "/client-mgmt/oidc-client", bytes.NewReader([]byte("not-json")))
		rec := httptest.NewRecorder()
		h.createClient(ProfileOIDC).ServeHTTP(rec, req)

		require.Equal(t, http.StatusOK, rec.Code)
		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs, ok := decoded["errors"].([]any)
		require.True(t, ok)
		require.Len(t, errs, 1)
	})

	t.Run("missing request time", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		body := []byte(`{"request":` + validCreateRequestJSON() + `}`)
		req := httptest.NewRequest(http.MethodPost, "/client-mgmt/oidc-client", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		h.createClient(ProfileOIDC).ServeHTTP(rec, req)

		require.Equal(t, http.StatusOK, rec.Code)
		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs, ok := decoded["errors"].([]any)
		require.True(t, ok)
		require.Len(t, errs, 1)
	})

	t.Run("validation error returns 200 with populated errors", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		reqObj := validCreateRequest()
		reqObj.ClientID = ""
		reqJSON, err := json.Marshal(reqObj)
		require.NoError(t, err)
		body := []byte(`{"requestTime":"2026-07-27T00:00:00.000Z","request":` + string(reqJSON) + `}`)
		req := httptest.NewRequest(http.MethodPost, "/client-mgmt/oidc-client", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		h.createClient(ProfileOIDC).ServeHTTP(rec, req)

		require.Equal(t, http.StatusOK, rec.Code)
		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs, ok := decoded["errors"].([]any)
		require.True(t, ok)
		require.Len(t, errs, 1)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_client_id", errObj["errorCode"])
	})

	t.Run("duplicate client id", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{createErr: &pgconn.PgError{Code: "23505", ConstraintName: "pk_clntdtl_id"}})
		body := []byte(`{"requestTime":"2026-07-27T00:00:00.000Z","request":` + validCreateRequestJSON() + `}`)
		req := httptest.NewRequest(http.MethodPost, "/client-mgmt/oidc-client", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		h.createClient(ProfileOIDC).ServeHTTP(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "duplicate_client_id", errObj["errorCode"])
	})

	t.Run("server error on unexpected failure", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{createErr: errors.New("connection reset")})
		body := []byte(`{"requestTime":"2026-07-27T00:00:00.000Z","request":` + validCreateRequestJSON() + `}`)
		req := httptest.NewRequest(http.MethodPost, "/client-mgmt/oidc-client", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		h.createClient(ProfileOIDC).ServeHTTP(rec, req)

		require.Equal(t, http.StatusInternalServerError, rec.Code)
		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "server_error", errObj["errorCode"])
	})
}

func (ts *HandlerTestSuite) TestUpdateClientHandler() {
	t := ts.T()

	t.Run("missing client id", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		req := httptest.NewRequest(http.MethodPut, "/client-mgmt/oidc-client/", bytes.NewReader([]byte(`{}`)))
		rec := httptest.NewRecorder()
		h.updateClient(ProfileOIDC).ServeHTTP(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_input", errObj["errorCode"])
	})

	t.Run("success via mux with path value", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{updateRow: existingClientRow()})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)

		updateJSON, err := json.Marshal(validUpdateRequest())
		require.NoError(t, err)
		body := []byte(`{"requestTime":"2026-07-27T00:00:00.000Z","request":` + string(updateJSON) + `}`)
		req := httptest.NewRequest(http.MethodPut, "/client-mgmt/oidc-client/client-1", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		require.Equal(t, http.StatusOK, rec.Code)
		decoded := decodeEnvelope(t, rec.Body.Bytes())
		require.Nil(t, decoded["errors"])
	})

	t.Run("client not found", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{updateErr: sql.ErrNoRows})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)

		updateJSON, err := json.Marshal(validUpdateRequest())
		require.NoError(t, err)
		body := []byte(`{"requestTime":"2026-07-27T00:00:00.000Z","request":` + string(updateJSON) + `}`)
		req := httptest.NewRequest(http.MethodPut, "/client-mgmt/oidc-client/missing", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_client_id", errObj["errorCode"])
	})

	t.Run("malformed body", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)
		req := httptest.NewRequest(http.MethodPut, "/client-mgmt/oidc-client/client-1", bytes.NewReader([]byte("not-json")))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_input", errObj["errorCode"])
	})

	t.Run("missing request time", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)
		updateJSON, err := json.Marshal(validUpdateRequest())
		require.NoError(t, err)
		body := []byte(`{"request":` + string(updateJSON) + `}`)
		req := httptest.NewRequest(http.MethodPut, "/client-mgmt/oidc-client/client-1", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_input", errObj["errorCode"])
	})
}

func (ts *HandlerTestSuite) TestPatchClientHandler() {
	t := ts.T()

	t.Run("missing client id", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		req := httptest.NewRequest(http.MethodPatch, "/client-mgmt/client/", bytes.NewReader([]byte(`{}`)))
		rec := httptest.NewRecorder()
		h.patchClient(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_input", errObj["errorCode"])
	})

	t.Run("success via mux", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{getRow: existingClientRow(), patchRow: existingClientRow()})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)

		body := []byte(`{"requestTime":"2026-07-27T00:00:00.000Z","request":{"logoUri":"https://example.com/new-logo.png"}}`)
		req := httptest.NewRequest(http.MethodPatch, "/client-mgmt/client/client-1", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		require.Equal(t, http.StatusOK, rec.Code)
		decoded := decodeEnvelope(t, rec.Body.Bytes())
		require.Nil(t, decoded["errors"])
	})

	t.Run("malformed body", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)
		req := httptest.NewRequest(http.MethodPatch, "/client-mgmt/client/client-1", bytes.NewReader([]byte("not-json")))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_input", errObj["errorCode"])
	})

	t.Run("missing request time", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)
		body := []byte(`{"request":{"logoUri":"https://example.com/new-logo.png"}}`)
		req := httptest.NewRequest(http.MethodPatch, "/client-mgmt/client/client-1", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_input", errObj["errorCode"])
	})

	t.Run("decode patch request error", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)
		body := []byte(`{"requestTime":"2026-07-27T00:00:00.000Z","request":{"logoUri":123}}`)
		req := httptest.NewRequest(http.MethodPatch, "/client-mgmt/client/client-1", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		require.Len(t, errs, 1)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_input", errObj["errorCode"])
	})

	t.Run("patch conflict", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{getRow: existingClientRow(), patchErr: sql.ErrNoRows})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)
		body := []byte(`{"requestTime":"2026-07-27T00:00:00.000Z","request":{"logoUri":"https://example.com/new-logo.png"}}`)
		req := httptest.NewRequest(http.MethodPatch, "/client-mgmt/client/client-1", bytes.NewReader(body))
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "patch_conflict", errObj["errorCode"])
	})
}

func (ts *HandlerTestSuite) TestGetClientHandler() {
	t := ts.T()

	t.Run("missing client id", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{})
		req := httptest.NewRequest(http.MethodGet, "/client-mgmt/client/", nil)
		rec := httptest.NewRecorder()
		h.getClient(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_input", errObj["errorCode"])
	})

	t.Run("success via mux", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{getRow: existingClientRow()})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)
		req := httptest.NewRequest(http.MethodGet, "/client-mgmt/client/client-1", nil)
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		require.Equal(t, http.StatusOK, rec.Code)
		decoded := decodeEnvelope(t, rec.Body.Bytes())
		response := decoded["response"].(map[string]any)
		require.Equal(t, "client-1", response["clientId"])
	})

	t.Run("not found", func(t *testing.T) {
		h := newTestHandler(&fakeQuerier{getErr: sql.ErrNoRows})
		mux := http.NewServeMux()
		h.RegisterRoutes(mux, nil)
		req := httptest.NewRequest(http.MethodGet, "/client-mgmt/client/missing", nil)
		rec := httptest.NewRecorder()
		mux.ServeHTTP(rec, req)

		decoded := decodeEnvelope(t, rec.Body.Bytes())
		errs := decoded["errors"].([]any)
		errObj := errs[0].(map[string]any)
		require.Equal(t, "invalid_client_id", errObj["errorCode"])
	})
}

func (ts *HandlerTestSuite) TestWriteSpecErrorDefaultsMessageToCode() {
	t := ts.T()
	rec := httptest.NewRecorder()
	writeSpecError(context.Background(), rec, "some_code", "")

	decoded := decodeEnvelope(t, rec.Body.Bytes())
	errs := decoded["errors"].([]any)
	errObj := errs[0].(map[string]any)
	require.Equal(t, "some_code", errObj["errorCode"])
	require.Equal(t, "some_code", errObj["errorMessage"])
}

func (ts *HandlerTestSuite) TestHandleServiceErrorDuplicatePublicKey() {
	t := ts.T()
	h := newTestHandler(&fakeQuerier{})
	rec := httptest.NewRecorder()
	h.handleServiceError(context.Background(), rec, ErrDuplicatePublicKey, "op")

	decoded := decodeEnvelope(t, rec.Body.Bytes())
	errs := decoded["errors"].([]any)
	errObj := errs[0].(map[string]any)
	require.Equal(t, "invalid_public_key", errObj["errorCode"])
}

func (ts *HandlerTestSuite) TestNewHandlerAndContextPropagation() {
	t := ts.T()
	q := &fakeQuerier{getRow: existingClientRow()}
	h := newTestHandler(q)
	require.NotNil(t, h)

	ctx := context.WithValue(context.Background(), struct{ key string }{"k"}, "v")
	req := httptest.NewRequest(http.MethodGet, "/client-mgmt/client/client-1", nil).WithContext(ctx)
	rec := httptest.NewRecorder()
	h.getClient(rec, req)
	require.Equal(t, http.StatusOK, rec.Code)
}

type HandlerTestSuite struct {
	suite.Suite
}

func TestHandlerTestSuite(t *testing.T) {
	suite.Run(t, new(HandlerTestSuite))
}
