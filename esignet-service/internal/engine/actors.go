// Package engine provides ThunderID engine host integrations for the embedder.
package engine

import (
	"context"
	"encoding/json"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"

	"github.com/mosip/esignet/internal/clientmgmt"
)

type actorProvider struct {
	clientSvc *clientmgmt.Service
	config    Config
}

// NewActorProvider returns a minimal host.ActorProvider stub. Declarative resources
// supply applications, flows, and related SoR data to the engine directly.
func NewActorProvider(clientSvc *clientmgmt.Service, config Config) host.ActorProvider {
	return &actorProvider{clientSvc: clientSvc, config: config}
}

func (*actorProvider) IdentifyEntity(map[string]any) (*string, error) {
	return nil, runtime.ErrNotFound
}

func (p *actorProvider) GetEntity(entityID string) (*host.Actor, error) {
	client, err := p.clientSvc.GetClient(context.Background(), entityID)
	if err != nil {
		return nil, err
	}

	systemAttributes := map[string]any{
		"name":        client.ClientName,
		"description": client.ClientName,
	}
	systemAttributesBytes, err := json.Marshal(systemAttributes)
	if err != nil {
		return nil, err
	}
	return &host.Actor{
		ID:               client.ClientID,
		EntityType:       "client",
		OUID:             client.RpID,
		SystemAttributes: systemAttributesBytes,
	}, nil
}

func (*actorProvider) SearchEntities(map[string]any) ([]*host.Actor, error) {
	return nil, nil
}

func (p *actorProvider) GetApplication(ctx context.Context, appID string) (*host.Application, error) {
	client, err := p.clientSvc.GetClient(ctx, appID)
	if err != nil {
		return nil, err
	}
	return &host.Application{
		ID:       client.RpID,
		Name:     client.ClientName,
		OUID:     client.RpID,
		EntityID: client.ClientID,
		ThemeID:  p.config.ThemeID,
		LayoutID: p.config.LayoutID,
	}, nil
}

func (p *actorProvider) GetInboundClientByEntityID(ctx context.Context, entityID string) (*host.InboundClient, error) {
	client, err := p.clientSvc.GetClient(ctx, entityID)
	if err != nil {
		return nil, err
	}
	return inboundClientFromDB(client, p.config), nil
}

func (p *actorProvider) GetInboundClientByClientID(ctx context.Context, clientID string) (*host.InboundClient, error) {
	client, err := p.clientSvc.GetClient(ctx, clientID)
	if err != nil {
		return nil, err
	}
	return inboundClientFromDB(client, p.config), nil
}

func inboundClientFromDB(client clientmgmt.ClientResponse, cfg Config) *host.InboundClient {
	tokenAuthMethod := "private_key_jwt"
	if len(client.AuthMethods) > 0 {
		tokenAuthMethod = client.AuthMethods[0]
	}
	pkceRequired := client.AdditionalConfig["require_pkce"] == "true" ||
		client.AdditionalConfig["pkce_required"] == "true"
	return &host.InboundClient{
		ClientID:                client.ClientID,
		EntityID:                client.ClientID,
		ApplicationID:           client.RpID,
		OUID:                    client.RpID,
		LogoURL:                 client.LogoURI,
		GrantTypes:              client.GrantTypes,
		RedirectURIs:            client.RedirectURIs,
		ResponseTypes:           []string{"code"},
		TokenEndpointAuthMethod: tokenAuthMethod,
		PKCERequired:            pkceRequired,
		PublicClient:            false,
		Certificate: &host.Certificate{
			Type:  "JWK",
			Value: client.PublicKey,
		},
		AuthFlowID:                cfg.AuthFlowID,
		RegistrationFlowID:        cfg.RegistrationFlowID,
		IsRegistrationFlowEnabled: false,
		RecoveryFlowID:            cfg.RecoveryFlowID,
		IsRecoveryFlowEnabled:     false,
	}
}

func (p *actorProvider) GetEntityType(_ context.Context, typeID string) (*host.EntityType, error) {
	return &host.EntityType{
		ID:   typeID,
		Name: "application",
	}, nil
}
