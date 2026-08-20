package conformance

import (
	"strings"
	"testing"

	"github.com/mosip/esignet/api-test/internal/config"
)

func newTestOrchestrator(base string) *Orchestrator {
	cfg := &config.Config{}
	cfg.Esignet.BaseURL = base
	return &Orchestrator{cfg: cfg, logf: func(string, ...any) {}}
}

// The suite's authorize URL is the authority on where eSignet lives; a configured one may only agree.
func TestEsignetBase(t *testing.T) {
	const authorize = "https://esignet.example/v1/esignet/oauth2/authorize?client_id=x"

	cases := []struct {
		name       string
		configured string
		want       string
		wantErr    bool
	}{
		{
			name:       "unset derives from the authorize URL",
			configured: "",
			want:       "https://esignet.example/v1/esignet",
		},
		{
			name:       "exact match is accepted",
			configured: "https://esignet.example/v1/esignet",
			want:       "https://esignet.example/v1/esignet",
		},
		{
			name:       "trailing slash is not a mismatch",
			configured: "https://esignet.example/v1/esignet/",
			want:       "https://esignet.example/v1/esignet",
		},
		{
			// The regression: a prefix test accepted this and then returned the
			// SHORT value, silently driving the flow at https://esignet.example/v1.
			name:       "configured base shorter than derived is rejected",
			configured: "https://esignet.example/v1",
			wantErr:    true,
		},
		{
			name:       "configured base longer than derived is rejected",
			configured: "https://esignet.example/v1/esignet/extra",
			wantErr:    true,
		},
		{
			name:       "different host is rejected",
			configured: "https://other.example/v1/esignet",
			wantErr:    true,
		},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			got, err := newTestOrchestrator(tc.configured).esignetBase(authorize)
			if tc.wantErr {
				if err == nil {
					t.Fatalf("esignetBase = %q, want ESIGNET_BASE_URL_MISMATCH", got)
				}
				if !strings.Contains(err.Error(), "ESIGNET_BASE_URL_MISMATCH") {
					t.Errorf("error = %v, want ESIGNET_BASE_URL_MISMATCH", err)
				}
				return
			}
			if err != nil {
				t.Fatalf("esignetBase: %v", err)
			}
			if got != tc.want {
				t.Errorf("esignetBase = %q, want %q", got, tc.want)
			}
		})
	}
}

// An authorize URL the suite never produced must be reported, not guessed at.
func TestEsignetBaseRejectsNonAuthorizeURL(t *testing.T) {
	if _, err := newTestOrchestrator("").esignetBase("https://esignet.example/v1/esignet/token"); err == nil {
		t.Fatal("esignetBase succeeded on a URL with no /oauth2/authorize segment")
	}
}

// form_post is not a module name: the suite runs the ordinary modules and puts
// the mode in the plan variant, so only the variant can reveal it.
func TestUnsupportedReasonReadsTheResponseModeVariant(t *testing.T) {
	if got := unsupportedReason("oidcc-server", map[string]any{"response_mode": "form_post"}); got != "form_post" {
		t.Errorf("unsupportedReason(form_post variant) = %q, want form_post", got)
	}
	if got := unsupportedReason("oidcc-server", map[string]any{"response_mode": "default"}); got != "" {
		t.Errorf("unsupportedReason(default variant) = %q, want it supported", got)
	}
	if got := unsupportedReason("oidcc-server", nil); got != "" {
		t.Errorf("unsupportedReason(no variant) = %q, want it supported", got)
	}
	// The name-substring hints still apply.
	if got := unsupportedReason("oidcc-rp-initiated-logout", nil); got != "logout" {
		t.Errorf("unsupportedReason(logout module) = %q, want logout", got)
	}
}
