/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package clientmgmt

import (
	"context"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/clientmgmt/db"
	"github.com/mosip/esignet/internal/engine/runtimestores/inmemory"
)

// fakeQuerier is an in-memory db.Querier stand-in that lets tests script each
// method's response and capture the params passed to write methods.
type fakeQuerier struct {
	createRow    db.ClientDetail
	createErr    error
	createParams db.CreateClientParams

	getRow db.ClientDetail
	getErr error

	updateRow    db.ClientDetail
	updateErr    error
	updateParams db.UpdateClientParams

	patchRow    db.ClientDetail
	patchErr    error
	patchParams db.PatchClientParams
}

var _ db.Querier = (*fakeQuerier)(nil)

func (f *fakeQuerier) CreateClient(_ context.Context, arg db.CreateClientParams) (db.ClientDetail, error) {
	f.createParams = arg
	return f.createRow, f.createErr
}

func (f *fakeQuerier) GetClient(_ context.Context, _ string) (db.ClientDetail, error) {
	return f.getRow, f.getErr
}

func (f *fakeQuerier) UpdateClient(_ context.Context, arg db.UpdateClientParams) (db.ClientDetail, error) {
	f.updateParams = arg
	return f.updateRow, f.updateErr
}

func (f *fakeQuerier) PatchClient(_ context.Context, arg db.PatchClientParams) (db.ClientDetail, error) {
	f.patchParams = arg
	return f.patchRow, f.patchErr
}

func b64(s string) string {
	return base64.RawURLEncoding.EncodeToString([]byte(s))
}

func validJWK() map[string]string {
	return map[string]string{"kty": "RSA", "n": b64("modulus-bytes"), "e": b64("AQAB"), "alg": "RSA-OAEP-256"}
}

func existingClientRow() db.ClientDetail {
	return db.ClientDetail{
		ID:            "client-1",
		Name:          `{"@none":"Test Client"}`,
		RpID:          "rp-1",
		LogoUri:       "https://example.com/logo.png",
		RedirectUris:  `["https://example.com/callback"]`,
		Claims:        `["name","email"]`,
		AcrValues:     `["mosip:idp:acr:static-code"]`,
		PublicKey:     `{"kty":"RSA"}`,
		PublicKeyHash: "hash-1",
		GrantTypes:    `["authorization_code"]`,
		AuthMethods:   `["private_key_jwt"]`,
		Status:        "ACTIVE",
		CrDtimes:      time.Now().UTC(),
		UpdDtimes:     sql.NullTime{Time: time.Now().UTC(), Valid: true},
	}
}

func (ts *ServiceTestSuite) TestCreateClient() {
	t := ts.T()

	t.Run("validation error short circuits", func(t *testing.T) {
		q := &fakeQuerier{}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		req := validCreateRequest()
		req.ClientID = ""
		_, err := s.CreateClient(context.Background(), ProfileOIDC, req)
		require.Error(t, err)
		var ve *ValidationError
		require.ErrorAs(t, err, &ve)
		require.Equal(t, "invalid_client_id", ve.Code)
	})

	t.Run("invalid enc public key", func(t *testing.T) {
		q := &fakeQuerier{}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		req := validCreateRequest()
		req.EncPublicKey = map[string]string{"kty": "bogus"}
		_, err := s.CreateClient(context.Background(), ProfileOIDC, req)
		require.Error(t, err)
	})

	t.Run("enc public key alg outside configured supported algorithms rejected", func(t *testing.T) {
		q := &fakeQuerier{}
		s := NewServiceWithQuerier(q, nil, 0, []string{"AES-GCM"})
		req := validCreateRequest()
		req.EncPublicKey = validJWK() // alg "RSA-OAEP-256", not in the supported list above
		_, err := s.CreateClient(context.Background(), ProfileOIDC, req)
		var ve *ValidationError
		require.ErrorAs(t, err, &ve)
		require.Equal(t, "invalid_public_key", ve.Code)
	})

	t.Run("enc public key alg within configured supported algorithms accepted", func(t *testing.T) {
		q := &fakeQuerier{createRow: existingClientRow()}
		s := NewServiceWithQuerier(q, nil, 0, []string{"RSA-OAEP-256"})
		req := validCreateRequest()
		req.EncPublicKey = validJWK()
		_, err := s.CreateClient(context.Background(), ProfileOIDC, req)
		require.NoError(t, err)
	})

	t.Run("success", func(t *testing.T) {
		q := &fakeQuerier{createRow: existingClientRow()}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		req := validCreateRequest()
		resp, err := s.CreateClient(context.Background(), ProfileOIDC, req)
		require.NoError(t, err)
		require.Equal(t, "client-1", resp.ClientID)
		require.Equal(t, "ACTIVE", resp.Status)
		require.Equal(t, "client-1", q.createParams.ID)
		require.Equal(t, "rp-1", q.createParams.RpID)
		require.Equal(t, "ACTIVE", q.createParams.Status)
	})

	t.Run("duplicate client id", func(t *testing.T) {
		q := &fakeQuerier{createErr: errors.New(`pq: duplicate key value violates unique constraint "pk_clntdtl_id" (SQLSTATE 23505)`)}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.CreateClient(context.Background(), ProfileOIDC, validCreateRequest())
		require.ErrorIs(t, err, ErrDuplicateClientID)
	})

	t.Run("duplicate public key hash", func(t *testing.T) {
		q := &fakeQuerier{createErr: errors.New(`pq: duplicate key value violates unique constraint "uk_clntdtl_public_key_hash"`)}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.CreateClient(context.Background(), ProfileOIDC, validCreateRequest())
		var ve *ValidationError
		require.ErrorAs(t, err, &ve)
		require.Equal(t, "invalid_public_key", ve.Code)
	})

	t.Run("generic db error", func(t *testing.T) {
		q := &fakeQuerier{createErr: errors.New("connection refused")}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.CreateClient(context.Background(), ProfileOIDC, validCreateRequest())
		require.Error(t, err)
		require.False(t, errors.Is(err, ErrDuplicateClientID))
	})
}

