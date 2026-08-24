package e2e

import (
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"
	"math/big"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/mosip/esignet/api-test/internal/result"
)

func specWith(names ...string) Spec {
	var s Spec
	for _, n := range names {
		s.Scenarios = append(s.Scenarios, Scenario{Name: n, AuthFactor: "otp"})
	}
	return s
}

func TestRegistrationFailureRows_EnvNotReadyWhenPartnerMissing(t *testing.T) {
	// mosip with no PMS config -> every scenario reported ENV_NOT_READY (not run),
	// so the testcases stay visible in the report while onboarding is pending.
	r := &Runner{Plugin: "mosip"} // PMSBaseURL/AuthPartnerID/PolicyID all empty
	spec := specWith("otp positive", "otp negative", "password positive")
	rows := r.registrationFailureRows(spec, nil, errors.New("needs AUTH_PARTNER_ID"))

	if len(rows) != 3 {
		t.Fatalf("got %d rows, want 3 (one per scenario)", len(rows))
	}
	for _, row := range rows {
		if row.HarnessOutcome != result.OutcomeEnvNotReady {
			t.Errorf("%s: HarnessOutcome = %q, want %q", row.Module, row.HarnessOutcome, result.OutcomeEnvNotReady)
		}
		if row.Result == "FAILED" {
			t.Errorf("%s: ENV_NOT_READY row should not be FAILED", row.Module)
		}
	}
}

func TestRegistrationFailureRows_FailedWhenConfiguredButRejected(t *testing.T) {
	// mosip fully configured but PMS rejects -> scenarios reported FAILED.
	r := &Runner{Plugin: "mosip", PMSBaseURL: "https://pms/x", AuthPartnerID: "p", PolicyID: "pol"}
	spec := specWith("otp positive", "otp negative")
	rows := r.registrationFailureRows(spec, nil, errors.New("PMS create rejected: policy_not_found"))

	if len(rows) != 2 {
		t.Fatalf("got %d rows, want 2", len(rows))
	}
	for _, row := range rows {
		if row.Result != "FAILED" {
			t.Errorf("%s: Result = %q, want FAILED", row.Module, row.Result)
		}
		if len(row.FailedConditions) == 0 {
			t.Errorf("%s: expected a failed condition with the registration error", row.Module)
		}
	}
}

func TestRegistrationFailureRows_FallbackWhenNoScenarios(t *testing.T) {
	r := &Runner{Plugin: "mosip"}
	rows := r.registrationFailureRows(Spec{}, nil, errors.New("boom"))
	if len(rows) != 1 {
		t.Fatalf("got %d rows, want 1 fallback row", len(rows))
	}
	if rows[0].Result != "FAILED" {
		t.Errorf("fallback row Result = %q, want FAILED", rows[0].Result)
	}
}

// The client-registration call is real eSignet traffic, so a successful run must still report it.
func TestAttachSetupCalls(t *testing.T) {
	setup := []result.HTTPCall{{Seq: 1, At: 100, Label: "create client", Method: "POST", URL: "https://esignet/client-mgmt/client", Status: 201}}
	out := []result.ModuleResult{
		{Module: "otp positive", Calls: []result.HTTPCall{{Seq: 1, At: 200, Label: "flow/execute", Method: "POST"}}},
		{Module: "otp negative"},
	}

	attachSetupCalls(out, setup)

	if len(out[0].Calls) != 2 {
		t.Fatalf("first row Calls = %d, want 2 (registration + scenario)", len(out[0].Calls))
	}
	if out[0].Calls[0].Label != "create client" || out[0].Calls[1].Label != "flow/execute" {
		t.Errorf("Calls not in chronological order: %+v", out[0].Calls)
	}
	// CollapseCalls renumbers on its own axis (chronological), which must not
	// collide: both inputs numbered their own Seq from 1 independently.
	if out[0].Calls[0].Seq != 1 || out[0].Calls[1].Seq != 2 {
		t.Errorf("Calls not renumbered: %+v", out[0].Calls)
	}
	// The second row is untouched — only the first carries the setup trace.
	if len(out[1].Calls) != 0 {
		t.Errorf("second row should not receive the registration call: %+v", out[1].Calls)
	}
}

