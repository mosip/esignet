// Package esignet drives the eSignet (thunder-go) user-authentication flow that
// sits behind the conformance suite's authorize URL — purely over HTTP/JSON, no
// browser. Given the suite's authorize URL it runs:
//
//	GET  /oauth2/authorize        -> 302 with authId + executionId
//	POST /flow/execute (loop)     -> COMPLETE + assertion
//	POST /oauth2/auth/callback    -> { redirect_uri: "<suite-cb>?code&state" }
//
// and returns that redirect_uri for the orchestrator to hand back to the suite.
// Every HTTP call is captured (headers + cookies + bodies) for the debug report.
// See plan doc §2.
package esignet

import (
	"bytes"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"os"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/mosip/esignet/api-test/internal/httpx"
	"github.com/mosip/esignet/api-test/internal/result"
)

// excludeActions are never auto-selected (they abort/deny the flow).
var excludeActions = []string{"cancel", "reject", "deny", "logout", "abort", "back"}

// submitHints mark actions that *proceed* the flow (submit the entered value,
// send the OTP, verify, etc.) — preferred over navigation.
var submitHints = []string{"submit", "send", "proceed", "continue", "verify", "authenticate", "complete"}

// navHints mark navigation/tab-switch actions (e.g. "login_id_mobile" switches
// the login-id tab). They only get chosen as a last resort, never over a submit.
var navHints = []string{"login_id", "resend", "tab_", "switch"}

// actionPreference orders generic happy-path actions when nothing else matches.
var actionPreference = []string{"login", "authenticate", "verify", "consent", "complete", "continue"}

// OTPProvider supplies a one-time password captured out-of-band (the dynamic OTP
// source: mosipid delivers a live OTP to email/SMS). It is consulted only when
// no explicit "otp" answer is configured, so negative "wrong OTP" cases (which
// set an explicit value) still work.
type OTPProvider interface {
	// OTP returns the OTP delivered at or after `since` (when the login flow that
	// will request it began), or an error if none arrives in time.
	OTP(since time.Time) (string, error)
}

type Driver struct {
	answers      map[string]string // normalized identifier -> value
	preferred    []string          // preferred action tokens for ACR / login-id selection
	tlsVerify    bool
	timeout      time.Duration
	maxHops      int
	maxFlowSteps int
	debug        bool
	otp          OTPProvider // dynamic OTP source; nil for static OTP
}

// SetOTPProvider installs a dynamic OTP source. When set, an "otp" flow input
// with no configured answer is resolved by fetching a fresh code from the
// provider instead of failing as a missing input.
func (d *Driver) SetOTPProvider(p OTPProvider) { d.otp = p }

func New(answers map[string]string, preferred []string, tlsVerify bool, timeout time.Duration) *Driver {
	return &Driver{
		answers:      answers,
		preferred:    preferred,
		tlsVerify:    tlsVerify,
		timeout:      timeout,
		maxHops:      8,
		maxFlowSteps: 20,
		debug:        os.Getenv("ESIGNET_DEBUG") != "",
	}
}

// FlowResult is what the driver returns for one authorization.
type FlowResult struct {
	RedirectURI     string // hand this back to the suite
	AuthorizeStatus int
	Steps           []result.FlowStep
	CallbackStatus  int
	Calls           []result.HTTPCall
	Error           string
}

func (r FlowResult) OK() bool { return r.RedirectURI != "" && r.Error == "" }

type flowResp struct {
	FlowStatus     string `json:"flowStatus"`
	ChallengeToken string `json:"challengeToken"`
	Assertion      string `json:"assertion"`
	Data           struct {
		Inputs []struct {
			Identifier string `json:"identifier"`
		} `json:"inputs"`
		Actions []struct {
			Ref string `json:"ref"`
		} `json:"actions"`
	} `json:"data"`
}

// session carries a cookie-jar HTTP client and the captured call trace for one
// Run (each authorization gets a fresh jar so modules don't leak cookies).
// noFollow is the same client with redirects disabled, built once so
// (*session).do doesn't allocate a new client on every non-follow request.
type session struct {
	client   *http.Client
	noFollow *http.Client
	calls    []result.HTTPCall
	debug    bool
}