func (ts *ServiceTestSuite) TestUpdateClient() {
	t := ts.T()

	t.Run("validation error", func(t *testing.T) {
		q := &fakeQuerier{}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		req := validUpdateRequest()
		req.Status = "not-a-status"
		_, err := s.UpdateClient(context.Background(), ProfileOIDC, "client-1", req)
		require.Error(t, err)
	})

	t.Run("success normalizes status and invalidates cache", func(t *testing.T) {
		cache := inmemory.Initialize("test")
		row := existingClientRow()
		data, err := json.Marshal(row)
		require.NoError(t, err)
		require.NoError(t, cache.Put(context.Background(), clientCacheNamespace, "client-1", data, 60))

		q := &fakeQuerier{updateRow: row}
		s := NewServiceWithQuerier(q, cache, 60, nil)
		resp, err := s.UpdateClient(context.Background(), ProfileOIDC, "client-1", validUpdateRequest())
		require.NoError(t, err)
		require.Equal(t, "client-1", resp.ClientID)
		require.Equal(t, "ACTIVE", q.updateParams.Status)

		cached, err := cache.Get(context.Background(), clientCacheNamespace, "client-1")
		require.NoError(t, err)
		require.Nil(t, cached)
	})

	t.Run("not found", func(t *testing.T) {
		q := &fakeQuerier{updateErr: sql.ErrNoRows}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.UpdateClient(context.Background(), ProfileOIDC, "missing", validUpdateRequest())
		require.ErrorIs(t, err, ErrClientNotFound)
	})

	t.Run("generic db error", func(t *testing.T) {
		q := &fakeQuerier{updateErr: errors.New("boom")}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.UpdateClient(context.Background(), ProfileOIDC, "client-1", validUpdateRequest())
		require.Error(t, err)
		require.False(t, errors.Is(err, ErrClientNotFound))
	})
}

