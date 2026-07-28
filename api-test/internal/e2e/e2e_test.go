package e2e

import (
	"errors"
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

// A userinfo JWT signed by an unknown key must fail the scenario, not merely
// record a claim: this is the in-transit integrity evidence the surface exists
// to produce.
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

// The client is registered once for the whole run, so narrowing the scenarios
// must not narrow the ACRs/claims it is registered with — a filtered run would
// then fail on client capability rather than on the behaviour under test.
func TestSelectKeepsClientRegistrationFields(t *testing.T) {
	got, err := mixedSpec().Select(Filter{AuthFactors: []string{"otp"}})
	if err != nil {
		t.Fatalf("Select: %v", err)
	}
	if len(got.Acr) != 3 || len(got.UserClaims) != 2 || got.RedirectURI == "" {
		t.Errorf("registration fields lost: acr=%v claims=%v redirect=%q", got.Acr, got.UserClaims, got.RedirectURI)
	}
}

// A filter that matches nothing must fail loudly. Returning an empty spec would
// produce a report reading "0 scenarios, 0 failed" — indistinguishable from a
// clean pass.
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
