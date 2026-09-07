/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"context"
	"database/sql"
	"errors"
	"testing"
	"time"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
)

// stubResourceServerProvider is a providers.ResourceServerProvider test double. By default it
// resolves any identifier to a single resource server and validates every permission, so tests
// that don't care about resource-server scoping can use it as a pass-through.
type stubResourceServerProvider struct {
	rs             *providers.ResourceServer
	getErr         *common.ServiceError
	invalid        []string
	invalidErr     *common.ServiceError
	gotIdentifiers []string
}

func passthroughResourceServerProvider() *stubResourceServerProvider {
	return &stubResourceServerProvider{rs: &providers.ResourceServer{ID: "rs-default"}}
}

func (s *stubResourceServerProvider) GetResourceServerByIdentifier(
	_ context.Context, identifier string,
) (*providers.ResourceServer, *common.ServiceError) {
	s.gotIdentifiers = append(s.gotIdentifiers, identifier)
	if s.getErr != nil {
		return nil, s.getErr
	}
	return s.rs, nil
}

func (s *stubResourceServerProvider) GetResourceServer(
	_ context.Context, _ string,
) (*providers.ResourceServer, *common.ServiceError) {
	if s.getErr != nil {
		return nil, s.getErr
	}
	return s.rs, nil
}

func (s *stubResourceServerProvider) ValidatePermissions(
	_ context.Context, _ string, _ []string,
) ([]string, *common.ServiceError) {
	if s.invalidErr != nil {
		return nil, s.invalidErr
	}
	return s.invalid, nil
}

type stubClientQuerier struct {
	db.Querier
	client db.ClientDetail
	found  bool
}

func (s *stubClientQuerier) GetClient(_ context.Context, id string) (db.ClientDetail, error) {
	if !s.found || id != s.client.ID {
		return db.ClientDetail{}, sql.ErrNoRows
	}
	return s.client, nil
}

func (s *stubClientQuerier) GetActiveClient(_ context.Context, id string) (db.ClientDetail, error) {
	if !s.found || id != s.client.ID || s.client.Status != "ACTIVE" {
		return db.ClientDetail{}, sql.ErrNoRows
	}
	return s.client, nil
}

type errorClientQuerier struct {
	db.Querier
}

func (errorClientQuerier) GetClient(_ context.Context, _ string) (db.ClientDetail, error) {
	return db.ClientDetail{}, errors.New("connection refused")
}

func (errorClientQuerier) GetActiveClient(_ context.Context, _ string) (db.ClientDetail, error) {
	return db.ClientDetail{}, errors.New("connection refused")
}

func testAuthzClientRow(additionalConfig string) db.ClientDetail {
	return db.ClientDetail{
		ID:               "client-001",
		Name:             `{"@none":"Test App"}`,
		RpID:             "rp-001",
		RedirectUris:     `["https://example.com/callback"]`,
		Claims:           `["name","email"]`,
		AcrValues:        `["mosip:idp:acr:static-code"]`,
		PublicKey:        `{"kty":"RSA","n":"abc","e":"AQAB"}`,
		GrantTypes:       `["authorization_code"]`,
		AuthMethods:      `["private_key_jwt"]`,
		Status:           "ACTIVE",
		AdditionalConfig: sql.NullString{String: additionalConfig, Valid: additionalConfig != ""},
		CrDtimes:         time.Now(),
	}
}

func nodeCtx(runtimeData map[string]string) *providers.NodeContext {
	return &providers.NodeContext{Context: context.Background(), RuntimeData: runtimeData}
}