func (ts *ServiceTestSuite) TestPatchClient() {
	t := ts.T()

	t.Run("client not found", func(t *testing.T) {
		q := &fakeQuerier{getErr: sql.ErrNoRows}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.PatchClient(context.Background(), "missing", PatchClientRequest{}, PatchFields{})
		require.ErrorIs(t, err, ErrClientNotFound)
	})

	t.Run("get client generic error", func(t *testing.T) {
		q := &fakeQuerier{getErr: errors.New("boom")}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.PatchClient(context.Background(), "client-1", PatchClientRequest{}, PatchFields{})
		require.Error(t, err)
		require.False(t, errors.Is(err, ErrClientNotFound))
	})

	t.Run("merge patch error on corrupt redirect uris", func(t *testing.T) {
		row := existingClientRow()
		row.RedirectUris = "not-json"
		q := &fakeQuerier{getRow: row}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.PatchClient(context.Background(), "client-1", PatchClientRequest{}, PatchFields{})
		require.Error(t, err)
	})

	t.Run("validation error on patched field", func(t *testing.T) {
		q := &fakeQuerier{getRow: existingClientRow()}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		req := PatchClientRequest{LogoURI: "not-a-uri"}
		fields := PatchFields{LogoURI: true}
		_, err := s.PatchClient(context.Background(), "client-1", req, fields)
		require.Error(t, err)
		var ve *ValidationError
		require.ErrorAs(t, err, &ve)
		require.Equal(t, "invalid_uri", ve.Code)
	})

	t.Run("success with no field changes", func(t *testing.T) {
		q := &fakeQuerier{getRow: existingClientRow(), patchRow: existingClientRow()}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		resp, err := s.PatchClient(context.Background(), "client-1", PatchClientRequest{}, PatchFields{})
		require.NoError(t, err)
		require.Equal(t, "client-1", resp.ClientID)
		require.Equal(t, "ACTIVE", q.patchParams.Status)
	})

	t.Run("status field patched", func(t *testing.T) {
		existing := existingClientRow()
		patched := existingClientRow()
		patched.Status = "INACTIVE"
		q := &fakeQuerier{getRow: existing, patchRow: patched}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		req := PatchClientRequest{Status: "inactive"}
		fields := PatchFields{Status: true}
		resp, err := s.PatchClient(context.Background(), "client-1", req, fields)
		require.NoError(t, err)
		require.Equal(t, "INACTIVE", resp.Status)
		require.Equal(t, "INACTIVE", q.patchParams.Status)
	})

	t.Run("invalid status value", func(t *testing.T) {
		q := &fakeQuerier{getRow: existingClientRow()}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		req := PatchClientRequest{Status: "bogus"}
		fields := PatchFields{Status: true}
		_, err := s.PatchClient(context.Background(), "client-1", req, fields)
		require.Error(t, err)
	})

	t.Run("enc public key cleared with null", func(t *testing.T) {
		existing := existingClientRow()
		existing.EncPublicKey = sql.NullString{String: `{"kty":"RSA"}`, Valid: true}
		existing.EncPublicKeyHash = sql.NullString{String: "hash", Valid: true}
		existing.EncPublicKeyCert = sql.NullString{String: "cert", Valid: true}
		q := &fakeQuerier{getRow: existing, patchRow: existingClientRow()}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		req := PatchClientRequest{EncPublicKey: NullableJWK{Defined: true, IsNull: true}}
		fields := PatchFields{EncPublicKey: true}
		_, err := s.PatchClient(context.Background(), "client-1", req, fields)
		require.NoError(t, err)
		require.False(t, q.patchParams.EncPublicKey.Valid)
		require.False(t, q.patchParams.EncPublicKeyHash.Valid)
		require.False(t, q.patchParams.EncPublicKeyCert.Valid)
	})

	t.Run("enc public key updated with new value", func(t *testing.T) {
		q := &fakeQuerier{getRow: existingClientRow(), patchRow: existingClientRow()}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		req := PatchClientRequest{EncPublicKey: NullableJWK{Defined: true, Value: validJWK()}}
		fields := PatchFields{EncPublicKey: true}
		_, err := s.PatchClient(context.Background(), "client-1", req, fields)
		require.NoError(t, err)
		require.True(t, q.patchParams.EncPublicKey.Valid)
		require.True(t, q.patchParams.EncPublicKeyHash.Valid)
	})

	t.Run("enc public key invalid new value", func(t *testing.T) {
		q := &fakeQuerier{getRow: existingClientRow()}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		req := PatchClientRequest{EncPublicKey: NullableJWK{Defined: true, Value: map[string]string{"kty": "bogus"}}}
		fields := PatchFields{EncPublicKey: true}
		_, err := s.PatchClient(context.Background(), "client-1", req, fields)
		require.Error(t, err)
	})

	t.Run("patch conflict", func(t *testing.T) {
		q := &fakeQuerier{getRow: existingClientRow(), patchErr: sql.ErrNoRows}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.PatchClient(context.Background(), "client-1", PatchClientRequest{}, PatchFields{})
		require.ErrorIs(t, err, ErrClientConflict)
	})

	t.Run("duplicate public key hash on patch", func(t *testing.T) {
		q := &fakeQuerier{getRow: existingClientRow(), patchErr: errors.New(`duplicate key value violates unique constraint "uk_clntdtl_public_key_hash"`)}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.PatchClient(context.Background(), "client-1", PatchClientRequest{}, PatchFields{})
		var ve *ValidationError
		require.ErrorAs(t, err, &ve)
		require.Equal(t, "invalid_public_key", ve.Code)
	})

	t.Run("generic patch db error", func(t *testing.T) {
		q := &fakeQuerier{getRow: existingClientRow(), patchErr: errors.New("boom")}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.PatchClient(context.Background(), "client-1", PatchClientRequest{}, PatchFields{})
		require.Error(t, err)
		require.False(t, errors.Is(err, ErrClientConflict))
	})

	t.Run("invalidates cache on success", func(t *testing.T) {
		cache := inmemory.Initialize("test")
		row := existingClientRow()
		data, err := json.Marshal(row)
		require.NoError(t, err)
		require.NoError(t, cache.Put(context.Background(), clientCacheNamespace, "client-1", data, 60))

		q := &fakeQuerier{getRow: row, patchRow: row}
		s := NewServiceWithQuerier(q, cache, 60, nil)
		_, err = s.PatchClient(context.Background(), "client-1", PatchClientRequest{}, PatchFields{})
		require.NoError(t, err)

		cached, err := cache.Get(context.Background(), clientCacheNamespace, "client-1")
		require.NoError(t, err)
		require.Nil(t, cached)
	})
}

