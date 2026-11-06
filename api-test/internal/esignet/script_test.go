package esignet

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"slices"
	"strings"
	"testing"
	"time"

	"github.com/mosip/esignet/api-test/internal/result"
)

func strp(s string) *string { return &s }

func TestValidateScript(t *testing.T) {
	ok := []ScriptStep{
		{Expect: &StepExpect{}},
		{Capture: &CaptureSpec{As: "c", Modality: "face"}, Action: strp("SBI_ID"), Inputs: map[string]string{"biometric": "{{capture.c}}", "username": "{{username}}"}},
		{Capture: &CaptureSpec{As: "later"}, CaptureOnly: true},
		{Restart: true},
		{Action: strp("SBI_ID"), Inputs: map[string]string{"biometric": "{{capture.later}}{{filler:10}}", "x": "[]"}, EncodeBase64: []string{"x"}},
		{Finish: true},
	}
	if msg := ValidateScript(ok); msg != "" {
		t.Fatalf("well-formed script rejected: %s", msg)
	}

	bad := map[string][]ScriptStep{
		"restart+finish":           {{Restart: true, Finish: true}},
		"restart with action":      {{Restart: true, Action: strp("x")}},
		"finish not last":          {{Finish: true}, {}},
		"capture_only no capture":  {{CaptureOnly: true}},
		"capture_only with expect": {{CaptureOnly: true, Capture: &CaptureSpec{}, Expect: &StepExpect{}}},
		"negative repeat":          {{Repeat: -1}},
		"unknown modality":         {{Capture: &CaptureSpec{Modality: "palm"}}},
		"encode unknown input":     {{Inputs: map[string]string{"a": "b"}, EncodeBase64: []string{"c"}}},
		"capture used before taken": {{Inputs: map[string]string{"biometric": "{{capture.good}}"}},
			{Capture: &CaptureSpec{As: "good"}, CaptureOnly: true}},
		"unknown answer key": {{Inputs: map[string]string{"username": "{{usrname}}"}}},
		"bad filler":         {{Inputs: map[string]string{"biometric": "{{filler:lots}}"}}},
	}
	for name, steps := range bad {
		if msg := ValidateScript(steps); msg == "" {
			t.Errorf("%s: accepted", name)
		}
	}
}

func TestResolvePlaceholders(t *testing.T) {
	captures := map[string]string{"good": "CAP"}
	answers := map[string]string{"username": "1234"}
	got, err := resolvePlaceholders("{{capture.good}}|{{username}}|{{filler:3}}", captures, answers)
	if err != nil || got != "CAP|1234|AAA" {
		t.Fatalf("got %q, %v", got, err)
	}
	if _, err := resolvePlaceholders("{{password}}", captures, answers); err == nil {
		t.Error("an unconfigured answer must not resolve to an empty string silently")
	}
	if _, err := resolvePlaceholders("{{capture.none}}", captures, answers); err == nil {
		t.Error("a capture never taken must not resolve")
	}
}

