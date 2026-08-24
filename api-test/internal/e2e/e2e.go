package e2e

import (
	"bytes"
	"context"
	"crypto/rsa"
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

// Filter narrows a Spec's scenarios for a focused run.
type Filter struct {
	AuthFactors []string // keep only these auth_factors (case insensitive)
	Include     []string // keep only names matching at least one regex
	Exclude     []string // drop names matching any regex; applied last, so it wins
}

// Empty reports whether the filter would keep every scenario.
func (f Filter) Empty() bool {
	return len(f.AuthFactors) == 0 && len(f.Include) == 0 && len(f.Exclude) == 0
}

// Select returns a copy of s holding only the scenarios the filter keeps.
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

// Scenario is one full-flow case: the ACR and credentials to drive, the scopes/claims to request.
type Scenario struct {
	Name string `json:"name"`

	// AuthFactor selects the ACR this scenario drives: otp | password | bio | kbi.
	AuthFactor string `json:"auth_factor"`

	// Credentials overrides the plugin's base identity answers for just this scenario.
	Credentials map[string]string `json:"credentials"`

	// ExpectLoginFailure marks a negative case: the flow is expected to be
	// rejected. It covers rejection anywhere in the chain, not only at login —
	// a DPoP-bound client refusing an unproven token call is a negative too.
	ExpectLoginFailure bool `json:"expect_login_failure"`

	// ExpectErrorContains additionally requires the rejection to mention this
	// substring. Without it a negative passes on any failure at all, so a case
	// meant to prove one protocol rule would still pass when something
	// unrelated broke first. Only meaningful with expect_login_failure.
	ExpectErrorContains string `json:"expect_error_contains"`

	// Consent controls how the consent step is answered and what is asserted about it.
	Consent *ConsentSpec `json:"consent"`

	// ClientConfig is the additionalConfig the scenario's client is registered
	// with. Scenarios that ask for the same config share one registered client.
	ClientConfig *ClientConfig `json:"client_config"`

	// Flow overrides what the relying party actually sends, independently of
	// what the client requires. Omitted, the RP does whatever the client config
	// demands — which is what a positive combination case wants.
	Flow *FlowSpec `json:"flow"`

	// Introspect asks the scenario to submit the tokens the flow obtained to
	// /oauth2/introspect once the flow has completed, one request per case.
	// Only meaningful on a scenario that expects the flow to succeed: a
	// rejected login has no token to inspect.
	Introspect []IntrospectCase `json:"introspect"`

	Scopes         []string          `json:"scopes"`
	UserinfoClaims map[string]any    `json:"userinfo_claims"` // OIDC "claims".userinfo request object
	ExpectPresent  []string          `json:"expect_present"`
	ExpectValues   map[string]string `json:"expect_values"`
	ExpectAbsent   []string          `json:"expect_absent"`
	// KnownIssue marks the scenario as a tracked environment gap, reported in the Known bucket.
	KnownIssue string `json:"known_issue"`
}

// ConsentSpec is a scenario's consent behaviour and expectations.
type ConsentSpec struct {
	// Deny withholds approval from these element names (case-insensitive). A name
	// the prompt never offers fails the scenario rather than passing vacuously.
	Deny []string `json:"deny"`
	// DenyAll withholds approval from every element offered.
	DenyAll bool `json:"deny_all"`

	// ExpectPrompt asserts whether a consent step appeared: "yes", "no", or "" for no assertion.
	ExpectPrompt string `json:"expect_prompt"`
}

// consentPolicy maps the scenario's spec onto the driver's policy.
func (c *ConsentSpec) consentPolicy() esignet.ConsentPolicy {
	if c == nil {
		return esignet.ConsentPolicy{}
	}
	return esignet.ConsentPolicy{Deny: c.Deny, DenyAll: c.DenyAll}
}

// consentObservation is what the flow actually did about consent, carried back even on login failure.
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

	// Evidence that a scenario's requested withholding actually happened.
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
	// PAREndpoint is discovery's pushed_authorization_request_endpoint. Empty
	// when the deployment advertises none, which blocks the PAR scenarios only.
	PAREndpoint string
	// IntrospectEndpoint is discovery's introspection_endpoint (RFC 7662).
	// Empty when the deployment advertises none, which blocks the
	// introspection scenarios only.
	IntrospectEndpoint string
	// DPoPAlgs is discovery's dpop_signing_alg_values_supported, narrowed to
	// one alg the harness can produce.
	DPoPAlgs   []string
	AdminToken string
	Plugin     string
	Answers    map[string]string // base identity answers; Scenario.Credentials overrides per case
	IDType     string            // uin | vid | phone | email — login-id-type preference, combined with each scenario's AuthFactor
	TLSVerify  bool
	Timeout    time.Duration
	Logf       func(string, ...any)
	OTP        esignet.OTPProvider // dynamic OTP source; nil for static OTP

	// PMS registration (mosip plugin only).
	PMSBaseURL    string
	AuthPartnerID string
	PolicyID      string

	acr    []string // registered ACRs, set from the spec in Run
	client *http.Client
}

