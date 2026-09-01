// Package esignet drives the eSignet user-authentication flow over HTTP/JSON, with no browser.
package esignet

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/http/cookiejar"
	"net/url"
	"os"
	"strings"
	"time"

	"github.com/mosip/esignet/api-test/internal/httpx"
	"github.com/mosip/esignet/api-test/internal/result"
	"github.com/mosip/esignet/api-test/internal/textx"
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

// OTPProvider supplies a one-time password captured out-of-band.
type OTPProvider interface {
	// OTP returns the OTP delivered at or after `since` (when the login flow that
	// will request it began), or an error if none arrives in time.
	OTP(since time.Time) (string, error)
}

// ConsentPolicy decides how the driver answers the consent step. The zero value
// approves everything, which is the happy path every other surface relies on.
type ConsentPolicy struct {
	// Deny lists element names to withhold approval from, matched case-insensitively.
	Deny []string
	// DenyAll withholds approval from every element the prompt offers.
	DenyAll bool
}

func (p ConsentPolicy) denies(name string) bool {
	if p.DenyAll {
		return true
	}
	for _, d := range p.Deny {
		if strings.EqualFold(strings.TrimSpace(d), name) {
			return true
		}
	}
	return false
}

type Driver struct {
	answers      map[string]string // normalized identifier -> value
	preferred    []string          // preferred action tokens for the ACR (auth-factor) selection
	idTokens     []string          // login-id-type tokens, matched against an action's nextNode (see selectAction)
	tlsVerify    bool
	timeout      time.Duration
	maxHops      int
	maxFlowSteps int
	debug        bool
	otp          OTPProvider   // dynamic OTP source; nil for static OTP
	consent      ConsentPolicy // how to answer the consent step; zero value approves all

	// followRejection makes a flow that ends in ERROR with an errorAssertion
	// post that assertion to /oauth2/auth/callback, so the resulting
	// error=access_denied redirect can be handed back to the caller instead of
	// the flow simply being reported as failed. Off by default: the e2e surface
	// asserts a denied consent is *rejected*, and following through to the
	// redirect would change what that assertion means.
	followRejection bool

	// Per-Run observations, reset at the top of Run because the conformance
	// orchestrator reuses one Driver across every module.
	consentPrompted bool
	consentDenied   []string
}

// SetOTPProvider installs a dynamic OTP source.
func (d *Driver) SetOTPProvider(p OTPProvider) { d.otp = p }

// SetConsentPolicy installs a non-default consent answer.
func (d *Driver) SetConsentPolicy(p ConsentPolicy) { d.consent = p }

// SetFollowRejection controls whether a flow ending in ERROR with an
// errorAssertion is carried through to the client redirect (see followRejection).
func (d *Driver) SetFollowRejection(v bool) { d.followRejection = v }

// UseIDType sets the login-id-type preference (see IDTypeTokens). Without it the
// driver stays on whichever login-id tab the flow opens on — the UIN tab, by
// default — and submits a phone or email there as if it were a UIN, which the
// ID system rejects (IDA-MLC-009) well after the point the mistake was made.
func (d *Driver) UseIDType(tokens []string) { d.idTokens = tokens }

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
	// ConsentPrompted reports whether the flow asked for a consent decision.
	ConsentPrompted bool
	// ConsentDenied lists the element names the driver withheld approval from,
	// for the report's evidence trail.
	ConsentDenied []string
	// AuthorizeErrorCode is the errorCode eSignet put on its /error page when it
	// rejected the authorize request, so the report names the server's reason
	// rather than just recording that no redirect arrived.
	AuthorizeErrorCode string
	// LoginReached reports that the authorize chain arrived at the login page.
	// Only meaningful for RunToLogin, where it is the success signal: that call
	// deliberately produces no RedirectURI, so OK() cannot be used.
	LoginReached bool
}

func (r FlowResult) OK() bool { return r.RedirectURI != "" && r.Error == "" }

