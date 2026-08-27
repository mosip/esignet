package report

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/mosip/esignet/api-test/internal/result"
)

// A gated surface reports ENV_NOT_READY with empty Result and Status, which must not render blank.
func TestDisplayResultRendersEnvNotReady(t *testing.T) {
	for _, surface := range []string{result.SurfaceClientMgmt, result.SurfaceE2E} {
		t.Run(surface, func(t *testing.T) {
			r := result.ModuleResult{Surface: surface, HarnessOutcome: result.OutcomeEnvNotReady}
			if got := displayResult(r); got != "ENV_NOT_READY" {
				t.Errorf("displayResult = %q, want ENV_NOT_READY", got)
			}
			if got := resultClass(r); got != "err" {
				t.Errorf("resultClass = %q, want err (matches the Errored summary bucket)", got)
			}
		})
	}
}

func TestWriteRendersReport(t *testing.T) {
	results := []result.ModuleResult{
		{
			Module: "oidcc-server", Result: "PASSED", Status: "FINISHED",
			HarnessOutcome: result.OutcomeOK, DurationMs: 1234,
			Variant:   map[string]any{"server_metadata": "discovery"},
			FlowTrace: result.FlowTrace{AuthorizeStatus: 302, EsignetCallbackStatus: 200, SuiteCallbackStatus: 200, ImplicitSubmitStatus: 204, Steps: []result.FlowStep{{FlowStatus: "INCOMPLETE", Action: "login", Inputs: []string{"username"}}, {FlowStatus: "COMPLETE"}}},
		},
		{
			Module: "oidcc-refresh-token", Result: "FAILED", Status: "FINISHED", HarnessOutcome: result.OutcomeOK, DurationMs: 800,
			FailedConditions: []result.Condition{{Src: "CheckX", Msg: "boom", Result: "FAILURE", Requirement: "OIDCC-3.1.2"}},
		},
		{
			Module: "rp-initiated-logout", Result: "SKIPPED", Status: "NOT_STARTED",
			HarnessOutcome: result.OutcomeSkippedByHarness, OutcomeDetail: "UNSUPPORTED_INTERACTION, detail: logout",
		},
		{
			Module: "oidcc-broken", Status: "INTERRUPTED", HarnessError: "timeout after 120s (last status WAITING)",
		},
		{
			Module: "oidcc-userinfo-post-body", Status: "NOT_RUN",
			HarnessOutcome: result.OutcomeKnownIssue, OutcomeDetail: "userinfo POST unsupported — bug ES-1234",
		},
	}

	results[0].Calls = []result.HTTPCall{{
		Label: "flow/execute #2", Method: "POST", URL: "https://esignet/v1/esignet/flow/execute",
		ReqHeaders: map[string][]string{"Content-Type": {"application/json"}}, ReqBody: `{"executionId":"x"}`,
		Status: 500, RespHeaders: map[string][]string{"Set-Cookie": {"sid=abc"}}, RespCookies: "sid=abc", RespBody: `{"code":"SSE-5000"}`,
	}}
	results[0].LogItems = []result.LogItem{
		{Time: "12:00:00", Src: "-START-BLOCK-", Msg: "Validate the JWK set in server JWKS", Kind: "INFO", Block: true},
		{Time: "12:00:01", Src: "CheckServerConfiguration", Msg: "Found required keys", Kind: "SUCCESS"},
		{Time: "12:00:02", Src: "GetDynamicServerConfiguration", Msg: "HTTP response", Kind: "RESPONSE", MoreN: 2,
			Details: []result.LogDetail{
				{Key: "response_status_code", Value: "200"},
				{Key: "response_body", Value: "{\n  \"issuer\": \"https://esignet\"\n}"},
			}},
	}

	cfgJSON := `{
  "esignet": { "provider": "mosip" },
  "conformance": { "token": "***redacted***" }
}`
	planCfgJSON := `{
  "client": { "client_id": "pm-client-1", "jwks": { "keys": [ { "kty": "RSA", "d": "***redacted***" } ] } }
}`

	dir := t.TempDir()
	path, err := Write(Options{
		Dir: dir, Plans: []string{"oidcc-test-plan"}, Provider: "mosip",
		ConfigJSON:  cfgJSON,
		PlanConfigs: []PlanConfig{{Plan: "oidcc-test-plan", JSON: planCfgJSON}},
		Results:     results,
	})
	if err != nil {
		t.Fatalf("Write: %v", err)
	}
	data, err := os.ReadFile(path)
	if err != nil {
		t.Fatalf("read report: %v", err)
	}
	html := string(data)
	for _, want := range []string{
		"oidcc-test-plan", "oidcc-server", "OIDCC-3.1.2", "SKIPPED (harness)", "HARNESS ERROR",
		"SSE-5000", "flow/execute #2", "Set-Cookie",
		"KNOWN ISSUE", "bug ES-1234", // known-issue bucket
		"Configuration used", "&#34;provider&#34;: &#34;mosip&#34;", // config panel (HTML-escaped)
		"Plan config", "pm-client-1", // plan config panel
		"test log", "SUCCESS", "GetDynamicServerConfiguration", // UI-style log
		"Validate the JWK set in server JWKS", "logblock", // block banner
		"+2 more", "response_body", "copyPre", "Go to Top", // detail rows + copy + go-to-top
	} {
		if !strings.Contains(html, want) {
			t.Errorf("report missing %q", want)
		}
	}
	// Suite plumbing calls must NOT appear in the report.
	for _, notWant := range []string{"Run setup", "/api/plan", "/api/runner/available"} {
		if strings.Contains(html, notWant) {
			t.Errorf("report should not contain suite plumbing %q", notWant)
		}
	}
	// The unique filename names the surfaces and encodes the counts, and a matching .json sidecar exists.
	// f is every module that did not pass, so the errored one is counted there
	// rather than going unnamed: 1 passed + 2 not passed + 1 skipped + 1 known = 5.
	if base := filepath.Base(path); !strings.HasPrefix(base, "conformance_mosip_") || !strings.Contains(base, "t-5_p-1_f-2_sk-1_ki-1") {
		t.Errorf("filename = %s, want conformance_mosip_<ts>_t-5_p-1_f-2_sk-1_ki-1", base)
	}
	jsons, _ := filepath.Glob(filepath.Join(dir, "*.json"))
	if len(jsons) == 0 {
		t.Errorf("no results json written")
	}
}

