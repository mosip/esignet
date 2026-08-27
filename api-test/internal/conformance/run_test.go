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

// The visit budget defaults to 1, which must reproduce the original
// "drive each browser URL once" behaviour exactly: a URL already driven, or one
// the suite has marked visited, is not returned again.
func TestPendingURLsDefaultBudgetDrivesEachURLOnce(t *testing.T) {
	b := Browser{URLs: []string{"https://a/authorize", "https://b/authorize"}}
	visits := map[string]int{}

	got := pendingURLs(b, visits, 1)
	if len(got) != 2 {
		t.Fatalf("first pass = %v, want both URLs", got)
	}
	for _, u := range got {
		visits[u]++
	}
	if got := pendingURLs(b, visits, 1); got != nil {
		t.Errorf("second pass = %v, want none (budget of 1 already spent)", got)
	}

	// A URL the suite reports as visited is never offered, budget notwithstanding.
	b2 := Browser{URLs: []string{"https://a/authorize"}, Visited: []string{"https://a/authorize"}}
	if got := pendingURLs(b2, map[string]int{}, 1); got != nil {
		t.Errorf("suite-visited URL = %v, want none", got)
	}
}

// A budget above 1 is what par-ensure-reused-request-uri needs: the same
// authorize URL has to be drivable a second time, because the first visit
// deliberately does not authenticate.
func TestPendingURLsBudgetAllowsASecondVisit(t *testing.T) {
	b := Browser{URLs: []string{"https://a/authorize"}}
	visits := map[string]int{"https://a/authorize": 1}

	if got := pendingURLs(b, visits, 2); len(got) != 1 {
		t.Errorf("budget 2 after 1 visit = %v, want the URL again", got)
	}
	visits["https://a/authorize"]++
	if got := pendingURLs(b, visits, 2); got != nil {
		t.Errorf("budget 2 after 2 visits = %v, want none", got)
	}
}

// Only the two modules that need special driving get it; everything else must
// keep the zero value, or a stray DenyAll would deny consent across the run.
func TestBehaviorForLeavesOtherModulesOnTheDefault(t *testing.T) {
	// moduleBehavior holds a ConsentPolicy (which carries a slice), so it is not
	// comparable with ==; check the fields that change driving instead.
	if b := behaviorFor("fapi2-security-profile-final-happy-flow"); b.consent.DenyAll ||
		len(b.consent.Deny) != 0 || b.followRejection || b.loadOnlyVisits != 0 || b.visitBudget() != 1 {
		t.Errorf("happy-flow behaviour = %+v, want the default driving", b)
	}
	if b := behaviorFor("fapi2-security-profile-final-user-rejects-authentication"); !b.consent.DenyAll || !b.followRejection {
		t.Errorf("user-rejects behaviour = %+v, want DenyAll + followRejection", b)
	}
	reuse := behaviorFor("fapi2-security-profile-final-par-ensure-reused-request-uri-prior-to-auth-completion-succeeds")
	if reuse.loadOnlyVisits != 1 || reuse.visitBudget() != 2 {
		t.Errorf("par-reuse behaviour = %+v, want 1 load-only visit and a budget of 2", reuse)
	}
	if got := (moduleBehavior{}).visitBudget(); got != 1 {
		t.Errorf("default visitBudget() = %d, want 1", got)
	}
}