func TestAttachSetupCallsNoopOnEmptyInputs(t *testing.T) {
	// No rows (e.g. a zero-scenario spec) — nothing to attach to, must not panic.
	attachSetupCalls(nil, []result.HTTPCall{{Label: "create client"}})

	// No setup calls (e.g. registration wasn't captured) — row must stay as-is.
	out := []result.ModuleResult{{Module: "otp positive", Calls: []result.HTTPCall{{Label: "flow/execute"}}}}
	attachSetupCalls(out, nil)
	if len(out[0].Calls) != 1 || out[0].Calls[0].Label != "flow/execute" {
		t.Errorf("row mutated with no setup calls to attach: %+v", out[0].Calls)
	}
}

// A userinfo JWT signed by an unknown key must fail the scenario, not merely record a claim.
func TestAssertClaimsFailsOnUnverifiedJWS(t *testing.T) {
	sc := Scenario{Name: "userinfo", ExpectPresent: []string{"sub"}}
	claims := map[string]any{"sub": "abc", "_jws_verified": false, "_jws_error": "no JWKS key verified the signature"}

	got := assertClaims(sc, claims)
	sig := -1
	for i, a := range got {
		if a.Field == "userinfo JWS signature" {
			sig = i
		}
	}
	if sig < 0 {
		t.Fatalf("no signature assertion emitted: %+v", got)
	}
	if got[sig].Passed {
		t.Errorf("signature assertion passed despite _jws_verified=false: %+v", got[sig])
	}

	claims["_jws_verified"] = true
	for _, a := range assertClaims(sc, claims) {
		if a.Field == "userinfo JWS signature" && !a.Passed {
			t.Errorf("signature assertion failed despite _jws_verified=true: %+v", a)
		}
	}

	// An unsigned (plain JSON) userinfo has no verification result to assert.
	for _, a := range assertClaims(sc, map[string]any{"sub": "abc"}) {
		if a.Field == "userinfo JWS signature" {
			t.Errorf("signature assertion emitted for a plain-JSON userinfo: %+v", a)
		}
	}
}

// mixedSpec mirrors a real spec file: several ACRs, positive and negative cases,
// plus client-registration fields that must survive filtering.
func mixedSpec() Spec {
	return Spec{
		RedirectURI: "https://rp.example/cb",
		Acr:         []string{"otp", "password", "bio"},
		UserClaims:  []string{"name", "email"},
		Scenarios: []Scenario{
			{Name: "otp positive: returns sub", AuthFactor: "otp"},
			{Name: "otp negative: wrong OTP is rejected", AuthFactor: "otp"},
			{Name: "password positive: login succeeds", AuthFactor: "password"},
			{Name: "password negative: wrong password is rejected", AuthFactor: "password"},
			{Name: "bio positive: login succeeds", AuthFactor: "bio"},
		},
	}
}

func selectedNames(t *testing.T, f Filter) []string {
	t.Helper()
	got, err := mixedSpec().Select(f)
	if err != nil {
		t.Fatalf("Select(%+v): %v", f, err)
	}
	var names []string
	for _, s := range got.Scenarios {
		names = append(names, s.Name)
	}
	return names
}

func TestSelectFilters(t *testing.T) {
	cases := []struct {
		name   string
		filter Filter
		want   []string
	}{
		{"empty keeps everything", Filter{}, []string{
			"otp positive: returns sub",
			"otp negative: wrong OTP is rejected",
			"password positive: login succeeds",
			"password negative: wrong password is rejected",
			"bio positive: login succeeds",
		}},
		{"by auth factor", Filter{AuthFactors: []string{"bio"}}, []string{
			"bio positive: login succeeds",
		}},
		{"auth factor is case insensitive", Filter{AuthFactors: []string{"OTP"}}, []string{
			"otp positive: returns sub",
			"otp negative: wrong OTP is rejected",
		}},
		{"several auth factors", Filter{AuthFactors: []string{"otp", "bio"}}, []string{
			"otp positive: returns sub",
			"otp negative: wrong OTP is rejected",
			"bio positive: login succeeds",
		}},
		{"include is OR-ed", Filter{Include: []string{"^otp positive", "^bio"}}, []string{
			"otp positive: returns sub",
			"bio positive: login succeeds",
		}},
		{"exclude beats include", Filter{Include: []string{"positive"}, Exclude: []string{"^bio"}}, []string{
			"otp positive: returns sub",
			"password positive: login succeeds",
		}},
		{"factor and name are AND-ed", Filter{AuthFactors: []string{"password"}, Include: []string{"negative"}}, []string{
			"password negative: wrong password is rejected",
		}},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got := selectedNames(t, tc.filter)
			if len(got) != len(tc.want) {
				t.Fatalf("got %d scenario(s) %v, want %d %v", len(got), got, len(tc.want), tc.want)
			}
			for i := range got {
				if got[i] != tc.want[i] {
					t.Errorf("scenario %d = %q, want %q", i, got[i], tc.want[i])
				}
			}
		})
	}
}

