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
	"errors"
	"fmt"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
)

type mockQuerier struct {
	createFn func(context.Context, db.CreateClientParams) (db.ClientDetail, error)
	getFn    func(context.Context, string) (db.ClientDetail, error)
	updateFn func(context.Context, db.UpdateClientParams) (db.ClientDetail, error)
	patchFn  func(context.Context, db.PatchClientParams) (db.ClientDetail, error)
}

func (m *mockQuerier) CreateClient(ctx context.Context, arg db.CreateClientParams) (db.ClientDetail, error) {
	return m.createFn(ctx, arg)
}
func (m *mockQuerier) GetClient(ctx context.Context, id string) (db.ClientDetail, error) {
	return m.getFn(ctx, id)
}
func (m *mockQuerier) UpdateClient(ctx context.Context, arg db.UpdateClientParams) (db.ClientDetail, error) {
	return m.updateFn(ctx, arg)
}
func (m *mockQuerier) PatchClient(ctx context.Context, arg db.PatchClientParams) (db.ClientDetail, error) {
	return m.patchFn(ctx, arg)
}

// fakeRuntimeStore is an in-memory providers.RuntimeStoreProvider stand-in,
// mirroring the one in internal/engine/consent_provider_test.go.
type fakeRuntimeStore struct {
	data map[string][]byte
	err  error
}

func newFakeRuntimeStore() *fakeRuntimeStore {
	return &fakeRuntimeStore{data: map[string][]byte{}}
}

func (f *fakeRuntimeStore) key(ns providers.RuntimeStoreNamespace, key string) string {
	return string(ns) + ":" + key
}

func (f *fakeRuntimeStore) Put(_ context.Context, ns providers.RuntimeStoreNamespace, key string, value []byte, _ int64) error {
	if f.err != nil {
		return f.err
	}
	f.data[f.key(ns, key)] = value
	return nil
}

func (f *fakeRuntimeStore) Get(_ context.Context, ns providers.RuntimeStoreNamespace, key string) ([]byte, error) {
	if f.err != nil {
		return nil, f.err
	}
	return f.data[f.key(ns, key)], nil
}

func (f *fakeRuntimeStore) Update(_ context.Context, ns providers.RuntimeStoreNamespace, key string, value []byte) error {
	if f.err != nil {
		return f.err
	}
	f.data[f.key(ns, key)] = value
	return nil
}

func (f *fakeRuntimeStore) Delete(_ context.Context, ns providers.RuntimeStoreNamespace, key string) error {
	if f.err != nil {
		return f.err
	}
	delete(f.data, f.key(ns, key))
	return nil
}

func (f *fakeRuntimeStore) Take(_ context.Context, ns providers.RuntimeStoreNamespace, key string) ([]byte, error) {
	if f.err != nil {
		return nil, f.err
	}
	v := f.data[f.key(ns, key)]
	delete(f.data, f.key(ns, key))
	return v, nil
}

func (f *fakeRuntimeStore) ExtendTTL(_ context.Context, _ providers.RuntimeStoreNamespace, _ string, _ int64) error {
	return f.err
}

var _ providers.RuntimeStoreProvider = (*fakeRuntimeStore)(nil)

func validRSAKey() map[string]string {
	return map[string]string{
		"kty": "RSA",
		"n":   "abc",
		"e":   "AQAB",
	}
}

func validCreateReq() clientmgmt.CreateClientRequest {
	return clientmgmt.CreateClientRequest{
		ClientID:          "client-001",
		ClientName:        "Test App",
		ClientNameLangMap: map[string]string{"eng": "Test App"},
		RpID:              "rp-001",
		LogoURI:           "https://example.com/logo.png",
		RedirectURIs:      []string{"https://example.com/callback"},
		Claims:            []string{"name", "email"},
		AcrValues:         []string{"mosip:idp:acr:static-code"},
		PublicKey:         validRSAKey(),
		GrantTypes:        []string{"authorization_code"},
		AuthMethods:       []string{"private_key_jwt"},
	}
}

func validOIDCCreateReq() clientmgmt.CreateClientRequest {
	req := validCreateReq()
	req.ClientNameLangMap = nil
	return req
}