func (r *Runner) httpClient() *http.Client {
	if r.client == nil {
		r.client = httpx.NewClient(r.TLSVerify, r.Timeout)
	}
	return r.client
}

// do performs an HTTP call and records it (Authorization redacted) into calls.
func (r *Runner) do(ctx context.Context, calls *[]result.HTTPCall, label, method, u string, headers map[string]string, body string) (int, []byte, error) {
	status, rb, _, err := r.doWithHeaders(ctx, calls, label, method, u, headers, body)
	return status, rb, err
}

// doWithHeaders is do, additionally returning the response headers. DPoP needs
// them: a server that wants a nonce supplies it in DPoP-Nonce alongside the
// rejection, and the retry has to read it.
func (r *Runner) doWithHeaders(ctx context.Context, calls *[]result.HTTPCall, label, method, u string, headers map[string]string, body string) (int, []byte, http.Header, error) {
	var rdr io.Reader
	if body != "" {
		rdr = bytes.NewBufferString(body)
	}
	req, err := http.NewRequestWithContext(ctx, method, u, rdr)
	if err != nil {
		return 0, nil, nil, err
	}
	for k, v := range headers {
		req.Header.Set(k, v)
	}
	resp, err := r.httpClient().Do(req)
	rec := result.HTTPCall{Seq: len(*calls) + 1, At: time.Now().UnixNano(), Label: label, Method: method, URL: u, ReqBody: body, ReqHeaders: redactHeaders(req.Header)}
	if err != nil {
		*calls = append(*calls, rec)
		return 0, nil, nil, err
	}
	rb, _ := io.ReadAll(resp.Body)
	_ = resp.Body.Close()
	rec.Status = resp.StatusCode
	rec.RespHeaders = httpx.CloneHeader(resp.Header)
	rec.RespBody = string(rb)
	*calls = append(*calls, rec)
	return resp.StatusCode, rb, resp.Header, nil
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

	// 1. Register the clients the scenarios ask for. Scenarios sharing a client
	// config share a client, registered on first use — so a spec that names no
	// client_config still registers exactly one, up front, as it always did.
	pool := &clientPool{runner: r, spec: spec}
	var setupCalls []result.HTTPCall
	if _, err := pool.get(ctx, &setupCalls, ClientConfig{}); err != nil {
		// Keep every scenario visible in the report rather than collapsing to a single error row.
		logf("e2e: client registration failed (%v) — reporting %d scenario(s) as blocked", err, len(spec.Scenarios))
		return r.registrationFailureRows(spec, setupCalls, err)
	}

	// 2. Run each scenario against the client its config calls for.
	for _, sc := range spec.Scenarios {
		start := time.Now()
		row := result.ModuleResult{Surface: result.SurfaceE2E, Plugin: r.Plugin, Module: sc.Name, HarnessOutcome: result.OutcomeOK}

		if msg := scenarioConfigError(sc); msg != "" {
			row.Result = "FAILED"
			row.FailedConditions = []result.Condition{{Src: "e2e", Result: "FAILURE", Msg: "scenario config error: " + msg}}
			logf("e2e: %-55s -> FAILED (%s)", sc.Name, msg)
			out = append(out, row)
			continue
		}

		cfg := ClientConfig{}
		if sc.ClientConfig != nil {
			cfg = *sc.ClientConfig
		}
		cl, cerr := pool.get(ctx, &setupCalls, cfg)
		if cerr != nil {
			// One combination's client failing to register must not stop the
			// rest of the run: report this scenario and carry on.
			row.Result = "FAILED"
			row.FailedConditions = []result.Condition{{Src: "e2e", Result: "FAILURE", Msg: fmt.Sprintf("client registration for config %s failed: %v", cfg.label(), cerr)}}
			row.DurationMs = time.Since(start).Milliseconds()
			logf("e2e: %-55s -> FAILED (client config %s: %v)", sc.Name, cfg.label(), cerr)
			out = append(out, row)
			continue
		}

		answers := mergeAnswers(r.Answers, sc.Credentials)
		preferred := append(esignet.AuthFactorTokens(sc.AuthFactor), esignet.IDTypeTokens(r.IDType)...)

		claims, calls, consentSeen, protoAsserts, ferr := r.runScenario(ctx, cl, spec.RedirectURI, sc, answers, preferred)
		row.Calls = calls
		row.DurationMs = time.Since(start).Milliseconds()

		// Consent expectations are asserted on every outcome, including the expected-rejection path.
		consentAssertions := assertConsent(sc, consentSeen)

		loginField := fmt.Sprintf("login (acr=%s)", sc.AuthFactor)
		if cfg != (ClientConfig{}) || sc.Flow != nil {
			// Name the protocol combination in the row, so a failure reads as
			// "this combination was rejected" rather than a bare login failure.
			loginField = fmt.Sprintf("flow (acr=%s, client=%s)", sc.AuthFactor, cfg.label())
		}
		switch {
		case ferr != nil && sc.ExpectLoginFailure:
			// Negative case: rejection is the expected outcome, provided the consent and echo assertions hold.
			row.Assertions = append([]result.Assertion{
				{Field: loginField, Expected: "rejected", Actual: "rejected: " + ferr.Error(), Passed: true},
			}, consentAssertions...)
			if want := sc.ExpectErrorContains; want != "" {
				row.Assertions = append(row.Assertions, result.Assertion{
					Field: "rejection reason", Expected: "contains " + strconv.Quote(want),
					Actual: ferr.Error(), Passed: strings.Contains(ferr.Error(), want),
				})
			}
			row.Assertions = append(row.Assertions, protoAsserts...)
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
			row.Assertions = append(row.Assertions, protoAsserts...)
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
			row.Assertions = append(row.Assertions, protoAsserts...)
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

	// The registration call is real eSignet traffic, so it belongs in the report for debugging.
	attachSetupCalls(out, setupCalls)
	return out
}

// attachSetupCalls folds the client-registration call trace into the first scenario's row.
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

// registrationFailureRows turns a registration failure into one report row per scenario.
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

// testClient is one registered throwaway client and the key that authenticates it.
type testClient struct {
	priv     *rsa.PrivateKey
	kid      string
	clientID string
	cfg      ClientConfig
}

// clientPool registers one client per distinct ClientConfig, on first use, and
// hands the same one back for every later scenario with that config.
//
// Sharing matters beyond saving a call: the consent scenarios are
// order-dependent and assert that a stored consent is reused, which only holds
// while they run against a single client. They name no client_config, so they
// all land on the same zero-value entry.
type clientPool struct {
	runner  *Runner
	spec    Spec
	clients map[string]*testClient
}

func (p *clientPool) get(ctx context.Context, calls *[]result.HTTPCall, cfg ClientConfig) (*testClient, error) {
	if cl, ok := p.clients[cfg.key()]; ok {
		return cl, nil
	}
	r := p.runner
	priv, err := generateRSA()
	if err != nil {
		return nil, fmt.Errorf("keygen: %w", err)
	}
	// Unique per client so two clients registered in the same second cannot
	// collide on either the kid or the harness-chosen client id.
	stamp := strconv.FormatInt(time.Now().UnixNano(), 10)
	cl := &testClient{priv: priv, kid: "e2e-" + stamp, cfg: cfg}
	cl.clientID, err = r.createClient(ctx, calls, cl, "api-e2e-"+stamp, p.spec)
	if err != nil {
		return nil, err
	}
	if p.clients == nil {
		p.clients = map[string]*testClient{}
	}
	p.clients[cfg.key()] = cl
	if r.Logf != nil {
		r.Logf("e2e: registered client %s (config: %s)", cl.clientID, cfg.label())
	}
	return cl, nil
}

// createClient registers the test client and returns the effective clientId for the rest of the flow.
func (r *Runner) createClient(ctx context.Context, calls *[]result.HTTPCall, cl *testClient, requestedID string, spec Spec) (string, error) {
	if r.Plugin == "mosip" {
		return r.createClientViaPMS(ctx, calls, cl, spec)
	}
	return r.createClientViaClientMgmt(ctx, calls, cl, requestedID, spec)
}

// createClientViaClientMgmt registers the test client via eSignet client-mgmt
// (admin bearer token). The harness picks the clientId and it is echoed back.
func (r *Runner) createClientViaClientMgmt(ctx context.Context, calls *[]result.HTTPCall, cl *testClient, clientID string, spec Spec) (string, error) {
	request := map[string]any{
		"clientId":          clientID,
		"clientName":        "api e2e",
		"clientNameLangMap": map[string]any{"eng": "api e2e"},
		"relyingPartyId":    "api-e2e-rp",
		"logoUri":           "https://example.org/logo.png",
		"redirectUris":      []string{spec.RedirectURI},
		"publicKey":         publicJWK(cl.priv, cl.kid),
		"userClaims":        spec.UserClaims,
		"authContextRefs":   spec.Acr,
		"grantTypes":        []string{"authorization_code"},
		"clientAuthMethods": []string{"private_key_jwt"},
	}
	if ac := cl.cfg.additionalConfig(); ac != nil {
		request["additionalConfig"] = ac
	}
	reqBody := map[string]any{
		"requestTime": time.Now().UTC().Format("2006-01-02T15:04:05.000Z"),
		"request":     request,
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

// createClientViaPMS registers the test client through partner-management-service /oauth/client (v2).
func (r *Runner) createClientViaPMS(ctx context.Context, calls *[]result.HTTPCall, cl *testClient, spec Spec) (string, error) {
	if r.PMSBaseURL == "" || r.AuthPartnerID == "" || r.PolicyID == "" {
		return "", fmt.Errorf("mosip client registration needs PMS_BASE_URL, AUTH_PARTNER_ID and AUTH_POLICY_ID")
	}
	request := map[string]any{
		"name":              "api e2e",
		"clientNameLangMap": map[string]any{"eng": "api e2e"},
		"authPartnerId":     r.AuthPartnerID,
		"policyId":          r.PolicyID,
		"logoUri":           "https://example.org/logo.png",
		"redirectUris":      []string{spec.RedirectURI},
		"publicKey":         publicJWK(cl.priv, cl.kid),
		"grantTypes":        []string{"authorization_code"},
		"clientAuthMethods": []string{"private_key_jwt"},
	}
	// PMS owns the client record for the mosip plugin, so whether it forwards
	// additionalConfig to eSignet is its call, not the harness's. Sending it is
	// the only way to find out; if PMS drops it, the scenario fails at the
	// protocol step it configured rather than passing while unhardened, which
	// is the honest outcome to report.
	if ac := cl.cfg.additionalConfig(); ac != nil {
		request["additionalConfig"] = ac
	}
	reqBody := map[string]any{
		"requestTime": time.Now().UTC().Format("2006-01-02T15:04:05.000Z"),
		"request":     request,
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

// scenarioConfigError reports why a scenario cannot be run at all, or "" when
// it is well formed. These are mistakes in the spec file rather than eSignet
// behaviour, so they are caught before the flow is driven.
func scenarioConfigError(sc Scenario) string {
	if sc.AuthFactor == "" {
		return "auth_factor is required (otp|password|bio|kbi)"
	}
	// A rejected flow reaches no introspection, and the expected-rejection
	// branch would report the scenario PASSED without a single introspection
	// call having been made.
	if len(sc.Introspect) > 0 && sc.ExpectLoginFailure {
		return "introspect and expect_login_failure are mutually exclusive — a rejected flow issues no token to introspect"
	}
	return ""
}

// runScenario drives authorize/login/token/userinfo and returns claims, trace and protocol assertions.
func (r *Runner) runScenario(ctx context.Context, cl *testClient, redirectURI string, sc Scenario, answers map[string]string, preferred []string) (map[string]any, []result.HTTPCall, consentObservation, []result.Assertion, error) {
	var calls []result.HTTPCall
	// proto collects the RP-side protocol checks (state, nonce, and the
	// PAR/DPoP evidence) so they land in the report's Validation tab rather
	// than only aborting the run.
	var proto []result.Assertion

	plan, perr := resolveFlow(cl.cfg, sc.Flow)
	if perr != nil {
		return nil, nil, consentObservation{}, proto, perr
	}
	// Validated before the flow is driven, so a mistyped selector costs a
	// config error rather than a full login that then cannot be introspected.
	introspectCases, ierr := resolveIntrospect(sc.Introspect)
	if ierr != nil {
		return nil, nil, consentObservation{}, proto, ierr
	}
	// One DPoP key for the whole flow: the proof at PAR binds the auth code, and
	// the proof at token must present the same thumbprint to redeem it.
	var dp *dpopSigner
	if plan.useDPoP {
		var derr error
		if dp, derr = newDPoPSigner(chooseDPoPAlg(r.DPoPAlgs)); derr != nil {
			return nil, nil, consentObservation{}, proto, derr
		}
	}

	verifier, challenge := pkce()
	state := "st-" + strconv.FormatInt(time.Now().UnixNano(), 10)
	nonce := "no-" + strconv.FormatInt(time.Now().UnixNano(), 10)

	// authorize parameters — pushed to /oauth2/par or sent on the authorize URL,
	// depending on the plan, but identical either way.
	q := url.Values{}
	q.Set("response_type", "code")
	q.Set("client_id", cl.clientID)
	q.Set("scope", strings.Join(sc.Scopes, " "))
	q.Set("redirect_uri", redirectURI)
	q.Set("state", state)
	q.Set("nonce", nonce)
	switch plan.pkce {
	case pkceS256:
		q.Set("code_challenge", challenge)
		q.Set("code_challenge_method", "S256")
	case pkcePlain:
		// The verifier itself is the challenge under the plain method, which
		// FAPI2 forbids and eSignet is expected to reject.
		q.Set("code_challenge", verifier)
		q.Set("code_challenge_method", "plain")
	case pkceNone:
		// No challenge at all: a client with require_pkce must refuse.
	}
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

	// Push the request first when the plan calls for it. RFC 9126: the
	// authorize URL then carries only client_id and the returned request_uri,
	// and every other parameter comes from what the server stored.
	authURL := r.AuthEndpoint + "?" + q.Encode()
	if plan.usePAR {
		requestURI, aerr, ferr := r.pushAuthorizationRequest(ctx, &calls, cl, q, dp)
		proto = append(proto, aerr...)
		if ferr != nil {
			return nil, calls, consentObservation{}, proto, ferr
		}
		authURL = r.AuthEndpoint + "?" + url.Values{
			"client_id":   {cl.clientID},
			"request_uri": {requestURI},
		}.Encode()
	}

	// login via the shared driver (authorize -> flow/execute -> auth/callback)
	driver := esignet.New(answers, preferred, r.TLSVerify, r.Timeout)
	if r.OTP != nil {
		driver.SetOTPProvider(r.OTP)
	}
	driver.SetConsentPolicy(sc.Consent.consentPolicy())
	fr := driver.Run(ctx, r.Base, authURL)
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

	// token exchange (private_key_jwt + PKCE): aud is the ISSUER, which is what eSignet validates against.
	aud := r.Issuer
	if aud == "" {
		aud = r.TokenEndpoint
	}
	assertion, err := clientAssertion(cl.priv, cl.kid, cl.clientID, aud)
	if err != nil {
		return nil, calls, consentSeen, proto, fmt.Errorf("client assertion: %w", err)
	}
	form := url.Values{
		"grant_type":            {"authorization_code"},
		"code":                  {code},
		"redirect_uri":          {redirectURI},
		"client_id":             {cl.clientID},
		"client_assertion_type": {clientAssertionTypeJWTBearer},
		"client_assertion":      {assertion},
	}
	// A flow that sent no challenge has no verifier to present.
	if plan.pkce != pkceNone {
		form.Set("code_verifier", verifier)
	}

	// The token proof normally uses the same key as the PAR proof, so the
	// thumbprint the auth code was bound to still matches. A mismatch scenario
	// deliberately swaps in a second key to prove that binding is enforced.
	tokenSigner := dp
	if plan.useDPoP && plan.dpopKeyMismatch {
		other, oerr := newDPoPSigner(dp.alg)
		if oerr != nil {
			return nil, calls, consentSeen, proto, oerr
		}
		tokenSigner = other
	}
	status, rb, err := r.postWithDPoP(ctx, &calls, "token", r.TokenEndpoint, form.Encode(), tokenSigner, "")
	if err != nil {
		return nil, calls, consentSeen, proto, fmt.Errorf("token request: %w", err)
	}
	var tok struct {
		AccessToken string `json:"access_token"`
		IDToken     string `json:"id_token"`
		TokenType   string `json:"token_type"`
		Error       string `json:"error"`
		ErrorDesc   string `json:"error_description"`
	}
	_ = json.Unmarshal(rb, &tok)
	if tok.AccessToken == "" {
		return nil, calls, consentSeen, proto, fmt.Errorf("token exchange failed (HTTP %d): %s", status, firstNonEmpty(tok.Error+" "+tok.ErrorDesc, snippet(rb)))
	}
	// What the introspection cases submit and are cross-checked against.
	tk := tokenSet{accessToken: tok.AccessToken, idToken: tok.IDToken}
	if plan.useDPoP {
		tk.dpopJKT = tokenSigner.jkt
	}

	// A DPoP-bound token must be issued as token_type=DPoP: a Bearer token here
	// would be usable without any proof, silently undoing the binding.
	if plan.useDPoP {
		proto = append(proto, result.Assertion{
			Field: "token_type", Expected: "DPoP",
			Actual: firstNonEmpty(tok.TokenType, "(absent)"),
			Passed: strings.EqualFold(tok.TokenType, "DPoP"),
		})
		// cnf.jkt is the binding itself — the thumbprint of the key that must
		// prove possession at every resource call.
		if payload, derr := decodeJWTPayload(tok.AccessToken); derr == nil {
			got := ""
			if cnf, ok := payload["cnf"].(map[string]any); ok {
				got, _ = cnf["jkt"].(string)
			}
			proto = append(proto, result.Assertion{
				Field: "access_token cnf.jkt", Expected: tokenSigner.jkt,
				Actual: firstNonEmpty(got, "(absent)"), Passed: got == tokenSigner.jkt,
			})
		}
	}

	// nonce echo: OIDC Core 3.1.3.7 requires the ID token to carry back the authorize request's nonce.
	if tok.IDToken != "" {
		got := ""
		if payload, derr := decodeJWTPayload(tok.IDToken); derr == nil {
			got, _ = payload["nonce"].(string)
			// The subject the introspection response must agree with.
			tk.sub, _ = payload["sub"].(string)
		}
		proto = append(proto, result.Assertion{
			Field: "id_token nonce echo", Expected: nonce, Actual: firstNonEmpty(got, "(absent)"),
			Passed: got == nonce,
		})
	}

	// userinfo — presented with the DPoP scheme and a proof carrying ath when
	// the token is bound, with Bearer otherwise. A scenario can force Bearer on
	// a bound token to check the resource endpoint honours the binding.
	uheaders := map[string]string{"Authorization": "Bearer " + tok.AccessToken}
	usigner := tokenSigner
	if plan.useDPoP && !plan.bearerAtUserinfo {
		uheaders["Authorization"] = "DPoP " + tok.AccessToken
	} else {
		// Bearer: no proof to sign, and none to retry with a nonce.
		usigner = nil
	}
	ustatus, ub, err := r.getWithDPoP(ctx, &calls, "userinfo", r.UserinfoEndpoint, uheaders, usigner, tok.AccessToken)
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

	// Introspection runs last, on tokens the flow has just proven usable: a
	// failure here is the endpoint's, not a stale or never-valid token's.
	if len(introspectCases) > 0 {
		proto = append(proto, r.introspect(ctx, &calls, cl, introspectCases, tk)...)
	}
	return claims, calls, consentSeen, proto, nil
}

// requestURIPrefix is the URN namespace RFC 9126 reserves for PAR request URIs.
const requestURIPrefix = "urn:ietf:params:oauth:request_uri:"

// pushAuthorizationRequest posts the authorization parameters to
// /oauth2/par and returns the request_uri to drive authorize with.
//
// The endpoint is client-authenticated exactly like token (private_key_jwt with
// the issuer as audience), and a DPoP proof sent here is what binds the
// resulting authorization code to the proof key.
//
// It returns any report assertions alongside the error so evidence survives a
// failure: a run that could not push still shows why in the Validation tab.
func (r *Runner) pushAuthorizationRequest(ctx context.Context, calls *[]result.HTTPCall, cl *testClient, params url.Values, dp *dpopSigner) (string, []result.Assertion, error) {
	if r.PAREndpoint == "" {
		return "", nil, fmt.Errorf("this scenario requires PAR, but discovery advertises no pushed_authorization_request_endpoint")
	}
	aud := r.Issuer
	if aud == "" {
		aud = r.PAREndpoint
	}
	assertion, err := clientAssertion(cl.priv, cl.kid, cl.clientID, aud)
	if err != nil {
		return "", nil, fmt.Errorf("par client assertion: %w", err)
	}
	form := url.Values{}
	maps.Copy(form, params)
	form.Set("client_assertion_type", clientAssertionTypeJWTBearer)
	form.Set("client_assertion", assertion)
	if dp != nil {
		// dpop_jkt states the binding in the request as well as the proof. The
		// server rejects the pair when they disagree, so sending both is a
		// stronger request than sending only the header.
		form.Set("dpop_jkt", dp.jkt)
	}

	status, rb, err := r.postWithDPoP(ctx, calls, "par", r.PAREndpoint, form.Encode(), dp, "")
	if err != nil {
		return "", nil, fmt.Errorf("par request: %w", err)
	}
	var resp struct {
		RequestURI string `json:"request_uri"`
		ExpiresIn  int64  `json:"expires_in"`
		Error      string `json:"error"`
		ErrorDesc  string `json:"error_description"`
	}
	_ = json.Unmarshal(rb, &resp)
	if resp.RequestURI == "" {
		return "", nil, fmt.Errorf("par failed (HTTP %d): %s", status, firstNonEmpty(resp.Error+" "+resp.ErrorDesc, snippet(rb)))
	}
	asserts := []result.Assertion{{
		Field: "PAR request_uri", Expected: requestURIPrefix + "…",
		Actual: resp.RequestURI, Passed: strings.HasPrefix(resp.RequestURI, requestURIPrefix),
	}}
	return resp.RequestURI, asserts, nil
}

// postWithDPoP posts a form, attaching a DPoP proof when dp is non-nil.
//
// A server may require a nonce it alone can issue: it answers the first proof
// with use_dpop_nonce and the nonce to use. That is a one-shot handshake, so
// the call is retried exactly once with the new nonce — a loop here would spin
// against a server that keeps rejecting.
func (r *Runner) postWithDPoP(ctx context.Context, calls *[]result.HTTPCall, label, endpoint, body string, dp *dpopSigner, accessToken string) (int, []byte, error) {
	headers := map[string]string{"Content-Type": "application/x-www-form-urlencoded"}
	if dp == nil {
		status, rb, err := r.do(ctx, calls, label, http.MethodPost, endpoint, headers, body)
		return status, rb, err
	}
	for attempt := 0; ; attempt++ {
		proof, err := dp.proof(http.MethodPost, endpoint, accessToken)
		if err != nil {
			return 0, nil, fmt.Errorf("%s dpop proof: %w", label, err)
		}
		headers["DPoP"] = proof
		status, rb, respHeaders, err := r.doWithHeaders(ctx, calls, label, http.MethodPost, endpoint, headers, body)
		if err != nil {
			return status, rb, err
		}
		if attempt == 0 && isDPoPNonceError(rb) && dp.dpopNonceFrom(respHeaders) {
			continue
		}
		return status, rb, nil
	}
}

// acrForClaims returns the client's registered ACRs for the authorize request.
// getWithDPoP is postWithDPoP for a resource read: it signs a proof over the
// GET, and retries once when the resource server answers use_dpop_nonce with a
// DPoP-Nonce of its own. RFC 9449 s8 lets it demand one, and without the retry
// every DPoP scenario would fail at userinfo against such a deployment — a
// harness gap reported as an eSignet conformance failure.
//
// dp nil means the token is presented as a Bearer: there is no proof to sign
// and nothing a nonce could be folded into, so the call is made once.
func (r *Runner) getWithDPoP(ctx context.Context, calls *[]result.HTTPCall, label, endpoint string, headers map[string]string, dp *dpopSigner, accessToken string) (int, []byte, error) {
	if dp == nil {
		return r.do(ctx, calls, label, http.MethodGet, endpoint, headers, "")
	}
	for attempt := 0; ; attempt++ {
		proof, err := dp.proof(http.MethodGet, endpoint, accessToken)
		if err != nil {
			return 0, nil, fmt.Errorf("%s dpop proof: %w", label, err)
		}
		headers["DPoP"] = proof
		status, rb, respHeaders, err := r.doWithHeaders(ctx, calls, label, http.MethodGet, endpoint, headers, "")
		if err != nil {
			return status, rb, err
		}
		if attempt == 0 && isDPoPNonceError(rb) && dp.dpopNonceFrom(respHeaders) {
			continue
		}
		return status, rb, nil
	}
}

func (r *Runner) acrForClaims() []string { return r.acr }
