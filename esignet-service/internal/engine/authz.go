package engine

import (
	"context"
	"log"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"
)

type roleProvider struct{}

// NewRoleProvider returns a permissive role provider for local development.
func NewRoleProvider() host.RoleProvider {
	return &roleProvider{}
}

func (*roleProvider) GetAuthorizedPermissions(
	_ context.Context, entityID string, groups []string, requestedPermissions []string,
) ([]string, error) {
	log.Println("GetAuthorizedPermissions", entityID, groups, requestedPermissions)
	return append([]string(nil), requestedPermissions...), nil
}

func (*roleProvider) GetUserRoles(_ context.Context, entityID string, groupIDs []string) ([]string, error) {
	log.Println("GetUserRoles", entityID, groupIDs)
	return []string{}, nil
}
