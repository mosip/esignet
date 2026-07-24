/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt_test

import (
	"context"
	"database/sql"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
	applog "github.com/mosip/esignet/internal/log"
)

func newTestHandler(q db.Querier) *clientmgmt.Handler {
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)
	return clientmgmt.NewHandler(svc, applog.GetLogger())
}

func newMux(h *clientmgmt.Handler) *http.ServeMux {
	mux := http.NewServeMux()
	h.RegisterRoutes(mux, nil)
	return mux
}

func validUpdateRequestForHandler() clientmgmt.UpdateClientRequest {
	return clientmgmt.UpdateClientRequest{
		ClientName:        "Test App",
		ClientNameLangMap: map[string]string{"eng": "Test App"},
		Status:            "active",
		LogoURI:           "https://example.com/logo.png",
		RedirectURIs:      []string{"https://example.com/callback"},
		Claims:            []string{"name", "email"},
		AcrValues:         []string{"mosip:idp:acr:static-code"},
		GrantTypes:        []string{"authorization_code"},
		AuthMethods:       []string{"private_key_jwt"},
	}
}

func decodeResponseWrapper(t *testing.T, body []byte) clientmgmt.ResponseWrapper {
	t.Helper()
	var wrapper clientmgmt.ResponseWrapper
	require.NoError(t, json.Unmarshal(body, &wrapper))
	return wrapper
}

func (ts *HandlerTestSuite) TestHandler_CreateClient() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		q := &mockQuerier{
			createFn: func(_ context.Context, arg db.CreateClientParams) (db.ClientDetail, error) {
				return stubRow(arg.ID), nil
			},
		}
		mux := newMux(newTestHandler(q))

		reqBody := `{"requestTime":"2026-06-29T16:20:10.980Z","request":` + mustJSON(t, validCreateReq()) + `}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/client-mgmt/client", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.NotNil(t, wrapper.Response)
		assert.Equal(t, "client-001", wrapper.Response.ClientID)
		assert.Empty(t, wrapper.Errors)
	})

	t.Run("missing request time", func(t *testing.T) {
		mux := newMux(newTestHandler(&mockQuerier{}))
		reqBody := `{"request":` + mustJSON(t, validCreateReq()) + `}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/client-mgmt/client", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "invalid_input", wrapper.Errors[0].ErrorCode)
	})

	t.Run("malformed json", func(t *testing.T) {
		mux := newMux(newTestHandler(&mockQuerier{}))
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/client-mgmt/client", strings.NewReader(`not-json`)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "invalid_input", wrapper.Errors[0].ErrorCode)
	})

	t.Run("validation error surfaced with its own code", func(t *testing.T) {
		mux := newMux(newTestHandler(&mockQuerier{}))
		req := validCreateReq()
		req.Claims = []string{"not-a-claim"}
		reqBody := `{"requestTime":"2026-06-29T16:20:10.980Z","request":` + mustJSON(t, req) + `}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/client-mgmt/client", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "invalid_claim", wrapper.Errors[0].ErrorCode)
	})

	t.Run("unexpected error maps to server_error", func(t *testing.T) {
		q := &mockQuerier{
			createFn: func(_ context.Context, _ db.CreateClientParams) (db.ClientDetail, error) {
				return db.ClientDetail{}, assert.AnError
			},
		}
		mux := newMux(newTestHandler(q))
		reqBody := `{"requestTime":"2026-06-29T16:20:10.980Z","request":` + mustJSON(t, validCreateReq()) + `}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/client-mgmt/client", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusInternalServerError, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "server_error", wrapper.Errors[0].ErrorCode)
	})

	t.Run("duplicate client id maps to duplicate_client_id", func(t *testing.T) {
		q := &mockQuerier{
			createFn: func(_ context.Context, _ db.CreateClientParams) (db.ClientDetail, error) {
				return db.ClientDetail{}, clientmgmt.ErrDuplicateClientID
			},
		}
		mux := newMux(newTestHandler(q))
		reqBody := `{"requestTime":"2026-06-29T16:20:10.980Z","request":` + mustJSON(t, validCreateReq()) + `}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/client-mgmt/client", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "duplicate_client_id", wrapper.Errors[0].ErrorCode)
	})

	t.Run("duplicate public key maps to invalid_public_key", func(t *testing.T) {
		q := &mockQuerier{
			createFn: func(_ context.Context, _ db.CreateClientParams) (db.ClientDetail, error) {
				return db.ClientDetail{}, clientmgmt.ErrDuplicatePublicKey
			},
		}
		mux := newMux(newTestHandler(q))
		reqBody := `{"requestTime":"2026-06-29T16:20:10.980Z","request":` + mustJSON(t, validCreateReq()) + `}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPost, "/client-mgmt/client", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "invalid_public_key", wrapper.Errors[0].ErrorCode)
	})
}

