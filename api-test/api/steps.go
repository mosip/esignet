// Package api is the godog engine for the API surface — endpoint-level scenarios
// over client-mgmt and flow/execute — kept as its own module because godog and
// gjson are the harness's only non-stdlib dependencies.
package api

import (
	"bytes"
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/tls"
	"encoding/base64"
	"fmt"
	"io"
	"math/big"
	"net/http"
	"net/url"
	"os"
	"regexp"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/cucumber/godog"
	"github.com/tidwall/gjson"
)

// Envelope mirrors result.ModuleResult's JSON field names so the consolidation runner can unmarshal it directly.
type Envelope struct {
	Surface          string
	Plugin           string
	Module           string
	Result           string // PASSED | FAILED
	HarnessOutcome   string // OK | ENV_NOT_READY
	OutcomeDetail    string
	DurationMs       int64
	FailedConditions []Condition
	Assertions       []Assertion
	Calls            []Call
	HarnessError     string
}

// Condition mirrors result.Condition.
type Condition struct {
	Src         string
	Msg         string
	Result      string // FAILURE | WARNING
	Requirement string
}

// Assertion mirrors result.Assertion: one expected-vs-actual check, recorded pass or fail.
type Assertion struct {
	Field    string
	Expected string
	Actual   string
	Passed   bool
}

// Call mirrors the fields of result.HTTPCall that the report renders.
type Call struct {
	Seq         int
	At          int64 // capture time (unix nanos), as in result.HTTPCall
	Label       string
	Method      string
	URL         string
	ReqHeaders  map[string][]string
	ReqBody     string
	Status      int
	RespHeaders map[string][]string
	RespBody    string
}

// Collector gathers one Envelope per scenario across the suite (thread-safe).
type Collector struct {
	mu   sync.Mutex
	rows []Envelope
}

// Add appends a scenario's envelope.
func (c *Collector) Add(e Envelope) {
	c.mu.Lock()
	c.rows = append(c.rows, e)
	c.mu.Unlock()
}

// Rows returns a copy of the collected envelopes.
func (c *Collector) Rows() []Envelope {
	c.mu.Lock()
	defer c.mu.Unlock()
	return append([]Envelope(nil), c.rows...)
}

// state is the per-scenario execution context: the {{stash}}, pending headers,
// the last response, and the recorded HTTP calls for the report trace.
type state struct {
	base         string
	plugin       string
	adminToken   string
	stash        map[string]string
	headers      map[string]string
	lastStatus   int
	lastBody     []byte
	lastLocation string // Location header of the last response (for redirect assertions)
	calls        []Call
	asserts      []Assertion
	start        time.Time
}

// recordAssertion appends one expected-vs-actual check (pass or fail) to the
// scenario's Validation trace.
func (s *state) recordAssertion(field, expected, actual string, passed bool) {
	s.asserts = append(s.asserts, Assertion{Field: field, Expected: expected, Actual: actual, Passed: passed})
}

type ctxKey struct{}

var stateKey = ctxKey{}

func getState(ctx context.Context) *state {
	if s, ok := ctx.Value(stateKey).(*state); ok {
		return s
	}
	return nil
}

// tlsConfig fails closed: verification is skipped only when API_TLS_VERIFY is explicitly "false".
func tlsConfig() *tls.Config {
	return &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: strings.EqualFold(os.Getenv("API_TLS_VERIFY"), "false"),
	}
}

// httpClient is shared across the generic steps.
var httpClient = &http.Client{
	Timeout:   30 * time.Second,
	Transport: &http.Transport{TLSClientConfig: tlsConfig()},
}

// noFollowClient does not follow redirects, so authorize-endpoint negatives can
// assert the 302 and its Location (errorCode) instead of the followed /error page.
var noFollowClient = &http.Client{
	Timeout:       30 * time.Second,
	Transport:     &http.Transport{TLSClientConfig: tlsConfig()},
	CheckRedirect: func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse },
}

// resolve replaces {{name}} placeholders from the stash.
func (s *state) resolve(in string) string {
	in = strings.ReplaceAll(in, "{{now}}", requestTime())
	for k, v := range s.stash {
		in = strings.ReplaceAll(in, "{{"+k+"}}", v)
	}
	return in
}

