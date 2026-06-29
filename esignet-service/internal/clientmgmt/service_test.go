package clientmgmt_test

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

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

func TestCreateClient_ClientProfile_Success(t *testing.T) {
	q := &mockQuerier{
		createFn: func(_ context.Context, arg db.CreateClientParams) (db.ClientDetail, error) {
			assert.Equal(t, "client-001", arg.ID)
			assert.Equal(t, "ACTIVE", arg.Status)
			assert.Contains(t, arg.Name, `"@none":"Test App"`)
			assert.Contains(t, arg.Name, `"eng":"Test App"`)
			return stubRow(arg.ID), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)

	resp, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, validCreateReq())
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
	assert.Equal(t, "ACTIVE", resp.Status)
	assert.Equal(t, "Test App", resp.ClientName)
}

func TestCreateClient_OIDCProfile_StoresPlainName(t *testing.T) {
	q := &mockQuerier{
		createFn: func(_ context.Context, arg db.CreateClientParams) (db.ClientDetail, error) {
			assert.Equal(t, "Test App", arg.Name)
			row := stubRow(arg.ID)
			row.Name = arg.Name
			return row, nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)

	resp, err := svc.CreateClient(context.Background(), clientmgmt.ProfileOIDC, validOIDCCreateReq())
	require.NoError(t, err)
	assert.Equal(t, "Test App", resp.ClientName)
}

func TestCreateClient_MissingLangMap_ClientProfile(t *testing.T) {
	svc := clientmgmt.NewServiceWithQuerier(&mockQuerier{})
	req := validCreateReq()
	req.ClientNameLangMap = nil
	_, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, req)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_input", ve.Code)
}

func TestCreateClient_InvalidClaim(t *testing.T) {
	svc := clientmgmt.NewServiceWithQuerier(&mockQuerier{})
	req := validCreateReq()
	req.Claims = []string{"not-a-claim"}
	_, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, req)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_claim", ve.Code)
}

func TestCreateClient_DuplicateClientID(t *testing.T) {
	q := &mockQuerier{
		createFn: func(_ context.Context, _ db.CreateClientParams) (db.ClientDetail, error) {
			return db.ClientDetail{}, fmt.Errorf(`ERROR: duplicate key value violates unique constraint "pk_clntdtl_id" (SQLSTATE 23505)`)
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)
	_, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, validCreateReq())
	assert.ErrorIs(t, err, clientmgmt.ErrDuplicateClientID)
}

func TestCreateClient_DuplicatePublicKeyHash(t *testing.T) {
	q := &mockQuerier{
		createFn: func(_ context.Context, _ db.CreateClientParams) (db.ClientDetail, error) {
			return db.ClientDetail{}, fmt.Errorf(`ERROR: duplicate key value violates unique constraint "uk_clntdtl_public_key_hash" (SQLSTATE 23505)`)
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)
	_, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, validCreateReq())
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_public_key", ve.Code)
}

func TestCreateClient_AdditionalConfigRoundTrip(t *testing.T) {
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
	svc := clientmgmt.NewServiceWithQuerier(q)
	req := validCreateReq()
	req.AdditionalConfig = raw
	resp, err := svc.CreateClient(context.Background(), clientmgmt.ProfileClient, req)
	require.NoError(t, err)
	assert.Equal(t, "JWS", resp.AdditionalConfig["userinfo_response_type"])
}

func TestUpdateClient_NormalizesStatus(t *testing.T) {
	q := &mockQuerier{
		updateFn: func(_ context.Context, arg db.UpdateClientParams) (db.ClientDetail, error) {
			assert.Equal(t, "INACTIVE", arg.Status)
			row := stubRow(arg.ID)
			row.Status = arg.Status
			return row, nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)
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

func TestUpdateClient_NotFound(t *testing.T) {
	q := &mockQuerier{
		updateFn: func(_ context.Context, _ db.UpdateClientParams) (db.ClientDetail, error) {
			return db.ClientDetail{}, sql.ErrNoRows
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)
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

func TestPatchClient_EncPublicKey(t *testing.T) {
	encKey := map[string]string{"kty": "RSA", "n": "xyz", "e": "AQAB"}
	q := &mockQuerier{
		getFn: func(_ context.Context, _ string) (db.ClientDetail, error) {
			return stubRow("client-001"), nil
		},
		patchFn: func(_ context.Context, arg db.PatchClientParams) (db.ClientDetail, error) {
			assert.True(t, arg.EncPublicKey.Valid)
			assert.Contains(t, arg.EncPublicKey.String, `"kty":"RSA"`)
			row := stubRow(arg.ID)
			row.EncPublicKey = arg.EncPublicKey
			return row, nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)
	req := clientmgmt.PatchClientRequest{
		EncPublicKey: clientmgmt.NullableJWK{Defined: true, Value: encKey},
	}
	fields := clientmgmt.PatchFields{EncPublicKey: true}
	resp, err := svc.PatchClient(context.Background(), "client-001", req, fields)
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
}

func TestPatchClient_ClearEncPublicKey(t *testing.T) {
	q := &mockQuerier{
		getFn: func(_ context.Context, _ string) (db.ClientDetail, error) {
			row := stubRow("client-001")
			row.EncPublicKey = sql.NullString{String: `{"kty":"RSA"}`, Valid: true}
			return row, nil
		},
		patchFn: func(_ context.Context, arg db.PatchClientParams) (db.ClientDetail, error) {
			assert.False(t, arg.EncPublicKey.Valid)
			return stubRow(arg.ID), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)
	req := clientmgmt.PatchClientRequest{
		EncPublicKey: clientmgmt.NullableJWK{Defined: true, IsNull: true},
	}
	fields := clientmgmt.PatchFields{EncPublicKey: true}
	_, err := svc.PatchClient(context.Background(), "client-001", req, fields)
	require.NoError(t, err)
}

func TestGetClient_Success(t *testing.T) {
	q := &mockQuerier{
		getFn: func(_ context.Context, id string) (db.ClientDetail, error) {
			return stubRow(id), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)
	resp, err := svc.GetClient(context.Background(), "client-001")
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
	assert.Equal(t, []string{"https://example.com/callback"}, resp.RedirectURIs)
}

func TestValidateCreate_InvalidLanguageCode(t *testing.T) {
	req := validCreateReq()
	req.ClientNameLangMap = map[string]string{"mhsdfsfd": "Name"}
	err := clientmgmt.ValidateCreate(clientmgmt.ProfileClient, req)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_language_code", ve.Code)
}

func TestValidateAdditionalConfig_InvalidResponseType(t *testing.T) {
	req := validCreateReq()
	req.AdditionalConfig = json.RawMessage(`{"userinfo_response_type":"swj"}`)
	err := clientmgmt.ValidateCreate(clientmgmt.ProfileClient, req)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_additional_config", ve.Code)
}

func TestOAuthProfile_RejectsAdditionalConfig(t *testing.T) {
	req := validCreateReq()
	req.AdditionalConfig = json.RawMessage(`{"userinfo_response_type":"JWS"}`)
	err := clientmgmt.ValidateCreate(clientmgmt.ProfileOAuth, req)
	var ve *clientmgmt.ValidationError
	require.ErrorAs(t, err, &ve)
	assert.Equal(t, "invalid_input", ve.Code)
}