// Narrowing the scenarios must not narrow the ACRs and claims the shared client is registered with.
func TestSelectKeepsClientRegistrationFields(t *testing.T) {
	got, err := mixedSpec().Select(Filter{AuthFactors: []string{"otp"}})
	if err != nil {
		t.Fatalf("Select: %v", err)
	}
	if len(got.Acr) != 3 || len(got.UserClaims) != 2 || got.RedirectURI == "" {
		t.Errorf("registration fields lost: acr=%v claims=%v redirect=%q", got.Acr, got.UserClaims, got.RedirectURI)
	}
}

// A filter that matches nothing must fail loudly.
func TestSelectEmptyResultIsAnError(t *testing.T) {
	_, err := mixedSpec().Select(Filter{AuthFactors: []string{"kbi"}})
	if err == nil {
		t.Fatal("Select accepted a filter matching zero scenarios")
	}
}

func TestSelectRejectsBadRegex(t *testing.T) {
	if _, err := mixedSpec().Select(Filter{Include: []string{"("}}); err == nil {
		t.Fatal("Select accepted an invalid regex")
	}
}

// --- consent assertions -----------------------------------------------------

// A scenario with no consent block must assert nothing about consent, so every
// pre-existing scenario keeps its current pass/fail semantics.
func TestAssertConsentNoSpecAssertsNothing(t *testing.T) {
	if got := assertConsent(Scenario{Name: "x"}, consentObservation{prompted: true}); got != nil {
		t.Fatalf("assertConsent with no spec produced %d assertion(s), want none", len(got))
	}
}

func TestAssertConsentExpectPrompt(t *testing.T) {
	cases := []struct {
		name     string
		expect   string
		prompted bool
		want     bool // assertion should pass
	}{
		{"yes and prompted", "yes", true, true},
		{"yes but not prompted", "yes", false, false},
		{"no and not prompted", "no", false, true},
		{"no but prompted", "no", true, false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			sc := Scenario{Consent: &ConsentSpec{ExpectPrompt: c.expect}}
			got := assertConsent(sc, consentObservation{prompted: c.prompted})
			if len(got) != 1 {
				t.Fatalf("got %d assertions, want 1", len(got))
			}
			if got[0].Passed != c.want {
				t.Errorf("assertion Passed=%v, want %v (%+v)", got[0].Passed, c.want, got[0])
			}
		})
	}
}

// An empty ExpectPrompt asserts nothing about prompting — used by deny-only
// scenarios that don't care whether this was a first or repeat authorization.
func TestAssertConsentEmptyExpectPromptSkipsThatCheck(t *testing.T) {
	sc := Scenario{Consent: &ConsentSpec{}}
	if got := assertConsent(sc, consentObservation{prompted: true}); len(got) != 0 {
		t.Fatalf("got %d assertions for an empty ConsentSpec, want 0", len(got))
	}
}

// A deny that never took effect must FAIL: the scenario's claim assertions would
// otherwise pass or fail for reasons unrelated to consent.
func TestAssertConsentDenyRequiresSomethingWithheld(t *testing.T) {
	sc := Scenario{Consent: &ConsentSpec{Deny: []string{"name"}}}

	got := assertConsent(sc, consentObservation{prompted: true, denied: []string{"name"}})
	if len(got) != 1 || !got[0].Passed {
		t.Fatalf("deny that took effect should pass: %+v", got)
	}

	got = assertConsent(sc, consentObservation{prompted: false})
	if len(got) != 1 || got[0].Passed {
		t.Fatalf("deny that never applied must fail: %+v", got)
	}
}