// do executes one HTTP call (following redirects), records it for the report
// trace, and stores the response as the "last response" the assertion steps read.
func (s *state) do(ctx context.Context, label, method, path, body string) error {
	return s.doClient(ctx, httpClient, label, method, path, body)
}

// doNoFollow is like do but does not follow redirects, so the 302 status and its
// Location header are observable (for authorize-endpoint negative assertions).
func (s *state) doNoFollow(ctx context.Context, label, method, path, body string) error {
	return s.doClient(ctx, noFollowClient, label, method, path, body)
}

// doClient carries the scenario context so a suite-level cancellation aborts an
// in-flight call instead of waiting out the client timeout.
func (s *state) doClient(ctx context.Context, client *http.Client, label, method, path, body string) error {
	fullURL := s.resolve(path)
	if !strings.HasPrefix(fullURL, "http") {
		fullURL = strings.TrimRight(s.base, "/") + "/" + strings.TrimLeft(fullURL, "/")
	}
	resolvedBody := s.resolve(body)

	var rdr io.Reader
	if resolvedBody != "" {
		rdr = bytes.NewBufferString(resolvedBody)
	}
	req, err := http.NewRequestWithContext(ctx, method, fullURL, rdr)
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/json")
	for k, v := range s.headers {
		req.Header.Set(k, v)
	}
	if s.adminToken != "" {
		req.Header.Set("Authorization", "Bearer "+s.adminToken)
	}

	resp, err := client.Do(req)
	if err != nil {
		s.record(label, method, fullURL, req.Header, resolvedBody, 0, nil, nil)
		return fmt.Errorf("%s %s: %w", method, fullURL, err)
	}
	respBody, _ := io.ReadAll(resp.Body)
	_ = resp.Body.Close()

	s.lastStatus = resp.StatusCode
	s.lastBody = respBody
	s.lastLocation = resp.Header.Get("Location")
	s.record(label, method, fullURL, req.Header, resolvedBody, resp.StatusCode, resp.Header, respBody)
	// per-request headers are one-shot
	s.headers = map[string]string{}
	return nil
}

// sensitiveHeaders carry credentials or session identifiers; reports are archived
// as CI artifacts, so their values must never reach the envelope.
var sensitiveHeaders = []string{"Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie", "X-API-KEY", "Api-Key"}

func redactHeaders(h http.Header) map[string][]string {
	out := map[string][]string{}
	for k, v := range h {
		redacted := false
		for _, s := range sensitiveHeaders {
			if strings.EqualFold(k, s) {
				redacted = true
				break
			}
		}
		if redacted {
			out[k] = []string{"***redacted***"}
			continue
		}
		out[k] = v
	}
	return out
}

func (s *state) record(label, method, u string, reqH http.Header, reqBody string, status int, respH http.Header, respBody []byte) {
	s.calls = append(s.calls, Call{
		Seq:         len(s.calls) + 1,
		At:          time.Now().UnixNano(),
		Label:       label,
		Method:      method,
		URL:         u,
		ReqHeaders:  redactHeaders(reqH),
		ReqBody:     reqBody,
		Status:      status,
		RespHeaders: redactHeaders(respH),
		RespBody:    string(respBody),
	})
}

// --- generic steps ---------------------------------------------------------

func iAuthenticateAsAdmin(ctx context.Context) error {
	s := getState(ctx)
	// A target that does not enforce scope never inspects this token: scope
	// middleware is only installed when both ISSUER_URL and JWKS_URL are set
	// (cmd/esignet/main.go), so a locally started server accepts client-mgmt
	// calls with any bearer, or none. Requiring a real Keycloak round-trip there
	// means a deployed IAM credential decides whether the local client-mgmt
	// scenarios run at all, to obtain a value the server then ignores.
	//
	// Deliberately an explicit opt-in rather than a fallback when Keycloak
	// fails: falling back silently would turn a genuine auth regression on a
	// real deployment into a green run.
	if tok := os.Getenv("ADMIN_TOKEN"); tok != "" {
		s.adminToken = tok
		return nil
	}
	tokenURL := os.Getenv("KEYCLOAK_TOKEN_URL")
	clientID := os.Getenv("KEYCLOAK_CLIENT_ID")
	secret := os.Getenv("KEYCLOAK_CLIENT_SECRET")
	if tokenURL == "" || clientID == "" || secret == "" {
		return fmt.Errorf("ENV_NOT_READY: KEYCLOAK_TOKEN_URL/CLIENT_ID/CLIENT_SECRET not set for admin auth (or set ADMIN_TOKEN for a target that does not enforce scope)")
	}
	form := url.Values{
		"grant_type":    {"client_credentials"},
		"client_id":     {clientID},
		"client_secret": {secret},
	}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, tokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err := httpClient.Do(req)
	if err != nil {
		return fmt.Errorf("keycloak token request: %w", err)
	}
	body, _ := io.ReadAll(resp.Body)
	_ = resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		// Status only: a gateway can echo the posted form (client_secret included)
		// in its error payload, and this error is archived in the envelope.
		return fmt.Errorf("keycloak token HTTP %d", resp.StatusCode)
	}
	tok := gjson.GetBytes(body, "access_token").String()
	if tok == "" {
		return fmt.Errorf("keycloak token response missing access_token")
	}
	s.adminToken = tok
	return nil
}

