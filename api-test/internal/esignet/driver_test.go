package esignet

import (
	"encoding/json"
	"strings"
	"testing"
	"time"
)

func TestSelectAction(t *testing.T) {
	cases := []struct {
		name      string
		in        []string
		preferred []string
		want      string
		wantErr   bool
	}{
		{"single", []string{"login"}, nil, "login", false},
		{"exclude-cancel", []string{"cancel", "login"}, nil, "login", false},
		{"only-excluded", []string{"cancel", "reject", "deny"}, nil, "", true},
		{"empty-ok", nil, nil, "", false},
		{"prefer-login", []string{"otp-login", "consent"}, nil, "otp-login", false},
		{"ambiguous", []string{"foo", "bar"}, nil, "", true},
		{"send-otp", []string{"send-otp"}, nil, "send-otp", false},
		{"acr-otp", []string{"acr_otp", "acr_password", "acr_bio", "acr_kbi"}, []string{"otp", "generated-code"}, "acr_otp", false},
		{"acr-kbi", []string{"acr_otp", "acr_password", "acr_bio", "acr_kbi"}, []string{"kbi", "knowledge"}, "acr_kbi", false},
		{"submit-over-tab-switch", []string{"submit_uin", "login_id_mobile", "login_id_email", "login_id_nrc", "BACK_BUTTON"}, []string{"otp", "generated-code", "mobile", "phone"}, "submit_uin", false},
		{"proceed-over-resend", []string{"resend_otp", "action_proceed_kbi"}, nil, "action_proceed_kbi", false},
		// Stage 5: a step offering only tab switches is progressable, so the
		// first navigation action is taken rather than aborting as ambiguous.
		{"nav-only-fallback", []string{"login_id_mobile", "login_id_email"}, nil, "login_id_mobile", false},
		// Stage 5 must still honour the caller preference (IDTypeTokens) among
		// navigation-only candidates: picking the server's first tab regardless
		// would authenticate against the wrong login-id type.
		{"nav-honours-preference", []string{"login_id_mobile", "login_id_email"}, []string{"otp", "email"}, "login_id_email", false},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			got, errMsg := selectAction(c.in, c.preferred)
			if c.wantErr && errMsg == "" {
				t.Fatalf("want error, got action %q", got)
			}
			if !c.wantErr && errMsg != "" {
				t.Fatalf("unexpected error: %s", errMsg)
			}
			if !c.wantErr && got != c.want {
				t.Fatalf("got %q want %q", got, c.want)
			}
		})
	}
}