func (ts *ServiceTestSuite) TestGetClient() {
	t := ts.T()

	t.Run("no cache configured queries db", func(t *testing.T) {
		q := &fakeQuerier{getRow: existingClientRow()}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		resp, err := s.GetClient(context.Background(), "client-1")
		require.NoError(t, err)
		require.Equal(t, "client-1", resp.ClientID)
	})

	t.Run("not found", func(t *testing.T) {
		q := &fakeQuerier{getErr: sql.ErrNoRows}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.GetClient(context.Background(), "missing")
		require.ErrorIs(t, err, ErrClientNotFound)
	})

	t.Run("generic db error", func(t *testing.T) {
		q := &fakeQuerier{getErr: errors.New("boom")}
		s := NewServiceWithQuerier(q, nil, 0, nil)
		_, err := s.GetClient(context.Background(), "client-1")
		require.Error(t, err)
		require.False(t, errors.Is(err, ErrClientNotFound))
	})

	t.Run("cache hit avoids db call", func(t *testing.T) {
		cache := inmemory.Initialize("test")
		row := existingClientRow()
		row.ID = "cached-client"
		data, err := json.Marshal(row)
		require.NoError(t, err)
		require.NoError(t, cache.Put(context.Background(), clientCacheNamespace, "cached-client", data, 60))

		q := &fakeQuerier{getErr: errors.New("db should not be called")}
		s := NewServiceWithQuerier(q, cache, 60, nil)
		resp, err := s.GetClient(context.Background(), "cached-client")
		require.NoError(t, err)
		require.Equal(t, "cached-client", resp.ClientID)
	})

	t.Run("cache miss populates cache", func(t *testing.T) {
		cache := inmemory.Initialize("test")
		q := &fakeQuerier{getRow: existingClientRow()}
		s := NewServiceWithQuerier(q, cache, 60, nil)
		resp, err := s.GetClient(context.Background(), "client-1")
		require.NoError(t, err)
		require.Equal(t, "client-1", resp.ClientID)

		cached, err := cache.Get(context.Background(), clientCacheNamespace, "client-1")
		require.NoError(t, err)
		require.NotNil(t, cached)
	})

	t.Run("corrupt cache entry falls back to db", func(t *testing.T) {
		cache := inmemory.Initialize("test")
		require.NoError(t, cache.Put(context.Background(), clientCacheNamespace, "client-1", []byte("not-json"), 60))

		q := &fakeQuerier{getRow: existingClientRow()}
		s := NewServiceWithQuerier(q, cache, 60, nil)
		resp, err := s.GetClient(context.Background(), "client-1")
		require.NoError(t, err)
		require.Equal(t, "client-1", resp.ClientID)
	})
}