// pmsRegistrationIsConfigured reports ENV_NOT_READY when the PMS base/partner/policy are unset.
func pmsRegistrationIsConfigured(ctx context.Context) error {
	s := getState(ctx)
	var missing []string
	// Stash keys do not uppercase into their env var names (pms_base is PMS_BASE_URL, policy_id is AUTH_POLICY_ID).
	for _, kv := range []struct{ stash, env string }{
		{"pms_base", "PMS_BASE_URL"},
		{"auth_partner_id", "AUTH_PARTNER_ID"},
		{"policy_id", "AUTH_POLICY_ID"},
	} {
		if s.stash[kv.stash] == "" {
			missing = append(missing, kv.env)
		}
	}
	if len(missing) > 0 {
		return fmt.Errorf("ENV_NOT_READY: PMS registration not configured (missing %s)", strings.Join(missing, ", "))
	}
	return nil
}

// requestTime is the MOSIP service request timestamp format.
func requestTime() string { return time.Now().UTC().Format("2006-01-02T15:04:05.000Z") }

// aFreshRequestTimestamp declares {{now}} as a Background step; resolve re-stamps it per request.
func aFreshRequestTimestamp(ctx context.Context) error {
	getState(ctx).stash["now"] = requestTime()
	return nil
}

// aNewClientIDAs stashes a per-run-unique clientId so a positive create can be re-run.
func aNewClientIDAs(ctx context.Context, name string) error {
	s := getState(ctx)
	s.stash[name] = fmt.Sprintf("api-pos-%d", time.Now().UnixNano())
	return nil
}

// aGeneratedRSAPublicKeyAs stashes a fresh, valid RSA public JWK so a feature can isolate another error.
func aGeneratedRSAPublicKeyAs(ctx context.Context, name string) error {
	s := getState(ctx)
	key, err := rsa.GenerateKey(rand.Reader, 2048)
	if err != nil {
		return err
	}
	pub := key.PublicKey
	n := base64.RawURLEncoding.EncodeToString(pub.N.Bytes())
	e := base64.RawURLEncoding.EncodeToString(big.NewInt(int64(pub.E)).Bytes())
	s.stash[name] = fmt.Sprintf(`{"kty":"RSA","e":%q,"n":%q,"use":"sig","alg":"RS256","kid":"api-test"}`, e, n)
	return nil
}

func iSetHeaderTo(ctx context.Context, name, value string) error {
	s := getState(ctx)
	s.headers[name] = s.resolve(value)
	return nil
}

func iSendRequestToWithBody(ctx context.Context, method, path string, body *godog.DocString) error {
	s := getState(ctx)
	return s.do(ctx, fmt.Sprintf("%s %s", method, path), strings.ToUpper(method), path, body.Content)
}

func iSendRequestTo(ctx context.Context, method, path string) error {
	s := getState(ctx)
	return s.do(ctx, fmt.Sprintf("%s %s", method, path), strings.ToUpper(method), path, "")
}

func iSendRequestToWithoutFollowingRedirects(ctx context.Context, method, path string) error {
	s := getState(ctx)
	return s.doNoFollow(ctx, fmt.Sprintf("%s %s", method, path), strings.ToUpper(method), path, "")
}

// aRegisteredClientIDIsConfigured guards the authorize-negative feature, which needs FLOW_CLIENT_ID.
func aRegisteredClientIDIsConfigured(ctx context.Context) error {
	s := getState(ctx)
	if s.stash["client_id"] == "" {
		return fmt.Errorf("ENV_NOT_READY: FLOW_CLIENT_ID not set — needed to drive authorize-negative cases")
	}
	return nil
}