type flowResp struct {
	FlowStatus     string `json:"flowStatus"`
	ChallengeToken string `json:"challengeToken"`
	Assertion      string `json:"assertion"`
	// ErrorAssertion is the signed assertion eSignet returns when a flow ends in
	// ERROR (the user denied consent, say). Posting it to /oauth2/auth/callback
	// in place of Assertion makes eSignet redirect back to the RP carrying an
	// OAuth error — access_denied for an end-user rejection, which is what the
	// conformance suite's user-rejects-authentication module waits for.
	ErrorAssertion string `json:"errorAssertion"`
	// Error is the per-step rejection eSignet reports while the flow is still
	// INCOMPLETE (FET-1005 "Invalid credentials provided", say). Without it the
	// driver cannot tell a rejected step from an unchanged view, so it re-submits
	// the same step until maxFlowSteps runs out — which on the OTP path spends a
	// real OTP attempt per retry and can trip the deployment's max-attempts lockout.
	Error *struct {
		Code    string `json:"code"`
		Message struct {
			DefaultValue string `json:"defaultValue"`
		} `json:"message"`
	} `json:"error"`
	Data struct {
		Inputs []struct {
			Identifier string `json:"identifier"`
		} `json:"inputs"`
		// NextNode is what distinguishes the login-id tabs: every tab's submit
		// action is named "submit_uin", and only the node it advances to says
		// which identifier it actually sends (send_mosip_otp_uin vs
		// send_mosip_otp_mobile). selectAction needs it to honour id_type.
		Actions []struct {
			Ref      string `json:"ref"`
			NextNode string `json:"nextNode"`
		} `json:"actions"`
		// AdditionalData.ConsentPrompt is a JSON *string* holding the purposes the
		// consent step is asking about. It only appears on the consent prompt.
		AdditionalData struct {
			ConsentPrompt string `json:"consentPrompt"`
		} `json:"additionalData"`
	} `json:"data"`
}

// consentPurpose is one entry of data.additionalData.consentPrompt: essential and optional claims.
type consentPurpose struct {
	PurposeName string           `json:"purposeName"`
	PurposeID   string           `json:"purposeId"`
	Type        string           `json:"type"`
	Essential   []consentElement `json:"essential"`
	Optional    []consentElement `json:"optional"`
}

// consentElement is one claim/permission name offered by a consent purpose.
type consentElement struct {
	Name string `json:"name"`
}

// consentDecision is the reply shape the ConsentExecutor expects, sent as a JSON-encoded string.
//
// Approved is the overall verdict and must be sent explicitly: the executor reads
// an absent field as false and rejects the whole decision with FET-1066
// ("User denied consent"), however many per-element approvals it carries.
type consentDecision struct {
	Approved bool                     `json:"approved"`
	Purposes []consentPurposeDecision `json:"purposes"`
}

type consentPurposeDecision struct {
	Approved    bool                     `json:"approved"`
	PurposeName string                   `json:"purposeName"`
	PurposeID   string                   `json:"purposeId,omitempty"`
	Elements    []consentElementDecision `json:"elements"`
}

type consentElementDecision struct {
	Approved bool   `json:"approved"`
	Name     string `json:"name"`
}

// buildConsentDecision answers the consent step from the prompt.
func buildConsentDecision(prompt string, policy ConsentPolicy) (string, []string, error) {
	prompt = strings.TrimSpace(prompt)
	if prompt == "" {
		return "", nil, fmt.Errorf("consent step gave no consentPrompt to derive a decision from")
	}
	var purposes []consentPurpose
	if err := json.Unmarshal([]byte(prompt), &purposes); err != nil {
		return "", nil, fmt.Errorf("parse consentPrompt: %w", err)
	}
	if len(purposes) == 0 {
		return "", nil, fmt.Errorf("consentPrompt listed no purposes")
	}

	var denied, offered []string
	out := consentDecision{}
	for _, p := range purposes {
		d := consentPurposeDecision{Approved: true, PurposeName: p.PurposeName, PurposeID: p.PurposeID}
		anyApproved := false
		for _, e := range append(append([]consentElement(nil), p.Essential...), p.Optional...) {
			offered = append(offered, e.Name)
			approved := !policy.denies(e.Name)
			if approved {
				anyApproved = true
			} else {
				denied = append(denied, e.Name)
			}
			d.Elements = append(d.Elements, consentElementDecision{Approved: approved, Name: e.Name})
		}
		// A purpose with every element withheld is itself not approved.
		if policy.DenyAll || (len(d.Elements) > 0 && !anyApproved) {
			d.Approved = false
		}
		if d.Approved {
			out.Approved = true
		}
		out.Purposes = append(out.Purposes, d)
	}

	// A deny naming an element the prompt never offered means the scenario asserts against nothing.
	for _, name := range policy.Deny {
		name = strings.TrimSpace(name)
		if name == "" {
			continue
		}
		if !containsFold(offered, name) {
			return "", nil, fmt.Errorf("consent deny %q not offered by the prompt (offered: %s)",
				name, strings.Join(offered, ", "))
		}
	}

	// The executor takes the decision as a JSON string, not a nested object.
	b, err := json.Marshal(out)
	if err != nil {
		return "", nil, fmt.Errorf("encode consent decision: %w", err)
	}
	return string(b), denied, nil
}

