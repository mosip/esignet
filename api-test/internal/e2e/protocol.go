package e2e

import (
	"fmt"
	"strings"
)

// Per-client protocol hardening, and what the relying party does about it.
//
// eSignet exposes three switches in a client's additionalConfig — PKCE, pushed
// authorization requests, and DPoP-bound access tokens. They are enforced by
// the engine at authorize, /oauth2/par, token and userinfo respectively, so
// covering them end to end means registering a client with a given combination
// and then driving the flow that combination demands.

// ClientConfig is the additionalConfig block a scenario's client is registered
// with. The zero value is an unhardened client, which is what every scenario
// that predates these fields gets.
type ClientConfig struct {
	RequirePKCE bool `json:"require_pkce"`
	RequirePAR  bool `json:"require_pushed_authorization_requests"`
	DPoPBound   bool `json:"dpop_bound_access_tokens"`
}

// additionalConfig renders the block for a client-registration request, or nil
// when nothing is switched on — an unhardened client is registered exactly as
// it was before, with no additionalConfig member at all.
func (c ClientConfig) additionalConfig() map[string]any {
	if c == (ClientConfig{}) {
		return nil
	}
	// Every switch is sent explicitly, including the false ones: it makes the
	// registration call in the report say what the client is, rather than
	// leaving the reader to infer it from an absence.
	return map[string]any{
		"require_pkce":                          c.RequirePKCE,
		"require_pushed_authorization_requests": c.RequirePAR,
		"dpop_bound_access_tokens":              c.DPoPBound,
	}
}

// key identifies the registered client this config needs. Scenarios sharing a
// key share a client, so the default (all-off) scenarios keep running against
// one client exactly as they did before — which the order-dependent consent
// cases rely on.
func (c ClientConfig) key() string {
	return fmt.Sprintf("pkce=%t/par=%t/dpop=%t", c.RequirePKCE, c.RequirePAR, c.DPoPBound)
}

// label is the short human form used in report assertions and log lines.
func (c ClientConfig) label() string {
	var on []string
	if c.RequirePKCE {
		on = append(on, "pkce")
	}
	if c.RequirePAR {
		on = append(on, "par")
	}
	if c.DPoPBound {
		on = append(on, "dpop")
	}
	if len(on) == 0 {
		return "none"
	}
	return strings.Join(on, "+")
}

// PKCE methods a scenario can ask the relying party to use.
const (
	pkceS256  = "S256"
	pkcePlain = "plain"
	pkceNone  = "none"
)

// FlowSpec overrides what the relying party actually sends. Left unset, the RP
// follows the client config. Setting a field against the client config is how a
// negative case is written: a client that requires PAR, driven by an RP that
// skips it, must be rejected.
type FlowSpec struct {
	// UsePAR pushes the authorization request to /oauth2/par first and drives
	// authorize with the returned request_uri.
	UsePAR *bool `json:"use_par"`

	// UseDPoP sends a DPoP proof at PAR, token and userinfo, and presents the
	// access token with the DPoP scheme instead of Bearer.
	UseDPoP *bool `json:"use_dpop"`

	// PKCE is S256 (default), plain, or none.
	PKCE string `json:"pkce"`

	// DPoPKeyMismatch mints a second proof key for the token call, so the code
	// bound at authorize cannot be redeemed. Only meaningful with DPoP on.
	DPoPKeyMismatch bool `json:"dpop_key_mismatch"`

	// BearerAtUserinfo presents a DPoP-bound token with the Bearer scheme,
	// which a resource endpoint honouring the binding must refuse.
	BearerAtUserinfo bool `json:"bearer_at_userinfo"`
}

// flowPlan is the resolved answer to "what does the RP do for this scenario".
type flowPlan struct {
	usePAR           bool
	useDPoP          bool
	pkce             string
	dpopKeyMismatch  bool
	bearerAtUserinfo bool
}

// resolveFlow derives what the RP does from the client config, then applies the
// scenario's explicit overrides.
func resolveFlow(cfg ClientConfig, f *FlowSpec) (flowPlan, error) {
	// PKCE defaults to on regardless of require_pkce: the harness has always
	// sent S256, and a client that does not require it still accepts it.
	plan := flowPlan{usePAR: cfg.RequirePAR, useDPoP: cfg.DPoPBound, pkce: pkceS256}
	if f == nil {
		return plan, nil
	}
	if f.UsePAR != nil {
		plan.usePAR = *f.UsePAR
	}
	if f.UseDPoP != nil {
		plan.useDPoP = *f.UseDPoP
	}
	if f.PKCE != "" {
		switch strings.ToLower(f.PKCE) {
		case strings.ToLower(pkceS256):
			plan.pkce = pkceS256
		case pkcePlain:
			plan.pkce = pkcePlain
		case pkceNone:
			plan.pkce = pkceNone
		default:
			return plan, fmt.Errorf("flow.pkce %q is not one of S256, plain, none", f.PKCE)
		}
	}
	plan.dpopKeyMismatch = f.DPoPKeyMismatch
	plan.bearerAtUserinfo = f.BearerAtUserinfo
	return plan, nil
}

// label describes the plan for the report, so a row says what was actually
// driven rather than only what the client required.
func (p flowPlan) label() string {
	parts := []string{"pkce=" + p.pkce}
	if p.usePAR {
		parts = append(parts, "par")
	}
	if p.useDPoP {
		parts = append(parts, "dpop")
	}
	if p.dpopKeyMismatch {
		parts = append(parts, "dpop-key-mismatch")
	}
	if p.bearerAtUserinfo {
		parts = append(parts, "bearer-at-userinfo")
	}
	return strings.Join(parts, ",")
}