// A two-plan run keeps them apart: one section and one config panel each, both names in the header.
func TestWriteSeparatesPlans(t *testing.T) {
	const (
		oidcc = "oidcc-test-plan"
		fapi  = "fapi2-security-profile-final-test-plan"
	)
	results := []result.ModuleResult{
		{Surface: result.SurfaceConformance, Plan: oidcc, Module: "oidcc-server", Result: "PASSED"},
		{Surface: result.SurfaceConformance, Plan: fapi, Module: "fapi2-security-profile-final-server", Result: "FAILED"},
		{Surface: result.SurfaceE2E, Module: "otp positive", Result: "PASSED"},
	}

	dir := t.TempDir()
	path, err := Write(Options{
		Dir: dir, Plans: []string{oidcc, fapi}, Provider: "mock",
		PlanConfigs: []PlanConfig{
			{Plan: oidcc, JSON: `{"client":{"client_id":"oidcc-client"}}`},
			{Plan: fapi, JSON: `{"client":{"client_id":"fapi-client"},"client2":{"client_id":"fapi-client2"}}`},
		},
		Results: results,
	})
	if err != nil {
		t.Fatalf("Write: %v", err)
	}
	data, _ := os.ReadFile(path)
	html := string(data)

	for _, want := range []string{
		"Conformance (OpenID suite) — " + oidcc,
		"Conformance (OpenID suite) — " + fapi,
		"surface-conformance-" + oidcc,
		"surface-conformance-fapi2-security-profile-final-test-plan",
		"oidcc-client", "fapi-client2", // both plan-config panels
	} {
		if !strings.Contains(html, want) {
			t.Errorf("report missing %q", want)
		}
	}
	// The e2e surface carries no plan, so it stays one unlabelled section.
	if strings.Contains(html, "End-to-end (create client → userinfo claims) — ") {
		t.Error("e2e section was labelled with a plan")
	}
	// The filename names the surfaces in the report, not the plans: two plan
	// names would not fit, and the header already carries them.
	if base := filepath.Base(path); !strings.HasPrefix(base, "conformance_e2e_mock_") {
		t.Errorf("filename = %s, want it to start with conformance_e2e_mock_", base)
	}

	// The sidecar keeps both plan configs, keyed by plan.
	jsons, _ := filepath.Glob(filepath.Join(dir, "*.json"))
	if len(jsons) != 1 {
		t.Fatalf("expected one sidecar, got %d", len(jsons))
	}
	var side struct {
		PlanConfigs []struct {
			Plan   string         `json:"plan"`
			Config map[string]any `json:"config"`
		} `json:"plan_configs"`
	}
	sdata, _ := os.ReadFile(jsons[0])
	if err := json.Unmarshal(sdata, &side); err != nil {
		t.Fatalf("sidecar is not valid JSON: %v", err)
	}
	if len(side.PlanConfigs) != 2 || side.PlanConfigs[1].Plan != fapi {
		t.Errorf("sidecar plan_configs = %+v, want one entry per plan in order", side.PlanConfigs)
	}
	if side.PlanConfigs[1].Config["client2"] == nil {
		t.Error("sidecar dropped the fapi plan's second client")
	}
}