func (ts *ServiceTestSuite) TestMarshalUnmarshalHelpers() {
	t := ts.T()

	t.Run("marshalStringSlice empty", func(t *testing.T) {
		s, err := marshalStringSlice(nil)
		require.NoError(t, err)
		require.Equal(t, "[]", s)
	})

	t.Run("unmarshalStringSlice invalid json", func(t *testing.T) {
		_, err := unmarshalStringSlice("not-json")
		require.Error(t, err)
	})

	t.Run("marshalAdditionalConfigRaw empty", func(t *testing.T) {
		v, err := marshalAdditionalConfigRaw(nil)
		require.NoError(t, err)
		require.False(t, v.Valid)
	})

	t.Run("unmarshalAdditionalConfig invalid", func(t *testing.T) {
		_, err := unmarshalAdditionalConfig(sql.NullString{String: "not-json", Valid: true})
		require.Error(t, err)
	})

	t.Run("unmarshalAdditionalConfig empty", func(t *testing.T) {
		v, err := unmarshalAdditionalConfig(sql.NullString{})
		require.NoError(t, err)
		require.Nil(t, v)
	})

	t.Run("marshalClientName oidc profile ignores lang map", func(t *testing.T) {
		name := marshalClientName("App", map[string]string{"eng": "App"}, ProfileOIDC)
		require.Equal(t, "App", name)
	})

	t.Run("marshalClientName non-oidc embeds none key", func(t *testing.T) {
		name := marshalClientName("App", map[string]string{"eng": "AppEng"}, ProfileClient)
		var decoded map[string]string
		require.NoError(t, json.Unmarshal([]byte(name), &decoded))
		require.Equal(t, "App", decoded["@none"])
		require.Equal(t, "AppEng", decoded["eng"])
	})

	t.Run("parseClientName non-json falls back to raw name", func(t *testing.T) {
		name, langMap, err := parseClientName("Plain Name")
		require.NoError(t, err)
		require.Equal(t, "Plain Name", name)
		require.Nil(t, langMap)
	})

	t.Run("parseClientName json strips none key", func(t *testing.T) {
		name, langMap, err := parseClientName(`{"@none":"App","eng":"AppEng"}`)
		require.NoError(t, err)
		require.Equal(t, "App", name)
		require.Equal(t, map[string]string{"eng": "AppEng"}, langMap)
	})

	t.Run("isDuplicateClientID matches constraint variants", func(t *testing.T) {
		require.True(t, isDuplicateClientID(errors.New(`SQLSTATE 23505 pk_clntdtl_id`)))
		require.True(t, isDuplicateClientID(errors.New(`SQLSTATE 23505 client_detail_pkey`)))
		require.False(t, isDuplicateClientID(errors.New("some other error")))
	})

	t.Run("isDuplicatePublicKeyHash", func(t *testing.T) {
		require.True(t, isDuplicatePublicKeyHash(errors.New("uk_clntdtl_public_key_hash violation")))
		require.False(t, isDuplicatePublicKeyHash(errors.New("some other error")))
	})

	t.Run("encKeyColumns empty key", func(t *testing.T) {
		pk, hash, cert, err := encKeyColumns(nil, "", nil)
		require.NoError(t, err)
		require.False(t, pk.Valid)
		require.False(t, hash.Valid)
		require.False(t, cert.Valid)
	})

	t.Run("encKeyColumns with cert", func(t *testing.T) {
		pk, hash, cert, err := encKeyColumns(validJWK(), "cert-data", nil)
		require.NoError(t, err)
		require.True(t, pk.Valid)
		require.True(t, hash.Valid)
		require.True(t, cert.Valid)
		require.Equal(t, "cert-data", cert.String)
	})

	t.Run("encKeyColumns invalid key", func(t *testing.T) {
		_, _, _, err := encKeyColumns(map[string]string{"kty": "bogus"}, "", nil)
		require.Error(t, err)
	})
}

func (ts *ServiceTestSuite) TestToResponse() {
	t := ts.T()

	t.Run("propagates unmarshal error", func(t *testing.T) {
		row := existingClientRow()
		row.Claims = "not-json"
		_, err := toResponse(row)
		require.Error(t, err)
	})

	t.Run("maps enc public key when present", func(t *testing.T) {
		row := existingClientRow()
		row.EncPublicKey = sql.NullString{String: `{"kty":"RSA"}`, Valid: true}
		resp, err := toResponse(row)
		require.NoError(t, err)
		require.Equal(t, `{"kty":"RSA"}`, resp.EncPublicKey)
	})
}

func (ts *ServiceTestSuite) TestNewService() {
	t := ts.T()
	require.NotPanics(t, func() {
		_ = NewService(nil, nil, 0, nil)
	})
}

type ServiceTestSuite struct {
	suite.Suite
}

func TestServiceTestSuite(t *testing.T) {
	suite.Run(t, new(ServiceTestSuite))
}
