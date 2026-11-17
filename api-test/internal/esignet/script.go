package esignet

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"net/http"
	"regexp"
	"slices"
	"strconv"
	"strings"

	"github.com/mosip/esignet/api-test/internal/result"
)

// ScriptStep is one hand-written /flow/execute call of a scripted login. The automatic driver picks the happy-path action and answers every input it is asked for; a script instead sends exactly what each step says and asserts on each response, so it can reach the rejections and retries the driver never would.
//
// A step is one of: a flow/execute call (the default), a capture with nothing sent (capture_only), a fresh login (restart), or the hand-off to the automatic driver for consent and the callback (finish).
type ScriptStep struct {
	// Name labels the step in the report; empty uses the step number and action.
	Name string `json:"name"`

	// Restart abandons the current execution and sends the authorize request again, so later steps drive a new login.
	Restart bool `json:"restart"`

	// Finish hands the flow, from its current view, to the automatic driver: consent is answered and the assertion delivered to the callback, so the scenario goes on to token and userinfo. It must be the last step.
	Finish bool `json:"finish"`

	// Capture takes a biometric capture for the current execution before the step's request is sent; inputs refer to it as {{capture.<as>}}.
	Capture *CaptureSpec `json:"capture"`

	// CaptureOnly takes Capture and sends nothing, keeping the capture for a later login.
	CaptureOnly bool `json:"capture_only"`

	// Action is sent as given; nil omits the field and "" sends an empty one.
	Action *string `json:"action"`

	// Inputs are sent exactly as given once their {{placeholders}} are resolved; nil omits the field and {} sends an empty object. Earlier steps' inputs are not re-sent: the engine keeps them itself.
	Inputs map[string]string `json:"inputs"`

	// EncodeBase64 names inputs whose resolved value is sent base64-encoded (standard alphabet, as the UI encodes a capture).
	EncodeBase64 []string `json:"encode_base64"`

	// Repeat sends the step this many times, each with a fresh capture when the step takes one. 0 means once.
	Repeat int `json:"repeat"`

	// Expect is checked against every response the step gets; nil still requires HTTP 200.
	Expect *StepExpect `json:"expect"`
}

// CaptureSpec selects what the mock MDS captures.
type CaptureSpec struct {
	// As names the capture for {{capture.<as>}}; empty is "default".
	As string `json:"as"`
	// Modality is Finger, Face or Iris; empty is Finger.
	Modality string `json:"modality"`
	// Profile is the mock MDS profile to capture from; empty leaves the MDS on its Default profile. A profile other than the one the identity was enrolled from gives a well-formed capture of someone else.
	Profile string `json:"profile"`
	// Tamper alters the signed capture after the MDS returns it, so its signature no longer verifies.
	Tamper bool `json:"tamper"`
}

func (c CaptureSpec) name() string {
	if c.As == "" {
		return "default"
	}
	return c.As
}

// Capturer takes a biometric capture bound to one flow execution. The returned value is what the UI submits as inputs.biometric; calls is the trace of the capture, for the report.
type Capturer interface {
	Capture(ctx context.Context, executionID string, spec CaptureSpec) (value string, calls []result.HTTPCall, err error)
}

// StepExpect is what one scripted step's response must show. Every field left empty asserts nothing.
type StepExpect struct {
	// HTTPStatus is the exact status required; 0 means 200 unless HTTPStatusBelow is set.
	HTTPStatus int `json:"http_status"`
	// HTTPStatusBelow accepts any status under it instead of one exact status — 500 for "refused cleanly, no server error".
	HTTPStatusBelow int `json:"http_status_below"`

	FlowStatus string `json:"flow_status"`
	// HasError and NoError require the response to carry, or not carry, an error (an error object, or the flat code of an entry-point error). The code itself is not asserted: flow/execute codes are the flow engine's own, not part of eSignet's API.
	HasError bool `json:"has_error"`
	NoError  bool `json:"no_error"`

	// Inputs must be requested by the returned view; RequiredInputs must also be marked required.
	Inputs         []string `json:"inputs"`
	RequiredInputs []string `json:"required_inputs"`
	NotInputs      []string `json:"not_inputs"`
	Actions        []string `json:"actions"`
	NotActions     []string `json:"not_actions"`

	// Authenticated true requires the user to be past authentication (the consent prompt, or COMPLETE); false requires them not to be.
	Authenticated *bool `json:"authenticated"`
	// FieldErrors true requires a non-empty data.fieldErrors; false requires none.
	FieldErrors *bool `json:"field_errors"`

	// BodyExcludes are strings that must not appear anywhere in the response body.
	BodyExcludes []string `json:"body_excludes"`
}

