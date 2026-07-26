/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package executors provides the built-in flow-step executors for the engine.
package executors

import (
	"strings"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

const (
	// ExecutorNameEsignetOTP sends OTP.
	requiredLocalesKey        = "required_locales"
	providerExtendedKeyPrefix = "provider_ext_"
	initiatorQueryKeyPrefix   = "initiator_query_"
	initiatorHeaderKeyPrefix  = "initiator_header_"
)

// baseExecutor implements providers.Executor with the defaults shared by this package's
// executors: no declared inputs, no execution policy, and no prerequisite/user validation.
// Embed it in an executor and override only the methods that need different behavior.
type baseExecutor struct{}

func (baseExecutor) GetDefaultInputs() []providers.Input {
	return nil
}

func (baseExecutor) GetPrerequisites() []providers.Input {
	return nil
}

func (baseExecutor) GetRequiredInputs(_ *providers.NodeContext) []providers.Input {
	return nil
}

func (baseExecutor) GetExecutionPolicy(_ string) *providers.ExecutionPolicy {
	return nil
}

func (baseExecutor) HasRequiredInputs(_ *providers.NodeContext, _ *providers.ExecutorResponse) bool {
	return true
}

func (baseExecutor) GetMeta() *providers.ExecutorMeta {
	return &providers.ExecutorMeta{}
}

func (baseExecutor) ValidatePrerequisites(_ *providers.NodeContext, _ *providers.ExecutorResponse,
	_ providers.AuthnProviderManager) bool {
	return true
}

func (baseExecutor) GetUserIDFromContext(_ *providers.NodeContext, _ *providers.ExecutorResponse,
	_ providers.AuthnProviderManager) string {
	return ""
}

// BuildProviderMetadata constructs the metadata for providers. It includes
// provider_ext_* runtime keys and the initiator request data (headers and query params).
func BuildProviderMetadata(ctx *providers.NodeContext) *providers.AuthnMetadata {
	return &providers.AuthnMetadata{
		RuntimeMetadata: buildRuntimeMetadata(ctx),
	}
}

// BuildGetAttributesMetadata constructs the metadata for fetching user attributes. It includes
// provider_ext_* runtime keys and the initiator request data (headers and query params).
func BuildGetAttributesMetadata(ctx *providers.NodeContext) *providers.GetAttributesMetadata {
	metadata := &providers.GetAttributesMetadata{
		RuntimeMetadata: buildRuntimeMetadata(ctx),
	}

	if locale, exists := ctx.RuntimeData[requiredLocalesKey]; exists && locale != "" {
		metadata.Locale = locale
	}

	return metadata
}

// buildRuntimeMetadata collects provider_ext_* runtime keys and flattens initiator request
// headers/query params into the metadata map.
func buildRuntimeMetadata(ctx *providers.NodeContext) map[string][]string {
	runtimeMetadata := make(map[string][]string)

	if ctx.RuntimeData != nil {
		for key, value := range ctx.RuntimeData {
			if strings.HasPrefix(key, providerExtendedKeyPrefix) {
				runtimeMetadata[key] = []string{value}
			}
		}
	}

	if req := ctx.GetInitiatorRequest(); req != nil {
		// Header names are case-insensitive per RFC 7230, so lowercase the key to give consumers
		// a stable form. Since distinct-casing entries (e.g. "Foo" and "foo") normalize to the
		// same key, merge the value slices instead of overwriting so nothing is silently dropped.
		for name, values := range req.Headers {
			key := initiatorHeaderKeyPrefix + strings.ToLower(name)
			runtimeMetadata[key] = append(runtimeMetadata[key], values...)
		}
		// Query parameter names are case-sensitive; preserve original casing verbatim.
		for name, values := range req.QueryParams {
			runtimeMetadata[initiatorQueryKeyPrefix+name] = values
		}
	}

	return runtimeMetadata
}