func containsFold(haystack []string, needle string) bool {
	for _, h := range haystack {
		if strings.EqualFold(h, needle) {
			return true
		}
	}
	return false
}

// session carries a cookie-jar HTTP client and the captured call trace for one Run.
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

// do performs one request, records it, and returns the body, status and Location header.
func (s *session) do(ctx context.Context, label, method, u string, body []byte, contentType string, follow bool) ([]byte, int, string, error) {
	var rdr io.Reader
	if body != nil {
		rdr = bytes.NewReader(body)
	}
	req, err := http.NewRequestWithContext(ctx, method, u, rdr)
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
	defer func() { _ = resp.Body.Close() }()
	rb, _ := io.ReadAll(resp.Body)

	call.Status = resp.StatusCode
	call.RespHeaders = httpx.CloneHeader(resp.Header)
	call.RespCookies = strings.Join(resp.Header["Set-Cookie"], "\n")
	call.RespBody = string(rb)
	s.calls = append(s.calls, call)

	return rb, resp.StatusCode, resp.Header.Get("Location"), nil
}

// Run drives one authorization request to a redirect_uri.
func (d *Driver) Run(ctx context.Context, base, authorizeURL string) FlowResult {
	return d.runFlow(ctx, base, authorizeURL, false)
}

// RunToLogin follows the authorize chain until the login page is reached and
// stops, without submitting a credential — the flow is left un-authenticated on
// purpose. The conformance suite's par-ensure-reused-request-uri module requires
// exactly this: the first visit to a request_uri must NOT authenticate, so that
// the second visit can prove the request_uri survived being reused. Success is
// LoginReached, not OK(): no redirect is produced.
func (d *Driver) RunToLogin(ctx context.Context, base, authorizeURL string) FlowResult {
	return d.runFlow(ctx, base, authorizeURL, true)
}

func (d *Driver) runFlow(ctx context.Context, base, authorizeURL string, stopAtLogin bool) FlowResult {
	// One Driver is reused for every module, so per-flow consent observations must start clean.
	d.consentPrompted, d.consentDenied = false, nil

	s := d.newSession()
	fr := d.run(ctx, s, base, authorizeURL, stopAtLogin)
	fr.Calls = s.calls
	fr.ConsentPrompted = d.consentPrompted
	fr.ConsentDenied = d.consentDenied
	return fr
}