// ScriptResult is a scripted login's outcome: the flow result the rest of the scenario continues from, plus every step's checks.
type ScriptResult struct {
	FlowResult
	Assertions []result.Assertion
	// Finished reports that the script reached its finish step, so RedirectURI holds the callback redirect.
	Finished bool
	// Captures is every biometric value the script took or submitted, for checking that none of it is echoed back.
	Captures []string
}

// biometricInput is the flow input a capture is submitted as.
const biometricInput = "biometric"

// ScriptNeedsCapture reports whether any step takes a capture, i.e. whether the script needs a mock MDS.
func ScriptNeedsCapture(steps []ScriptStep) bool {
	for _, st := range steps {
		if st.Capture != nil {
			return true
		}
	}
	return false
}

// ScriptSubmitsBiometric reports whether any step takes a capture or sends a biometric input, i.e. whether there is anything for an echo check to look for.
func ScriptSubmitsBiometric(steps []ScriptStep) bool {
	for _, st := range steps {
		if _, ok := st.Inputs[biometricInput]; ok || st.Capture != nil {
			return true
		}
	}
	return false
}

// ValidateScript reports a spec-file mistake in steps, or "" when they are well formed.
func ValidateScript(steps []ScriptStep) string {
	captured := map[string]bool{}
	for i, st := range steps {
		at := fmt.Sprintf("steps[%d]", i)
		sends := st.Action != nil || st.Inputs != nil
		switch {
		case st.Restart && st.Finish:
			return at + ": restart and finish are mutually exclusive"
		case (st.Restart || st.Finish) && (sends || st.Capture != nil || st.Expect != nil || st.Repeat != 0):
			return at + ": a restart or finish step takes no action, inputs, capture, repeat or expect"
		case st.Finish && i != len(steps)-1:
			return at + ": finish must be the last step"
		case st.CaptureOnly && st.Capture == nil:
			return at + ": capture_only needs a capture"
		case st.CaptureOnly && (sends || st.Expect != nil):
			return at + ": a capture_only step sends nothing, so it takes no action, inputs or expect"
		case st.Repeat < 0:
			return at + ": repeat must not be negative"
		}
		if st.Capture != nil {
			if _, ok := captureModalities[strings.ToLower(st.Capture.Modality)]; !ok {
				return fmt.Sprintf("%s: capture modality %q is not one of Finger, Face, Iris", at, st.Capture.Modality)
			}
			captured[st.Capture.name()] = true
		}
		for k, v := range st.Inputs {
			if msg := checkPlaceholders(v, captured); msg != "" {
				return fmt.Sprintf("%s: input %q: %s", at, k, msg)
			}
		}
		for _, k := range st.EncodeBase64 {
			if _, ok := st.Inputs[k]; !ok {
				return fmt.Sprintf("%s: encode_base64 names %q, which is not one of the step's inputs", at, k)
			}
		}
	}
	return ""
}

// captureModalities are the SBI device types a capture can name, keyed lower-case; "" defaults to Finger.
var captureModalities = map[string]string{"": "Finger", "finger": "Finger", "face": "Face", "iris": "Iris"}

// CaptureModality returns the SBI device type a capture spec names.
func CaptureModality(spec CaptureSpec) string {
	if m, ok := captureModalities[strings.ToLower(spec.Modality)]; ok {
		return m
	}
	return spec.Modality
}

// RunScript drives one login by hand through steps; consent policy and answers apply only at the finish hand-off.
func (d *Driver) RunScript(ctx context.Context, base, authorizeURL string, steps []ScriptStep, capturer Capturer) ScriptResult {
	d.consentPrompted, d.consentDenied = false, nil

	s := d.newSession()
	res := d.runScript(ctx, s, base, authorizeURL, steps, capturer)
	res.Calls = s.calls
	res.ConsentPrompted = d.consentPrompted
	res.ConsentDenied = d.consentDenied
	return res
}

