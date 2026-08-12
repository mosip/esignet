package e2e

import (
	"bytes"
	"context"
	"crypto/rsa"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"maps"
	"net/http"
	"net/url"
	"regexp"
	"strconv"
	"strings"
	"time"

	"github.com/mosip/esignet/api-test/internal/esignet"
	"github.com/mosip/esignet/api-test/internal/httpx"
	"github.com/mosip/esignet/api-test/internal/result"
)

// Spec is the E2E test definition (loaded from JSON): how to register the test
// client and the list of scenarios to run against it.
type Spec struct {
	RedirectURI string     `json:"redirect_uri"`
	UserClaims  []string   `json:"user_claims"` // claims registered on the client
	Acr         []string   `json:"acr"`         // authContextRefs registered on the client
	Scenarios   []Scenario `json:"scenarios"`
}

// Filter narrows a Spec's scenarios for a focused run. A spec file carries
// scenarios across several ACRs and one run drives them all by default; these
// criteria are AND-ed, and an empty criterion matches everything.
type Filter struct {
	AuthFactors []string // keep only these auth_factors (case insensitive)
	Include     []string // keep only names matching at least one regex
	Exclude     []string // drop names matching any regex; applied last, so it wins
}

// Empty reports whether the filter would keep every scenario.
func (f Filter) Empty() bool {
	return len(f.AuthFactors) == 0 && len(f.Include) == 0 && len(f.Exclude) == 0
}

// Select returns a copy of s holding only the scenarios the filter keeps. The
// client registration fields (redirect URI, claims, ACRs) are carried over
// unchanged: the test client must still be registered with the full ACR set, or
// a narrowed run would fail on client capability rather than on the behaviour
// under test.
//
// An empty result is an error rather than a silently empty run — a typo'd filter
// that produced zero scenarios would otherwise report a green "0 failed".
func (s Spec) Select(f Filter) (Spec, error) {
	if f.Empty() {
		return s, nil
	}
	include, err := compileAll(f.Include)
	if err != nil {
		return s, err
	}
	exclude, err := compileAll(f.Exclude)
	if err != nil {
		return s, err
	}
	factors := map[string]bool{}
	for _, x := range f.AuthFactors {
		factors[strings.ToLower(strings.TrimSpace(x))] = true
	}

	out := s
	out.Scenarios = nil
	for _, sc := range s.Scenarios {
		if len(factors) > 0 && !factors[strings.ToLower(strings.TrimSpace(sc.AuthFactor))] {
			continue
		}
		if len(include) > 0 && !matchAny(include, sc.Name) {
			continue
		}
		if matchAny(exclude, sc.Name) {
			continue
		}
		out.Scenarios = append(out.Scenarios, sc)
	}
	if len(out.Scenarios) == 0 {
		return out, fmt.Errorf("e2e scenario filter matched none of the %d scenarios (auth_factors=%v include=%v exclude=%v)",
			len(s.Scenarios), f.AuthFactors, f.Include, f.Exclude)
	}
	return out, nil
}

func compileAll(exprs []string) ([]*regexp.Regexp, error) {
	var out []*regexp.Regexp
	for _, e := range exprs {
		re, err := regexp.Compile(e)
		if err != nil {
			return nil, fmt.Errorf("invalid e2e scenario regex %q: %w", e, err)
		}
		out = append(out, re)
	}
	return out, nil
}

func matchAny(res []*regexp.Regexp, s string) bool {
	for _, re := range res {
		if re.MatchString(s) {
			return true
		}
	}
	return false
}