// The filename says which surfaces a report covers, so a directory of runs can
// be read at a glance. The two godog surfaces collapse to one "api" part.
func TestSurfaceSlug(t *testing.T) {
	rows := func(surfaces ...string) []result.ModuleResult {
		var out []result.ModuleResult
		for _, s := range surfaces {
			out = append(out, result.ModuleResult{Surface: s})
		}
		return out
	}
	cases := []struct {
		name string
		in   []result.ModuleResult
		want string
	}{
		{"full run", rows(result.SurfaceConformance, result.SurfaceClientMgmt, result.SurfaceFlowExecute, result.SurfaceE2E), "conformance_api_e2e"},
		{"conformance only", rows(result.SurfaceConformance), "conformance"},
		{"api only", rows(result.SurfaceFlowExecute, result.SurfaceClientMgmt), "api"},
		{"e2e and api", rows(result.SurfaceE2E, result.SurfaceClientMgmt), "api_e2e"},
		// Rows written before Surface existed default to conformance.
		{"blank surface", rows(""), "conformance"},
		{"unknown surface is still named", rows(result.SurfaceE2E, "load test"), "e2e_load-test"},
		{"no rows", nil, "run"},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			if got := surfaceSlug(tc.in); got != tc.want {
				t.Errorf("surfaceSlug = %q, want %q", got, tc.want)
			}
		})
	}
}

func TestRedactCallBodies(t *testing.T) {
	results := []result.ModuleResult{{
		Module: "oidcc-server",
		Calls: []result.HTTPCall{{
			Label: "flow/execute", Method: "POST",
			ReqBody:  `{"executionId":"x","inputs":{"otp":"111111","password":"hunter2"}}`,
			RespBody: `{"flowStatus":"COMPLETE","assertion":"eyJhbGciOi..."}`,
		}},
	}}
	out := redactCallBodies(results)
	req := out[0].Calls[0].ReqBody
	resp := out[0].Calls[0].RespBody
	if strings.Contains(req, "111111") || strings.Contains(req, "hunter2") {
		t.Errorf("request body not redacted: %s", req)
	}
	if strings.Contains(resp, "eyJhbGciOi") {
		t.Errorf("response body not redacted: %s", resp)
	}
	if !strings.Contains(req, "***redacted***") || !strings.Contains(resp, "***redacted***") {
		t.Errorf("expected redaction markers, got req=%s resp=%s", req, resp)
	}
	// The original slice passed in must be untouched.
	if !strings.Contains(results[0].Calls[0].ReqBody, "111111") {
		t.Errorf("redactCallBodies mutated the caller's results")
	}
}

// The raw authorize URL is what went over the wire, but its percent-encoded query is hard to read.
func TestDecodedQuery(t *testing.T) {
	raw := "https://esignet/oauth2/authorize?" +
		"acr_values=mosip%3Aidp%3Aacr%3Agenerated-code+mosip%3Aidp%3Aacr%3Apassword&" +
		`claims=%7B%22userinfo%22%3A%7B%22name%22%3A%7B%22essential%22%3Atrue%7D%7D%7D&` +
		"state=abc123"

	got := decodedQuery(raw)

	if !strings.Contains(got, "acr_values = mosip:idp:acr:generated-code mosip:idp:acr:password") {
		t.Errorf("acr_values not decoded (%%3A -> :, + -> space): %s", got)
	}
	if !strings.Contains(got, "state = abc123") {
		t.Errorf("plain param missing: %s", got)
	}
	// The claims param is JSON, so it should be re-indented, not left as one line.
	if !strings.Contains(got, "claims =\n{\n") {
		t.Errorf("JSON query value not pretty-printed: %s", got)
	}
	if !strings.Contains(got, `"essential": true`) {
		t.Errorf("decoded JSON content missing: %s", got)
	}
	// No raw percent-encoding should survive into the decoded rendering.
	if strings.Contains(got, "%") {
		t.Errorf("decodedQuery left percent-encoding behind: %s", got)
	}
}