func theRedirectLocationShouldContain(ctx context.Context, substr string) error {
	s := getState(ctx)
	substr = s.resolve(substr)
	passed := strings.Contains(s.lastLocation, substr)
	s.recordAssertion("redirect location contains", substr, s.lastLocation, passed)
	if !passed {
		return fmt.Errorf("redirect Location %q does not contain %q", s.lastLocation, substr)
	}
	return nil
}

func theResponseStatusShouldBe(ctx context.Context, code int) error {
	s := getState(ctx)
	passed := s.lastStatus == code
	s.recordAssertion("HTTP status", strconv.Itoa(code), strconv.Itoa(s.lastStatus), passed)
	if !passed {
		return fmt.Errorf("expected HTTP %d, got %d (body: %s)", code, s.lastStatus, snippet(s.lastBody))
	}
	return nil
}

func iSaveTheJSONValueAtAs(ctx context.Context, path, name string) error {
	s := getState(ctx)
	r := gjson.GetBytes(s.lastBody, path)
	if !r.Exists() {
		return fmt.Errorf("json path %q not found in response: %s", path, snippet(s.lastBody))
	}
	s.stash[name] = r.String()
	return nil
}

func theJSONValueAtShouldBe(ctx context.Context, path, expected string) error {
	s := getState(ctx)
	expected = s.resolve(expected)
	got := gjson.GetBytes(s.lastBody, path).String()
	passed := got == expected
	s.recordAssertion("JSON "+path, expected, got, passed)
	if !passed {
		return fmt.Errorf("json %q: expected %q, got %q", path, expected, got)
	}
	return nil
}

func theJSONPathShouldExist(ctx context.Context, path string) error {
	s := getState(ctx)
	exists := gjson.GetBytes(s.lastBody, path).Exists()
	actual := "absent"
	if exists {
		actual = "present"
	}
	s.recordAssertion("JSON "+path+" exists", "present", actual, exists)
	if !exists {
		return fmt.Errorf("json path %q does not exist: %s", path, snippet(s.lastBody))
	}
	return nil
}

// theJSONPathShouldBeNull asserts an explicit JSON null.
func theJSONPathShouldBeNull(ctx context.Context, path string) error {
	s := getState(ctx)
	v := gjson.GetBytes(s.lastBody, path)
	isNull := v.Exists() && v.Type == gjson.Null
	actual := v.Raw
	if !v.Exists() {
		actual = "absent"
	}
	s.recordAssertion("JSON "+path, "null", actual, isNull)
	if !isNull {
		return fmt.Errorf("json path %q is not null (got %s): %s", path, actual, snippet(s.lastBody))
	}
	return nil
}

// sensitiveValueRe matches a JSON "key": "value" pair whose key names a
// credential. Prose-wrapped bodies (see snippet) are not parseable JSON, so
// this is a scan rather than a decode. "code" is deliberately absent:
// flow/execute answers with {code, message} and the features assert FES-1004
// on it, so masking it would blank the value the failure is read for — and no
// feature on this surface calls /token or /userinfo, so no OAuth code lands here.
var sensitiveValueRe = regexp.MustCompile(
	`(?i)("[a-z_]*(?:password|otp|secret|assertion|token)[a-z_]*"\s*:\s*)"[^"]*"`)

// redactSnippet masks credential values in a body excerpt. The report applies
// its own redactor to captured call bodies, but not to condition messages, and
// this excerpt is embedded in one — so it is masked here, at the source.
func redactSnippet(s string) string {
	return sensitiveValueRe.ReplaceAllString(s, `${1}"***redacted***"`)
}

func snippet(b []byte) string {
	const n = 400
	s := redactSnippet(string(b))
	if len(s) > n {
		return s[:n] + "…"
	}
	return s
}