func (d *Driver) newSession() *session {
	jar, _ := cookiejar.New(nil)
	client := httpx.NewClient(d.tlsVerify, d.timeout)
	client.Jar = jar
	noFollow := *client
	noFollow.CheckRedirect = func(*http.Request, []*http.Request) error { return http.ErrUseLastResponse }
	return &session{
		client:   client,
		noFollow: &noFollow,
		debug:    d.debug,
	}
}

func (s *session) dbg(format string, args ...any) {
	if s.debug {
		fmt.Fprintf(os.Stderr, "[esignet-debug] "+format+"\n", args...)
	}
}

// do performs one request, records it (headers/cookies/body), and returns the
// response body, status, and Location header. When follow is false, redirects
// are not followed (so we can read the Location).
func (s *session) do(label, method, u string, body []byte, contentType string, follow bool) ([]byte, int, string, error) {
	var rdr io.Reader
	if body != nil {
		rdr = bytes.NewReader(body)
	}
	req, err := http.NewRequest(method, u, rdr)
	if err != nil {
		return nil, 0, "", err
	}
	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}

	client := s.client
	if !follow {
		client = s.noFollow
	}

	// Cookies the jar will attach for this URL (for the record).
	var reqCookies string
	if s.client.Jar != nil {
		var cs []string
		for _, c := range s.client.Jar.Cookies(req.URL) {
			cs = append(cs, c.Name+"="+c.Value)
		}
		reqCookies = strings.Join(cs, "; ")
	}

	call := result.HTTPCall{
		Seq: len(s.calls) + 1, At: time.Now().UnixNano(), Label: label, Method: method, URL: u,
		ReqHeaders: httpx.CloneHeader(req.Header), ReqCookies: reqCookies, ReqBody: string(body),
	}

	resp, err := client.Do(req)
	if err != nil {
		call.RespBody = "ERROR: " + err.Error()
		s.calls = append(s.calls, call)
		return nil, 0, "", err
	}
	defer resp.Body.Close()
	rb, _ := io.ReadAll(resp.Body)

	call.Status = resp.StatusCode
	call.RespHeaders = httpx.CloneHeader(resp.Header)
	call.RespCookies = strings.Join(resp.Header["Set-Cookie"], "\n")
	call.RespBody = string(rb)
	s.calls = append(s.calls, call)

	return rb, resp.StatusCode, resp.Header.Get("Location"), nil
}

// Run drives one authorization request to a redirect_uri.
func (d *Driver) Run(base, authorizeURL string) FlowResult {
	s := d.newSession()
	fr := d.run(s, base, authorizeURL)
	fr.Calls = s.calls
	return fr
}

func (d *Driver) run(s *session, base, authorizeURL string) FlowResult {
	var fr FlowResult

	current := authorizeURL
	for hop := 0; hop < d.maxHops; hop++ {
		body, status, loc, err := s.do(fmt.Sprintf("authorize (hop %d)", hop), http.MethodGet, current, nil, "", false)
		if hop == 0 {
			fr.AuthorizeStatus = status
		}
		if err != nil {
			fr.Error = fmt.Sprintf("authorize GET: %v", err)
			return fr
		}
		if status >= 300 && status < 400 {
			if loc == "" {
				fr.Error = "authorize redirect without Location header"
				return fr
			}
			target := resolve(current, loc)
			q := queryOf(target)
			if q.Get("authId") != "" && q.Get("executionId") != "" {
				return d.completeLogin(s, base, q.Get("authId"), q.Get("executionId"), fr)
			}
			if q.Get("code") != "" || q.Get("error") != "" {
				fr.RedirectURI = target
				return fr
			}
			current = target
			continue
		}
		fr.Error = fmt.Sprintf("authorize returned HTTP %d (expected redirect): %s", status, snippet(body))
		return fr
	}
	fr.Error = fmt.Sprintf("exceeded %d redirect hops without reaching login", d.maxHops)
	return fr
}

