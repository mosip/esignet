/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package engine provides ThunderID engine host integrations for the embedder.
package engine

import (
	"context"
	"encoding/json"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

type actorProvider struct {
	clientSvc *clientmgmt.Service
	config    *config.AppConfig
}

// NewActorProvider returns a minimal host.ActorProvider stub. Declarative resources
// supply applications, flows, and related SoR data to the engine directly.
func NewActorProvider(clientSvc *clientmgmt.Service, config *config.AppConfig) providers.ActorProvider {
	return &actorProvider{clientSvc: clientSvc, config: config}
}

func (p *actorProvider) GetOAuthClientByClientID(
	ctx context.Context, clientID string,
) (*providers.OAuthClient, *common.ServiceError) {
	client, err := p.clientSvc.GetClient(ctx, clientID)
	if err != nil {
		return nil, shared.ClientNotFoundError
	}
	requirePushedAuthorizationRequests, _ := client.AdditionalConfig["require_pushed_authorization_requests"].(bool)
	dpopBoundAccessTokens, _ := client.AdditionalConfig["dpop_bound_access_tokens"].(bool)
	pkceRequired, _ := client.AdditionalConfig["require_pkce"].(bool)
	return &providers.OAuthClient{
		ID:                      client.ClientID,
		OUID:                    client.RpID,
		ClientID:                client.ClientID,
		GrantTypes:              []providers.GrantType{providers.GrantTypeAuthorizationCode},
		RedirectURIs:            client.RedirectURIs,
		ResponseTypes:           []providers.ResponseType{providers.ResponseTypeCode},
		TokenEndpointAuthMethod: providers.TokenEndpointAuthMethodPrivateKeyJWT,
		PKCERequired:            pkceRequired,
		PublicClient:            false,
		Certificate: &providers.Certificate{
			Type:  "JWKS",
			Value: getJWKS(client.PublicKey, client.EncPublicKey),
		},
		RequirePushedAuthorizationRequests: requirePushedAuthorizationRequests,
		DPoPBoundAccessTokens:              dpopBoundAccessTokens,
		AcrValues:                          client.AcrValues,
	}, nil
}

func (p *actorProvider) GetOAuthProfileByID(
	ctx context.Context, id string,
) (*providers.OAuthProfile, *common.ServiceError) {
	client, err := p.clientSvc.GetClient(ctx, id)
	if err != nil {
		return nil, shared.ClientNotFoundError
	}
	requirePushedAuthorizationRequests, _ := client.AdditionalConfig["require_pushed_authorization_requests"].(bool)
	dpopBoundAccessTokens, _ := client.AdditionalConfig["dpop_bound_access_tokens"].(bool)
	pkceRequired, _ := client.AdditionalConfig["require_pkce"].(bool)
	return &providers.OAuthProfile{
		RedirectURIs:                       client.RedirectURIs,
		GrantTypes:                         client.GrantTypes,
		ResponseTypes:                      []string{string(providers.ResponseTypeCode)},
		TokenEndpointAuthMethod:            string(providers.TokenEndpointAuthMethodPrivateKeyJWT),
		PKCERequired:                       pkceRequired,
		PublicClient:                       false,
		RequirePushedAuthorizationRequests: requirePushedAuthorizationRequests,
		DPoPBoundAccessTokens:              dpopBoundAccessTokens,
		IncludeActClaim:                    false,
		Token: &providers.OAuthTokenConfig{
			AccessToken: &providers.AccessTokenConfig{
				UserConfig: &providers.AccessTokenSubConfig{
					ValidityPeriod: 5 * 60,
					Attributes:     client.Claims,
				},
			},
			IDToken: &providers.IDTokenConfig{
				ValidityPeriod: 5 * 60,
				UserAttributes: []string{},
			},
		},
		Scopes: []string{"openid"},
		UserInfo: &providers.UserInfoConfig{
			ResponseType:   providers.UserInfoResponseTypeJSON,
			UserAttributes: client.Claims,
		},
		ScopeClaims: map[string][]string{
			"openid": {"sub"},
		},
		Certificate: &providers.Certificate{
			Type:  "JWKS",
			Value: getJWKS(client.PublicKey, client.EncPublicKey),
		},
		AcrValues: client.AcrValues,
	}, nil
}

func (p *actorProvider) GetInboundClientByID(
	ctx context.Context, id string,
) (*providers.InboundClient, *common.ServiceError) {
	client, err := p.clientSvc.GetClient(ctx, id)
	if err != nil {
		return nil, shared.ClientNotFoundError
	}

	properties := make(map[string]interface{})
	properties["name"] = client.ClientName
	properties["description"] = client.ClientName
	properties["logo_url"] = client.LogoURI

	return &providers.InboundClient{
		ID:                        client.ClientID,
		AuthFlowID:                p.config.AuthFlowID,
		IsRegistrationFlowEnabled: false,
		IsRecoveryFlowEnabled:     false,
		ThemeID:                   p.config.ThemeID,
		LayoutID:                  p.config.LayoutID,
		Assertion: &providers.AssertionConfig{
			ValidityPeriod: 5 * 60, // 5 minutes
			UserAttributes: client.Claims,
		},
		LoginConsent: &providers.LoginConsentConfig{
			ValidityPeriod: configInt64(client.AdditionalConfig, "consent_expire_in_mins", 0),
		},
		Properties: properties,
		IsReadOnly: false,
	}, nil
}

func (p *actorProvider) AuthenticateActor(
	_ context.Context, _, _ map[string]interface{},
) *common.ServiceError {
	return shared.NotImplementedError
}

func (p *actorProvider) GetActor(id string) (*providers.Entity, *common.ServiceError) {
	client, err := p.clientSvc.GetClient(context.Background(), id)
	if err != nil {
		return nil, shared.ClientNotFoundError
	}

	clientAttributes := map[string]interface{}{
		"name":        client.ClientName,
		"description": client.ClientName,
	}
	data, err := json.Marshal(clientAttributes)
	if err != nil {
		applog.GetLogger().Warn("failed to marshal client attributes", applog.Error(err))
	}

	return &providers.Entity{
		ID:               id,
		Category:         providers.EntityCategoryApp,
		Type:             "app",
		State:            providers.EntityStateActive,
		OUID:             client.RpID,
		OUHandle:         client.RpID,
		SystemAttributes: json.RawMessage(data),
		IsReadOnly:       true,
	}, nil
}

func (p *actorProvider) GetActorGroups(_ string) ([]providers.EntityGroup, *common.ServiceError) {
	return nil, nil
}

func getJWKS(publicKey string, encPublicKey string) string {
	keys := extractJWKs(publicKey)
	keys = append(keys, extractJWKs(encPublicKey)...)
	data, err := json.Marshal(map[string][]json.RawMessage{"keys": keys})
	if err != nil {
		return publicKey
	}
	return string(data)
}

// extractJWKs returns the individual keys contained in a JWK or JWKS JSON
// string. It returns nil for empty or unparsable input.
func extractJWKs(jwk string) []json.RawMessage {
	if jwk == "" {
		return nil
	}
	var jwks struct {
		Keys []json.RawMessage `json:"keys"`
	}
	if err := json.Unmarshal([]byte(jwk), &jwks); err == nil && jwks.Keys != nil {
		return jwks.Keys
	}
	return []json.RawMessage{json.RawMessage(jwk)}
}

func configInt64(cfg map[string]any, key string, defaultValue int64) int64 {
	if cfg == nil {
		return defaultValue
	}
	v, ok := cfg[key]
	if !ok {
		return defaultValue
	}
	switch n := v.(type) {
	case int64:
		return n
	case int:
		return int64(n)
	case float64:
		return int64(n)
	default:
		return defaultValue
	}
}
