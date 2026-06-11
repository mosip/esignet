package clientmgmt_test

import (
	"context"
	"database/sql"
	"fmt"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
)

// ── mock querier ──────────────────────────────────────────────────────────────

type mockQuerier struct {
	createFn func(context.Context, db.CreateClientParams) (db.ClientDetail, error)
	getFn    func(context.Context, string) (db.ClientDetail, error)
	updateFn func(context.Context, db.UpdateClientParams) (db.ClientDetail, error)
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

// ── helpers ───────────────────────────────────────────────────────────────────

func validCreateReq() clientmgmt.CreateClientRequest {
	return clientmgmt.CreateClientRequest{
		ClientID:     "client-001",
		ClientName:   "Test App",
		RpID:         "rp-001",
		LogoURI:      "https://example.com/logo.png",
		RedirectURIs: []string{"https://example.com/callback"},
		Claims:       []string{"sub", "email"},
		AcrValues:    []string{"mosip:idp:acr:static-code"},
		PublicKey:    `{"kty":"RSA","n":"abc","e":"AQAB"}`,
		GrantTypes:   []string{"authorization_code"},
		AuthMethods:  []string{"private_key_jwt"},
	}
}

func stubRow(clientID string) db.ClientDetail {
	return db.ClientDetail{
		ID:           clientID,
		Name:         "Test App",
		RpID:         "rp-001",
		LogoURI:      "https://example.com/logo.png",
		RedirectUris: `["https://example.com/callback"]`,
		Claims:       `["sub","email"]`,
		AcrValues:    `["mosip:idp:acr:static-code"]`,
		PublicKey:    `{"kty":"RSA","n":"abc","e":"AQAB"}`,
		PublicKeyHash: "deadbeef",
		GrantTypes:   `["authorization_code"]`,
		AuthMethods:  `["private_key_jwt"]`,
		Status:       "ACTIVE",
		CrDtimes:     time.Now(),
	}
}

// ── CreateClient ──────────────────────────────────────────────────────────────

func TestCreateClient_Success(t *testing.T) {
	q := &mockQuerier{
		createFn: func(_ context.Context, arg db.CreateClientParams) (db.ClientDetail, error) {
			assert.Equal(t, "client-001", arg.ID)
			assert.Equal(t, "ACTIVE", arg.Status)
			assert.NotEmpty(t, arg.PublicKeyHash)
			return stubRow(arg.ID), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)

	resp, err := svc.CreateClient(context.Background(), validCreateReq())
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
	assert.Equal(t, "ACTIVE", resp.Status)
	assert.Equal(t, []string{"https://example.com/callback"}, resp.RedirectURIs)
}

func TestCreateClient_MissingRequiredFields(t *testing.T) {
	svc := clientmgmt.NewServiceWithQuerier(&mockQuerier{})

	tests := []struct {
		name string
		mutate func(*clientmgmt.CreateClientRequest)
	}{
		{"missing client_id", func(r *clientmgmt.CreateClientRequest) { r.ClientID = "" }},
		{"missing client_name", func(r *clientmgmt.CreateClientRequest) { r.ClientName = "" }},
		{"missing rp_id", func(r *clientmgmt.CreateClientRequest) { r.RpID = "" }},
		{"missing logo_uri", func(r *clientmgmt.CreateClientRequest) { r.LogoURI = "" }},
		{"missing redirect_uris", func(r *clientmgmt.CreateClientRequest) { r.RedirectURIs = nil }},
		{"missing public_key", func(r *clientmgmt.CreateClientRequest) { r.PublicKey = "" }},
		{"missing grant_types", func(r *clientmgmt.CreateClientRequest) { r.GrantTypes = nil }},
		{"missing auth_methods", func(r *clientmgmt.CreateClientRequest) { r.AuthMethods = nil }},
	}

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			req := validCreateReq()
			tt.mutate(&req)
			_, err := svc.CreateClient(context.Background(), req)
			require.Error(t, err)
		})
	}
}

func TestCreateClient_DuplicatePublicKey(t *testing.T) {
	q := &mockQuerier{
		createFn: func(_ context.Context, _ db.CreateClientParams) (db.ClientDetail, error) {
			// Simulate Postgres unique-violation error (code 23505)
			return db.ClientDetail{}, fmt.Errorf("ERROR: duplicate key value violates unique constraint \"uk_clntdtl_public_key_hash\" (SQLSTATE 23505)")
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)

	_, err := svc.CreateClient(context.Background(), validCreateReq())
	assert.ErrorIs(t, err, clientmgmt.ErrDuplicatePublicKey)
}

// ── UpdateClient ──────────────────────────────────────────────────────────────

func TestUpdateClient_Success(t *testing.T) {
	q := &mockQuerier{
		updateFn: func(_ context.Context, arg db.UpdateClientParams) (db.ClientDetail, error) {
			assert.Equal(t, "client-001", arg.ID)
			assert.Equal(t, "INACTIVE", arg.Status)
			return stubRow(arg.ID), nil
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)

	req := clientmgmt.UpdateClientRequest{
		ClientName:   "Updated App",
		LogoURI:      "https://example.com/logo.png",
		RedirectURIs: []string{"https://example.com/callback"},
		GrantTypes:   []string{"authorization_code"},
		AuthMethods:  []string{"private_key_jwt"},
		Status:       "INACTIVE",
	}
	resp, err := svc.UpdateClient(context.Background(), "client-001", req)
	require.NoError(t, err)
	assert.Equal(t, "client-001", resp.ClientID)
}

func TestUpdateClient_NotFound(t *testing.T) {
	q := &mockQuerier{
		updateFn: func(_ context.Context, _ db.UpdateClientParams) (db.ClientDetail, error) {
			return db.ClientDetail{}, sql.ErrNoRows
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)

	req := clientmgmt.UpdateClientRequest{ClientName: "X", Status: "ACTIVE"}
	_, err := svc.UpdateClient(context.Background(), "no-such-client", req)
	assert.ErrorIs(t, err, clientmgmt.ErrClientNotFound)
}

func TestUpdateClient_MissingRequiredFields(t *testing.T) {
	svc := clientmgmt.NewServiceWithQuerier(&mockQuerier{})

	_, err := svc.UpdateClient(context.Background(), "client-001", clientmgmt.UpdateClientRequest{})
	require.Error(t, err)
}

// ── GetClient ─────────────────────────────────────────────────────────────────

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
	assert.Equal(t, "ACTIVE", resp.Status)
}

func TestGetClient_NotFound(t *testing.T) {
	q := &mockQuerier{
		getFn: func(_ context.Context, _ string) (db.ClientDetail, error) {
			return db.ClientDetail{}, sql.ErrNoRows
		},
	}
	svc := clientmgmt.NewServiceWithQuerier(q)

	_, err := svc.GetClient(context.Background(), "no-such-client")
	assert.ErrorIs(t, err, clientmgmt.ErrClientNotFound)
}