func (d *Driver) completeLogin(s *session, base, authID, executionID string, fr FlowResult) FlowResult {
	var challengeToken, assertion, flowStatus string
	payload := map[string]any{"executionId": executionID}
	// Boundary for dynamic OTP freshness: any OTP delivered before login started
	// belongs to a previous flow and is ignored (the send happens later in this
	// loop, well after this point).
	flowStart := time.Now()

	for step := 0; step < d.maxFlowSteps; step++ {
		pj, _ := json.Marshal(payload)
		s.dbg("step %d REQUEST %s", step, string(pj))
		respBody, status, _, err := s.do(fmt.Sprintf("flow/execute #%d", step), http.MethodPost, base+"/flow/execute", pj, "application/json", true)
		if err != nil {
			fr.Error = fmt.Sprintf("flow/execute step %d: %v", step, err)
			return fr
		}
		s.dbg("step %d RESPONSE HTTP %d %s", step, status, truncate(string(respBody), 1500))
		if status != http.StatusOK {
			fr.Error = fmt.Sprintf("flow/execute step %d HTTP %d: %s", step, status, snippet(respBody))
			return fr
		}
		var r flowResp
		if err := json.Unmarshal(respBody, &r); err != nil {
			fr.Error = fmt.Sprintf("flow/execute step %d parse: %v", step, err)
			return fr
		}
		flowStatus = r.FlowStatus
		if r.ChallengeToken != "" {
			challengeToken = r.ChallengeToken
		}

		var inputIDs []string
		for _, in := range r.Data.Inputs {
			if in.Identifier != "" {
				inputIDs = append(inputIDs, in.Identifier)
			}
		}
		var actionRefs []string
		for _, a := range r.Data.Actions {
			if a.Ref != "" {
				actionRefs = append(actionRefs, a.Ref)
			}
		}

		if flowStatus == "COMPLETE" {
			assertion = r.Assertion
			fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs})
			break
		}
		if flowStatus != "" && flowStatus != "INCOMPLETE" {
			fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs})
			fr.Error = fmt.Sprintf("flow terminated with status %q", flowStatus)
			return fr
		}

		action, aerr := selectAction(actionRefs, d.preferred)
		if aerr != "" {
			fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs, Action: strings.Join(actionRefs, ",")})
			fr.Error = aerr
			return fr
		}

		inputs, merr := d.resolveInputs(inputIDs, flowStart)
		if merr != "" {
			fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs, Action: action})
			fr.Error = merr
			return fr
		}

		fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs, Action: action})

		next := map[string]any{"executionId": executionID}
		if challengeToken != "" {
			next["challengeToken"] = challengeToken
		}
		if action != "" {
			next["action"] = action
		}
		if len(inputs) > 0 {
			next["inputs"] = inputs
		}
		payload = next
	}

	if flowStatus != "COMPLETE" || assertion == "" {
		if fr.Error == "" {
			fr.Error = fmt.Sprintf("flow did not COMPLETE with an assertion (last status %q)", flowStatus)
		}
		return fr
	}

	cbBody, cbStatus, _, err := s.do("oauth2/auth/callback", http.MethodPost, base+"/oauth2/auth/callback", mustJSON(map[string]any{
		"authId": authID, "assertion": assertion,
	}), "application/json", true)
	fr.CallbackStatus = cbStatus
	if err != nil {
		fr.Error = fmt.Sprintf("auth/callback: %v", err)
		return fr
	}
	if cbStatus != http.StatusOK {
		fr.Error = fmt.Sprintf("auth/callback HTTP %d: %s", cbStatus, snippet(cbBody))
		return fr
	}
	var cb struct {
		RedirectURI  string `json:"redirect_uri"`
		RedirectURI2 string `json:"redirectUri"`
	}
	if err := json.Unmarshal(cbBody, &cb); err != nil {
		fr.Error = fmt.Sprintf("auth/callback parse: %v (%s)", err, snippet(cbBody))
		return fr
	}
	fr.RedirectURI = firstNonEmpty(cb.RedirectURI, cb.RedirectURI2)
	if fr.RedirectURI == "" {
		fr.Error = fmt.Sprintf("auth/callback missing redirect_uri: %s", snippet(cbBody))
	}
	return fr
}

func (d *Driver) resolveInputs(identifiers []string, since time.Time) (map[string]string, string) {
	out := map[string]string{}
	var missing []string
	for _, id := range identifiers {
		if v, ok := d.answers[Normalize(id)]; ok {
			out[id] = v
			continue
		}
		// Dynamic OTP fallback: only when no explicit answer is configured, so a
		// negative "wrong OTP" case (which sets an explicit value) is untouched.
		if d.otp != nil && Normalize(id) == "otp" {
			code, err := d.otp.OTP(since)
			if err != nil {
				return nil, fmt.Sprintf("dynamic OTP: %v", err)
			}
			out[id] = code
			continue
		}
		missing = append(missing, id)
	}
	if len(missing) > 0 {
		return nil, fmt.Sprintf("no configured answer for flow input(s): %s", strings.Join(missing, ", "))
	}
	return out, ""
}