func stubRow(clientID string) db.ClientDetail {
	return db.ClientDetail{
		ID:            clientID,
		Name:          `{"@none":"Test App","eng":"Test App"}`,
		RpID:          "rp-001",
		LogoUri:       "https://example.com/logo.png",
		RedirectUris:  `["https://example.com/callback"]`,
		Claims:        `["name","email"]`,
		AcrValues:     `["mosip:idp:acr:static-code"]`,
		PublicKey:     `{"kty":"RSA","n":"abc","e":"AQAB"}`,
		PublicKeyHash: "deadbeef",
		GrantTypes:    `["authorization_code"]`,
		AuthMethods:   `["private_key_jwt"]`,
		Status:        "ACTIVE",
		CrDtimes:      time.Now(),
	}
}

func (ts *ServiceTestSuite) TestCreateClient_ClientProfile_Success() {
	t := ts.T()
	q := &mockQuerier{
		createFn: func(_ context.Context, arg db.CreateClientParams) (db.ClientDetail, error) {
			assert.Equal(t, "client-001", arg.ID)
			assert.Equal(t, "ACTIVE", arg.Status)
			assert.Contains(t, arg.Name, `"@none":"Test App"`)
			assert.Contains(t, arg.Name, `"eng":"Test App"`)
			return stubRow(arg.ID), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)

	resp, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, validCreateReq())
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
	assert.Equal(t, "ACTIVE", resp.Status)
	assert.Equal(t, "Test App", resp.ClientName)
}

func (ts *ServiceTestSuite) TestCreateClient_OIDCProfile_StoresPlainName() {
	t := ts.T()
	q := &mockQuerier{
		createFn: func(_ context.Context, arg db.CreateClientParams) (db.ClientDetail, error) {
			assert.Equal(t, "Test App", arg.Name)
			row := stubRow(arg.ID)
			row.Name = arg.Name
			return row, nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)

	resp, err := svc.CreateClient(context.Background(), clientmgmt.ProfileOIDC, validOIDCCreateReq())
	require.NoError(t, err)
	assert.Equal(t, "Test App", resp.ClientName)
}

func (ts *ServiceTestSuite) TestCreateClient_MissingLangMap_ClientProfile() {
	t := ts.T()
	svc := clientmgmt.NewServiceWithQuerier(&mockQuerier{}, nil, 0)
	req := validCreateReq()
	req.ClientNameLangMap = nil
	_, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, req)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_input", ve.Code)
}

func (ts *ServiceTestSuite) TestCreateClient_InvalidClaim() {
	t := ts.T()
	svc := clientmgmt.NewServiceWithQuerier(&mockQuerier{}, nil, 0)
	req := validCreateReq()
	req.Claims = []string{"not-a-claim"}
	_, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, req)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_claim", ve.Code)
}

func (ts *ServiceTestSuite) TestCreateClient_DuplicateClientID() {
	t := ts.T()
	q := &mockQuerier{
		createFn: func(_ context.Context, _ db.CreateClientParams) (db.ClientDetail, error) {
			return db.ClientDetail{}, fmt.Errorf(`ERROR: duplicate key value violates unique constraint "pk_clntdtl_id" (SQLSTATE 23505)`)
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)
	_, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, validCreateReq())
	assert.ErrorIs(t, err, clientmgmt.ErrDuplicateClientID)
}

func (ts *ServiceTestSuite) TestCreateClient_DuplicatePublicKeyHash() {
	t := ts.T()
	q := &mockQuerier{
		createFn: func(_ context.Context, _ db.CreateClientParams) (db.ClientDetail, error) {
			return db.ClientDetail{}, fmt.Errorf(`ERROR: duplicate key value violates unique constraint "uk_clntdtl_public_key_hash" (SQLSTATE 23505)`)
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)
	_, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, validCreateReq())
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_public_key", ve.Code)
}

func (ts *ServiceTestSuite) TestCreateClient_AdditionalConfigRoundTrip() {
	t := ts.T()
	raw := json.RawMessage(`{"userinfo_response_type":"JWS","consent_expire_in_mins":30,"purpose":{"type":"verify","title":{"@none":"Title"}}}`)
	q := &mockQuerier{
		createFn: func(_ context.Context, arg db.CreateClientParams) (db.ClientDetail, error) {
			assert.True(t, arg.AdditionalConfig.Valid)
			assert.JSONEq(t, string(raw), arg.AdditionalConfig.String)
			row := stubRow(arg.ID)
			row.AdditionalConfig = arg.AdditionalConfig
			return row, nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)
	req := validCreateReq()
	req.AdditionalConfig = raw
	resp, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, req)
	require.NoError(t, err)
	assert.Equal(t, "JWS", resp.AdditionalConfig["userinfo_response_type"])
}

