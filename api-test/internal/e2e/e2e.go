package e2e

import (
	"bytes"
	"crypto/rsa"
	"crypto/tls"
	"encoding/json"
	"fmt"
	"io"
	"maps"
	"net/http"
	"net/url"
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
			Timeout:   r.Timeout,
			Transport: &http.Transport{TLSClientConfig: &tls.Config{
				MinVersion:         tls.VersionTLS12,
				InsecureSkipVerify: !r.TLSVerify,
			}},
		}
	}
	return r.client
}

// do performs an HTTP call and records it (Authorization redacted) into calls.
func (r *Runner) do(calls *[]result.HTTPCall, label, method, u string, headers map[string]string, body string) (int, []byte, error) {
	var rdr io.Reader
	if body != "" {
		rdr = bytes.NewBufferString(body)
	}
	req, err := http.NewRequest(method, u, rdr)
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
func (r *Runner) Run(spec Spec) []result.ModuleResult {
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
	clientID, err := r.createClient(&setupCalls, priv, kid, requestedID, spec)
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

		claims, calls, ferr := r.runScenario(priv, kid, clientID, spec.RedirectURI, sc, answers, preferred)
		row.Calls = calls
		row.DurationMs = time.Since(start).Milliseconds()

		loginField := fmt.Sprintf("login (acr=%s)", sc.AuthFactor)
		switch {
		case ferr != nil && sc.ExpectLoginFailure:
			// Negative case: rejection is the expected, correct outcome.
			row.Assertions = []result.Assertion{{Field: loginField, Expected: "rejected", Actual: "rejected: " + ferr.Error(), Passed: true}}
			row.Result = "PASSED"
			logf("e2e: %-55s -> PASSED (login correctly rejected: %v)", sc.Name, ferr)
			out = append(out, row)
			continue
		case ferr != nil && !sc.ExpectLoginFailure:
			// Positive case that couldn't log in — real failure (may be a
			// missing-credential ACR gap; reported, not silently skipped).
			row.Assertions = []result.Assertion{{Field: loginField, Expected: "accepted", Actual: "rejected: " + ferr.Error(), Passed: false}}
			row.Result = "FAILED"
			row.FailedConditions = []result.Condition{{Src: "e2e", Result: "FAILURE", Msg: ferr.Error()}}
			logf("e2e: %-55s -> FAILED (login: %v)", sc.Name, ferr)
			out = append(out, row)
			continue
		case ferr == nil && sc.ExpectLoginFailure:
			// Negative case whose bad credential was wrongly accepted.
			row.Assertions = []result.Assertion{{Field: loginField, Expected: "rejected", Actual: "accepted", Passed: false}}
			row.Result = "FAILED"
			row.FailedConditions = []result.Condition{{Src: "e2e", Result: "FAILURE", Msg: "expected login to be rejected (negative case), but it was accepted"}}
			logf("e2e: %-55s -> FAILED (expected rejection, login succeeded)", sc.Name)
			out = append(out, row)
			continue
		}

		// Positive case, login succeeded as expected — proceed to claim checks.
		loginAssertion := result.Assertion{Field: loginField, Expected: "accepted", Actual: "accepted", Passed: true}
		assertions := append([]result.Assertion{loginAssertion}, assertClaims(sc, claims)...)
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
	return out
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
func (r *Runner) createClient(calls *[]result.HTTPCall, priv *rsa.PrivateKey, kid, requestedID string, spec Spec) (string, error) {
	if r.Plugin == "mosip" {
		return r.createClientViaPMS(calls, priv, kid, spec)
	}
	return r.createClientViaClientMgmt(calls, priv, kid, requestedID, spec)
}

// createClientViaClientMgmt registers the test client via eSignet client-mgmt
// (admin bearer token). The harness picks the clientId and it is echoed back.
func (r *Runner) createClientViaClientMgmt(calls *[]result.HTTPCall, priv *rsa.PrivateKey, kid, clientID string, spec Spec) (string, error) {
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
	body, _ := json.Marshal(reqBody)
	status, rb, err := r.do(calls, "create client", http.MethodPost, r.Base+"/client-mgmt/client",
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
func (r *Runner) createClientViaPMS(calls *[]result.HTTPCall, priv *rsa.PrivateKey, kid string, spec Spec) (string, error) {
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
	body, _ := json.Marshal(reqBody)
	url := strings.TrimRight(r.PMSBaseURL, "/") + "/oauth/client"
	status, rb, err := r.do(calls, "create client (PMS)", http.MethodPost, url,
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
// and returns the parsed userinfo claims plus the full call trace. answers and
// preferred are the scenario-specific credential/ACR resolution the driver uses.
func (r *Runner) runScenario(priv *rsa.PrivateKey, kid, clientID, redirectURI string, sc Scenario, answers map[string]string, preferred []string) (map[string]any, []result.HTTPCall, error) {
	var calls []result.HTTPCall
	verifier, challenge := pkce()
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
		cj, _ := json.Marshal(map[string]any{"userinfo": sc.UserinfoClaims})
		q.Set("claims", string(cj))
	}
	authURL := r.AuthEndpoint + "?" + q.Encode()

	// login via the shared driver (authorize -> flow/execute -> auth/callback)
	driver := esignet.New(answers, preferred, r.TLSVerify, r.Timeout)
	if r.OTP != nil {
		driver.SetOTPProvider(r.OTP)
	}
	fr := driver.Run(r.Base, authURL)
	calls = append(calls, fr.Calls...)
	if !fr.OK() {
		return nil, calls, fmt.Errorf("login flow failed: %s", firstNonEmpty(fr.Error, "no redirect_uri"))
	}
	code, err := codeFromRedirect(fr.RedirectURI)
	if err != nil {
		return nil, calls, err
	}

	// token exchange (private_key_jwt + PKCE)
	assertion, err := clientAssertion(priv, kid, clientID, r.TokenEndpoint)
	if err != nil {
		return nil, calls, fmt.Errorf("client assertion: %w", err)
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
	status, rb, err := r.do(&calls, "token", http.MethodPost, r.TokenEndpoint,
		map[string]string{"Content-Type": "application/x-www-form-urlencoded"}, form.Encode())
	if err != nil {
		return nil, calls, fmt.Errorf("token request: %w", err)
	}
	var tok struct {
		AccessToken string `json:"access_token"`
		IDToken     string `json:"id_token"`
		Error       string `json:"error"`
		ErrorDesc   string `json:"error_description"`
	}
	_ = json.Unmarshal(rb, &tok)
	if tok.AccessToken == "" {
		return nil, calls, fmt.Errorf("token exchange failed (HTTP %d): %s", status, firstNonEmpty(tok.Error+" "+tok.ErrorDesc, snippet(rb)))
	}

	// userinfo
	ustatus, ub, err := r.do(&calls, "userinfo", http.MethodGet, r.UserinfoEndpoint,
		map[string]string{"Authorization": "Bearer " + tok.AccessToken}, "")
	if err != nil {
		return nil, calls, fmt.Errorf("userinfo request: %w", err)
	}
	// A JSON error body would otherwise parse as a claims map and surface later
	// as a vague "claim X absent" instead of the actual HTTP failure.
	if ustatus < 200 || ustatus > 299 {
		return nil, calls, fmt.Errorf("userinfo request failed (HTTP %d): %s", ustatus, snippet(ub))
	}
	claims, err := parseUserinfo(ub, r.JWKSURI, r.TLSVerify)
	if err != nil {
		return nil, calls, fmt.Errorf("userinfo parse: %w", err)
	}
	return claims, calls, nil
}

// acrForClaims returns the client's registered ACRs for the authorize request.
func (r *Runner) acrForClaims() []string { return r.acr }