func (ts *HandlerTestSuite) TestHandler_UpdateClient() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		q := &mockQuerier{
			updateFn: func(_ context.Context, arg db.UpdateClientParams) (db.ClientDetail, error) {
				return stubRow(arg.ID), nil
			},
		}
		mux := newMux(newTestHandler(q))
		reqBody := `{"requestTime":"2026-06-29T16:20:10.980Z","request":` + mustJSON(t, validUpdateRequestForHandler()) + `}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPut, "/client-mgmt/client/client-001", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.NotNil(t, wrapper.Response)
		assert.Equal(t, "client-001", wrapper.Response.ClientID)
	})

	t.Run("not found maps to invalid_client_id", func(t *testing.T) {
		q := &mockQuerier{
			updateFn: func(_ context.Context, _ db.UpdateClientParams) (db.ClientDetail, error) {
				return db.ClientDetail{}, sql.ErrNoRows
			},
		}
		mux := newMux(newTestHandler(q))
		reqBody := `{"requestTime":"2026-06-29T16:20:10.980Z","request":` + mustJSON(t, validUpdateRequestForHandler()) + `}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPut, "/client-mgmt/client/no-such-client", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "invalid_client_id", wrapper.Errors[0].ErrorCode)
	})

	t.Run("malformed body", func(t *testing.T) {
		mux := newMux(newTestHandler(&mockQuerier{}))
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPut, "/client-mgmt/client/client-001", strings.NewReader(`not-json`)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "invalid_input", wrapper.Errors[0].ErrorCode)
	})
}

func (ts *HandlerTestSuite) TestHandler_PatchClient() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		q := &mockQuerier{
			getFn: func(_ context.Context, id string) (db.ClientDetail, error) {
				return stubRow(id), nil
			},
			patchFn: func(_ context.Context, arg db.PatchClientParams) (db.ClientDetail, error) {
				return stubRow(arg.ID), nil
			},
		}
		mux := newMux(newTestHandler(q))
		reqBody := `{"requestTime":"2026-06-29T16:20:10.980Z","request":{"clientName":"New Name"}}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPatch, "/client-mgmt/client/client-001", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.NotNil(t, wrapper.Response)
		assert.Equal(t, "client-001", wrapper.Response.ClientID)
	})

	t.Run("conflict maps to patch_conflict", func(t *testing.T) {
		q := &mockQuerier{
			getFn: func(_ context.Context, id string) (db.ClientDetail, error) {
				return stubRow(id), nil
			},
			patchFn: func(_ context.Context, _ db.PatchClientParams) (db.ClientDetail, error) {
				return db.ClientDetail{}, sql.ErrNoRows
			},
		}
		mux := newMux(newTestHandler(q))
		reqBody := `{"requestTime":"2026-06-29T16:20:10.980Z","request":{"clientName":"New Name"}}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPatch, "/client-mgmt/client/client-001", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "patch_conflict", wrapper.Errors[0].ErrorCode)
	})

	t.Run("missing request key", func(t *testing.T) {
		mux := newMux(newTestHandler(&mockQuerier{}))
		reqBody := `{"requestTime":"2026-06-29T16:20:10.980Z"}`
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodPatch, "/client-mgmt/client/client-001", strings.NewReader(reqBody)))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "invalid_input", wrapper.Errors[0].ErrorCode)
	})
}

func (ts *HandlerTestSuite) TestHandler_GetClient() {
	t := ts.T()
	t.Run("success", func(t *testing.T) {
		q := &mockQuerier{
			getFn: func(_ context.Context, id string) (db.ClientDetail, error) {
				return stubRow(id), nil
			},
		}
		mux := newMux(newTestHandler(q))
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/client-mgmt/client/client-001", nil))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.NotNil(t, wrapper.Response)
		assert.Equal(t, "client-001", wrapper.Response.ClientID)
	})

	t.Run("not found", func(t *testing.T) {
		q := &mockQuerier{
			getFn: func(_ context.Context, _ string) (db.ClientDetail, error) {
				return db.ClientDetail{}, sql.ErrNoRows
			},
		}
		mux := newMux(newTestHandler(q))
		w := httptest.NewRecorder()
		mux.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/client-mgmt/client/no-such-client", nil))

		require.Equal(t, http.StatusOK, w.Code)
		wrapper := decodeResponseWrapper(t, w.Body.Bytes())
		require.Len(t, wrapper.Errors, 1)
		assert.Equal(t, "invalid_client_id", wrapper.Errors[0].ErrorCode)
	})
}

func (ts *HandlerTestSuite) TestHandler_RegisterRoutes_WithMiddleware() {
	t := ts.T()
	q := &mockQuerier{
		getFn: func(_ context.Context, id string) (db.ClientDetail, error) {
			return stubRow(id), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)
	h := clientmgmt.NewHandler(svc, applog.GetLogger())

	var calledMiddleware bool
	middleware := func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			calledMiddleware = true
			next.ServeHTTP(w, r)
		})
	}

	mux := http.NewServeMux()
	h.RegisterRoutes(mux, middleware)

	w := httptest.NewRecorder()
	mux.ServeHTTP(w, httptest.NewRequest(http.MethodGet, "/client-mgmt/client/client-001", nil))

	require.Equal(t, http.StatusOK, w.Code)
	assert.True(t, calledMiddleware)
}

func mustJSON(t *testing.T, v any) string {
	t.Helper()
	b, err := json.Marshal(v)
	require.NoError(t, err)
	return string(b)
}

type HandlerTestSuite struct {
	suite.Suite
}

func TestHandlerTestSuite(t *testing.T) {
	suite.Run(t, new(HandlerTestSuite))
}