func (d *Driver) run(ctx context.Context, s *session, base, authorizeURL string, stopAtLogin bool) FlowResult {
	var fr FlowResult

	current := authorizeURL
	for hop := 0; hop < d.maxHops; hop++ {
		body, status, loc, err := s.do(ctx, fmt.Sprintf("authorize (hop %d)", hop), http.MethodGet, current, nil, "", false)
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
				fr.LoginReached = true
				if stopAtLogin {
					return fr
				}
				return d.completeLogin(ctx, s, base, q.Get("authId"), q.Get("executionId"), fr)
			}
			if q.Get("code") != "" || q.Get("error") != "" {
				fr.RedirectURI = target
				return fr
			}
			// eSignet sends the browser to its own /error page (an HTML SPA
			// route, not a redirect back to the RP) when it rejects the
			// authorize request. Report the errorCode it carries: following the
			// hop would just fetch the HTML and fail with an unhelpful
			// "expected redirect, got <!doctype html>".
			// Phrased to keep the "authorize returned" prefix the e2e protocol
			// negatives assert on (expect_error_contains); only the tail is new.
			if code := q.Get("errorCode"); code != "" {
				fr.AuthorizeErrorCode = code
				fr.Error = fmt.Sprintf("authorize returned eSignet's error page: %s (%s)",
					code, q.Get("errorMessage"))
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

func (d *Driver) completeLogin(ctx context.Context, s *session, base, authID, executionID string, fr FlowResult) FlowResult {
	var challengeToken, assertion, flowStatus string
	payload := map[string]any{"executionId": executionID}
	// Freshness boundary: an OTP delivered before login started belongs to a previous flow.
	flowStart := time.Now()
	// Every input answered so far in this flow. Re-sent in full on each step, the
	// way the browser does — see where it is populated below.
	carriedInputs := map[string]string{}

	for step := 0; step < d.maxFlowSteps; step++ {
		pj, err := json.Marshal(payload)
		if err != nil {
			fr.Error = fmt.Sprintf("flow/execute step %d encode payload: %v", step, err)
			return fr
		}
		s.dbg("step %d REQUEST %s", step, string(pj))
		respBody, status, _, err := s.do(ctx, fmt.Sprintf("flow/execute #%d", step), http.MethodPost, base+"/flow/execute", pj, "application/json", true)
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
		var actions []flowAction
		for _, a := range r.Data.Actions {
			if a.Ref != "" {
				actionRefs = append(actionRefs, a.Ref)
				actions = append(actions, flowAction{Ref: a.Ref, NextNode: a.NextNode})
			}
		}

		if flowStatus == "COMPLETE" {
			assertion = r.Assertion
			fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs})
			break
		}
		if flowStatus != "" && flowStatus != "INCOMPLETE" {
			fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs})
			// A rejected flow still has something to hand the RP: the signed
			// errorAssertion turns into an error=access_denied redirect.
			if d.followRejection && r.ErrorAssertion != "" {
				return d.deliverAssertion(ctx, s, base, authID, r.ErrorAssertion, fr)
			}
			fr.Error = fmt.Sprintf("flow terminated with status %q", flowStatus)
			return fr
		}

		// The step was rejected but the flow is still INCOMPLETE, so the response
		// is the same view again. Re-submitting it cannot succeed — report what
		// eSignet said rather than looping to maxFlowSteps.
		if r.Error != nil && r.Error.Code != "" {
			fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs})
			fr.Error = fmt.Sprintf("flow step rejected: %s (%s)", r.Error.Code, r.Error.Message.DefaultValue)
			return fr
		}

		action, aerr := selectAction(actions, d.preferred, d.idTokens)
		if aerr != "" {
			fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs, Action: strings.Join(actionRefs, ",")})
			fr.Error = aerr
			return fr
		}

		inputs, merr := d.resolveInputs(inputIDs, flowStart, r.Data.AdditionalData.ConsentPrompt)
		if merr != "" {
			fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs, Action: action})
			fr.Error = merr
			return fr
		}

		// Inputs accumulate across the whole flow, and every step re-sends all of
		// them — that is what the browser does, and eSignet depends on it. Each
		// view declares only the field it has just added, so the OTP step declares
		// `otp` alone; submitting only that leaves basic_auth with no username to
		// verify the code against, and it answers FET-1005 "Invalid credentials
		// provided" as though the OTP itself were wrong. This also covers a
		// login-id tab switch, whose destination view declares no inputs at all.
		for k, v := range inputs {
			carriedInputs[k] = v
		}
		fr.Steps = append(fr.Steps, result.FlowStep{FlowStatus: flowStatus, Inputs: inputIDs, Action: action})

		next := map[string]any{"executionId": executionID}
		if challengeToken != "" {
			next["challengeToken"] = challengeToken
		}
		if action != "" {
			next["action"] = action
		}
		if len(carriedInputs) > 0 {
			// Copied, not aliased: carriedInputs keeps growing after this payload is
			// built but before it is marshalled at the top of the next iteration.
			send := make(map[string]string, len(carriedInputs))
			for k, v := range carriedInputs {
				send[k] = v
			}
			next["inputs"] = send
		}
		payload = next
	}

	if flowStatus != "COMPLETE" || assertion == "" {
		if fr.Error == "" {
			fr.Error = fmt.Sprintf("flow did not COMPLETE with an assertion (last status %q)", flowStatus)
		}
		return fr
	}

	return d.deliverAssertion(ctx, s, base, authID, assertion, fr)
}

