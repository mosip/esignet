/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"strings"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

const (
	// ExecutorNameEsignetAuthorization authorizes the flow's requested permission scopes against
	// the requesting OAuth client's allowed_authorization_scopes.
	ExecutorNameEsignetAuthorization = "eSignetAuthorizationExecutor"

	requestedPermissionsKey  = "requested_permissions"
	clientIDRuntimeKey       = "clientId"
	authorizedPermissionsKey = "authorized_permissions"
	// resourceServerIdentifierKey mirrors the vendor engine's RuntimeKeyResourceServerIdentifier
	// (internal/flow/common.RuntimeKeyResourceServerIdentifier, not exported by the public SDK),
	// which the OAuth layer seeds in RuntimeData before invoking the flow.
	resourceServerIdentifierKey = "resource_server_identifier"
)

type authorizationExecutor struct {
	baseExecutor
	clientSvc   *clientmgmt.Service
	resourceSvc providers.ResourceServerProvider
}

var _ providers.Executor = (*authorizationExecutor)(nil)

// NewAuthorizationExecutor creates an executor that authorizes the requested permission scopes
// against both the resolved resource server's configured scopes and the requesting client's
// allowed_authorization_scopes, and writes the result to RuntimeData as "authorized_permissions".
func NewAuthorizationExecutor(clientSvc *clientmgmt.Service, resourceSvc providers.ResourceServerProvider) providers.Executor {
	return &authorizationExecutor{clientSvc: clientSvc, resourceSvc: resourceSvc}
}

func (e *authorizationExecutor) GetName() string {
	return ExecutorNameEsignetAuthorization
}

func (e *authorizationExecutor) GetType() providers.ExecutorType {
	return providers.ExecutorTypeUtility
}

func (e *authorizationExecutor) Execute(ctx *providers.NodeContext) (*providers.ExecutorResponse, error) {
	execResp := &providers.ExecutorResponse{
		RuntimeData: make(map[string]string),
		Status:      providers.ExecComplete,
	}

	requested := strings.Fields(ctx.RuntimeData[requestedPermissionsKey])
	if len(requested) == 0 {
		return execResp, nil
	}

	// Permission evaluation must be scoped to a resource server, so when none can be resolved the
	// requested permission scopes are dropped rather than evaluated unscoped (which could authorize
	// a scope the client only holds on a different resource server).
	resourceServerID, ok := e.resolveResourceServerID(ctx)
	if !ok {
		applog.GetLogger().Debug(ctx.Context,
			"authorization: no resource server bound to request; dropping requested permission scopes")
		return execResp, nil
	}

	invalid, svcErr := e.resourceSvc.ValidatePermissions(ctx.Context, resourceServerID, requested)
	if svcErr != nil {
		applog.GetLogger().Error(ctx.Context, "authorization: resource server permission validation failed",
			applog.String("resourceServerId", resourceServerID), applog.String("error", svcErr.Code))
		return execResp, nil
	}
	requested = excludeScopes(requested, invalid)
	if len(requested) == 0 {
		return execResp, nil
	}

	clientID := ctx.RuntimeData[clientIDRuntimeKey]
	client, err := e.clientSvc.GetClient(ctx.Context, clientID)
	if err != nil {
		applog.GetLogger().Error(ctx.Context, "authorization: client lookup failed",
			applog.String("clientId", clientID), applog.Error(err))
		return execResp, nil
	}

	allowed := shared.AllowedAuthorizationScopes(client.AdditionalConfig)
	authorized := intersect(requested, allowed)
	execResp.RuntimeData[authorizedPermissionsKey] = strings.Join(authorized, " ")

	return execResp, nil
}

// resolveResourceServerID determines the internal ID of the single resource server the requested
// permission scopes are evaluated against, using the identifier the OAuth layer seeds in
// RuntimeData. An empty identifier asks the provider to resolve the deployment's configured
// default resource server. Returns ok=false when none can be resolved.
func (e *authorizationExecutor) resolveResourceServerID(ctx *providers.NodeContext) (string, bool) {
	if e.resourceSvc == nil {
		return "", false
	}
	rs, svcErr := e.resourceSvc.GetResourceServerByIdentifier(ctx.Context, ctx.RuntimeData[resourceServerIdentifierKey])
	if svcErr != nil {
		return "", false
	}
	return rs.ID, true
}

// intersect returns the elements of requested that are also present in allowed, preserving the
// order of requested.
func intersect(requested, allowed []string) []string {
	allowedSet := make(map[string]struct{}, len(allowed))
	for _, s := range allowed {
		allowedSet[s] = struct{}{}
	}
	out := make([]string, 0, len(requested))
	for _, s := range requested {
		if _, ok := allowedSet[s]; ok {
			out = append(out, s)
		}
	}
	return out
}

// excludeScopes returns the elements of requested that are not present in excluded, preserving
// the order of requested.
func excludeScopes(requested, excluded []string) []string {
	excludedSet := make(map[string]struct{}, len(excluded))
	for _, s := range excluded {
		excludedSet[s] = struct{}{}
	}
	out := make([]string, 0, len(requested))
	for _, s := range requested {
		if _, ok := excludedSet[s]; !ok {
			out = append(out, s)
		}
	}
	return out
}