func TestStepExpectCheck(t *testing.T) {
	body := `{"flowStatus":"INCOMPLETE","challengeToken":"ct","error":{"code":"FET-1005"},
		"data":{"inputs":[{"identifier":"biometric","required":true},{"identifier":"username"}],"actions":[{"ref":"SBI_ID"},{"ref":"BACK_BUTTON"}]}}`
	v := parseFlowView(200, []byte(body))
	no := false
	pass := StepExpect{FlowStatus: "INCOMPLETE", HasError: true, RequiredInputs: []string{"biometric"},
		Inputs: []string{"username"}, NotInputs: []string{"captcha_token"}, Actions: []string{"SBI_ID"},
		NotActions: []string{"submit_uin"}, Authenticated: &no, FieldErrors: &no, BodyExcludes: []string{"<script>"}}
	for _, a := range pass.check("s", v) {
		if !a.Passed {
			t.Errorf("expected pass: %+v", a)
		}
	}
	fail := StepExpect{HTTPStatus: 400, NoError: true, RequiredInputs: []string{"username"}, Actions: []string{"sbi_id"}, BodyExcludes: []string{"FET-1005"}}
	for _, a := range fail.check("s", v) {
		if a.Field != "s: HTTP status" && a.Passed {
			t.Errorf("expected failure: %+v", a)
		}
	}

	// A flat entry-point error, an error object without a code, and a stored-consent COMPLETE are all read.
	if v := parseFlowView(400, []byte(`{"code":"FLC-1002"}`)); !v.hasError || v.errorCode != "FLC-1002" {
		t.Errorf("flat error not read: %+v", v)
	}
	if v := parseFlowView(200, []byte(`{"flowStatus":"INCOMPLETE","error":{"message":"m"}}`)); !v.hasError {
		t.Error("an error object without a code must count as an error")
	}
	if v := parseFlowView(200, []byte(`{"flowStatus":"INCOMPLETE","error":null}`)); v.hasError {
		t.Error("a null error must not count as an error")
	}
	if a := (StepExpect{HasError: true}).check("s", parseFlowView(200, []byte(`{"flowStatus":"INCOMPLETE"}`))); a[len(a)-1].Passed {
		t.Error("has_error must fail when the response carries no error")
	}
	if !parseFlowView(200, []byte(`{"flowStatus":"COMPLETE","assertion":"a"}`)).authenticated() {
		t.Error("COMPLETE must count as authenticated")
	}
	if !parseFlowView(200, []byte(`{"flowStatus":"INCOMPLETE","data":{"inputs":[{"identifier":"consent_decisions"}]}}`)).authenticated() {
		t.Error("the consent prompt must count as authenticated")
	}
	below := StepExpect{HTTPStatusBelow: 500}
	if a := below.check("s", parseFlowView(413, nil)); !a[0].Passed {
		t.Errorf("413 is below 500: %+v", a[0])
	}
}

type fakeCapturer struct{ executions []string }

func (f *fakeCapturer) Capture(_ context.Context, executionID string, spec CaptureSpec) (string, []result.HTTPCall, error) {
	f.executions = append(f.executions, executionID)
	return "cap-" + spec.name() + "-" + executionID, []result.HTTPCall{{Label: "mock MDS capture", At: time.Now().UnixNano()}}, nil
}

