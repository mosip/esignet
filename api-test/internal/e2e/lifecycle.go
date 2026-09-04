/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package e2e

import (
	"context"
	"encoding/json"
	"fmt"
	"net/http"
	"strings"
	"time"

	"github.com/mosip/esignet/api-test/internal/result"
)

// Client statuses, spelled as eSignet stores and returns them.
const (
	statusActive   = "ACTIVE"
	statusInactive = "INACTIVE"
)

// The points in the flow at which a scenario may flip its client's status; after_authorize also exercises the client-cache invalidation the status write triggers.
const (
	stageBeforeAuthorize = "before_authorize"
	stageAfterAuthorize  = "after_authorize"
	stageAfterToken      = "after_token"
)

// ClientLifecycle drives the registered client's status mid-scenario; a scenario carrying one gets its OWN client, since deactivating a shared one would silently break every other scenario using it.
type ClientLifecycle struct {
	// Deactivate names the stage at which the client is set INACTIVE.
	Deactivate string `json:"deactivate"`

	// Reactivate sets the client back to ACTIVE before the flow continues; it is the positive control proving the same plumbing leaves the client usable.
	Reactivate bool `json:"reactivate"`
}

// stage returns the normalized deactivation stage, or "" when unset.
func (l *ClientLifecycle) stage() string {
	if l == nil {
		return ""
	}
	return strings.ToLower(strings.TrimSpace(l.Deactivate))
}

// validate reports why the lifecycle spec cannot be run, or "" when it is well formed.
func (l *ClientLifecycle) validate() string {
	if l == nil {
		return ""
	}
	switch l.stage() {
	case stageBeforeAuthorize, stageAfterAuthorize, stageAfterToken:
		return ""
	case "":
		return "client_lifecycle.deactivate is required (" +
			stageBeforeAuthorize + "|" + stageAfterAuthorize + "|" + stageAfterToken + ")"
	default:
		return fmt.Sprintf("client_lifecycle.deactivate %q is not a stage (%s|%s|%s)",
			l.Deactivate, stageBeforeAuthorize, stageAfterAuthorize, stageAfterToken)
	}
}

// label describes the lifecycle for the report, e.g. "deactivated before_authorize".
func (l *ClientLifecycle) label() string {
	if l == nil {
		return ""
	}
	if l.Reactivate {
		return "deactivated+reactivated " + l.stage()
	}
	return "deactivated " + l.stage()
}

// applyAt performs the lifecycle's status writes if stage is the one it names; a write eSignet refuses is an error, since the scenario's premise never held.
func (r *Runner) applyAt(ctx context.Context, calls *[]result.HTTPCall, cl *testClient, l *ClientLifecycle, stage string) ([]result.Assertion, error) {
	if l == nil || l.stage() != stage {
		return nil, nil
	}
	var out []result.Assertion
	a, err := r.setClientStatus(ctx, calls, cl.clientID, statusInactive)
	out = append(out, a...)
	if err != nil {
		return out, err
	}
	if l.Reactivate {
		a, err = r.setClientStatus(ctx, calls, cl.clientID, statusActive)
		out = append(out, a...)
		if err != nil {
			return out, err
		}
	}
	return out, nil
}

// setClientStatus patches clientID to want and reads it back, always via eSignet's own client-mgmt (even for mosip) since PMS refuses reactivation (PMS_ESI_008).
func (r *Runner) setClientStatus(ctx context.Context, calls *[]result.HTTPCall, clientID, want string) ([]result.Assertion, error) {
	body, err := json.Marshal(map[string]any{
		"requestTime": time.Now().UTC().Format("2006-01-02T15:04:05.000Z"),
		"request":     map[string]any{"status": want},
	})
	if err != nil {
		return nil, fmt.Errorf("marshal status patch: %w", err)
	}
	label := "set client " + strings.ToLower(want)
	status, rb, err := r.do(ctx, calls, label, http.MethodPatch, r.Base+"/client-mgmt/client/"+clientID,
		map[string]string{"Content-Type": "application/json", "Authorization": "Bearer " + r.AdminToken}, string(body))
	if err != nil {
		return nil, fmt.Errorf("%s: %w", label, err)
	}
	if code := firstErrorCode(rb); code != "" {
		return nil, fmt.Errorf("%s rejected (HTTP %d): %s", label, status, code)
	}
	if status < 200 || status > 299 {
		return nil, fmt.Errorf("%s failed (HTTP %d): %s", label, status, snippet(rb))
	}

	// Read it back rather than trusting the patch response's echo, since the engine resolves clients through a cache the write is supposed to invalidate.
	got, err := r.readClientStatus(ctx, calls, clientID)
	if err != nil {
		return nil, err
	}
	return []result.Assertion{{
		Field: "client status (" + strings.ToLower(want) + ")", Expected: want,
		Actual: got, Passed: got == want,
	}}, nil
}

// readClientStatus GETs the client and returns its stored status.
func (r *Runner) readClientStatus(ctx context.Context, calls *[]result.HTTPCall, clientID string) (string, error) {
	status, rb, err := r.do(ctx, calls, "read client status", http.MethodGet, r.Base+"/client-mgmt/client/"+clientID,
		map[string]string{"Authorization": "Bearer " + r.AdminToken}, "")
	if err != nil {
		return "", fmt.Errorf("read client status: %w", err)
	}
	if code := firstErrorCode(rb); code != "" {
		return "", fmt.Errorf("read client status rejected (HTTP %d): %s", status, code)
	}
	if status < 200 || status > 299 {
		return "", fmt.Errorf("read client status failed (HTTP %d): %s", status, snippet(rb))
	}
	var resp struct {
		Response struct {
			Status string `json:"status"`
		} `json:"response"`
	}
	if err := json.Unmarshal(rb, &resp); err != nil {
		return "", fmt.Errorf("read client status: parse response (HTTP %d): %w", status, err)
	}
	return resp.Response.Status, nil
}