// deliverAssertion posts a flow assertion to /oauth2/auth/callback and records
// the client redirect it returns. eSignet routes on the assertion's claims, so
// the same call serves both a successful login (a code lands on the redirect)
// and a rejected one (an errorAssertion yields error=access_denied) — see
// handleFailedCallback in the engine's authz service.
func (d *Driver) deliverAssertion(ctx context.Context, s *session, base, authID, assertion string, fr FlowResult) FlowResult {
	cbBody, cbStatus, _, err := s.do(ctx, "oauth2/auth/callback", http.MethodPost, base+"/oauth2/auth/callback", mustJSON(map[string]any{
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

func (d *Driver) resolveInputs(identifiers []string, since time.Time, consentPrompt string) (map[string]string, string) {
	out := map[string]string{}
	var missing []string
	for _, id := range identifiers {
		if v, ok := d.answers[Normalize(id)]; ok {
			out[id] = v
			continue
		}
		// Consent is synthesized from the prompt in this same response, not from a configured answer.
		if Normalize(id) == "consentdecisions" {
			decision, denied, err := buildConsentDecision(consentPrompt, d.consent)
			if err != nil {
				return nil, fmt.Sprintf("consent: %v", err)
			}
			d.consentPrompted = true
			d.consentDenied = append(d.consentDenied, denied...)
			out[id] = decision
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

// flowAction is one selectable action of a flow view: its ref, and the node it
// advances to. Both matter — the login-id tabs all submit under the same ref.
type flowAction struct {
	Ref      string
	NextNode string
}

// selectAction picks the happy-path action. idTokens are the login-id-type
// preference (IDTypeTokens); they are kept apart from preferred because they are
// matched against nextNode rather than the ref, and because preferred also
// carries auth-factor tokens like "otp" that appear in every id tab's node name.
func selectAction(actions []flowAction, preferred, idTokens []string) (string, string) {
	if len(actions) == 0 {
		return "", ""
	}
	var candidates []flowAction
	for _, a := range actions {
		if !containsAny(strings.ToLower(a.Ref), excludeActions) {
			candidates = append(candidates, a)
		}
	}
	if len(candidates) == 0 {
		return "", fmt.Sprintf("AMBIGUOUS_FLOW_ACTION: only excluded actions available=%v", refsOf(actions))
	}
	if len(candidates) == 1 {
		return candidates[0].Ref, ""
	}
	isNav := func(c string) bool { return containsAny(strings.ToLower(c), navHints) }

	// 0. login-id tab correction, ahead of everything else. Every tab submits
	// under the ref "submit_uin"; only nextNode says which identifier kind goes
	// out (send_mosip_otp_uin vs send_mosip_otp_mobile). Landing on the default
	// UIN tab with a phone in hand therefore looks like a perfectly good submit
	// and IDA rejects the value (IDA-MLC-009). So when an id_type is configured
	// and this view's submit would send the wrong kind, switch tabs first.
	//
	// This cannot loop: the tab it switches to has a submit whose nextNode does
	// match, so the next pass falls straight through to the submit below.
	if len(idTokens) > 0 {
		submitMatches, navToIDType := false, ""
		for _, c := range candidates {
			node := strings.ToLower(c.NextNode)
			if node == "" || !containsAny(node, idTokens) {
				continue
			}
			if isNav(c.Ref) {
				if navToIDType == "" {
					navToIDType = c.Ref
				}
			} else {
				submitMatches = true
			}
		}
		if !submitMatches && navToIDType != "" {
			return navToIDType, ""
		}
	}

	// 1. caller preference (skip navigation).
	for _, pref := range preferred {
		pref = strings.ToLower(pref)
		if pref == "" {
			continue
		}
		for _, c := range candidates {
			if !isNav(c.Ref) && strings.Contains(strings.ToLower(c.Ref), pref) {
				return c.Ref, ""
			}
		}
	}
	// 2. submit-like actions advance the flow, skipping navigation so a resend cannot beat the real submit.
	for _, hint := range submitHints {
		for _, c := range candidates {
			if !isNav(c.Ref) && strings.Contains(strings.ToLower(c.Ref), hint) {
				return c.Ref, ""
			}
		}
	}
	// 3. generic happy-path (skip navigation).
	for _, pref := range actionPreference {
		for _, c := range candidates {
			if !isNav(c.Ref) && strings.Contains(strings.ToLower(c.Ref), pref) {
				return c.Ref, ""
			}
		}
	}
	// 4. any non-navigation candidate.
	var nonNav []flowAction
	for _, c := range candidates {
		if !isNav(c.Ref) {
			nonNav = append(nonNav, c)
		}
	}
	if len(nonNav) == 1 {
		return nonNav[0].Ref, ""
	}
	// 5. navigation tabs as a last resort, honouring the caller's IDTypeTokens preference first.
	if len(nonNav) == 0 && len(candidates) > 0 {
		for _, pref := range preferred {
			pref = strings.ToLower(pref)
			if pref == "" {
				continue
			}
			for _, c := range candidates {
				if strings.Contains(strings.ToLower(c.Ref), pref) {
					return c.Ref, ""
				}
			}
		}
		return candidates[0].Ref, ""
	}
	return "", fmt.Sprintf("AMBIGUOUS_FLOW_ACTION: %d non-navigation actions available=%v (set esignet.auth_factor to disambiguate)", len(nonNav), refsOf(candidates))
}

// refsOf renders actions for an error message.
func refsOf(actions []flowAction) []string {
	out := make([]string, 0, len(actions))
	for _, a := range actions {
		out = append(out, a.Ref)
	}
	return out
}

// ----- utils -----

// mustJSON encodes v, panicking on failure: every call site passes a literal map of strings.
func mustJSON(v any) []byte {
	b, err := json.Marshal(v)
	if err != nil {
		panic(fmt.Sprintf("mustJSON: %v", err))
	}
	return b
}

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

// truncate cuts s to at most n bytes for a debug/error message.
func truncate(s string, n int) string { return textx.Truncate(s, n, "…") }

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