func TestDecodedQueryEmptyWhenNoQuery(t *testing.T) {
	for _, raw := range []string{"https://esignet/oauth2/token", "not a url at all", ""} {
		if got := decodedQuery(raw); got != "" {
			t.Errorf("decodedQuery(%q) = %q, want empty", raw, got)
		}
	}
}

func TestRedactCallBodiesFormAndURL(t *testing.T) {
	results := []result.ModuleResult{{
		Module: "keycloak",
		Calls: []result.HTTPCall{{
			Label:   "admin token",
			URL:     "https://esignet.example/authorize/callback?state=abc&code=live-auth-code",
			ReqBody: "grant_type=client_credentials&client_id=x&client_secret=hunter2&code_verifier=live-pkce-verifier",
		}},
	}}
	out := redactCallBodies(results)[0].Calls[0]
	if strings.Contains(out.ReqBody, "hunter2") {
		t.Errorf("form body not redacted: %s", out.ReqBody)
	}
	if strings.Contains(out.ReqBody, "live-pkce-verifier") {
		t.Errorf("pkce verifier not redacted: %s", out.ReqBody)
	}
	if !strings.Contains(out.ReqBody, "client_id=x") {
		t.Errorf("non-sensitive form field dropped: %s", out.ReqBody)
	}
	if strings.Contains(out.URL, "live-auth-code") {
		t.Errorf("url code not redacted: %s", out.URL)
	}
	if !strings.Contains(out.URL, "state=abc") {
		t.Errorf("non-sensitive query param dropped: %s", out.URL)
	}
}

func TestRedactCallBodiesFragmentAndCookies(t *testing.T) {
	results := []result.ModuleResult{{
		Module: "oidcc-implicit",
		Calls: []result.HTTPCall{{
			Label:       "authorize callback",
			URL:         "https://rp.example/cb#id_token=live-id-token&access_token=live-at&state=xyz",
			ReqCookies:  "SESSION=live-session-id; theme=dark",
			RespCookies: "SESSION=new-session-id; Path=/; HttpOnly",
		}},
	}}
	out := redactCallBodies(results)[0].Calls[0]
	for _, secret := range []string{"live-id-token", "live-at", "live-session-id", "new-session-id"} {
		if strings.Contains(out.URL+out.ReqCookies+out.RespCookies, secret) {
			t.Errorf("%q leaked: url=%s reqc=%s respc=%s", secret, out.URL, out.ReqCookies, out.RespCookies)
		}
	}
	if !strings.Contains(out.URL, "state=xyz") {
		t.Errorf("non-sensitive fragment param dropped: %s", out.URL)
	}
	// Cookie names and Set-Cookie attributes stay readable for debugging.
	if !strings.Contains(out.ReqCookies, "SESSION=") || !strings.Contains(out.RespCookies, "Path=/") {
		t.Errorf("cookie names/attributes not preserved: reqc=%s respc=%s", out.ReqCookies, out.RespCookies)
	}
}

func TestRedactCallBodiesIdentityIsRequestOnly(t *testing.T) {
	results := []result.ModuleResult{{
		Module: "e2e otp",
		Calls: []result.HTTPCall{{
			Label:    "flow/execute",
			ReqBody:  `{"individualId":"+911234567890","fullName":"Ada Lovelace","dob":"1815-12-10"}`,
			RespBody: `{"name":"Ada Lovelace","email":"ada@example.org"}`,
		}, {
			Label:   "create client",
			ReqBody: `{"request":{"clientName":"api e2e","relyingPartyId":"api-e2e-rp"}}`,
		}},
	}}
	out := redactCallBodies(results)[0].Calls
	// Request-side login inputs are authenticators — masked.
	for _, secret := range []string{"+911234567890", "1815-12-10"} {
		if strings.Contains(out[0].ReqBody, secret) {
			t.Errorf("identity input %q leaked: %s", secret, out[0].ReqBody)
		}
	}
	// Response-side userinfo claims are the artifact under test — kept.
	if !strings.Contains(out[0].RespBody, "Ada Lovelace") {
		t.Errorf("userinfo claim was redacted, report loses its evidence: %s", out[0].RespBody)
	}
	// Exact-match keying must not swallow clientName in the client-mgmt trace.
	if !strings.Contains(out[1].ReqBody, "api e2e") {
		t.Errorf("clientName wrongly redacted: %s", out[1].ReqBody)
	}
}