func (d *Driver) runScript(ctx context.Context, s *session, base, authorizeURL string, steps []ScriptStep, capturer Capturer) ScriptResult {
	var res ScriptResult
	login := d.run(ctx, s, base, authorizeURL, true)
	res.AuthorizeStatus = login.AuthorizeStatus
	if !login.LoginReached {
		res.Error = firstNonEmpty(login.Error, "authorize did not reach the login page")
		return res
	}
	authID, executionID := login.authID, login.executionID
	var challengeToken string
	var last *flowView
	captures := map[string]string{}

	for i, st := range steps {
		label := st.label(i)
		if st.Restart {
			again := d.run(ctx, s, base, authorizeURL, true)
			if !again.LoginReached {
				res.Error = fmt.Sprintf("%s: %s", label, firstNonEmpty(again.Error, "authorize did not reach the login page"))
				return res
			}
			authID, executionID, challengeToken, last = again.authID, again.executionID, "", nil
			continue
		}
		if st.Finish {
			var fr FlowResult
			if last != nil && last.flowStatus == "COMPLETE" && last.assertion != "" {
				fr = d.deliverAssertion(ctx, s, base, authID, last.assertion, fr)
			} else {
				fr = d.completeLogin(ctx, s, base, authID, executionID, challengeToken, fr)
			}
			res.RedirectURI, res.CallbackStatus, res.Error = fr.RedirectURI, fr.CallbackStatus, fr.Error
			res.Finished = true
			return res
		}

		times := max(st.Repeat, 1)
		for n := range times {
			stepLabel := label
			if times > 1 {
				stepLabel = fmt.Sprintf("%s (%d/%d)", label, n+1, times)
			}
			if st.Capture != nil {
				if capturer == nil {
					res.Error = stepLabel + ": the step takes a biometric capture, but no mock MDS is configured"
					return res
				}
				v, calls, err := capturer.Capture(ctx, executionID, *st.Capture)
				s.append(calls)
				if err != nil {
					res.Error = fmt.Sprintf("%s: capture: %v", stepLabel, err)
					return res
				}
				captures[st.Capture.name()] = v
				res.Captures = append(res.Captures, v)
			}
			if st.CaptureOnly {
				continue
			}

			payload, inputs, err := st.payload(executionID, challengeToken, captures, d.answers)
			if err != nil {
				res.Error = fmt.Sprintf("%s: %v", stepLabel, err)
				return res
			}
			if v := inputs[biometricInput]; v != "" && !slices.Contains(res.Captures, v) {
				res.Captures = append(res.Captures, v)
			}
			body, status, _, err := s.do(ctx, "flow/execute: "+stepLabel, http.MethodPost, base+"/flow/execute", payload, "application/json", true)
			if err != nil {
				res.Error = fmt.Sprintf("%s: flow/execute: %v", stepLabel, err)
				return res
			}
			v := parseFlowView(status, body)
			if v.challengeToken != "" {
				challengeToken = v.challengeToken
			}
			last = &v
			expect := StepExpect{}
			if st.Expect != nil {
				expect = *st.Expect
			}
			res.Assertions = append(res.Assertions, expect.check(stepLabel, v)...)
		}
	}
	return res
}

// append adds calls made outside the session (the capture) to its trace, renumbered into its sequence.
func (s *session) append(calls []result.HTTPCall) {
	for _, c := range calls {
		c.Seq = len(s.calls) + 1
		s.calls = append(s.calls, c)
	}
}

func (st ScriptStep) label(i int) string {
	name := st.Name
	if name == "" {
		switch {
		case st.CaptureOnly:
			name = "capture"
		case st.Action == nil:
			name = "(no action)"
		case *st.Action == "":
			name = `(action "")`
		default:
			name = *st.Action
		}
	}
	return fmt.Sprintf("step %d %s", i+1, name)
}

// payload builds the step's flow/execute body; inputs is what it sends, resolved and encoded.
func (st ScriptStep) payload(executionID, challengeToken string, captures, answers map[string]string) ([]byte, map[string]string, error) {
	p := map[string]any{"executionId": executionID}
	if challengeToken != "" {
		p["challengeToken"] = challengeToken
	}
	if st.Action != nil {
		p["action"] = *st.Action
	}
	var in map[string]string
	if st.Inputs != nil {
		in = make(map[string]string, len(st.Inputs))
		for k, raw := range st.Inputs {
			v, err := resolvePlaceholders(raw, captures, answers)
			if err != nil {
				return nil, nil, fmt.Errorf("input %q: %w", k, err)
			}
			in[k] = v
		}
		for _, k := range st.EncodeBase64 {
			in[k] = base64.StdEncoding.EncodeToString([]byte(in[k]))
		}
		p["inputs"] = in
	}
	body, err := json.Marshal(p)
	return body, in, err
}

