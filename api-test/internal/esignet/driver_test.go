package esignet

import (
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
	got, errMsg := d.resolveInputs([]string{"username", "otp"}, time.Now())
	if errMsg != "" {
		t.Fatalf("unexpected error: %s", errMsg)
	}
	if got["username"] != "u1" || got["otp"] != "111111" {
		t.Fatalf("bad resolve: %v", got)
	}
	if _, errMsg := d.resolveInputs([]string{"password"}, time.Now()); errMsg == "" {
		t.Fatalf("expected error for missing input")
	}
}
