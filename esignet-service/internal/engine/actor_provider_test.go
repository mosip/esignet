/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"database/sql"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

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

func newActorTestService(client db.ClientDetail) *clientmgmt.Service {
	return clientmgmt.NewServiceWithQuerier(&stubQuerier{client: client, found: true})
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

	t.Run("not found", func(t *testing.T) {
		if _, svcErr := p.GetOAuthClientByClientID(context.Background(), "no-such-client"); svcErr == nil {
			t.Fatal("expected error for unknown client")
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
	if len(profile.Token.AccessToken.UserConfig.Attributes) != 2 {
		t.Errorf("Attributes = %v, want 2 claims", profile.Token.AccessToken.UserConfig.Attributes)
	}

	if _, svcErr := p.GetOAuthProfileByID(context.Background(), "no-such-client"); svcErr == nil {
		t.Fatal("expected error for unknown client")
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

type ActorProviderTestSuite struct {
	suite.Suite
}

func TestActorProviderTestSuite(t *testing.T) {
	suite.Run(t, new(ActorProviderTestSuite))
}