// The shipped spec files must parse into the current schema and satisfy the consent invariants.
func TestShippedSpecsParseAndAreConsistent(t *testing.T) {
	for _, f := range []string{"e2e-scenarios.json", "e2e-scenarios-mosip.json", "e2e-scenarios-sunbird.json"} {
		t.Run(f, func(t *testing.T) {
			data, err := os.ReadFile(filepath.Join("..", "..", "data", "scenarios", f))
			if err != nil {
				t.Fatalf("read: %v", err)
			}
			var spec Spec
			if err := json.Unmarshal(data, &spec); err != nil {
				t.Fatalf("parse: %v", err)
			}

			// Every successful login stores a consent record keyed by its
			// scopes+claims hash — per client, so the client config is part of
			// the key: the same scopes against a differently-configured client
			// are a different client and get prompted afresh.
			var sawConsent, sawReuse bool
			consented := map[string]string{}
			for _, sc := range spec.Scenarios {
				if sc.AuthFactor == "" {
					t.Errorf("scenario %q has no auth_factor", sc.Name)
				}
				cfg := ClientConfig{}
				if sc.ClientConfig != nil {
					cfg = *sc.ClientConfig
				}
				key := cfg.key() + "|" + strings.Join(sc.Scopes, " ") + "|" + fmt.Sprint(sc.UserinfoClaims)

				if sc.Consent != nil {
					sawConsent = true
					switch strings.ToLower(sc.Consent.ExpectPrompt) {
					case "", "yes", "no":
					default:
						t.Errorf("scenario %q: expect_prompt %q is not yes|no|\"\"", sc.Name, sc.Consent.ExpectPrompt)
					}

					switch strings.ToLower(sc.Consent.ExpectPrompt) {
					case "no":
						// Must repeat an earlier succeeding scenario's exact request, or
						// the hash differs and the server re-prompts.
						sawReuse = true
						if _, ok := consented[key]; !ok {
							t.Errorf("scenario %q expects NO prompt but no earlier succeeding scenario requested the same scopes/claims (%s)", sc.Name, key)
						}
					case "yes":
						if prev, ok := consented[key]; ok {
							t.Errorf("scenario %q expects a prompt but %q already consented to the same scopes/claims (%s) earlier in this spec — the stored consent would suppress the prompt",
								sc.Name, prev, key)
						}
					}
				}

				// A rejected login never reaches RecordConsent, so it stores nothing.
				if !sc.ExpectLoginFailure {
					if _, ok := consented[key]; !ok {
						consented[key] = sc.Name
					}
				}
			}
			if !sawConsent {
				t.Error("no consent scenarios found — captcha/consent coverage missing from this spec")
			}
			if !sawReuse {
				t.Error("no stored-consent reuse scenario (expect_prompt=no) found in this spec")
			}
		})
	}
}

// RFC 7515 mandates unpadded base64url, but padded JWKS values turn up in the wild.
func TestRSAPubFromJWKAcceptsPaddedBase64(t *testing.T) {
	key, err := generateRSA()
	if err != nil {
		t.Fatal(err)
	}
	nRaw := base64.RawURLEncoding.EncodeToString(key.N.Bytes())
	eRaw := base64.RawURLEncoding.EncodeToString(big.NewInt(int64(key.E)).Bytes())
	nPad := base64.URLEncoding.EncodeToString(key.N.Bytes())
	ePad := base64.URLEncoding.EncodeToString(big.NewInt(int64(key.E)).Bytes())

	for _, tc := range []struct{ name, n, e string }{
		{"unpadded", nRaw, eRaw},
		{"padded", nPad, ePad},
		{"mixed", nPad, eRaw},
	} {
		t.Run(tc.name, func(t *testing.T) {
			pub, err := rsaPubFromJWK(jwksKey{Kid: "k1", N: tc.n, E: tc.e})
			if err != nil {
				t.Fatalf("rsaPubFromJWK: %v", err)
			}
			if pub.N.Cmp(key.N) != 0 {
				t.Error("modulus does not round-trip")
			}
			if pub.E != key.E {
				t.Errorf("exponent = %d, want %d", pub.E, key.E)
			}
		})
	}
}

// Padding tolerance must not extend to genuine garbage.
func TestRSAPubFromJWKRejectsInvalidBase64(t *testing.T) {
	if _, err := rsaPubFromJWK(jwksKey{Kid: "k1", N: "not!base64", E: "AQAB"}); err == nil {
		t.Fatal("rsaPubFromJWK accepted a non-base64 modulus")
	}
}

// A degenerate key must be rejected outright rather than failing later as a signature mismatch.
func TestRSAPubFromJWKRejectsDegenerateKeys(t *testing.T) {
	n := base64.RawURLEncoding.EncodeToString([]byte{0x01, 0x02, 0x03})
	for _, tc := range []struct{ name, n, e string }{
		{"empty exponent", n, ""},
		{"empty modulus", "", "AQAB"},
		{"exponent 1", n, base64.RawURLEncoding.EncodeToString([]byte{0x01})},
		{"exponent too large", n, base64.RawURLEncoding.EncodeToString([]byte{0x01, 0x02, 0x03, 0x04, 0x05})},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if _, err := rsaPubFromJWK(jwksKey{Kid: "k1", N: tc.n, E: tc.e}); err == nil {
				t.Fatalf("rsaPubFromJWK accepted %s", tc.name)
			}
		})
	}
}
