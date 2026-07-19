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

const (

	// Keys used in the client additionalConfig map
	parRequired                = "require_pushed_authorization_requests"
	dpopRequired               = "dpop_bound_access_tokens"
	pkceRequired               = "require_pkce"
	userinfoResponseType       = "userinfo_response_type"
	consentExpireInMins        = "consent_expire_in_mins"
	allowedAuthorizationScopes = "allowed_authorization_scopes"

	// Map keys
	jwks        = "JWKS"
	name        = "name"
	description = "description"
	logoURL     = "logo_url"
	app         = "app"
)

type actorProvider struct {
	clientSvc *clientmgmt.Service
	config    *config.AppConfig
}

// NewActorProvider returns a providers.ActorProvider implementation
func NewActorProvider(clientSvc *clientmgmt.Service, config *config.AppConfig) providers.ActorProvider {
	return &actorProvider{clientSvc: clientSvc, config: config}
}

func (p *actorProvider) GetOAuthClientByClientID(
	ctx context.Context, clientID string,
) (*providers.OAuthClient, *common.ServiceError) {
	client, err := p.clientSvc.GetClient(ctx, clientID)
	if err != nil {
		applog.GetLogger().Debug("OAuth client lookup failed", applog.String("clientId", clientID), applog.Error(err))
		return nil, shared.ClientNotFoundError
	}

	requirePushedAuthorizationRequests, _ := client.AdditionalConfig[parRequired].(bool)
	dpopBoundAccessTokens, _ := client.AdditionalConfig[dpopRequired].(bool)
	pkceRequired, _ := client.AdditionalConfig[pkceRequired].(bool)
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
			Type:  jwks,
			Value: getJWKS(client.PublicKey, client.EncPublicKey),
		},
		RequirePushedAuthorizationRequests: requirePushedAuthorizationRequests,
		DPoPBoundAccessTokens:              dpopBoundAccessTokens,
		AcrValues:                          client.AcrValues,
		UserInfo: &providers.UserInfoConfig{
			ResponseType:   getUserinfoResponseType(client.AdditionalConfig),
			UserAttributes: client.Claims,
		},
		Scopes:      getAllowedScopes(p.config.ScopeClaims, client.AdditionalConfig),
		ScopeClaims: getScopeClaimsMapping(p.config.ScopeClaims, client.Claims),
		Token: &providers.OAuthTokenConfig{
			AccessToken: &providers.AccessTokenConfig{
				UserConfig: &providers.AccessTokenSubConfig{
					Attributes: []string{},
				},
			},
			IDToken: &providers.IDTokenConfig{
				UserAttributes: []string{},
			},
		},
	}, nil
}

func (p *actorProvider) GetOAuthProfileByID(
	ctx context.Context, id string,
) (*providers.OAuthProfile, *common.ServiceError) {
	client, err := p.clientSvc.GetClient(ctx, id)
	if err != nil {
		applog.GetLogger().Debug("OAuth profile lookup failed", applog.String("clientId", id), applog.Error(err))
		return nil, shared.ClientNotFoundError
	}
	requirePushedAuthorizationRequests, _ := client.AdditionalConfig[parRequired].(bool)
	dpopBoundAccessTokens, _ := client.AdditionalConfig[dpopRequired].(bool)
	pkceRequired, _ := client.AdditionalConfig[pkceRequired].(bool)
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
					Attributes: []string{},
				},
			},
			IDToken: &providers.IDTokenConfig{
				UserAttributes: []string{},
			},
		},
		UserInfo: &providers.UserInfoConfig{
			ResponseType:   getUserinfoResponseType(client.AdditionalConfig),
			UserAttributes: client.Claims,
		},
		Scopes:      getAllowedScopes(p.config.ScopeClaims, client.AdditionalConfig),
		ScopeClaims: getScopeClaimsMapping(p.config.ScopeClaims, client.Claims),
		Certificate: &providers.Certificate{
			Type:  jwks,
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
		applog.GetLogger().Debug("inbound client lookup failed", applog.String("clientId", id), applog.Error(err))
		return nil, shared.ClientNotFoundError
	}

	properties := make(map[string]interface{})
	properties[name] = client.ClientName
	properties[description] = client.ClientName
	properties[logoURL] = client.LogoURI

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
			ValidityPeriod: configInt64(client.AdditionalConfig, consentExpireInMins, 0),
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
		applog.GetLogger().Debug("actor lookup failed", applog.String("clientId", id), applog.Error(err))
		return nil, shared.ClientNotFoundError
	}

	clientAttributes := map[string]interface{}{
		name:        client.ClientName,
		description: client.ClientName,
	}
	data, err := json.Marshal(clientAttributes)
	if err != nil {
		applog.GetLogger().Warn("failed to marshal client attributes", applog.Error(err))
	}

	return &providers.Entity{
		ID:               id,
		Category:         providers.EntityCategoryApp,
		Type:             app,
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

// getAllowedScopes combines both OIDC scopes and authorization scopes
func getAllowedScopes(standardScopeClaims map[string][]string, additionalConfig map[string]any) []string {
	scopes := make([]string, 0, len(standardScopeClaims))
	for k := range standardScopeClaims {
		scopes = append(scopes, k)
	}

	additionalScopes, _ := additionalConfig[allowedAuthorizationScopes].([]string)
	return append(scopes, additionalScopes...)
}

// getScopeClaimsMapping builds a scope-to-claims mapping for the standard
// scopes, populating each scope with only the claims present in the given
// claims list.
func getScopeClaimsMapping(standardScopeClaims map[string][]string, claims []string) map[string][]string {
	claimSet := make(map[string]struct{}, len(claims))
	for _, claim := range claims {
		claimSet[claim] = struct{}{}
	}

	mapping := make(map[string][]string, len(standardScopeClaims))
	for scope, scopeClaims := range standardScopeClaims {
		var matched []string
		for _, claim := range scopeClaims {
			if _, ok := claimSet[claim]; ok {
				matched = append(matched, claim)
			}
		}
		mapping[scope] = matched
	}
	return mapping
}

func getUserinfoResponseType(additionalConfig map[string]any) providers.UserInfoResponseType {
	respType, _ := additionalConfig[userinfoResponseType].(string)
	if respType == "JWE" {
		return providers.UserInfoResponseTypeJWE
	}
	return providers.UserInfoResponseTypeJWS
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
