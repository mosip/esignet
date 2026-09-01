package esignet

import (
	"encoding/json"
	"errors"
	"strings"
	"testing"
	"time"
)

func TestSelectAction(t *testing.T) {
	cases := []struct {
		name      string
		in        []string
		nodes     map[string]string // action ref -> nextNode, for the login-id-tab cases
		idTokens  []string
		preferred []string
		want      string
		wantErr   bool
	}{
		{name: "single", in: []string{"login"}, want: "login"},
		{name: "exclude-cancel", in: []string{"cancel", "login"}, want: "login"},
		{name: "only-excluded", in: []string{"cancel", "reject", "deny"}, wantErr: true},
		{name: "empty-ok", in: nil, want: ""},
		{name: "prefer-login", in: []string{"otp-login", "consent"}, want: "otp-login"},
		{name: "ambiguous", in: []string{"foo", "bar"}, wantErr: true},
		{name: "send-otp", in: []string{"send-otp"}, want: "send-otp"},
		{name: "acr-otp", in: []string{"acr_otp", "acr_password", "acr_bio", "acr_kbi"},
			preferred: []string{"otp", "generated-code"}, want: "acr_otp"},
		{name: "acr-kbi", in: []string{"acr_otp", "acr_password", "acr_bio", "acr_kbi"},
			preferred: []string{"kbi", "knowledge"}, want: "acr_kbi"},
		{name: "submit-over-tab-switch", in: []string{"submit_uin", "login_id_mobile", "login_id_email", "login_id_nrc", "BACK_BUTTON"},
			preferred: []string{"otp", "generated-code", "mobile", "phone"}, want: "submit_uin"},
		{name: "proceed-over-resend", in: []string{"resend_otp", "action_proceed_kbi"}, want: "action_proceed_kbi"},
		// Stage 5: a step offering only tab switches is progressable, so the
		// first navigation action is taken rather than aborting as ambiguous.
		{name: "nav-only-fallback", in: []string{"login_id_mobile", "login_id_email"}, want: "login_id_mobile"},
		// Stage 5 must honour the caller's IDTypeTokens preference among navigation-only candidates.
		{name: "nav-honours-preference", in: []string{"login_id_mobile", "login_id_email"},
			preferred: []string{"otp", "email"}, want: "login_id_email"},

		// Stage 0, the login-id tab correction. Every tab submits under the ref
		// "submit_uin"; only nextNode says which identifier kind goes out, so the
		// default UIN tab looks like a valid submit even when the identity is a
		// phone. With id_type set, switch tabs first.
		{name: "id-tab-switch-to-mobile",
			in:       []string{"submit_uin", "login_id_mobile", "login_id_email", "login_id_nrc", "BACK_BUTTON"},
			nodes:    map[string]string{"submit_uin": "send_mosip_otp_uin", "login_id_mobile": "prompt_mobile_otp", "login_id_email": "prompt_email_otp", "login_id_nrc": "prompt_nrc_otp", "BACK_BUTTON": "prompt_acr"},
			idTokens: []string{"mobile", "phone"}, preferred: []string{"otp", "generated-code"},
			want: "login_id_mobile"},
		// Already on the mobile tab: its submit targets the right node, so the
		// correction must NOT fire again — that would loop between tabs.
		{name: "id-tab-already-correct-submits",
			in:       []string{"submit_uin", "login_id_email", "BACK_BUTTON"},
			nodes:    map[string]string{"submit_uin": "send_mosip_otp_mobile", "login_id_email": "prompt_email_otp", "BACK_BUTTON": "prompt_acr"},
			idTokens: []string{"mobile", "phone"}, preferred: []string{"otp", "generated-code"},
			want: "submit_uin"},
		// id_type=uin on the UIN tab: no switch, submit straight away.
		{name: "id-tab-uin-stays",
			in:       []string{"submit_uin", "login_id_mobile", "BACK_BUTTON"},
			nodes:    map[string]string{"submit_uin": "send_mosip_otp_uin", "login_id_mobile": "prompt_mobile_otp", "BACK_BUTTON": "prompt_acr"},
			idTokens: []string{"uin"}, preferred: []string{"otp"},
			want: "submit_uin"},
		// No tab matches the id_type (a flow without that identifier): fall
		// through to the ordinary submit rather than picking an arbitrary tab.
		{name: "id-tab-no-match-falls-through",
			in:       []string{"submit_uin", "login_id_email", "BACK_BUTTON"},
			nodes:    map[string]string{"submit_uin": "send_mosip_otp_uin", "login_id_email": "prompt_email_otp", "BACK_BUTTON": "prompt_acr"},
			idTokens: []string{"mobile", "phone"}, preferred: []string{"otp"},
			want: "submit_uin"},
	}
	for _, c := range cases {
		t.Run(c.name, func(t *testing.T) {
			var actions []flowAction
			for _, ref := range c.in {
				actions = append(actions, flowAction{Ref: ref, NextNode: c.nodes[ref]})
			}
			got, errMsg := selectAction(actions, c.preferred, c.idTokens)
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

// fakeOTP stands in for the mock-SMTP listener.
type fakeOTP struct {
	code string
	err  error
}

func (f fakeOTP) OTP(time.Time) (string, error) { return f.code, f.err }

// The provider fills otp only when no explicit answer is configured. The
// "wrong OTP" negative scenario depends on that precedence: if the live OTP
// won, the case would silently start passing a valid code and assert nothing.
func TestResolveInputsDynamicOTP(t *testing.T) {
	d := &Driver{answers: map[string]string{}, otp: fakeOTP{code: "424242"}}
	got, errMsg := d.resolveInputs([]string{"otp"}, time.Now(), "")
	if errMsg != "" {
		t.Fatalf("resolveInputs: %s", errMsg)
	}
	if got["otp"] != "424242" {
		t.Errorf("otp = %q, want the provider value", got["otp"])
	}

	d = &Driver{answers: map[string]string{"otp": "000000"}, otp: fakeOTP{code: "424242"}}
	if got, errMsg := d.resolveInputs([]string{"otp"}, time.Now(), ""); errMsg != "" || got["otp"] != "000000" {
		t.Errorf("otp = %q (err %q), want the configured value to win", got["otp"], errMsg)
	}

	d = &Driver{answers: map[string]string{}, otp: fakeOTP{err: errors.New("no OTP arrived")}}
	if _, errMsg := d.resolveInputs([]string{"otp"}, time.Now(), ""); !strings.Contains(errMsg, "dynamic OTP") {
		t.Errorf("errMsg = %q, want one naming the dynamic OTP source", errMsg)
	}
}

// The consent decision must approve every purpose and every claim on the happy path.
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

// A ConsentPolicy withholds approval from exactly the named elements, leaving the rest approved.
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

// Denying every element by name must reach the same submission as DenyAll.
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

// A deny naming a claim the prompt never offered must be a loud error.
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