// Scenario is one full-flow case: which ACR/credentials to drive the login with,
// which scopes/claims to request, and what the resulting userinfo must (and must
// not) contain.
type Scenario struct {
	Name string `json:"name"`

	// AuthFactor selects the ACR this scenario drives at the flow's login-method
	// step: otp | password | bio | kbi. Required — a scenario with no AuthFactor
	// fails immediately with a clear config error rather than silently falling
	// back to some default, since ACR coverage (one scenario per factor) is the
	// point of this surface.
	AuthFactor string `json:"auth_factor"`

	// Credentials overrides the plugin's base identity answers for just this
	// scenario (e.g. {"otp":"000000"} for a wrong-OTP negative case, or
	// {"password":"wrong-value"} when the base config has no real password).
	// Merged over the base answers; only the listed keys are replaced. When an
	// ACR's required input has no answer at all (base or override) — e.g. `bio`,
	// which has no configured biometric capture — the flow driver fails cleanly
	// with "no configured answer for flow input(s): <name>", which is the
	// expected outcome for those scenarios (see ExpectLoginFailure).
	Credentials map[string]string `json:"credentials"`

	// ExpectLoginFailure marks a negative auth case: the login is EXPECTED to be
	// rejected (wrong credential, or no credential configured for this ACR yet).
	// The scenario PASSES when the login fails and FAILS if it unexpectedly
	// succeeds — the inverse of a positive scenario, where success is required
	// and failure (including "no config for this ACR yet") is reported FAILED,
	// not skipped, so the case stays visible until real credentials exist.
	ExpectLoginFailure bool `json:"expect_login_failure"`

	// Consent controls how the consent step is answered and what is asserted
	// about it. Omitted ⇒ approve everything and assert nothing, which is the
	// happy path every pre-existing scenario relies on.
	Consent *ConsentSpec `json:"consent"`

	Scopes         []string          `json:"scopes"`
	UserinfoClaims map[string]any    `json:"userinfo_claims"` // OIDC "claims".userinfo request object
	ExpectPresent  []string          `json:"expect_present"`
	ExpectValues   map[string]string `json:"expect_values"`
	ExpectAbsent   []string          `json:"expect_absent"`
	// KnownIssue, if set, marks this scenario as an already-tracked environment
	// gap: a claim-assertion failure is reported in the Known bucket (not
	// Failed/exit-code-affecting) with this reason, while the claim detail is
	// still shown in the report drill-down. If the scenario starts passing, it
	// is still reported PASSED (not silently downgraded).
	KnownIssue string `json:"known_issue"`
}

// ConsentSpec is a scenario's consent behaviour and expectations.
//
// The consent step only appears when the authorize request actually asks for
// consent-gated claims, so a scenario using this must request scopes/claims
// beyond bare "openid" — otherwise the server has nothing to prompt for and
// ExpectPrompt "yes" fails with "consent step never appeared".
type ConsentSpec struct {
	// Deny withholds approval from these element names (case-insensitive). A name
	// the prompt never offers fails the scenario rather than passing vacuously.
	Deny []string `json:"deny"`
	// DenyAll withholds approval from every element offered.
	DenyAll bool `json:"deny_all"`

	// ExpectPrompt asserts whether a consent step appeared at all:
	//   "yes" — the flow must prompt (first authorization, or a re-prompt)
	//   "no"  — the flow must NOT prompt (a stored consent still covers it)
	//   ""    — no assertion
	// This is the observable for the stored-consent paths in the service's
	// ResolveConsent: skip on hash match, re-prompt on expiry/claims change.
	ExpectPrompt string `json:"expect_prompt"`
}

// consentPolicy maps the scenario's spec onto the driver's policy.
func (c *ConsentSpec) consentPolicy() esignet.ConsentPolicy {
	if c == nil {
		return esignet.ConsentPolicy{}
	}
	return esignet.ConsentPolicy{Deny: c.Deny, DenyAll: c.DenyAll}
}

// consentObservation is what the flow actually did about consent, carried back
// even when the login fails — deny-an-essential-claim is precisely a case where
// the prompt appeared, was answered, and the login was then (correctly)
// rejected.
type consentObservation struct {
	prompted bool
	denied   []string
}

// assertConsent turns the scenario's consent expectations into report assertions.
// Returns nil when the scenario asserts nothing about consent.
func assertConsent(sc Scenario, obs consentObservation) []result.Assertion {
	if sc.Consent == nil {
		return nil
	}
	var out []result.Assertion

	switch strings.ToLower(strings.TrimSpace(sc.Consent.ExpectPrompt)) {
	case "yes":
		out = append(out, result.Assertion{
			Field: "consent prompt", Expected: "prompted",
			Actual: promptedWord(obs.prompted), Passed: obs.prompted,
		})
	case "no":
		out = append(out, result.Assertion{
			Field: "consent prompt", Expected: "not prompted (stored consent reused)",
			Actual: promptedWord(obs.prompted), Passed: !obs.prompted,
		})
	}

	// When a scenario asked to withhold claims, evidence that the withholding
	// actually happened — otherwise a server that never prompted would make the
	// claim assertions pass or fail for unrelated reasons.
	if len(sc.Consent.Deny) > 0 || sc.Consent.DenyAll {
		want := "withheld: " + strings.Join(sc.Consent.Deny, ", ")
		if sc.Consent.DenyAll {
			want = "withheld: all offered elements"
		}
		got := "withheld: " + strings.Join(obs.denied, ", ")
		if len(obs.denied) == 0 {
			got = "nothing withheld (no consent step reached)"
		}
		out = append(out, result.Assertion{
			Field: "consent decision", Expected: want, Actual: got, Passed: len(obs.denied) > 0,
		})
	}
	return out
}

