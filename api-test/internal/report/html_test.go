package report

import (
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/mosip/esignet/api-test/internal/result"
)

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
	path, err := Write(dir, "oidcc-test-plan", "mosip", cfgJSON, planCfgJSON, results)
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
	// Unique filename encodes counts (incl. known), and a matching .json sidecar exists.
	if !strings.Contains(filepath.Base(path), "p-1_f-1_sk-1_ki-1") {
		t.Errorf("filename missing count suffix: %s", filepath.Base(path))
	}
	jsons, _ := filepath.Glob(filepath.Join(dir, "*.json"))
	if len(jsons) == 0 {
		t.Errorf("no results json written")
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
			ReqBody: `{"request":{"clientName":"bdd e2e","relyingPartyId":"bdd-e2e-rp"}}`,
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
	if !strings.Contains(out[1].ReqBody, "bdd e2e") {
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
