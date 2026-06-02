package host

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"
)

type authorizationProvider struct{}

// NewAuthorizationProvider returns a permissive authorization provider for local development.
func NewAuthorizationProvider() host.AuthorizationProvider {
	return &authorizationProvider{}
}

func (p *authorizationProvider) GetAuthorizedPermissions(ctx context.Context,
	req host.GetAuthorizedPermissionsRequest) (*host.GetAuthorizedPermissionsResponse, error) {
	_ = ctx
	return &host.GetAuthorizedPermissionsResponse{
		AuthorizedPermissions: append([]string(nil), req.RequestedPermissions...),
	}, nil
}