func TestRedactBodyLeavesPlainTextAlone(t *testing.T) {
	const plain = "Bad Gateway: upstream did not respond"
	if got := redactBody(plain); got != plain {
		t.Errorf("plain text body altered: %q", got)
	}
}

func TestHeaderStrRedactsSessionHeaders(t *testing.T) {
	got := headerStr(map[string][]string{
		"Set-Cookie":   {"SESSION=abc123; Path=/"},
		"Cookie":       {"SESSION=abc123"},
		"X-API-KEY":    {"live-key"},
		"Content-Type": {"application/json"},
	})
	for _, secret := range []string{"abc123", "live-key"} {
		if strings.Contains(got, secret) {
			t.Errorf("header %q leaked: %s", secret, got)
		}
	}
	if !strings.Contains(got, "application/json") {
		t.Errorf("non-sensitive header dropped: %s", got)
	}
}

func TestSummarize(t *testing.T) {
	rs := []result.ModuleResult{
		{Result: "PASSED", HarnessOutcome: result.OutcomeOK},
		{Result: "FAILED", HarnessOutcome: result.OutcomeOK},
		{Result: "WARNING", HarnessOutcome: result.OutcomeOK},
		{Result: "REVIEW", HarnessOutcome: result.OutcomeOK},
		{Result: "SKIPPED", HarnessOutcome: result.OutcomeSkippedByHarness},
		{HarnessOutcome: result.OutcomeKnownIssue},
		{HarnessError: "x"},
	}
	s := result.Summarize(rs)
	if s.Total != 7 || s.Passed != 1 || s.Failed != 1 || s.Warning != 1 || s.Review != 1 || s.Skipped != 1 || s.Known != 1 || s.Errored != 1 {
		t.Fatalf("unexpected summary: %+v", s)
	}
	if !s.HasFailures() {
		t.Errorf("expected HasFailures = true")
	}
	// NotPassed folds in errored, warning and review — the four numbers the
	// report filename carries must account for every module, or a reader is left
	// hunting for the difference.
	if got := s.NotPassed(); got != 4 {
		t.Errorf("NotPassed() = %d, want 4 (failed + warning + review + errored)", got)
	}
	if s.Passed+s.NotPassed()+s.Skipped+s.Known != s.Total {
		t.Errorf("p+f+sk+ki = %d, want Total %d", s.Passed+s.NotPassed()+s.Skipped+s.Known, s.Total)
	}
}

// The filename invariant has to hold for any mix of outcomes, not just the one
// the fixture above happens to use.
func TestNotPassedAlwaysAccountsForTheTotal(t *testing.T) {
	for _, rs := range [][]result.ModuleResult{
		{},
		{{Result: "PASSED", HarnessOutcome: result.OutcomeOK}},
		{{HarnessError: "boom"}, {HarnessError: "boom"}},
		{{HarnessOutcome: result.OutcomeEnvNotReady}, {Result: "SKIPPED", HarnessOutcome: result.OutcomeSkippedByHarness}},
		{{HarnessOutcome: result.OutcomeKnownIssue}, {Result: "WARNING", HarnessOutcome: result.OutcomeOK}, {Result: "WEIRD"}},
	} {
		s := result.Summarize(rs)
		if s.Passed+s.NotPassed()+s.Skipped+s.Known != s.Total {
			t.Errorf("summary %+v: p+f+sk+ki != Total", s)
		}
		if s.NotPassed() < 0 {
			t.Errorf("summary %+v: NotPassed() is negative", s)
		}
	}
}

