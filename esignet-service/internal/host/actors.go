// Package host provides ThunderID engine host integrations for the embedder.
package host

import (
	"context"
	"encoding/json"
	"errors"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"

	"github.com/mosip/esignet/internal/catalog"
)

type actorProvider struct {
	catalog *catalog.Catalog
}

// NewActorProvider creates a host.ActorProvider backed by the catalog.
func NewActorProvider(c *catalog.Catalog) host.ActorProvider {
	return &actorProvider{catalog: c}
}

func (p *actorProvider) IdentifyEntity(filters map[string]interface{}) (*string, error) {
	username, _ := filters["username"].(string)
	if username == "" {
		return nil, errors.New("username is required")
	}
	user, ok := p.catalog.FindUserByUsername(username)
	if !ok {
		return nil, runtime.ErrNotFound
	}
	id := user.ID
	return &id, nil
}

func (p *actorProvider) GetEntity(entityID string) (*host.Actor, error) {
	if app, ok := p.catalog.ApplicationByID(entityID); ok {
		attrs, err := json.Marshal(map[string]interface{}{
			"name":     app.Name,
			"clientId": app.ClientID,
		})
		if err != nil {
			return nil, err
		}
		return &host.Actor{
			ID:         app.ID,
			EntityType: "application",
			Attributes: attrs,
		}, nil
	}
	// for users return nil, nil
	return nil, nil
}

func (p *actorProvider) SearchEntities(filters map[string]interface{}) ([]*host.Actor, error) {
	username, _ := filters["username"].(string)
	if username == "" {
		return nil, errors.New("username is required")
	}
	user, ok := p.catalog.FindUserByUsername(username)
	if !ok {
		return []*host.Actor{}, nil
	}
	return []*host.Actor{{
		ID:         user.ID,
		EntityType: user.EntityType,
		Attributes: user.Attributes,
	}}, nil
}

func (p *actorProvider) GetApplication(ctx context.Context, appID string) (*host.Application, error) {
	app, ok := p.catalog.ApplicationByID(appID)
	if !ok {
		return nil, runtime.ErrNotFound
	}
	_ = ctx
	return &host.Application{
		ID:       app.ID,
		Name:     app.Name,
		OUID:     app.OUID,
		EntityID: app.ID,
	}, nil
}

func (p *actorProvider) GetInboundClientByEntityID(ctx context.Context, entityID string) (*host.InboundClient, error) {
	app, ok := p.catalog.ApplicationByID(entityID)
	if !ok {
		app, ok = p.catalog.ApplicationByClientID(entityID)
		if !ok {
			return nil, runtime.ErrNotFound
		}
	}
	_ = ctx
	return inboundClientFromApp(app), nil
}

func (p *actorProvider) GetInboundClientByClientID(ctx context.Context, clientID string) (*host.InboundClient, error) {
	app, ok := p.catalog.ApplicationByClientID(clientID)
	if !ok {
		return nil, runtime.ErrNotFound
	}
	_ = ctx
	return inboundClientFromApp(app), nil
}

func (p *actorProvider) GetEntityType(ctx context.Context, typeID string) (*host.EntityType, error) {
	_ = ctx
	_ = typeID
	return nil, runtime.ErrNotFound
}

func inboundClientFromApp(app *catalog.Application) *host.InboundClient {
	client := &host.InboundClient{
		ClientID:                  app.ClientID,
		EntityID:                  app.ID,
		ApplicationID:             app.ID,
		OUID:                      app.OUID,
		GrantTypes:                app.GrantTypes,
		RedirectURIs:              app.RedirectURIs,
		ResponseTypes:             app.ResponseTypes,
		TokenEndpointAuthMethod:   app.TokenEndpointAuthMethod,
		PKCERequired:              app.PKCERequired,
		PublicClient:              app.PublicClient,
		AuthFlowID:                app.AuthFlowID,
		RegistrationFlowID:        app.RegistrationFlowID,
		IsRegistrationFlowEnabled: app.IsRegistrationFlowEnabled,
		RecoveryFlowID:            app.RecoveryFlowID,
		IsRecoveryFlowEnabled:     app.IsRecoveryFlowEnabled,
	}
	if app.Certificate != nil {
		client.Certificate = &host.Certificate{
			Type:  app.Certificate.Type,
			Value: app.Certificate.Value,
		}
	}
	return client
}