// The script sends exactly each step's payload, rebinds captures to the execution after a restart, and hands consent to the driver at finish.
func TestRunScript(t *testing.T) {
	var bodies []map[string]any
	logins := 0
	mux := http.NewServeMux()
	mux.HandleFunc("/authorize", func(w http.ResponseWriter, r *http.Request) {
		logins++
		http.Redirect(w, r, "/login?authId=auth&executionId=exec"+string(rune('0'+logins)), http.StatusFound)
	})
	mux.HandleFunc("/flow/execute", func(w http.ResponseWriter, r *http.Request) {
		raw, _ := io.ReadAll(r.Body)
		var b map[string]any
		_ = json.Unmarshal(raw, &b)
		bodies = append(bodies, b)
		_, hasAction := b["action"]
		switch {
		case !hasAction && b["challengeToken"] == "ct1":
			// the finish hand-off resuming the flow: the user is past authentication
			_, _ = io.WriteString(w, `{"flowStatus":"INCOMPLETE","challengeToken":"ct2","data":{"inputs":[{"identifier":"consent_decisions","required":true}],"actions":[{"ref":"allow"}],
				"additionalData":{"consentPrompt":"[{\"purposeName\":\"p\",\"essential\":[{\"name\":\"openid\"}]}]"}}}`)
		case b["action"] == "allow":
			_, _ = io.WriteString(w, `{"flowStatus":"COMPLETE","assertion":"signed"}`)
		case b["action"] == "SBI_ID":
			_, _ = io.WriteString(w, `{"flowStatus":"INCOMPLETE","error":{"code":"FET-1005"},"data":{"inputs":[{"identifier":"biometric","required":true}]}}`)
		default:
			_, _ = io.WriteString(w, `{"flowStatus":"INCOMPLETE","challengeToken":"ct1","data":{"inputs":[{"identifier":"username","required":true}],"actions":[{"ref":"submit_uin"}]}}`)
		}
	})
	mux.HandleFunc("/oauth2/auth/callback", func(w http.ResponseWriter, _ *http.Request) {
		_, _ = io.WriteString(w, `{"redirect_uri":"https://rp/cb?code=c&state=s"}`)
	})
	srv := httptest.NewServer(mux)
	defer srv.Close()

	no := false
	steps := []ScriptStep{
		{Expect: &StepExpect{RequiredInputs: []string{"username"}}},
		{Capture: &CaptureSpec{As: "good"}, CaptureOnly: true},
		{Restart: true},
		{Name: "replay", Action: strp("SBI_ID"), Inputs: map[string]string{"biometric": "{{capture.good}}"}, Expect: &StepExpect{HasError: true, Authenticated: &no}},
		{Action: strp(""), Inputs: map[string]string{"biometric": "[]"}, EncodeBase64: []string{"biometric"}},
		{Finish: true},
	}
	capturer := &fakeCapturer{}
	d := New(map[string]string{}, nil, true, 5*time.Second)
	res := d.RunScript(context.Background(), srv.URL, srv.URL+"/authorize", steps, capturer)
	if res.Error != "" {
		t.Fatalf("script error: %s", res.Error)
	}
	if !res.Finished || res.RedirectURI != "https://rp/cb?code=c&state=s" || !res.ConsentPrompted {
		t.Fatalf("finish did not complete consent and the callback: %+v", res.FlowResult)
	}
	for _, a := range res.Assertions {
		if !a.Passed {
			t.Errorf("assertion failed: %+v", a)
		}
	}
	// Submitted values count for the echo check as well as captures: a mock run submits a literal, never a capture.
	if !slices.Contains(res.Captures, base64.StdEncoding.EncodeToString([]byte("[]"))) || !slices.Contains(res.Captures, "cap-good-exec1") {
		t.Errorf("submitted biometrics not recorded for the echo check: %v", res.Captures)
	}
	if len(capturer.executions) != 1 || capturer.executions[0] != "exec1" {
		t.Fatalf("capture must be bound to the execution it was taken in: %v", capturer.executions)
	}

	// bodies: step 1 (exec1), replay (exec2), empty action (exec2), finish resume + consent (exec2).
	if _, has := bodies[0]["action"]; has {
		t.Errorf("a step with no action must omit the field: %v", bodies[0])
	}
	if bodies[1]["executionId"] != "exec2" || bodies[1]["inputs"].(map[string]any)["biometric"] != "cap-good-exec1" {
		t.Errorf("the replayed capture must be sent into the new execution: %v", bodies[1])
	}
	if a, has := bodies[2]["action"]; !has || a != "" {
		t.Errorf(`action "" must be sent as an empty string: %v`, bodies[2])
	}
	if got := bodies[2]["inputs"].(map[string]any)["biometric"]; got != base64.StdEncoding.EncodeToString([]byte("[]")) {
		t.Errorf("encode_base64 not applied: %v", got)
	}
	if _, has := bodies[2]["inputs"].(map[string]any)["username"]; has {
		t.Error("earlier inputs must not be carried into a scripted step")
	}
	if bodies[3]["challengeToken"] != "ct1" {
		t.Errorf("the finish hand-off must resume with the last challenge token: %v", bodies[3])
	}
	var labels []string
	for _, c := range res.Calls {
		labels = append(labels, c.Label)
	}
	if !strings.Contains(strings.Join(labels, "|"), "mock MDS capture") {
		t.Errorf("capture calls missing from the trace: %v", labels)
	}
}

func TestRunScriptWithoutCapturerFails(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, "/login?authId=a&executionId=e", http.StatusFound)
	}))
	defer srv.Close()
	d := New(nil, nil, true, 5*time.Second)
	res := d.RunScript(context.Background(), srv.URL, srv.URL+"/authorize", []ScriptStep{{Capture: &CaptureSpec{}, CaptureOnly: true}}, nil)
	if !strings.Contains(res.Error, "no mock MDS") {
		t.Fatalf("want a no-MDS error, got %q", res.Error)
	}
}