var placeholderRe = regexp.MustCompile(`\{\{([^{}]+)\}\}`)

// maxFiller bounds {{filler:N}}, well past any request size a deployment should accept.
const maxFiller = 16 << 20

// checkPlaceholders reports a placeholder in s that could never resolve: a capture not taken by an earlier (or the same) step, a bad filler length, or a name that is no answer key.
func checkPlaceholders(s string, captured map[string]bool) string {
	for _, m := range placeholderRe.FindAllStringSubmatch(s, -1) {
		key := strings.TrimSpace(m[1])
		switch {
		case strings.HasPrefix(key, "capture."):
			if !captured[strings.TrimPrefix(key, "capture.")] {
				return m[0] + " names no capture taken by this or an earlier step"
			}
		case strings.HasPrefix(key, "filler:"):
			if n, err := strconv.Atoi(strings.TrimPrefix(key, "filler:")); err != nil || n < 0 || n > maxFiller {
				return fmt.Sprintf("%s: filler length must be 0..%d", m[0], maxFiller)
			}
		case !KnownAnswerKey(key):
			return m[0] + " is not a known answer key"
		}
	}
	return ""
}

// resolvePlaceholders substitutes {{capture.<name>}} (a capture taken earlier in the script), {{filler:<n>}} (n base64-safe characters) and {{<answer key>}} (a configured identity answer, e.g. {{username}}).
func resolvePlaceholders(s string, captures, answers map[string]string) (string, error) {
	var firstErr error
	out := placeholderRe.ReplaceAllStringFunc(s, func(m string) string {
		key := strings.TrimSpace(m[2 : len(m)-2])
		switch {
		case strings.HasPrefix(key, "capture."):
			v, ok := captures[strings.TrimPrefix(key, "capture.")]
			if !ok && firstErr == nil {
				firstErr = fmt.Errorf("%s: no capture by that name was taken earlier in the script", m)
			}
			return v
		case strings.HasPrefix(key, "filler:"):
			n, err := strconv.Atoi(strings.TrimPrefix(key, "filler:"))
			if (err != nil || n < 0 || n > maxFiller) && firstErr == nil {
				firstErr = fmt.Errorf("%s: filler length must be 0..%d", m, maxFiller)
				return ""
			}
			return strings.Repeat("A", n)
		default:
			v := answers[Normalize(key)]
			if v == "" && firstErr == nil {
				firstErr = fmt.Errorf("%s: no configured answer for %q", m, key)
			}
			return v
		}
	})
	return out, firstErr
}

// flowView is the part of one flow/execute response a step's expectations read.
type flowView struct {
	status         int
	body           string
	flowStatus     string
	challengeToken string
	assertion      string
	// errorCode is the rejection code, reported but never asserted; hasError is whether there was a rejection at all.
	errorCode   string
	hasError    bool
	inputs      map[string]bool // normalized identifier -> required
	actions     []string
	fieldErrors bool
}

func parseFlowView(status int, body []byte) flowView {
	v := flowView{status: status, body: string(body), inputs: map[string]bool{}}
	var r struct {
		FlowStatus     string `json:"flowStatus"`
		ChallengeToken string `json:"challengeToken"`
		Assertion      string `json:"assertion"`
		// Code is the flat {code, message} shape of an entry-point rejection.
		Code  string          `json:"code"`
		Error json.RawMessage `json:"error"`
		Data  struct {
			Inputs []struct {
				Identifier string `json:"identifier"`
				Required   bool   `json:"required"`
			} `json:"inputs"`
			Actions []struct {
				Ref string `json:"ref"`
			} `json:"actions"`
			FieldErrors json.RawMessage `json:"fieldErrors"`
		} `json:"data"`
	}
	if json.Unmarshal(body, &r) != nil {
		return v
	}
	v.flowStatus, v.challengeToken, v.assertion = r.FlowStatus, r.ChallengeToken, r.Assertion
	v.errorCode = r.Code
	switch strings.TrimSpace(string(r.Error)) {
	case "", "null", "{}":
	default:
		v.hasError = true
		var e struct {
			Code string `json:"code"`
		}
		if json.Unmarshal(r.Error, &e) == nil && e.Code != "" {
			v.errorCode = e.Code
		}
	}
	if v.errorCode != "" {
		v.hasError = true
	}
	for _, in := range r.Data.Inputs {
		if in.Identifier != "" {
			v.inputs[Normalize(in.Identifier)] = in.Required
		}
	}
	for _, a := range r.Data.Actions {
		if a.Ref != "" {
			v.actions = append(v.actions, a.Ref)
		}
	}
	switch strings.TrimSpace(string(r.Data.FieldErrors)) {
	case "", "null", "[]", "{}":
	default:
		v.fieldErrors = true
	}
	return v
}