func TestAuthorizationExecutor_NoRequestedPermissions(t *testing.T) {
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{}, nil, 0, nil)
	e := NewAuthorizationExecutor(svc, passthroughResourceServerProvider())

	resp, err := e.Execute(nodeCtx(map[string]string{"clientId": "client-001"}))
	require.NoError(t, err)
	assert.Equal(t, providers.ExecComplete, resp.Status)
	assert.Empty(t, resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_FullOverlap(t *testing.T) {
	row := testAuthzClientRow(`{"allowed_authorization_scopes":["read","write"]}`)
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{client: row, found: true}, nil, 0, nil)
	e := NewAuthorizationExecutor(svc, passthroughResourceServerProvider())

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":              "client-001",
		"requested_permissions": "read write",
	}))
	require.NoError(t, err)
	assert.Equal(t, "read write", resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_PartialOverlap(t *testing.T) {
	row := testAuthzClientRow(`{"allowed_authorization_scopes":["read"]}`)
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{client: row, found: true}, nil, 0, nil)
	e := NewAuthorizationExecutor(svc, passthroughResourceServerProvider())

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":              "client-001",
		"requested_permissions": "read write admin",
	}))
	require.NoError(t, err)
	assert.Equal(t, "read", resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_NoOverlap(t *testing.T) {
	row := testAuthzClientRow(`{"allowed_authorization_scopes":["admin"]}`)
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{client: row, found: true}, nil, 0, nil)
	e := NewAuthorizationExecutor(svc, passthroughResourceServerProvider())

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":              "client-001",
		"requested_permissions": "read write",
	}))
	require.NoError(t, err)
	assert.Empty(t, resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_MissingAllowedScopesConfig(t *testing.T) {
	row := testAuthzClientRow("")
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{client: row, found: true}, nil, 0, nil)
	e := NewAuthorizationExecutor(svc, passthroughResourceServerProvider())

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":              "client-001",
		"requested_permissions": "read write",
	}))
	require.NoError(t, err)
	assert.Empty(t, resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_ClientNotFound(t *testing.T) {
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{}, nil, 0, nil)
	e := NewAuthorizationExecutor(svc, passthroughResourceServerProvider())

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":              "no-such-client",
		"requested_permissions": "read write",
	}))
	require.NoError(t, err)
	assert.Equal(t, providers.ExecComplete, resp.Status)
	assert.Empty(t, resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_ClientLookupError(t *testing.T) {
	svc := clientmgmt.NewServiceWithQuerier(errorClientQuerier{}, nil, 0, nil)
	e := NewAuthorizationExecutor(svc, passthroughResourceServerProvider())

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":              "client-001",
		"requested_permissions": "read write",
	}))
	require.NoError(t, err)
	assert.Empty(t, resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_NameAndType(t *testing.T) {
	e := NewAuthorizationExecutor(clientmgmt.NewServiceWithQuerier(&stubClientQuerier{}, nil, 0, nil), passthroughResourceServerProvider())
	assert.Equal(t, ExecutorNameEsignetAuthorization, e.GetName())
	assert.Equal(t, providers.ExecutorTypeUtility, e.GetType())
}

func TestAuthorizationExecutor_NoResourceServerBound_DropsPermissions(t *testing.T) {
	row := testAuthzClientRow(`{"allowed_authorization_scopes":["read","write"]}`)
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{client: row, found: true}, nil, 0, nil)
	resourceSvc := &stubResourceServerProvider{getErr: &common.ServiceError{Code: "resource_server_not_found"}}
	e := NewAuthorizationExecutor(svc, resourceSvc)

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":              "client-001",
		"requested_permissions": "read write",
	}))
	require.NoError(t, err)
	assert.Equal(t, providers.ExecComplete, resp.Status)
	assert.Empty(t, resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_NilResourceServerProvider_DropsPermissions(t *testing.T) {
	row := testAuthzClientRow(`{"allowed_authorization_scopes":["read","write"]}`)
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{client: row, found: true}, nil, 0, nil)
	e := NewAuthorizationExecutor(svc, nil)

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":              "client-001",
		"requested_permissions": "read write",
	}))
	require.NoError(t, err)
	assert.Equal(t, providers.ExecComplete, resp.Status)
	assert.Empty(t, resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_AllPermissionsInvalidForResourceServer_DropsPermissions(t *testing.T) {
	row := testAuthzClientRow(`{"allowed_authorization_scopes":["read","write"]}`)
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{client: row, found: true}, nil, 0, nil)
	resourceSvc := &stubResourceServerProvider{
		rs:      &providers.ResourceServer{ID: "rs-1"},
		invalid: []string{"read", "write"}, // none of the requested scopes are defined on this resource server
	}
	e := NewAuthorizationExecutor(svc, resourceSvc)

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":                   "client-001",
		"requested_permissions":      "read write",
		"resource_server_identifier": "https://api.example.com",
	}))
	require.NoError(t, err)
	assert.Equal(t, providers.ExecComplete, resp.Status)
	assert.Empty(t, resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_ResourceServerFiltersOutOfScopePermissions(t *testing.T) {
	row := testAuthzClientRow(`{"allowed_authorization_scopes":["read","write","admin"]}`)
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{client: row, found: true}, nil, 0, nil)
	resourceSvc := &stubResourceServerProvider{
		rs:      &providers.ResourceServer{ID: "rs-1"},
		invalid: []string{"admin"}, // client is allowed "admin", but it isn't defined on this resource server
	}
	e := NewAuthorizationExecutor(svc, resourceSvc)

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":                   "client-001",
		"requested_permissions":      "read write admin",
		"resource_server_identifier": "https://api.example.com",
	}))
	require.NoError(t, err)
	assert.Equal(t, "read write", resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_ResourceServerIdentifierIgnoresUserInputs(t *testing.T) {
	row := testAuthzClientRow(`{"allowed_authorization_scopes":["read"]}`)
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{client: row, found: true}, nil, 0, nil)
	resourceSvc := passthroughResourceServerProvider()
	e := NewAuthorizationExecutor(svc, resourceSvc)

	resp, err := e.Execute(&providers.NodeContext{
		Context: context.Background(),
		RuntimeData: map[string]string{
			"clientId":              "client-001",
			"requested_permissions": "read",
		},
		UserInputs: map[string]string{"resource_server_identifier": "https://api.example.com"},
	})
	require.NoError(t, err)
	// The identifier is only ever read from RuntimeData, so the provider must see "" rather than
	// the UserInputs value, even though a resource server still resolves (to the deployment default).
	assert.Equal(t, []string{""}, resourceSvc.gotIdentifiers)
	assert.Equal(t, "read", resp.RuntimeData["authorized_permissions"])
}

func TestAuthorizationExecutor_ValidatePermissionsError_DropsPermissions(t *testing.T) {
	row := testAuthzClientRow(`{"allowed_authorization_scopes":["read"]}`)
	svc := clientmgmt.NewServiceWithQuerier(&stubClientQuerier{client: row, found: true}, nil, 0, nil)
	resourceSvc := &stubResourceServerProvider{
		rs:         &providers.ResourceServer{ID: "rs-1"},
		invalidErr: &common.ServiceError{Code: "internal_error"},
	}
	e := NewAuthorizationExecutor(svc, resourceSvc)

	resp, err := e.Execute(nodeCtx(map[string]string{
		"clientId":              "client-001",
		"requested_permissions": "read",
	}))
	require.NoError(t, err)
	assert.Empty(t, resp.RuntimeData["authorized_permissions"])
}