// The sidecar JSON serialises the header maps themselves, so masking only at
// render time (headerStr) would still archive live credentials.
func TestRedactCallBodiesHeaders(t *testing.T) {
	results := []result.ModuleResult{{
		Module: "flow",
		Calls: []result.HTTPCall{{
			Label:       "flow/execute",
			ReqHeaders:  map[string][]string{"Authorization": {"Bearer live-token"}, "Cookie": {"SESSION=live"}, "Content-Type": {"application/json"}},
			RespHeaders: map[string][]string{"Set-Cookie": {"SESSION=new-live"}, "Content-Type": {"application/json"}},
		}},
	}}
	out := redactCallBodies(results)[0].Calls[0]
	for _, leak := range []string{"live-token", "SESSION=live", "SESSION=new-live"} {
		if strings.Contains(headerStr(out.ReqHeaders)+headerStr(out.RespHeaders), leak) {
			t.Errorf("%q survived header redaction: req=%v resp=%v", leak, out.ReqHeaders, out.RespHeaders)
		}
	}
	if got := out.ReqHeaders["Content-Type"]; len(got) != 1 || got[0] != "application/json" {
		t.Errorf("non-sensitive header altered: %v", got)
	}
	// The caller's map must not be mutated — the sidecar and HTML share input.
	if results[0].Calls[0].ReqHeaders["Authorization"][0] != "Bearer live-token" {
		t.Error("redaction mutated the source header map")
	}
}

// Suite log entries carry whole HTTP exchanges from /api/log, so they need the
// same scrubbing as a captured call before the report is archived.
func TestRedactCallBodiesLogItems(t *testing.T) {
	results := []result.ModuleResult{{
		Module: "oidcc-server",
		LogItems: []result.LogItem{{
			Msg: `{"access_token":"live-at-in-msg"}`,
			Details: []result.LogDetail{
				{Key: "response_body", Value: `{"access_token":"live-at","token_type":"Bearer"}`},
				{Key: "access_token", Value: "live-bare-at"},
				{Key: "full_url", Value: "https://esignet.example/cb?state=keep-me&code=live-code"},
				{Key: "http_status", Value: "200"},
			},
		}},
	}}
	out := redactCallBodies(results)[0].LogItems[0]

	blob := out.Msg
	for _, d := range out.Details {
		blob += "|" + d.Value
	}
	for _, leak := range []string{"live-at-in-msg", "live-at", "live-bare-at", "live-code"} {
		if strings.Contains(blob, leak) {
			t.Errorf("%q survived log-item redaction: %s", leak, blob)
		}
	}
	// Non-sensitive detail values and the surrounding structure stay intact.
	if !strings.Contains(blob, "keep-me") || !strings.Contains(blob, "200") {
		t.Errorf("non-sensitive log detail lost: %s", blob)
	}
	if out.Details[0].Key != "response_body" || len(out.Details) != 4 {
		t.Errorf("log detail keys/shape altered: %+v", out.Details)
	}
	if results[0].LogItems[0].Details[1].Value != "live-bare-at" {
		t.Error("redaction mutated the source log items")
	}
}

// A sensitive key holding a bare number must be masked too.
func TestRedactBodyMasksNonStringScalars(t *testing.T) {
	got := redactBody(`{"otp":111111,"secret":12345678,"debug":true,"clientName":"keep"}`)
	for _, leak := range []string{"111111", "12345678"} {
		if strings.Contains(got, leak) {
			t.Errorf("%q survived redaction: %s", leak, got)
		}
	}
	if !strings.Contains(got, `"clientName":"keep"`) {
		t.Errorf("non-sensitive field altered: %s", got)
	}
}

// A sensitive key holding a composite must be masked whole, not walked into.
func TestRedactBodyMasksCompositeUnderSensitiveKey(t *testing.T) {
	got := redactBody(`{"secret":{"kty":"RSA","d":"live-private-key"},"otp":["111111"]}`)
	for _, leak := range []string{"live-private-key", "111111"} {
		if strings.Contains(got, leak) {
			t.Errorf("%q survived redaction: %s", leak, got)
		}
	}
}

// secretsSpec is one run's worth of trace holding every kind of value the
// redactor scrubs, so both modes can be asserted against the same input.
func secretsSpec() []result.ModuleResult {
	return []result.ModuleResult{{
		Surface: "e2e", Plugin: "mock", Module: "otp positive", Result: "PASSED",
		Calls: []result.HTTPCall{{
			Seq: 1, Label: "flow/execute", Method: "POST",
			URL:        "https://esignet/v1/esignet/flow/execute?code=AUTHCODE123",
			ReqHeaders: map[string][]string{"Authorization": {"Bearer BEARERTOK"}},
			ReqBody:    `{"otp":"111111","password":"Mosip@123","individualId":"+912532509749"}`,
			Status:     200,
			RespBody:   `{"sub":"user-1","name":"Test User"}`,
		}},
	}}
}