func promptedWord(b bool) string {
	if b {
		return "prompted"
	}
	return "not prompted"
}

// Runner drives the E2E surface for one plugin.
type Runner struct {
	Base             string // eSignet base, e.g. https://host/v1/esignet
	Issuer           string
	AuthEndpoint     string
	TokenEndpoint    string
	UserinfoEndpoint string
	JWKSURI          string
	AdminToken       string
	Plugin           string
	Answers          map[string]string // base identity answers; Scenario.Credentials overrides per case
	IDType           string            // uin | vid | phone | email — login-id-type preference, combined with each scenario's AuthFactor
	TLSVerify        bool
	Timeout          time.Duration
	Logf             func(string, ...any)
	OTP              esignet.OTPProvider // dynamic OTP source; nil for static OTP

	// PMS registration (mosip plugin only). When Plugin == "mosip" the test
	// client is registered through partner-management-service /oauth/client
	// instead of eSignet client-mgmt, so IDA gets the partner+policy binding.
	// PMS generates the clientId (hash of the public key); the harness reads it
	// back and uses it for the rest of the flow. See config.PMS.
	PMSBaseURL    string
	AuthPartnerID string
	PolicyID      string

	acr    []string // registered ACRs, set from the spec in Run
	client *http.Client
}

func (r *Runner) httpClient() *http.Client {
	if r.client == nil {
		r.client = &http.Client{
			Timeout: r.Timeout,
			Transport: &http.Transport{TLSClientConfig: &tls.Config{
				MinVersion:         tls.VersionTLS12,
				InsecureSkipVerify: !r.TLSVerify,
			}},
		}
	}
	return r.client
}