// authenticated reports whether the view is past authentication: the consent prompt, or the flow already complete because consent was stored.
func (v flowView) authenticated() bool {
	_, consent := v.inputs["consentdecisions"]
	return v.flowStatus == "COMPLETE" || consent
}

// errorSummary is the error as the report shows it: its code when it has one.
func (v flowView) errorSummary() string {
	switch {
	case v.errorCode != "":
		return v.errorCode
	case v.hasError:
		return "present"
	}
	return "(none)"
}

func (v flowView) inputList() string {
	if len(v.inputs) == 0 {
		return "(none)"
	}
	var ids []string
	for id, req := range v.inputs {
		if req {
			id += " (required)"
		}
		ids = append(ids, id)
	}
	slices.Sort(ids)
	return strings.Join(ids, ", ")
}

func (e StepExpect) check(label string, v flowView) []result.Assertion {
	var out []result.Assertion
	add := func(field, expected, actual string, passed bool) {
		out = append(out, result.Assertion{Field: label + ": " + field, Expected: expected, Actual: actual, Passed: passed})
	}

	if e.HTTPStatusBelow > 0 {
		add("HTTP status", fmt.Sprintf("below %d", e.HTTPStatusBelow), strconv.Itoa(v.status), v.status < e.HTTPStatusBelow)
	} else {
		want := e.HTTPStatus
		if want == 0 {
			want = http.StatusOK
		}
		add("HTTP status", strconv.Itoa(want), strconv.Itoa(v.status), v.status == want)
	}
	if e.FlowStatus != "" {
		add("flowStatus", e.FlowStatus, orNone(v.flowStatus), strings.EqualFold(v.flowStatus, e.FlowStatus))
	}
	if e.HasError {
		add("error", "present", v.errorSummary(), v.hasError)
	}
	if e.NoError {
		add("error", "(none)", v.errorSummary(), !v.hasError)
	}
	for _, id := range e.Inputs {
		_, ok := v.inputs[Normalize(id)]
		add("input "+id, "requested", v.inputList(), ok)
	}
	for _, id := range e.RequiredInputs {
		add("input "+id, "requested (required)", v.inputList(), v.inputs[Normalize(id)])
	}
	for _, id := range e.NotInputs {
		_, ok := v.inputs[Normalize(id)]
		add("input "+id, "not requested", v.inputList(), !ok)
	}
	actions := orNone(strings.Join(v.actions, ", "))
	for _, a := range e.Actions {
		add("action "+a, "offered", actions, slices.Contains(v.actions, a))
	}
	for _, a := range e.NotActions {
		add("action "+a, "not offered", actions, !slices.Contains(v.actions, a))
	}
	if e.Authenticated != nil {
		want, got := "not authenticated", "not authenticated"
		if *e.Authenticated {
			want = "authenticated (consent prompt or COMPLETE)"
		}
		if v.authenticated() {
			got = "authenticated (flowStatus " + orNone(v.flowStatus) + ")"
		}
		add("authentication", want, got, v.authenticated() == *e.Authenticated)
	}
	if e.FieldErrors != nil {
		want := map[bool]string{true: "present", false: "absent"}
		add("data.fieldErrors", want[*e.FieldErrors], want[v.fieldErrors], v.fieldErrors == *e.FieldErrors)
	}
	for _, x := range e.BodyExcludes {
		in := strings.Contains(v.body, x)
		add("response body", "does not contain "+strconv.Quote(x), map[bool]string{true: "contains it", false: "absent"}[in], !in)
	}
	return out
}

func orNone(s string) string {
	if s == "" {
		return "(none)"
	}
	return s
}
