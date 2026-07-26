/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

func (ts *BaseExecutorTestSuite) TestDefaults() {
	t := ts.T()
	var e baseExecutor

	if got := e.GetDefaultInputs(); got != nil {
		t.Errorf("GetDefaultInputs() = %v, want nil", got)
	}
	if got := e.GetPrerequisites(); got != nil {
		t.Errorf("GetPrerequisites() = %v, want nil", got)
	}
	if got := e.GetRequiredInputs(&providers.NodeContext{}); got != nil {
		t.Errorf("GetRequiredInputs() = %v, want nil", got)
	}
	if got := e.GetExecutionPolicy("mode"); got != nil {
		t.Errorf("GetExecutionPolicy() = %v, want nil", got)
	}
	if !e.HasRequiredInputs(&providers.NodeContext{}, &providers.ExecutorResponse{}) {
		t.Error("HasRequiredInputs() = false, want true")
	}
	if meta := e.GetMeta(); meta == nil {
		t.Error("GetMeta() = nil, want non-nil")
	}
	if !e.ValidatePrerequisites(&providers.NodeContext{}, &providers.ExecutorResponse{}, nil) {
		t.Error("ValidatePrerequisites() = false, want true")
	}
	if got := e.GetUserIDFromContext(&providers.NodeContext{}, &providers.ExecutorResponse{}, nil); got != "" {
		t.Errorf("GetUserIDFromContext() = %q, want empty", got)
	}
}

func (ts *BaseExecutorTestSuite) TestBuildProviderMetadata() {
	t := ts.T()
	ctx := &providers.NodeContext{
		RuntimeData: map[string]string{
			providerExtendedKeyPrefix + "TransactionID": "txn-1",
			"unrelated_key": "ignored",
		},
	}
	ctx.SetInitiatorRequest(&providers.InitiatorRequest{
		Headers:     map[string][]string{"X-Custom": {"v1"}, "x-custom": {"v2"}},
		QueryParams: map[string][]string{"client_id": {"c1"}},
	})

	metadata := BuildProviderMetadata(ctx)
	if metadata.RuntimeMetadata[providerExtendedKeyPrefix+"TransactionID"][0] != "txn-1" {
		t.Errorf("expected provider_ext_TransactionID to be forwarded, got %v", metadata.RuntimeMetadata)
	}
	if _, ok := metadata.RuntimeMetadata["unrelated_key"]; ok {
		t.Error("expected non-provider_ext runtime keys to be excluded")
	}
	headerValues := metadata.RuntimeMetadata[initiatorHeaderKeyPrefix+"x-custom"]
	if len(headerValues) != 2 {
		t.Errorf("expected differently-cased header values to merge, got %v", headerValues)
	}
	if got := metadata.RuntimeMetadata[initiatorQueryKeyPrefix+"client_id"]; len(got) != 1 || got[0] != "c1" {
		t.Errorf("expected query param to be forwarded verbatim, got %v", got)
	}
}

func (ts *BaseExecutorTestSuite) TestBuildProviderMetadataNoInitiatorRequest() {
	t := ts.T()
	ctx := &providers.NodeContext{RuntimeData: map[string]string{}}
	metadata := BuildProviderMetadata(ctx)
	if len(metadata.RuntimeMetadata) != 0 {
		t.Errorf("expected empty runtime metadata, got %v", metadata.RuntimeMetadata)
	}
}

func (ts *BaseExecutorTestSuite) TestBuildGetAttributesMetadata() {
	t := ts.T()

	t.Run("locale present", func(t *testing.T) {
		ctx := &providers.NodeContext{RuntimeData: map[string]string{requiredLocalesKey: "fr"}}
		metadata := BuildGetAttributesMetadata(ctx)
		if metadata.Locale != "fr" {
			t.Errorf("Locale = %q, want fr", metadata.Locale)
		}
	})

	t.Run("locale absent", func(t *testing.T) {
		ctx := &providers.NodeContext{RuntimeData: map[string]string{}}
		metadata := BuildGetAttributesMetadata(ctx)
		if metadata.Locale != "" {
			t.Errorf("Locale = %q, want empty", metadata.Locale)
		}
	})

	t.Run("locale empty string is not set", func(t *testing.T) {
		ctx := &providers.NodeContext{RuntimeData: map[string]string{requiredLocalesKey: ""}}
		metadata := BuildGetAttributesMetadata(ctx)
		if metadata.Locale != "" {
			t.Errorf("Locale = %q, want empty", metadata.Locale)
		}
	})

	t.Run("nil runtime data", func(t *testing.T) {
		ctx := &providers.NodeContext{}
		metadata := BuildGetAttributesMetadata(ctx)
		if metadata == nil {
			t.Fatal("expected non-nil metadata")
		}
		if len(metadata.RuntimeMetadata) != 0 {
			t.Errorf("expected empty runtime metadata, got %v", metadata.RuntimeMetadata)
		}
	})
}

type BaseExecutorTestSuite struct {
	suite.Suite
}

func TestBaseExecutorTestSuite(t *testing.T) {
	suite.Run(t, new(BaseExecutorTestSuite))
}