func TestNormalize(t *testing.T) {
	cases := map[string]string{
		"fullName": "fullname", "full_name": "fullname", "FullName": "fullname",
		"OTP": "otp", "individual-id": "individualid", "  dob ": "dob",
	}
	for in, want := range cases {
		if got := Normalize(in); got != want {
			t.Errorf("Normalize(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestResolveInputs(t *testing.T) {
	d := &Driver{answers: map[string]string{"username": "u1", "otp": "111111"}}
	got, errMsg := d.resolveInputs([]string{"username", "otp"}, time.Now(), "")
	if errMsg != "" {
		t.Fatalf("unexpected error: %s", errMsg)
	}
	if got["username"] != "u1" || got["otp"] != "111111" {
		t.Fatalf("bad resolve: %v", got)
	}
	if _, errMsg := d.resolveInputs([]string{"password"}, time.Now(), ""); errMsg == "" {
		t.Fatalf("expected error for missing input")
	}
}

// The consent decision must approve every purpose and every claim: the harness
// drives the happy path, and a scenario asserting which claims userinfo releases
// needs them all granted — a withheld claim is indistinguishable from the server
// failing to release one.
func TestBuildConsentDecision(t *testing.T) {
	prompt := `[{"purposeName":"attributes:client-1","purposeId":"","type":"attributes",
	             "essential":[{"name":"name"}],
	             "optional":[{"name":"email"},{"name":"phone_number"}]}]`

	got, denied, err := buildConsentDecision(prompt, ConsentPolicy{})
	if err != nil {
		t.Fatalf("buildConsentDecision: %v", err)
	}
	if len(denied) != 0 {
		t.Errorf("default policy denied %v, want nothing denied", denied)
	}
	// The executor takes the decision as a JSON string, not a nested object.
	var d consentDecision
	if err := json.Unmarshal([]byte(got), &d); err != nil {
		t.Fatalf("result is not a JSON string of the decision: %v (%s)", err, got)
	}
	if len(d.Purposes) != 1 {
		t.Fatalf("got %d purposes, want 1", len(d.Purposes))
	}
	p := d.Purposes[0]
	if !p.Approved || p.PurposeName != "attributes:client-1" {
		t.Errorf("purpose not approved / wrong name: %+v", p)
	}
	// Essential and optional both get approved, in that order.
	want := []string{"name", "email", "phone_number"}
	if len(p.Elements) != len(want) {
		t.Fatalf("got %d elements %+v, want %d", len(p.Elements), p.Elements, len(want))
	}
	for i, w := range want {
		if p.Elements[i].Name != w || !p.Elements[i].Approved {
			t.Errorf("element %d = %+v, want approved %q", i, p.Elements[i], w)
		}
	}
}

// A ConsentPolicy withholds approval from exactly the named elements, leaving
// the rest approved — this is what the consent-deny scenarios rely on to prove
// the server honours a withheld optional claim (and rejects a withheld essential
// one).
func TestBuildConsentDecisionDeny(t *testing.T) {
	prompt := `[{"purposeName":"attributes:client-1","type":"attributes",
	             "essential":[{"name":"name"}],
	             "optional":[{"name":"email"},{"name":"phone_number"}]}]`

	got, denied, err := buildConsentDecision(prompt, ConsentPolicy{Deny: []string{"EMAIL"}})
	if err != nil {
		t.Fatalf("buildConsentDecision: %v", err)
	}
	if len(denied) != 1 || denied[0] != "email" {
		t.Fatalf("denied = %v, want [email]", denied)
	}
	var d consentDecision
	if err := json.Unmarshal([]byte(got), &d); err != nil {
		t.Fatalf("not a JSON string of the decision: %v (%s)", err, got)
	}
	want := map[string]bool{"name": true, "email": false, "phone_number": true}
	for _, e := range d.Purposes[0].Elements {
		if e.Approved != want[e.Name] {
			t.Errorf("element %q approved=%v, want %v", e.Name, e.Approved, want[e.Name])
		}
	}
}

// DenyAll withholds every element and marks the purpose itself unapproved,
// mirroring a UI where the user unticks the whole block.
func TestBuildConsentDecisionDenyAll(t *testing.T) {
	prompt := `[{"purposeName":"attributes:c1","essential":[{"name":"name"}],"optional":[{"name":"email"}]}]`
	got, denied, err := buildConsentDecision(prompt, ConsentPolicy{DenyAll: true})
	if err != nil {
		t.Fatalf("buildConsentDecision: %v", err)
	}
	if len(denied) != 2 {
		t.Fatalf("denied = %v, want both elements", denied)
	}
	var d consentDecision
	if err := json.Unmarshal([]byte(got), &d); err != nil {
		t.Fatalf("not a JSON string: %v", err)
	}
	if d.Purposes[0].Approved {
		t.Error("purpose approved despite DenyAll")
	}
	for _, e := range d.Purposes[0].Elements {
		if e.Approved {
			t.Errorf("element %q approved despite DenyAll", e.Name)
		}
	}
}

// Denying every element BY NAME must reach the same submission as DenyAll: the
// consent executor would otherwise receive Approved:true over an all-denied
// element list, and an essential_consent_denied assertion could pass for the
// wrong reason.
func TestBuildConsentDecisionDenyEveryElementByName(t *testing.T) {
	prompt := `[{"purposeName":"attributes:c1","essential":[{"name":"name"}],"optional":[{"name":"email"}]}]`
	got, denied, err := buildConsentDecision(prompt, ConsentPolicy{Deny: []string{"name", "email"}})
	if err != nil {
		t.Fatalf("buildConsentDecision: %v", err)
	}
	if len(denied) != 2 {
		t.Fatalf("denied = %v, want both elements", denied)
	}
	var d consentDecision
	if err := json.Unmarshal([]byte(got), &d); err != nil {
		t.Fatalf("not a JSON string: %v", err)
	}
	if d.Purposes[0].Approved {
		t.Error("purpose approved although every element was denied by name")
	}
}

// A deny naming a claim the prompt never offered must be a loud error: the
// scenario would otherwise "pass" while asserting nothing, because the claim it
// meant to withhold was never consent-gated by that authorize request.
func TestBuildConsentDecisionDenyUnofferedIsError(t *testing.T) {
	prompt := `[{"purposeName":"attributes:c1","essential":[{"name":"name"}],"optional":[]}]`
	_, _, err := buildConsentDecision(prompt, ConsentPolicy{Deny: []string{"email"}})
	if err == nil {
		t.Fatal("accepted a deny for an element the prompt never offered")
	}
	if !strings.Contains(err.Error(), "not offered") {
		t.Errorf("unhelpful error for unoffered deny: %v", err)
	}
}

// A consent step with nothing to derive from must fail loudly rather than send
// an empty approval the server would reject with a confusing error.
func TestBuildConsentDecisionRejectsUnusablePrompt(t *testing.T) {
	for name, prompt := range map[string]string{
		"empty":       "",
		"whitespace":  "   ",
		"not json":    "n/a",
		"no purposes": "[]",
	} {
		t.Run(name, func(t *testing.T) {
			if _, _, err := buildConsentDecision(prompt, ConsentPolicy{}); err == nil {
				t.Errorf("accepted unusable consentPrompt %q", prompt)
			}
		})
	}
}

// consent_decisions is synthesized, never configured, so resolveInputs must
// answer it from the prompt instead of reporting it missing.
func TestResolveInputsSynthesizesConsent(t *testing.T) {
	d := &Driver{answers: map[string]string{}}
	prompt := `[{"purposeName":"attributes:c1","essential":[{"name":"name"}],"optional":[]}]`

	got, errMsg := d.resolveInputs([]string{"consent_decisions"}, time.Now(), prompt)
	if errMsg != "" {
		t.Fatalf("resolveInputs: %s", errMsg)
	}
	if !strings.Contains(got["consent_decisions"], `"approved":true`) {
		t.Errorf("consent_decisions not synthesized: %q", got["consent_decisions"])
	}

	// Without a prompt it must report the consent problem, not "no configured answer".
	if _, errMsg := d.resolveInputs([]string{"consent_decisions"}, time.Now(), ""); !strings.Contains(errMsg, "consent") {
		t.Errorf("errMsg = %q, want one naming consent", errMsg)
	}
}