// do performs an HTTP call and records it (Authorization redacted) into calls.
func (r *Runner) do(ctx context.Context, calls *[]result.HTTPCall, label, method, u string, headers map[string]string, body string) (int, []byte, error) {
	var rdr io.Reader
	if body != "" {
		rdr = bytes.NewBufferString(body)
	}
	req, err := http.NewRequestWithContext(ctx, method, u, rdr)
	if err != nil {
		return 0, nil, err
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := r.httpClient().Do(req)
	rec := result.HTTPCall{Seq: len(*calls) + 1, At: time.Now().UnixNano(), Label: label, Method: method, URL: u, ReqBody: body, ReqHeaders: redactHeaders(req.Header)}
	if err != nil {
		*calls = append(*calls, rec)
		return 0, nil, err
	}
	rb, _ := io.ReadAll(resp.Body)
	resp.Body.Close()
	rec.Status = resp.StatusCode
	rec.RespHeaders = httpx.CloneHeader(resp.Header)
	rec.RespBody = string(rb)
	*calls = append(*calls, rec)
	return resp.StatusCode, rb, nil
}

func redactHeaders(h http.Header) map[string][]string {
	out := map[string][]string{}
	for k, v := range h {
		if strings.EqualFold(k, "Authorization") {
			out[k] = []string{"***redacted***"}
			continue
		}
		out[k] = v
	}
	return out
}

// Run registers a throwaway client, executes every scenario against it, and
// returns one ModuleResult per scenario (Surface=e2e).
func (r *Runner) Run(ctx context.Context, spec Spec) []result.ModuleResult {
	logf := r.Logf
	if logf == nil {
		logf = func(string, ...any) {}
	}
	var out []result.ModuleResult
	r.acr = spec.Acr

	// 1. Register a throwaway client with a fresh key we control.
	priv, err := generateRSA()
	if err != nil {
		return []result.ModuleResult{r.errRow("e2e client keygen", fmt.Errorf("keygen: %w", err))}
	}
	kid := "e2e-" + strconv.FormatInt(time.Now().Unix(), 10)
	// requestedID is the harness-chosen id used when registering directly against
	// eSignet client-mgmt (mock/sunbird). For mosip, PMS derives the id from the
	// public key, so createClient returns the effective id to use downstream.
	requestedID := "bdd-e2e-" + strconv.FormatInt(time.Now().UnixNano(), 10)

	var setupCalls []result.HTTPCall
	clientID, err := r.createClient(ctx, &setupCalls, priv, kid, requestedID, spec)
	if err != nil {
		// Keep every scenario visible in the report rather than collapsing to a
		// single error row: when the mosipid partner/policy aren't configured
		// (expected while onboarding is pending) the cases show ENV_NOT_READY;
		// a real PMS rejection shows FAILED. Either way the testcases stay in place.
		logf("e2e: client registration failed (%v) — reporting %d scenario(s) as blocked", err, len(spec.Scenarios))
		return r.registrationFailureRows(spec, setupCalls, err)
	}
	logf("e2e: registered client %s", clientID)

	// 2. Run each scenario against the registered client.
	for _, sc := range spec.Scenarios {
		start := time.Now()
		row := result.ModuleResult{Surface: result.SurfaceE2E, Plugin: r.Plugin, Module: sc.Name, HarnessOutcome: result.OutcomeOK}

		if sc.AuthFactor == "" {
			row.Result = "FAILED"
			row.FailedConditions = []result.Condition{{Src: "e2e", Result: "FAILURE", Msg: "scenario config error: auth_factor is required (otp|password|bio|kbi)"}}
			logf("e2e: %-55s -> FAILED (no auth_factor configured)", sc.Name)
			out = append(out, row)
			continue
		}

		answers := mergeAnswers(r.Answers, sc.Credentials)
		preferred := append(esignet.AuthFactorTokens(sc.AuthFactor), esignet.IDTypeTokens(r.IDType)...)

		claims, calls, consentSeen, protoAsserts, ferr := r.runScenario(ctx, priv, kid, clientID, spec.RedirectURI, sc, answers, preferred)
		row.Calls = calls
		row.DurationMs = time.Since(start).Milliseconds()

		// Consent expectations are asserted on every outcome, including the
		// expected-rejection path: a deny-an-essential-claim case that "passed"
		// because the OTP happened to be wrong would evidence nothing about
		// consent. Folding these in makes the rejection reason itself checkable.
		consentAssertions := assertConsent(sc, consentSeen)

		loginField := fmt.Sprintf("login (acr=%s)", sc.AuthFactor)
		switch {
		case ferr != nil && sc.ExpectLoginFailure:
			// Negative case: rejection is the expected, correct outcome — provided
			// anything the scenario asserts about consent also holds.
			row.Assertions = append([]result.Assertion{
				{Field: loginField, Expected: "rejected", Actual: "rejected: " + ferr.Error(), Passed: true},
			}, consentAssertions...)
			if conds := failedConditions(row.Assertions); len(conds) > 0 {
				row.Result = "FAILED"
				row.FailedConditions = conds
				logf("e2e: %-55s -> FAILED (login rejected as expected, but %d consent assertion(s) failed)", sc.Name, len(conds))
				out = append(out, row)
				continue
			}
			row.Result = "PASSED"
			logf("e2e: %-55s -> PASSED (login correctly rejected: %v)", sc.Name, ferr)
			out = append(out, row)
			continue
		case ferr != nil && !sc.ExpectLoginFailure:
			// Positive case that couldn't log in — real failure (may be a
			// missing-credential ACR gap; reported, not silently skipped).
			row.Assertions = append([]result.Assertion{
				{Field: loginField, Expected: "accepted", Actual: "rejected: " + ferr.Error(), Passed: false},
			}, consentAssertions...)
			row.Result = "FAILED"
			row.FailedConditions = []result.Condition{{Src: "e2e", Result: "FAILURE", Msg: ferr.Error()}}
			logf("e2e: %-55s -> FAILED (login: %v)", sc.Name, ferr)
			out = append(out, row)
			continue
		case ferr == nil && sc.ExpectLoginFailure:
			// Negative case whose bad credential was wrongly accepted.
			row.Assertions = append([]result.Assertion{
				{Field: loginField, Expected: "rejected", Actual: "accepted", Passed: false},
			}, consentAssertions...)
			row.Result = "FAILED"
			row.FailedConditions = []result.Condition{{Src: "e2e", Result: "FAILURE", Msg: "expected login to be rejected (negative case), but it was accepted"}}
			logf("e2e: %-55s -> FAILED (expected rejection, login succeeded)", sc.Name)
			out = append(out, row)
			continue
		}

		// Positive case, login succeeded as expected — proceed to claim checks.
		loginAssertion := result.Assertion{Field: loginField, Expected: "accepted", Actual: "accepted", Passed: true}
		assertions := append([]result.Assertion{loginAssertion}, consentAssertions...)
		assertions = append(assertions, protoAsserts...)
		assertions = append(assertions, assertClaims(sc, claims)...)
		row.Assertions = assertions
		conds := failedConditions(assertions)
		if len(conds) > 0 {
			row.Result = "FAILED"
			row.FailedConditions = conds
			if sc.KnownIssue != "" {
				row.HarnessOutcome = result.OutcomeKnownIssue
				row.OutcomeDetail = sc.KnownIssue
				logf("e2e: %-55s -> KNOWN ISSUE (%d claim assertion(s): %s)", sc.Name, len(conds), sc.KnownIssue)
			} else {
				logf("e2e: %-55s -> FAILED (%d claim assertion(s))", sc.Name, len(conds))
			}
		} else {
			row.Result = "PASSED"
			logf("e2e: %-55s -> PASSED (%d claims verified)", sc.Name, len(sc.ExpectPresent))
		}
		out = append(out, row)
	}

	// The registration call is real eSignet traffic, not harness plumbing, so it
	// belongs in the report for debugging — same treatment the failure path
	// (registrationFailureRows) already gives it.
	attachSetupCalls(out, setupCalls)
	return out
}

// attachSetupCalls folds the client-registration call trace into the first
// scenario's row. Both setupCalls and a fresh row's own Calls number their Seq
// from 1 independently, so a plain concatenation would produce two "#1"
// entries in the same row; CollapseCalls re-sorts by capture time and
// renumbers, the same normalization the conformance surface already applies to
// its own per-module trace.
func attachSetupCalls(out []result.ModuleResult, setupCalls []result.HTTPCall) {
	if len(out) == 0 || len(setupCalls) == 0 {
		return
	}
	merged := make([]result.HTTPCall, 0, len(setupCalls)+len(out[0].Calls))
	merged = append(merged, setupCalls...)
	merged = append(merged, out[0].Calls...)
	out[0].Calls = result.CollapseCalls(merged)
}

// mergeAnswers overlays per-scenario credential overrides onto the plugin's base
// identity answers, normalizing override keys the same way the driver does.
func mergeAnswers(base, overrides map[string]string) map[string]string {
	out := make(map[string]string, len(base)+len(overrides))
	maps.Copy(out, base)
	for k, v := range overrides {
		out[esignet.Normalize(k)] = v
	}
	return out
}

// registrationFailureRows turns a client-registration failure into one report
// row per scenario, so the testcases stay visible. A missing mosipid PMS config
// (partner/policy/base not set) is reported ENV_NOT_READY (not run); any other
// failure — e.g. PMS rejecting the request — is reported FAILED. The registration
// call trace is attached to the first row.
func (r *Runner) registrationFailureRows(spec Spec, calls []result.HTTPCall, err error) []result.ModuleResult {
	if len(spec.Scenarios) == 0 {
		row := r.errRow("e2e client registration", err)
		row.Calls = calls
		return []result.ModuleResult{row}
	}
	envNotReady := r.Plugin == "mosip" && (r.PMSBaseURL == "" || r.AuthPartnerID == "" || r.PolicyID == "")
	out := make([]result.ModuleResult, 0, len(spec.Scenarios))
	for i, sc := range spec.Scenarios {
		row := result.ModuleResult{Surface: result.SurfaceE2E, Plugin: r.Plugin, Module: sc.Name}
		if envNotReady {
			row.HarnessOutcome = result.OutcomeEnvNotReady
			row.OutcomeDetail = "client registration unavailable: " + err.Error()
			row.Status = "NOT_RUN"
		} else {
			row.HarnessOutcome = result.OutcomeOK
			row.Result = "FAILED"
			row.FailedConditions = []result.Condition{{Src: "e2e", Result: "FAILURE", Msg: "client registration failed: " + err.Error()}}
		}
		if i == 0 {
			row.Calls = calls
		}
		out = append(out, row)
	}
	return out
}

func (r *Runner) errRow(name string, err error) result.ModuleResult {
	return result.ModuleResult{
		Surface: result.SurfaceE2E, Plugin: r.Plugin, Module: name,
		Result: "FAILED", HarnessOutcome: result.OutcomeOK,
		FailedConditions: []result.Condition{{Src: "e2e", Result: "FAILURE", Msg: err.Error()}},
	}
}

// createClient registers the test client and returns the effective clientId to
// use for the rest of the flow. For mosip it registers through PMS /oauth/client
// (which generates the id); for every other plugin it registers directly against
// eSignet client-mgmt with the harness-chosen requestedID.
func (r *Runner) createClient(ctx context.Context, calls *[]result.HTTPCall, priv *rsa.PrivateKey, kid, requestedID string, spec Spec) (string, error) {
	if r.Plugin == "mosip" {
		return r.createClientViaPMS(ctx, calls, priv, kid, spec)
	}
	return r.createClientViaClientMgmt(ctx, calls, priv, kid, requestedID, spec)
}

// createClientViaClientMgmt registers the test client via eSignet client-mgmt
// (admin bearer token). The harness picks the clientId and it is echoed back.
func (r *Runner) createClientViaClientMgmt(ctx context.Context, calls *[]result.HTTPCall, priv *rsa.PrivateKey, kid, clientID string, spec Spec) (string, error) {
	reqBody := map[string]any{
		"requestTime": time.Now().UTC().Format("2006-01-02T15:04:05.000Z"),
		"request": map[string]any{
			"clientId":          clientID,
			"clientName":        "bdd e2e",
			"clientNameLangMap": map[string]any{"eng": "bdd e2e"},
			"relyingPartyId":    "bdd-e2e-rp",
			"logoUri":           "https://example.org/logo.png",
			"redirectUris":      []string{spec.RedirectURI},
			"publicKey":         publicJWK(priv, kid),
			"userClaims":        spec.UserClaims,
			"authContextRefs":   spec.Acr,
			"grantTypes":        []string{"authorization_code"},
			"clientAuthMethods": []string{"private_key_jwt"},
		},
	}
	body, err := json.Marshal(reqBody)
	if err != nil {
		return "", fmt.Errorf("marshal client-mgmt request: %w", err)
	}
	status, rb, err := r.do(ctx, calls, "create client", http.MethodPost, r.Base+"/client-mgmt/client",
		map[string]string{"Content-Type": "application/json", "Authorization": "Bearer " + r.AdminToken}, string(body))
	if err != nil {
		return "", fmt.Errorf("create client: %w", err)
	}
	if code := firstErrorCode(rb); code != "" {
		return "", fmt.Errorf("create client rejected (HTTP %d): %s", status, code)
	}
	if status < 200 || status > 299 {
		return "", fmt.Errorf("create client failed (HTTP %d): %s", status, snippet(rb))
	}
	return clientID, nil
}

// createClientViaPMS registers the test client through partner-management-service
// /oauth/client (v2). PMS binds it to the configured Auth partner + policy and
// generates the clientId (hash of the public key), which it returns in
// response.clientId — the harness uses that id for authorize + private_key_jwt.
// userClaims/authContextRefs are NOT sent here: they are governed by the policy.
func (r *Runner) createClientViaPMS(ctx context.Context, calls *[]result.HTTPCall, priv *rsa.PrivateKey, kid string, spec Spec) (string, error) {
	if r.PMSBaseURL == "" || r.AuthPartnerID == "" || r.PolicyID == "" {
		return "", fmt.Errorf("mosip client registration needs PMS_BASE_URL, AUTH_PARTNER_ID and AUTH_POLICY_ID")
	}
	reqBody := map[string]any{
		"requestTime": time.Now().UTC().Format("2006-01-02T15:04:05.000Z"),
		"request": map[string]any{
			"name":              "bdd e2e",
			"clientNameLangMap": map[string]any{"eng": "bdd e2e"},
			"authPartnerId":     r.AuthPartnerID,
			"policyId":          r.PolicyID,
			"logoUri":           "https://example.org/logo.png",
			"redirectUris":      []string{spec.RedirectURI},
			"publicKey":         publicJWK(priv, kid),
			"grantTypes":        []string{"authorization_code"},
			"clientAuthMethods": []string{"private_key_jwt"},
		},
	}
	body, err := json.Marshal(reqBody)
	if err != nil {
		return "", fmt.Errorf("marshal PMS request: %w", err)
	}
	url := strings.TrimRight(r.PMSBaseURL, "/") + "/oauth/client"
	status, rb, err := r.do(ctx, calls, "create client (PMS)", http.MethodPost, url,
		map[string]string{"Content-Type": "application/json", "Authorization": "Bearer " + r.AdminToken}, string(body))
	if err != nil {
		return "", fmt.Errorf("create client via PMS: %w", err)
	}
	if code := firstErrorCode(rb); code != "" {
		return "", fmt.Errorf("PMS create client rejected (HTTP %d): %s", status, code)
	}
	if status < 200 || status > 299 {
		return "", fmt.Errorf("PMS create client failed (HTTP %d): %s", status, snippet(rb))
	}
	var resp struct {
		Response struct {
			ClientID string `json:"clientId"`
		} `json:"response"`
	}
	if err := json.Unmarshal(rb, &resp); err != nil {
		return "", fmt.Errorf("PMS create client: parse response (HTTP %d): %w", status, err)
	}
	if resp.Response.ClientID == "" {
		return "", fmt.Errorf("PMS create client: no clientId in response (HTTP %d): %s", status, snippet(rb))
	}
	return resp.Response.ClientID, nil
}

// runScenario runs authorize -> login (driver) -> token -> userinfo for one case
// and returns the parsed userinfo claims, the full call trace, and the protocol
// assertions this surface makes as the relying party (the state/nonce echoes).
// answers and preferred are the scenario-specific credential/ACR resolution the
// driver uses.
func (r *Runner) runScenario(ctx context.Context, priv *rsa.PrivateKey, kid, clientID, redirectURI string, sc Scenario, answers map[string]string, preferred []string) (map[string]any, []result.HTTPCall, consentObservation, []result.Assertion, error) {
	var calls []result.HTTPCall
	verifier, challenge := pkce()
	// proto collects the RP-side protocol checks (state, nonce) so they land in
	// the report's Validation tab rather than only aborting the run.
	var proto []result.Assertion
	state := "st-" + strconv.FormatInt(time.Now().UnixNano(), 10)
	nonce := "no-" + strconv.FormatInt(time.Now().UnixNano(), 10)

	// authorize URL
	q := url.Values{}
	q.Set("response_type", "code")
	q.Set("client_id", clientID)
	q.Set("scope", strings.Join(sc.Scopes, " "))
	q.Set("redirect_uri", redirectURI)
	q.Set("state", state)
	q.Set("nonce", nonce)
	q.Set("code_challenge", challenge)
	q.Set("code_challenge_method", "S256")
	if len(r.acrForClaims()) > 0 {
		q.Set("acr_values", strings.Join(r.acrForClaims(), " "))
	}
	if len(sc.UserinfoClaims) > 0 {
		cj, cerr := json.Marshal(map[string]any{"userinfo": sc.UserinfoClaims})
		if cerr != nil {
			return nil, nil, consentObservation{}, proto, fmt.Errorf("marshal claims request: %w", cerr)
		}
		q.Set("claims", string(cj))
	}
	authURL := r.AuthEndpoint + "?" + q.Encode()

	// login via the shared driver (authorize -> flow/execute -> auth/callback)
	driver := esignet.New(answers, preferred, r.TLSVerify, r.Timeout)
	if r.OTP != nil {
		driver.SetOTPProvider(r.OTP)
	}
	driver.SetConsentPolicy(sc.Consent.consentPolicy())
	fr := driver.Run(r.Base, authURL)
	calls = append(calls, fr.Calls...)
	consentSeen := consentObservation{prompted: fr.ConsentPrompted, denied: fr.ConsentDenied}
	if !fr.OK() {
		return nil, calls, consentSeen, proto,
			fmt.Errorf("login flow failed: %s", firstNonEmpty(fr.Error, "no redirect_uri"))
	}
	// state echo: an RP must reject a callback whose state is not the one it
	// sent, otherwise a code from another authorization request is accepted.
	gotState := stateFromRedirect(fr.RedirectURI)
	proto = append(proto, result.Assertion{
		Field: "authorize state echo", Expected: state, Actual: firstNonEmpty(gotState, "(absent)"),
		Passed: gotState == state,
	})
	code, err := codeFromRedirect(fr.RedirectURI, state)
	if err != nil {
		return nil, calls, consentSeen, proto, err
	}

	// token exchange (private_key_jwt + PKCE).
	//
	// aud is the ISSUER, not the token endpoint. RFC 7523 §3 allows either, and
	// OIDC Core §9 suggests the token endpoint, but eSignet validates against a
	// single configured audience that it sets to the issuer
	// (esignet-service/internal/config/app.go: cfg.JWT.Audience = cfg.Issuer).
	// Sending the token endpoint is rejected as "invalid_client Invalid client
	// assertion" (verified against esdev 2026-07-29). Falls back to the token
	// endpoint if discovery gave no issuer.
	aud := r.Issuer
	if aud == "" {
		aud = r.TokenEndpoint
	}
	assertion, err := clientAssertion(priv, kid, clientID, aud)
	if err != nil {
		return nil, calls, consentSeen, proto, fmt.Errorf("client assertion: %w", err)
	}
	form := url.Values{
		"grant_type":            {"authorization_code"},
		"code":                  {code},
		"redirect_uri":          {redirectURI},
		"client_id":             {clientID},
		"client_assertion_type": {"urn:ietf:params:oauth:client-assertion-type:jwt-bearer"},
		"client_assertion":      {assertion},
		"code_verifier":         {verifier},
	}
	status, rb, err := r.do(ctx, &calls, "token", http.MethodPost, r.TokenEndpoint,
		map[string]string{"Content-Type": "application/x-www-form-urlencoded"}, form.Encode())
	if err != nil {
		return nil, calls, consentSeen, proto, fmt.Errorf("token request: %w", err)
	}
	var tok struct {
		AccessToken string `json:"access_token"`
		IDToken     string `json:"id_token"`
		Error       string `json:"error"`
		ErrorDesc   string `json:"error_description"`
	}
	_ = json.Unmarshal(rb, &tok)
	if tok.AccessToken == "" {
		return nil, calls, consentSeen, proto, fmt.Errorf("token exchange failed (HTTP %d): %s", status, firstNonEmpty(tok.Error+" "+tok.ErrorDesc, snippet(rb)))
	}

	// nonce echo: OIDC Core 3.1.3.7 requires the ID token to carry back the nonce
	// the authorize request sent. Only asserted when an ID token was issued —
	// a scenario that does not request openid gets none.
	if tok.IDToken != "" {
		got := ""
		if payload, derr := decodeJWTPayload(tok.IDToken); derr == nil {
			got, _ = payload["nonce"].(string)
		}
		proto = append(proto, result.Assertion{
			Field: "id_token nonce echo", Expected: nonce, Actual: firstNonEmpty(got, "(absent)"),
			Passed: got == nonce,
		})
	}

	// userinfo
	ustatus, ub, err := r.do(ctx, &calls, "userinfo", http.MethodGet, r.UserinfoEndpoint,
		map[string]string{"Authorization": "Bearer " + tok.AccessToken}, "")
	if err != nil {
		return nil, calls, consentSeen, proto, fmt.Errorf("userinfo request: %w", err)
	}
	// A JSON error body would otherwise parse as a claims map and surface later
	// as a vague "claim X absent" instead of the actual HTTP failure.
	if ustatus < 200 || ustatus > 299 {
		return nil, calls, consentSeen, proto, fmt.Errorf("userinfo request failed (HTTP %d): %s", ustatus, snippet(ub))
	}
	claims, err := parseUserinfo(ctx, ub, r.JWKSURI, r.TLSVerify)
	if err != nil {
		return nil, calls, consentSeen, proto, fmt.Errorf("userinfo parse: %w", err)
	}
	return claims, calls, consentSeen, proto, nil
}

// acrForClaims returns the client's registered ACRs for the authorize request.
func (r *Runner) acrForClaims() []string { return r.acr }
