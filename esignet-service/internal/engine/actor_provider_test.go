/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"database/sql"
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
	"github.com/mosip/esignet/internal/config"
)

type stubQuerier struct {
	db.Querier
	client db.ClientDetail
	found  bool
}

func (s *stubQuerier) GetClient(_ context.Context, id string) (db.ClientDetail, error) {
	if !s.found || id != s.client.ID {
		return db.ClientDetail{}, sql.ErrNoRows
	}
	return s.client, nil
}

// dbErrorQuerier simulates an infrastructure failure (e.g. connection refused).
type dbErrorQuerier struct {
	db.Querier
}

func (q *dbErrorQuerier) GetClient(_ context.Context, _ string) (db.ClientDetail, error) {
	return db.ClientDetail{}, errors.New("connection refused")
}

func newActorTestService(client db.ClientDetail) *clientmgmt.Service {
	return clientmgmt.NewServiceWithQuerier(&stubQuerier{client: client, found: true}, nil, 0, nil)
}

func testClientRow() db.ClientDetail {
	return db.ClientDetail{
		ID:               "client-001",
		Name:             `{"@none":"Test App"}`,
		RpID:             "rp-001",
		LogoUri:          "https://example.com/logo.png",
		RedirectUris:     `["https://example.com/callback"]`,
		Claims:           `["name","email"]`,
		AcrValues:        `["mosip:idp:acr:static-code"]`,
		PublicKey:        `{"kty":"RSA","n":"abc","e":"AQAB"}`,
		GrantTypes:       `["authorization_code"]`,
		AuthMethods:      `["private_key_jwt"]`,
		Status:           "ACTIVE",
		AdditionalConfig: sql.NullString{String: `{"require_pushed_authorization_requests":true,"dpop_bound_access_tokens":true,"consent_expire_in_mins":30}`, Valid: true},
		CrDtimes:         time.Now(),
	}
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetOAuthClientByClientID() {
	t := ts.T()
	svc := newActorTestService(testClientRow())
	p := NewActorProvider(svc, &config.AppConfig{})

	t.Run("success", func(t *testing.T) {
		client, svcErr := p.GetOAuthClientByClientID(context.Background(), "client-001")
		if svcErr != nil {
			t.Fatalf("GetOAuthClientByClientID: %v", svcErr)
		}
		if client.ClientID != "client-001" {
			t.Errorf("ClientID = %q, want client-001", client.ClientID)
		}
		if !client.RequirePushedAuthorizationRequests || !client.DPoPBoundAccessTokens {
			t.Errorf("expected additional config flags to propagate, got %+v", client)
		}
	})

	t.Run("not found returns nil without error", func(t *testing.T) {
		client, svcErr := p.GetOAuthClientByClientID(context.Background(), "no-such-client")
		if svcErr != nil {
			t.Fatalf("expected nil error for unknown client, got %v", svcErr)
		}
		if client != nil {
			t.Fatalf("expected nil client for unknown client, got %v", client)
		}
	})
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetOAuthClientByClientID_JWEUserInfo() {
	t := ts.T()

	t.Run("JWE with alg propagates encryption alg and fixed enc", func(t *testing.T) {
		row := testClientRow()
		row.AdditionalConfig = sql.NullString{String: `{"userinfo_response_type":"JWE"}`, Valid: true}
		row.EncPublicKey = sql.NullString{String: `{"kty":"RSA","n":"abc","e":"AQAB","alg":"RSA-OAEP-256"}`, Valid: true}
		svc := newActorTestService(row)
		p := NewActorProvider(svc, &config.AppConfig{})

		client, svcErr := p.GetOAuthClientByClientID(context.Background(), "client-001")
		if svcErr != nil {
			t.Fatalf("GetOAuthClientByClientID: %v", svcErr)
		}
		if client.UserInfo.EncryptionAlg != "RSA-OAEP-256" {
			t.Errorf("EncryptionAlg = %q, want RSA-OAEP-256", client.UserInfo.EncryptionAlg)
		}
		if client.UserInfo.EncryptionEnc != encryptionEncA256GCM {
			t.Errorf("EncryptionEnc = %q, want %q", client.UserInfo.EncryptionEnc, encryptionEncA256GCM)
		}
	})

	t.Run("JWE without alg on encryption key is rejected", func(t *testing.T) {
		row := testClientRow()
		row.AdditionalConfig = sql.NullString{String: `{"userinfo_response_type":"JWE"}`, Valid: true}
		row.EncPublicKey = sql.NullString{String: `{"kty":"RSA","n":"abc","e":"AQAB"}`, Valid: true}
		svc := newActorTestService(row)
		p := NewActorProvider(svc, &config.AppConfig{})

		_, svcErr := p.GetOAuthClientByClientID(context.Background(), "client-001")
		if svcErr == nil {
			t.Fatal("expected error for JWE userinfo without an alg on the encryption key")
		}
		if svcErr.Code != "missing_encryption_key_alg" {
			t.Errorf("error code = %q, want missing_encryption_key_alg", svcErr.Code)
		}
	})
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetOAuthClientByClientID_ServerError() {
	t := ts.T()
	svc := clientmgmt.NewServiceWithQuerier(&dbErrorQuerier{}, nil, 0, nil)
	p := NewActorProvider(svc, &config.AppConfig{})

	client, svcErr := p.GetOAuthClientByClientID(context.Background(), "any-client")
	if client != nil {
		t.Fatalf("expected nil client on server error, got %v", client)
	}
	if svcErr == nil {
		t.Fatal("expected non-nil error on server error")
	}
	if svcErr.Type != common.ServerErrorType {
		t.Errorf("expected ServerErrorType, got %v", svcErr.Type)
	}
	if svcErr.Code != "server_error" {
		t.Errorf("expected Code \"server_error\", got %q", svcErr.Code)
	}
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetOAuthClientByClientID_JWEIDToken() {
	t := ts.T()

	t.Run("default response type is JWT with no encryption", func(t *testing.T) {
		svc := newActorTestService(testClientRow())
		p := NewActorProvider(svc, &config.AppConfig{})

		client, svcErr := p.GetOAuthClientByClientID(context.Background(), "client-001")
		if svcErr != nil {
			t.Fatalf("GetOAuthClientByClientID: %v", svcErr)
		}
		if client.Token.IDToken.ResponseType != providers.IDTokenResponseTypeJWT {
			t.Errorf("ResponseType = %q, want %q", client.Token.IDToken.ResponseType, providers.IDTokenResponseTypeJWT)
		}
		if client.Token.IDToken.EncryptionAlg != "" || client.Token.IDToken.EncryptionEnc != "" {
			t.Errorf("expected no encryption fields, got %+v", client.Token.IDToken)
		}
	})

	t.Run("JWE with alg propagates encryption alg and fixed enc", func(t *testing.T) {
		row := testClientRow()
		row.AdditionalConfig = sql.NullString{String: `{"id_token_response_type":"JWE"}`, Valid: true}
		row.EncPublicKey = sql.NullString{String: `{"kty":"RSA","n":"abc","e":"AQAB","alg":"RSA-OAEP-256"}`, Valid: true}
		svc := newActorTestService(row)
		p := NewActorProvider(svc, &config.AppConfig{})

		client, svcErr := p.GetOAuthClientByClientID(context.Background(), "client-001")
		if svcErr != nil {
			t.Fatalf("GetOAuthClientByClientID: %v", svcErr)
		}
		if client.Token.IDToken.ResponseType != providers.IDTokenResponseTypeNESTEDJWT {
			t.Errorf("ResponseType = %q, want %q", client.Token.IDToken.ResponseType, providers.IDTokenResponseTypeNESTEDJWT)
		}
		if client.Token.IDToken.EncryptionAlg != "RSA-OAEP-256" {
			t.Errorf("EncryptionAlg = %q, want RSA-OAEP-256", client.Token.IDToken.EncryptionAlg)
		}
		if client.Token.IDToken.EncryptionEnc != encryptionEncA256GCM {
			t.Errorf("EncryptionEnc = %q, want %q", client.Token.IDToken.EncryptionEnc, encryptionEncA256GCM)
		}
	})

	t.Run("JWE without alg on encryption key is rejected", func(t *testing.T) {
		row := testClientRow()
		row.AdditionalConfig = sql.NullString{String: `{"id_token_response_type":"JWE"}`, Valid: true}
		row.EncPublicKey = sql.NullString{String: `{"kty":"RSA","n":"abc","e":"AQAB"}`, Valid: true}
		svc := newActorTestService(row)
		p := NewActorProvider(svc, &config.AppConfig{})

		_, svcErr := p.GetOAuthClientByClientID(context.Background(), "client-001")
		if svcErr == nil {
			t.Fatal("expected error for JWE id_token without an alg on the encryption key")
		}
		if svcErr.Code != "missing_encryption_key_alg" {
			t.Errorf("error code = %q, want missing_encryption_key_alg", svcErr.Code)
		}
	})
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetOAuthProfileByID() {
	t := ts.T()
	svc := newActorTestService(testClientRow())
	p := NewActorProvider(svc, &config.AppConfig{})

	profile, svcErr := p.GetOAuthProfileByID(context.Background(), "client-001")
	if svcErr != nil {
		t.Fatalf("GetOAuthProfileByID: %v", svcErr)
	}
	if len(profile.Token.AccessToken.UserConfig.Attributes) != 0 {
		t.Errorf("Attributes = %v, want empty", profile.Token.AccessToken.UserConfig.Attributes)
	}
	if profile.Token.IDToken.ResponseType != providers.IDTokenResponseTypeJWT {
		t.Errorf("ResponseType = %q, want %q", profile.Token.IDToken.ResponseType, providers.IDTokenResponseTypeJWT)
	}

	if _, svcErr := p.GetOAuthProfileByID(context.Background(), "no-such-client"); svcErr == nil {
		t.Fatal("expected error for unknown client")
	}
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetOAuthProfileByID_ServerError() {
	t := ts.T()
	svc := clientmgmt.NewServiceWithQuerier(&dbErrorQuerier{}, nil, 0, nil)
	p := NewActorProvider(svc, &config.AppConfig{})

	profile, svcErr := p.GetOAuthProfileByID(context.Background(), "any-client")
	if profile != nil {
		t.Fatalf("expected nil profile on server error, got %v", profile)
	}
	if svcErr == nil {
		t.Fatal("expected non-nil error on server error")
	}
	if svcErr.Type != common.ServerErrorType {
		t.Errorf("expected ServerErrorType, got %v", svcErr.Type)
	}
	if svcErr.Code != "server_error" {
		t.Errorf("expected Code \"server_error\", got %q", svcErr.Code)
	}
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetInboundClientByID() {
	t := ts.T()
	svc := newActorTestService(testClientRow())
	p := NewActorProvider(svc, &config.AppConfig{AuthFlowID: "flow-1", ThemeID: "theme-1", LayoutID: "layout-1"})

	client, svcErr := p.GetInboundClientByID(context.Background(), "client-001")
	if svcErr != nil {
		t.Fatalf("GetInboundClientByID: %v", svcErr)
	}
	if client.AuthFlowID != "flow-1" || client.ThemeID != "theme-1" || client.LayoutID != "layout-1" {
		t.Errorf("client = %+v, unexpected flow/theme/layout ids", client)
	}
	if client.LoginConsent.ValidityPeriod != 30 {
		t.Errorf("LoginConsent.ValidityPeriod = %d, want 30", client.LoginConsent.ValidityPeriod)
	}
	if client.Properties["name"] != "Test App" {
		t.Errorf("Properties[name] = %v, want Test App", client.Properties["name"])
	}

	if _, svcErr := p.GetInboundClientByID(context.Background(), "no-such-client"); svcErr == nil {
		t.Fatal("expected error for unknown client")
	}
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetInboundClientByID_ServerError() {
	t := ts.T()
	svc := clientmgmt.NewServiceWithQuerier(&dbErrorQuerier{}, nil, 0, nil)
	p := NewActorProvider(svc, &config.AppConfig{})

	client, svcErr := p.GetInboundClientByID(context.Background(), "any-client")
	if client != nil {
		t.Fatalf("expected nil client on server error, got %v", client)
	}
	if svcErr == nil {
		t.Fatal("expected non-nil error on server error")
	}
	if svcErr.Type != common.ServerErrorType {
		t.Errorf("expected ServerErrorType, got %v", svcErr.Type)
	}
	if svcErr.Code != "server_error" {
		t.Errorf("expected Code \"server_error\", got %q", svcErr.Code)
	}
}

func (ts *ActorProviderTestSuite) TestActorProvider_AuthenticateActor() {
	t := ts.T()
	p := NewActorProvider(newActorTestService(testClientRow()), &config.AppConfig{})
	if svcErr := p.AuthenticateActor(context.Background(), nil, nil); svcErr == nil {
		t.Fatal("expected NotImplemented service error")
	}
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetActor() {
	t := ts.T()
	p := NewActorProvider(newActorTestService(testClientRow()), &config.AppConfig{})

	entity, svcErr := p.GetActor("client-001")
	if svcErr != nil {
		t.Fatalf("GetActor: %v", svcErr)
	}
	if entity.ID != "client-001" || entity.OUID != "rp-001" {
		t.Errorf("entity = %+v, unexpected ID/OUID", entity)
	}

	if _, svcErr := p.GetActor("no-such-client"); svcErr == nil {
		t.Fatal("expected error for unknown client")
	}
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetActor_ServerError() {
	t := ts.T()
	svc := clientmgmt.NewServiceWithQuerier(&dbErrorQuerier{}, nil, 0, nil)
	p := NewActorProvider(svc, &config.AppConfig{})

	entity, svcErr := p.GetActor("any-client")
	if entity != nil {
		t.Fatalf("expected nil entity on server error, got %v", entity)
	}
	if svcErr == nil {
		t.Fatal("expected non-nil error on server error")
	}
	if svcErr.Type != common.ServerErrorType {
		t.Errorf("expected ServerErrorType, got %v", svcErr.Type)
	}
	if svcErr.Code != "server_error" {
		t.Errorf("expected Code \"server_error\", got %q", svcErr.Code)
	}
}

func (ts *ActorProviderTestSuite) TestActorProvider_GetActorGroups() {
	t := ts.T()
	p := NewActorProvider(newActorTestService(testClientRow()), &config.AppConfig{})
	groups, svcErr := p.GetActorGroups("client-001")
	if groups != nil || svcErr != nil {
		t.Errorf("GetActorGroups() = (%v, %v), want (nil, nil)", groups, svcErr)
	}
}

func (ts *ActorProviderTestSuite) TestExtractJWKs() {
	t := ts.T()
	t.Run("empty", func(t *testing.T) {
		if got := extractJWKs(""); got != nil {
			t.Errorf("extractJWKs(\"\") = %v, want nil", got)
		}
	})

	t.Run("jwks object", func(t *testing.T) {
		got := extractJWKs(`{"keys":[{"kty":"RSA"},{"kty":"EC"}]}`)
		if len(got) != 2 {
			t.Errorf("len(got) = %d, want 2", len(got))
		}
	})

	t.Run("single jwk", func(t *testing.T) {
		got := extractJWKs(`{"kty":"RSA"}`)
		if len(got) != 1 || string(got[0]) != `{"kty":"RSA"}` {
			t.Errorf("got = %v, want single raw JWK", got)
		}
	})
}

func (ts *ActorProviderTestSuite) TestGetJWKS() {
	t := ts.T()
	got := getJWKS(`{"kty":"RSA"}`, `{"kty":"EC"}`)
	if got == "" {
		t.Fatal("expected non-empty JWKS")
	}
}

func (ts *ActorProviderTestSuite) TestConfigInt64() {
	t := ts.T()
	if got := configInt64(nil, "k", 5); got != 5 {
		t.Errorf("configInt64(nil) = %d, want 5", got)
	}
	if got := configInt64(map[string]any{}, "k", 5); got != 5 {
		t.Errorf("configInt64(missing key) = %d, want 5", got)
	}
	if got := configInt64(map[string]any{"k": int64(42)}, "k", 5); got != 42 {
		t.Errorf("configInt64(int64) = %d, want 42", got)
	}
	if got := configInt64(map[string]any{"k": 42}, "k", 5); got != 42 {
		t.Errorf("configInt64(int) = %d, want 42", got)
	}
	if got := configInt64(map[string]any{"k": float64(42)}, "k", 5); got != 42 {
		t.Errorf("configInt64(float64) = %d, want 42", got)
	}
	if got := configInt64(map[string]any{"k": "not-a-number"}, "k", 5); got != 5 {
		t.Errorf("configInt64(unsupported type) = %d, want default 5", got)
	}
}

func (ts *ActorProviderTestSuite) TestGetAllowedScopes() {
	t := ts.T()
	standardScopeClaims := map[string][]string{
		"profile": {"name"},
		"address": {"address"},
		"email":   {"email"},
		"openid":  nil,
	}

	t.Run("base scopes are sorted deterministically", func(t *testing.T) {
		got := getAllowedScopes(standardScopeClaims, nil)
		want := []string{"address", "email", "openid", "profile"}
		if len(got) != len(want) {
			t.Fatalf("getAllowedScopes() = %v, want %v", got, want)
		}
		for i := range want {
			if got[i] != want[i] {
				t.Errorf("getAllowedScopes() = %v, want %v", got, want)
				break
			}
		}
	})

	t.Run("accepts []string additional scopes", func(t *testing.T) {
		additionalConfig := map[string]any{allowedAuthorizationScopes: []string{"custom_scope"}}
		got := getAllowedScopes(standardScopeClaims, additionalConfig)
		if got[len(got)-1] != "custom_scope" {
			t.Errorf("getAllowedScopes() = %v, want last element custom_scope", got)
		}
	})

	t.Run("accepts []any additional scopes decoded from JSON", func(t *testing.T) {
		additionalConfig := map[string]any{allowedAuthorizationScopes: []any{"custom_scope", "other_scope"}}
		got := getAllowedScopes(standardScopeClaims, additionalConfig)
		if got[len(got)-2] != "custom_scope" || got[len(got)-1] != "other_scope" {
			t.Errorf("getAllowedScopes() = %v, want trailing [custom_scope other_scope]", got)
		}
	})
}

type ActorProviderTestSuite struct {
	suite.Suite
}

func TestActorProviderTestSuite(t *testing.T) {
	suite.Run(t, new(ActorProviderTestSuite))
}