// InitScenario registers the generic steps and the per-scenario collector hooks.
func InitScenario(sc *godog.ScenarioContext, coll *Collector) {
	base := strings.TrimRight(os.Getenv("MOSIP_ESIGNET_BASE_URL"), "/")
	plugin := os.Getenv("MOSIP_ESIGNET_AUTHN_PROVIDER")
	if plugin == "" {
		plugin = "mock"
	}

	sc.Before(func(ctx context.Context, _ *godog.Scenario) (context.Context, error) {
		st := &state{
			base:    base,
			plugin:  plugin,
			stash:   map[string]string{},
			headers: map[string]string{},
			start:   time.Now(),
		}
		// PMS registration context for the mosipid client-mgmt feature.
		st.stash["pms_base"] = strings.TrimRight(os.Getenv("PMS_BASE_URL"), "/")
		st.stash["auth_partner_id"] = os.Getenv("AUTH_PARTNER_ID")
		st.stash["policy_id"] = os.Getenv("AUTH_POLICY_ID")
		// Authorize-negative context: the endpoint and a pre-registered client id
		// (FLOW_CLIENT_ID) to exercise redirect/param validation against.
		st.stash["authz_endpoint"] = strings.TrimRight(base, "/") + "/oauth2/authorize"
		st.stash["client_id"] = os.Getenv("FLOW_CLIENT_ID")
		return context.WithValue(ctx, stateKey, st), nil
	})

	sc.After(func(ctx context.Context, scn *godog.Scenario, scenErr error) (context.Context, error) {
		st := getState(ctx)
		if st == nil {
			return ctx, nil
		}
		env := Envelope{
			Surface:    surfaceFromTags(scn),
			Plugin:     st.plugin,
			Module:     scn.Name,
			DurationMs: time.Since(st.start).Milliseconds(),
			Calls:      st.calls,
			Assertions: st.asserts,
		}
		switch {
		case scenErr == nil:
			env.Result = "PASSED"
			env.HarnessOutcome = "OK"
		case strings.Contains(scenErr.Error(), "ENV_NOT_READY"):
			env.HarnessOutcome = "ENV_NOT_READY"
			env.OutcomeDetail = firstLine(scenErr.Error())
		default:
			env.Result = "FAILED"
			env.HarnessOutcome = "OK"
			env.FailedConditions = []Condition{{Src: "godog", Result: "FAILURE", Msg: firstLine(scenErr.Error())}}
		}
		coll.Add(env)
		return ctx, nil
	})

	sc.Step(`^I authenticate as admin$`, iAuthenticateAsAdmin)
	sc.Step(`^a fresh request timestamp$`, aFreshRequestTimestamp)
	sc.Step(`^PMS registration is configured$`, pmsRegistrationIsConfigured)
	sc.Step(`^a new client id as "([^"]*)"$`, aNewClientIDAs)
	sc.Step(`^a generated RSA public key as "([^"]*)"$`, aGeneratedRSAPublicKeyAs)
	sc.Step(`^I set header "([^"]*)" to "([^"]*)"$`, iSetHeaderTo)
	sc.Step(`^I send a "([^"]*)" request to "([^"]*)" with body:$`, iSendRequestToWithBody)
	sc.Step(`^I send a "([^"]*)" request to "([^"]*)" without following redirects$`, iSendRequestToWithoutFollowingRedirects)
	sc.Step(`^I send a "([^"]*)" request to "([^"]*)"$`, iSendRequestTo)
	sc.Step(`^a registered client id is configured$`, aRegisteredClientIDIsConfigured)
	sc.Step(`^the redirect location should contain "([^"]*)"$`, theRedirectLocationShouldContain)
	sc.Step(`^the response status should be (\d+)$`, theResponseStatusShouldBe)
	sc.Step(`^I save the JSON value at "([^"]*)" as "([^"]*)"$`, iSaveTheJSONValueAtAs)
	sc.Step(`^the JSON value at "([^"]*)" should be "([^"]*)"$`, theJSONValueAtShouldBe)
	sc.Step(`^the JSON path "([^"]*)" should exist$`, theJSONPathShouldExist)
	sc.Step(`^the JSON path "([^"]*)" should be null$`, theJSONPathShouldBeNull)
}

// surfaceFromTags maps a scenario's tags to a report surface.
func surfaceFromTags(scn *godog.Scenario) string {
	for _, t := range scn.Tags {
		switch strings.TrimPrefix(t.Name, "@") {
		case "client-mgmt", "client-mgmt-pms":
			return "client-mgmt"
		case "flow-execute", "flow-authz-neg":
			return "flow-execute"
		}
	}
	return "flow-execute"
}

func firstLine(s string) string {
	if i := strings.IndexByte(s, '\n'); i >= 0 {
		return s[:i]
	}
	return s
}
