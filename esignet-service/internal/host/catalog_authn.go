package host

import (
	"context"
	"errors"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"

	"github.com/mosip/esignet/internal/catalog"
)

// OTPAuthnProvider extends host.AuthnProvider with OTP send capability.
type OTPAuthnProvider interface {
	host.AuthnProvider
	SendOTP(ctx context.Context, identifiers map[string]interface{},
		metadata *host.AuthnMetadata) (*SendOTPResult, error)
}

type authnProvider struct {
	catalog *catalog.Catalog
}

// NewAuthnProvider creates a host.AuthnProvider backed by the catalog.
func NewAuthnProvider(c *catalog.Catalog) host.AuthnProvider {
	return &authnProvider{catalog: c}
}

func (p *authnProvider) Authenticate(ctx context.Context, identifiers, credentials map[string]interface{},
	metadata *host.AuthnMetadata) (*host.AuthnResult, error) {
	_ = ctx
	_ = metadata
	username, _ := identifiers["username"].(string)
	if username == "" {
		username, _ = credentials["username"].(string)
	}
	password, _ := credentials["password"].(string)
	if username == "" || password == "" {
		return nil, errors.New("username and password are required")
	}
	user, ok := p.catalog.FindUserByUsername(username)
	if !ok || user.Password != password {
		return nil, errors.New("authentication failed")
	}
	return &host.AuthnResult{
		Authenticated: true,
		UserID:        user.ID,
		AuthToken:     user.ID,
		Attributes:    user.Attributes,
	}, nil
}

func (p *authnProvider) GetAttributes(ctx context.Context, token string, requested *host.RequestedAttributes,
	metadata *host.GetAttributesMetadata) (*host.GetAttributesResult, error) {
	_ = ctx
	_ = requested
	_ = metadata
	user, ok := p.catalog.Users[token]
	if !ok {
		return nil, errors.New("user not found")
	}
	return &host.GetAttributesResult{Attributes: user.Attributes}, nil
}