// secretsOptions is the Write input the redaction cases share: one plan, the
// trace from secretsSpec, and the mode under test.
func secretsOptions(dir string, showSecrets bool) Options {
	return Options{
		Dir: dir, Plans: []string{"plan"}, Provider: "mock",
		ConfigJSON:  "{}",
		PlanConfigs: []PlanConfig{{Plan: "plan", JSON: "{}"}},
		ShowSecrets: showSecrets,
		Results:     secretsSpec(),
	}
}

// The report must redact by default: a run that nobody configured specially is
// the one most likely to be attached to a ticket or archived by CI.
func TestWriteRedactsByDefault(t *testing.T) {
	dir := t.TempDir()
	path, err := Write(secretsOptions(dir, false))
	if err != nil {
		t.Fatalf("Write: %v", err)
	}
	data, _ := os.ReadFile(path)
	html := string(data)

	for _, secret := range []string{"111111", "Mosip@123", "+912532509749", "AUTHCODE123", "BEARERTOK"} {
		if strings.Contains(html, secret) {
			t.Errorf("redacted report leaked %q", secret)
		}
	}
	// The userinfo response claims are the artifact the harness exists to
	// evidence, so they must survive redaction.
	if !strings.Contains(html, "Test User") {
		t.Error("redacted report dropped the userinfo response claims")
	}
	// Match the banner's own text — the .unredacted CSS class is always present
	// in the stylesheet, so the bare word would match every report.
	if strings.Contains(html, "Unredacted report.") {
		t.Error("redacted report should not carry the unredacted warning banner")
	}
}

// With the debug flag the wire trace comes through verbatim — that is the point
// of the flag — but the report must say so loudly.
func TestWriteShowSecretsKeepsTraceAndWarns(t *testing.T) {
	dir := t.TempDir()
	path, err := Write(secretsOptions(dir, true))
	if err != nil {
		t.Fatalf("Write: %v", err)
	}
	data, _ := os.ReadFile(path)
	html := string(data)

	for _, want := range []string{"111111", "Mosip@123", "AUTHCODE123"} {
		if !strings.Contains(html, want) {
			t.Errorf("show-secrets report is missing %q from the wire trace", want)
		}
	}
	if !strings.Contains(html, "Unredacted report.") {
		t.Error("show-secrets report is missing the warning banner")
	}
}

// The debug flag covers the per-run wire trace only.
func TestWriteShowSecretsDoesNotUnmaskConfigPanel(t *testing.T) {
	cfgJSON := `{"keycloak":{"client_secret":"***redacted***"}}`
	planJSON := `{"client":{"jwks":{"keys":[{"kty":"RSA","d":"***redacted***"}]}}}`

	for _, showSecrets := range []bool{false, true} {
		dir := t.TempDir()
		opts := secretsOptions(dir, showSecrets)
		opts.ConfigJSON = cfgJSON
		opts.PlanConfigs = []PlanConfig{{Plan: "plan", JSON: planJSON}}
		path, err := Write(opts)
		if err != nil {
			t.Fatalf("Write(showSecrets=%v): %v", showSecrets, err)
		}
		data, _ := os.ReadFile(path)
		if n := strings.Count(string(data), "***redacted***"); n < 2 {
			t.Errorf("showSecrets=%v: config/plan panels lost their masking (%d markers)", showSecrets, n)
		}
	}
}

// The sidecar JSON is a separate artifact from the HTML and is just as
// shareable, so it must follow the same rule.
func TestSidecarJSONFollowsRedactionMode(t *testing.T) {
	for _, tc := range []struct{ showSecrets, wantLeak bool }{{false, false}, {true, true}} {
		dir := t.TempDir()
		if _, err := Write(secretsOptions(dir, tc.showSecrets)); err != nil {
			t.Fatalf("Write: %v", err)
		}
		matches, _ := filepath.Glob(filepath.Join(dir, "*.json"))
		if len(matches) != 1 {
			t.Fatalf("expected one sidecar JSON, got %d", len(matches))
		}
		data, _ := os.ReadFile(matches[0])
		if got := strings.Contains(string(data), "Mosip@123"); got != tc.wantLeak {
			t.Errorf("showSecrets=%v: sidecar contains password = %v, want %v", tc.showSecrets, got, tc.wantLeak)
		}
	}
}