func (ts *ServiceTestSuite) TestUpdateClient_NormalizesStatus() {
	t := ts.T()
	q := &mockQuerier{
		updateFn: func(_ context.Context, arg db.UpdateClientParams) (db.ClientDetail, error) {
			assert.Equal(t, "INACTIVE", arg.Status)
			row := stubRow(arg.ID)
			row.Status = arg.Status
			return row, nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)
	req := clientmgmt.UpdateClientRequest{
		ClientName:        "Updated App",
		ClientNameLangMap: map[string]string{"eng": "Updated App"},
		Status:            "inactive",
		LogoURI:           "https://example.com/logo.png",
		RedirectURIs:      []string{"https://example.com/callback"},
		Claims:            []string{"email"},
		AcrValues:         []string{"mosip:idp:acr:static-code"},
		GrantTypes:        []string{"authorization_code"},
		AuthMethods:       []string{"private_key_jwt"},
	}
	resp, err := svc.UpdateClient(context.Background(), clientmgmt.ProfileClient, "client-001", req)
	require.NoError(t, err)
	assert.Equal(t, "INACTIVE", resp.Status)
}

func (ts *ServiceTestSuite) TestUpdateClient_NotFound() {
	t := ts.T()
	q := &mockQuerier{
		updateFn: func(_ context.Context, _ db.UpdateClientParams) (db.ClientDetail, error) {
			return db.ClientDetail{}, sql.ErrNoRows
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)
	req := clientmgmt.UpdateClientRequest{
		ClientName:        "X",
		ClientNameLangMap: map[string]string{},
		Status:            "active",
		LogoURI:           "https://example.com/logo.png",
		RedirectURIs:      []string{"https://example.com/callback"},
		Claims:            []string{"email"},
		AcrValues:         []string{"mosip:idp:acr:static-code"},
		GrantTypes:        []string{"authorization_code"},
		AuthMethods:       []string{"private_key_jwt"},
	}
	_, err := svc.UpdateClient(context.Background(), clientmgmt.ProfileClient, "no-such-client", req)
	assert.ErrorIs(t, err, clientmgmt.ErrClientNotFound)
}

func (ts *ServiceTestSuite) TestPatchClient_EncPublicKey() {
	t := ts.T()
	encKey := map[string]string{"kty": "RSA", "n": "xyz", "e": "AQAB"}
	q := &mockQuerier{
		getFn: func(_ context.Context, _ string) (db.ClientDetail, error) {
			return stubRow("client-001"), nil
		},
		patchFn: func(_ context.Context, arg db.PatchClientParams) (db.ClientDetail, error) {
			assert.True(t, arg.EncPublicKey.Valid)
			assert.Contains(t, arg.EncPublicKey.String, `"kty":"RSA"`)
			assert.True(t, arg.EncPublicKeyHash.Valid)
			assert.NotEmpty(t, arg.EncPublicKeyHash.String)
			assert.False(t, arg.EncPublicKeyCert.Valid)
			row := stubRow(arg.ID)
			row.EncPublicKey = arg.EncPublicKey
			return row, nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)
	req := clientmgmt.PatchClientRequest{
		EncPublicKey: clientmgmt.NullableJWK{Defined: true, Value: encKey},
	}
	fields := clientmgmt.PatchFields{EncPublicKey: true}
	resp, err := svc.PatchClient(context.Background(), "client-001", req, fields)
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
}

func (ts *ServiceTestSuite) TestPatchClient_ClearEncPublicKey() {
	t := ts.T()
	q := &mockQuerier{
		getFn: func(_ context.Context, _ string) (db.ClientDetail, error) {
			row := stubRow("client-001")
			row.EncPublicKey = sql.NullString{String: `{"kty":"RSA"}`, Valid: true}
			return row, nil
		},
		patchFn: func(_ context.Context, arg db.PatchClientParams) (db.ClientDetail, error) {
			assert.False(t, arg.EncPublicKey.Valid)
			assert.False(t, arg.EncPublicKeyHash.Valid)
			assert.False(t, arg.EncPublicKeyCert.Valid)
			return stubRow(arg.ID), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)
	req := clientmgmt.PatchClientRequest{
		EncPublicKey: clientmgmt.NullableJWK{Defined: true, IsNull: true},
	}
	fields := clientmgmt.PatchFields{EncPublicKey: true}
	_, err := svc.PatchClient(context.Background(), "client-001", req, fields)
	require.NoError(t, err)
}

func (ts *ServiceTestSuite) TestGetClient_Success() {
	t := ts.T()
	q := &mockQuerier{
		getFn: func(_ context.Context, id string) (db.ClientDetail, error) {
			return stubRow(id), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, nil, 0)
	resp, err := svc.GetClient(context.Background(), "client-001")
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
	assert.Equal(t, []string{"https://example.com/callback"}, resp.RedirectURIs)
}

func (ts *ServiceTestSuite) TestGetClient_CachesOnMiss() {
	t := ts.T()
	cache := newFakeRuntimeStore()
	q := &mockQuerier{
		getFn: func(_ context.Context, id string) (db.ClientDetail, error) {
			return stubRow(id), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, cache, 60)
	resp, err := svc.GetClient(context.Background(), "client-001")
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
	assert.Len(t, cache.data, 1)
}

func (ts *ServiceTestSuite) TestGetClient_ServesFromCache() {
	t := ts.T()
	cache := newFakeRuntimeStore()
	q := &mockQuerier{
		getFn: func(_ context.Context, _ string) (db.ClientDetail, error) {
			t.Fatal("db should not be queried on a cache hit")
			return db.ClientDetail{}, nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, cache, 60)

	row := stubRow("client-001")
	data, err := json.Marshal(row)
	require.NoError(t, err)
	require.NoError(t, cache.Put(context.Background(), "client:detail", "client-001", data, 60))

	resp, err := svc.GetClient(context.Background(), "client-001")
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
}

func (ts *ServiceTestSuite) TestGetClient_CacheErrorFallsBackToDB() {
	t := ts.T()
	cache := newFakeRuntimeStore()
	cache.err = errors.New("cache unavailable")
	q := &mockQuerier{
		getFn: func(_ context.Context, id string) (db.ClientDetail, error) {
			return stubRow(id), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, cache, 60)
	resp, err := svc.GetClient(context.Background(), "client-001")
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
}

func (ts *ServiceTestSuite) TestUpdateClient_InvalidatesCache() {
	t := ts.T()
	cache := newFakeRuntimeStore()
	data, err := json.Marshal(stubRow("client-001"))
	require.NoError(t, err)
	require.NoError(t, cache.Put(context.Background(), "client:detail", "client-001", data, 60))

	q := &mockQuerier{
		updateFn: func(_ context.Context, arg db.UpdateClientParams) (db.ClientDetail, error) {
			return stubRow(arg.ID), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, cache, 60)
	_, err = svc.UpdateClient(context.Background(), clientmgmt.ProfileClient, "client-001", validUpdateReq())
	require.NoError(t, err)
	assert.Empty(t, cache.data)
}

func (ts *ServiceTestSuite) TestPatchClient_InvalidatesCache() {
	t := ts.T()
	cache := newFakeRuntimeStore()
	data, err := json.Marshal(stubRow("client-001"))
	require.NoError(t, err)
	require.NoError(t, cache.Put(context.Background(), "client:detail", "client-001", data, 60))

	q := &mockQuerier{
		getFn: func(_ context.Context, _ string) (db.ClientDetail, error) {
			return stubRow("client-001"), nil
		},
		patchFn: func(_ context.Context, arg db.PatchClientParams) (db.ClientDetail, error) {
			return stubRow(arg.ID), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, cache, 60)
	req := clientmgmt.PatchClientRequest{LogoURI: "https://example.com/new-logo.png"}
	fields := clientmgmt.PatchFields{LogoURI: true}
	_, err = svc.PatchClient(context.Background(), "client-001", req, fields)
	require.NoError(t, err)
	assert.Empty(t, cache.data)
}

func (ts *ServiceTestSuite) TestUpdateClient_InvalidateCacheFailure_PropagatesError() {
	t := ts.T()
	cache := newFakeRuntimeStore()
	cache.err = errors.New("cache unavailable")

	q := &mockQuerier{
		updateFn: func(_ context.Context, arg db.UpdateClientParams) (db.ClientDetail, error) {
			return stubRow(arg.ID), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, cache, 60)
	_, err := svc.UpdateClient(context.Background(), clientmgmt.ProfileClient, "client-001", validUpdateReq())
	require.Error(t, err)
}

func (ts *ServiceTestSuite) TestPatchClient_InvalidateCacheFailure_PropagatesError() {
	t := ts.T()
	cache := newFakeRuntimeStore()
	cache.err = errors.New("cache unavailable")

	q := &mockQuerier{
		getFn: func(_ context.Context, _ string) (db.ClientDetail, error) {
			return stubRow("client-001"), nil
		},
		patchFn: func(_ context.Context, arg db.PatchClientParams) (db.ClientDetail, error) {
			return stubRow(arg.ID), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q, cache, 60)
	req := clientmgmt.PatchClientRequest{LogoURI: "https://example.com/new-logo.png"}
	fields := clientmgmt.PatchFields{LogoURI: true}
	_, err := svc.PatchClient(context.Background(), "client-001", req, fields)
	require.Error(t, err)
}

func validUpdateReq() clientmgmt.UpdateClientRequest {
	return clientmgmt.UpdateClientRequest{
		ClientName:        "Test App",
		ClientNameLangMap: map[string]string{"eng": "Test App"},
		Status:            "ACTIVE",
		LogoURI:           "https://example.com/logo.png",
		RedirectURIs:      []string{"https://example.com/callback"},
		Claims:            []string{"name", "email"},
		AcrValues:         []string{"mosip:idp:acr:static-code"},
		GrantTypes:        []string{"authorization_code"},
		AuthMethods:       []string{"private_key_jwt"},
	}
}

func (ts *ServiceTestSuite) TestValidatePatch_RejectsClearedRedirectURIs() {
	t := ts.T()
	merged := validUpdateReq()
	merged.RedirectURIs = nil
	fields := clientmgmt.PatchFields{RedirectURIs: true}
	err := clientmgmt.ValidatePatch(clientmgmt.ProfileClient, merged, fields, clientmgmt.NullableJWK{})
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_redirect_uri", ve.Code)
}

func (ts *ServiceTestSuite) TestValidatePatch_RejectsInvalidEncPublicKey() {
	t := ts.T()
	merged := validUpdateReq()
	fields := clientmgmt.PatchFields{EncPublicKey: true}
	encKey := clientmgmt.NullableJWK{Defined: true, Value: map[string]string{"kty": "RSA"}}
	err := clientmgmt.ValidatePatch(clientmgmt.ProfileClient, merged, fields, encKey)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_public_key", ve.Code)
}

func (ts *ServiceTestSuite) TestValidateCreate_InvalidLanguageCode() {
	t := ts.T()
	req := validCreateReq()
	req.ClientNameLangMap = map[string]string{"mhsdfsfd": "Name"}
	err := clientmgmt.ValidateCreate(clientmgmt.ProfileClient, req)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_language_code", ve.Code)
}

func (ts *ServiceTestSuite) TestValidateAdditionalConfig_InvalidResponseType() {
	t := ts.T()
	req := validCreateReq()
	req.AdditionalConfig = json.RawMessage(`{"userinfo_response_type":"swj"}`)
	err := clientmgmt.ValidateCreate(clientmgmt.ProfileClient, req)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_additional_config", ve.Code)
}

func (ts *ServiceTestSuite) TestOAuthProfile_RejectsAdditionalConfig() {
	t := ts.T()
	req := validCreateReq()
	req.AdditionalConfig = json.RawMessage(`{"userinfo_response_type":"JWS"}`)
	err := clientmgmt.ValidateCreate(clientmgmt.ProfileOAuth, req)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_input", ve.Code)
}

type ServiceTestSuite struct {
	suite.Suite
}

func TestServiceTestSuite(t *testing.T) {
	suite.Run(t, new(ServiceTestSuite))
}
