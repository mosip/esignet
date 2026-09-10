/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"os"
	"path/filepath"
	"testing"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
	"gopkg.in/yaml.v3"

	"github.com/mosip/esignet/internal/engine/shared"
)

// loadShippedFlow parses the flow definition shipped in data/flows.
func loadShippedFlow(t *testing.T, name string) providers.CompleteFlowDefinition {
	t.Helper()

	raw, err := os.ReadFile(filepath.Join("..", "..", "data", "flows", name+".yaml"))
	if err != nil {
		t.Fatalf("read %s: %v", name, err)
	}
	var flow providers.CompleteFlowDefinition
	if err := yaml.Unmarshal([]byte(os.ExpandEnv(string(raw))), &flow); err != nil {
		t.Fatalf("parse %s: %v", name, err)
	}
	return flow
}

func nodeByID(t *testing.T, flow providers.CompleteFlowDefinition, id string) providers.NodeDefinition {
	t.Helper()

	for _, node := range flow.Nodes {
		if node.ID == id {
			return node
		}
	}
	t.Fatalf("node %q not found in flow %q", id, flow.ID)
	return providers.NodeDefinition{}
}

func identifiersOf(inputs []providers.InputDefinition) []string {
	ids := make([]string, len(inputs))
	for i, input := range inputs {
		ids[i] = input.Identifier
	}
	return ids
}

// TestShippedFlowIdentifierInputs guards the login ID type wiring in flow-esignet.yaml:
// every credential-auth node must declare exactly the identifiers it can receive.
func TestShippedFlowIdentifierInputs(t *testing.T) {
	flow := loadShippedFlow(t, "flow-esignet")
	allLoginIDs := []string{shared.LoginIDUIN, shared.LoginIDPhone, shared.LoginIDEmail, shared.LoginIDNRC}

	tests := []struct {
		node string
		want []string
	}{
		{"basic_cred_auth_uin", []string{shared.LoginIDUIN}},
		{"basic_cred_auth_mobile", []string{shared.LoginIDPhone}},
		{"basic_cred_auth_email", []string{shared.LoginIDEmail}},
		{"basic_cred_auth_nrc", []string{shared.LoginIDNRC}},
		// Reached from all four identifier prompts.
		{"basic_auth", allLoginIDs},
		{"basic_bio_auth", allLoginIDs},
	}

	for _, tt := range tests {
		t.Run(tt.node, func(t *testing.T) {
			node := nodeByID(t, flow, tt.node)
			got := identifiersOf(node.Executor.IdentifierInputs)
			if len(got) != len(tt.want) {
				t.Fatalf("identifierInputs = %v, want %v", got, tt.want)
			}
			for i := range tt.want {
				if got[i] != tt.want[i] {
					t.Fatalf("identifierInputs = %v, want %v", got, tt.want)
				}
			}
		})
	}
}

// TestShippedFlowClearsEveryLoginID guards that back-navigation clears whichever login ID
// the user supplied, so the identifier prompt re-asks instead of reusing a stale value.
func TestShippedFlowClearsEveryLoginID(t *testing.T) {
	flow := loadShippedFlow(t, "flow-esignet")

	for _, nodeID := range []string{"clear_otp_username", "clear_bio_username"} {
		t.Run(nodeID, func(t *testing.T) {
			got := identifiersOf(nodeByID(t, flow, nodeID).Executor.Inputs)
			if len(got) != 4 {
				t.Fatalf("cleared inputs = %v, want all four login IDs", got)
			}
		})
	}
}

// TestShippedOTPFlowKeepsUntypedIdentifier pins the backward-compatible path: a flow that
// offers a single, untyped login ID declares no identifierInputs and keeps "username".
func TestShippedOTPFlowKeepsUntypedIdentifier(t *testing.T) {
	node := nodeByID(t, loadShippedFlow(t, "otp-flow"), "basic_auth")

	if len(node.Executor.IdentifierInputs) != 0 {
		t.Errorf("identifierInputs = %v, want none", node.Executor.IdentifierInputs)
	}
	if got := identifiersOf(node.Executor.Inputs); got[0] != shared.LoginIDUsername {
		t.Errorf("inputs = %v, want %q first", got, shared.LoginIDUsername)
	}
}