// selectAction picks the happy-path action. Order of preference:
//  1. caller-preferred tokens (ACR / auth-factor), excluding navigation
//  2. submit/send/proceed actions (they advance the flow)
//  3. generic happy-path list, excluding navigation
//  4. any non-navigation candidate
//  5. navigation (tab switches) only as a last resort
//
// Reject/cancel/deny are removed outright. This stops the harness from picking a
// tab switch (e.g. login_id_mobile) over the actual submit that sends the OTP.
func selectAction(actions, preferred []string) (string, string) {
	if len(actions) == 0 {
		return "", ""
	}
	var candidates []string
	for _, a := range actions {
		if !containsAny(strings.ToLower(a), excludeActions) {
			candidates = append(candidates, a)
		}
	}
	if len(candidates) == 0 {
		return "", fmt.Sprintf("AMBIGUOUS_FLOW_ACTION: only excluded actions available=%v", actions)
	}
	if len(candidates) == 1 {
		return candidates[0], ""
	}
	isNav := func(c string) bool { return containsAny(strings.ToLower(c), navHints) }

	// 1. caller preference (skip navigation).
	for _, pref := range preferred {
		pref = strings.ToLower(pref)
		if pref == "" {
			continue
		}
		for _, c := range candidates {
			if !isNav(c) && strings.Contains(strings.ToLower(c), pref) {
				return c, ""
			}
		}
	}
	// 2. submit-like actions advance the flow (skip navigation — "send" is a
	// substring of "resend", so without this filter a resend action could win
	// over the real submit before stage 4/5 ever defers it).
	for _, hint := range submitHints {
		for _, c := range candidates {
			if !isNav(c) && strings.Contains(strings.ToLower(c), hint) {
				return c, ""
			}
		}
	}
	// 3. generic happy-path (skip navigation).
	for _, pref := range actionPreference {
		for _, c := range candidates {
			if !isNav(c) && strings.Contains(strings.ToLower(c), pref) {
				return c, ""
			}
		}
	}
	// 4. any non-navigation candidate.
	var nonNav []string
	for _, c := range candidates {
		if !isNav(c) {
			nonNav = append(nonNav, c)
		}
	}
	if len(nonNav) == 1 {
		return nonNav[0], ""
	}
	// 5. navigation (tab switches) only as a last resort: a step offering just
	// login_id_* tabs is progressable, so take the first rather than aborting.
	if len(nonNav) == 0 && len(candidates) > 0 {
		return candidates[0], ""
	}
	return "", fmt.Sprintf("AMBIGUOUS_FLOW_ACTION: %d non-navigation actions available=%v (set esignet.auth_factor to disambiguate)", len(nonNav), candidates)
}

// ----- utils -----

func mustJSON(v any) []byte { b, _ := json.Marshal(v); return b }

func resolve(base, ref string) string {
	b, err := url.Parse(base)
	if err != nil {
		return ref
	}
	r, err := url.Parse(ref)
	if err != nil {
		return ref
	}
	return b.ResolveReference(r).String()
}

func queryOf(u string) url.Values {
	parsed, err := url.Parse(u)
	if err != nil {
		return url.Values{}
	}
	return parsed.Query()
}

func containsAny(s string, subs []string) bool {
	for _, sub := range subs {
		if strings.Contains(s, sub) {
			return true
		}
	}
	return false
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if v != "" {
			return v
		}
	}
	return ""
}

func snippet(b []byte) string { return truncate(strings.TrimSpace(string(b)), 300) }

// truncate cuts s to at most n bytes, backing off to the last full rune so a
// multi-byte UTF-8 character is never split.
func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	cut := n
	for cut > 0 && !utf8.RuneStart(s[cut]) {
		cut--
	}
	return s[:cut] + "…"
}

// Normalize lowercases and strips non-alphanumerics so "fullName", "full_name"
// and "FullName" all resolve to the same answer key.
func Normalize(s string) string {
	var b strings.Builder
	for _, r := range strings.ToLower(s) {
		if (r >= 'a' && r <= 'z') || (r >= '0' && r <= '9') {
			b.WriteRune(r)
		}
	}
	return b.String()
}
