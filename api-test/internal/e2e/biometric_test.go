package e2e

import (
	"encoding/base64"
	"encoding/json"
	"strings"
	"testing"

	"github.com/mosip/esignet/api-test/internal/esignet"
	"github.com/mosip/esignet/api-test/internal/result"
)

func testCapture(t *testing.T) (capture, payload, hash string) {
	t.Helper()
	payload = base64.RawURLEncoding.EncodeToString([]byte(`{"bioType":"Finger","bioValue":"` + strings.Repeat("Q", 40) + `"}`))
	hash = strings.Repeat("ab12", 16)
	rec, _ := json.Marshal([]map[string]string{{
		"data": "eyJhbGciOiJSUzI1NiIsIng1YyI6WyJNSUkiXX0." + payload + "." + strings.Repeat("s", 43),
		"hash": hash, "sessionKey": strings.Repeat("k", 40), "thumbprint": "t",
	}})
	return base64.StdEncoding.EncodeToString(rec), payload, hash
}

func TestAssertCaptureNotEchoed(t *testing.T) {
	capture, payload, hash := testCapture(t)

	clean := []result.HTTPCall{
		{Label: "mock MDS capture (Finger)", RespBody: `{"biometrics":[{"data":"x.` + payload + `.y"}]}`},
		{Label: "flow/execute: step 3 SBI_ID", ReqBody: capture, RespBody: `{"flowStatus":"INCOMPLETE"}`},
		// Another RS256 x5c JWT shares the capture's JWS header, which must not count as an echo.
		{Label: "token", RespBody: `{"id_token":"eyJhbGciOiJSUzI1NiIsIng1YyI6WyJNSUkiXX0.eyJzdWIiOiJ1In0.sig"}`},
	}
	if a := assertCaptureNotEchoed(clean, []string{capture}); !a.Passed {
		t.Fatalf("MDS responses and request bodies are not echoes: %+v", a)
	}

	echoed := append(clean, result.HTTPCall{Label: "userinfo", RespBody: `{"sub":"u","x":"` + hash + `"}`})
	if a := assertCaptureNotEchoed(echoed, []string{capture}); a.Passed || !strings.Contains(a.Actual, "userinfo") {
		t.Fatalf("a hash in userinfo is an echo: %+v", a)
	}

	// An echo inside a JWT's payload is found once the payload is decoded.
	inner := base64.RawURLEncoding.EncodeToString([]byte(`{"sub":"u","bio":"` + hash + `"}`))
	inJWT := append(clean, result.HTTPCall{Label: "token", RespBody: `{"id_token":"eyJhbGciOiJSUzI1NiJ9.` + inner + `.sig"}`})
	if a := assertCaptureNotEchoed(inJWT, []string{capture}); a.Passed {
		t.Fatalf("a hash inside the ID token is an echo: %+v", a)
	}

	// The bioValue decoded out of the signed payload matches no prefix of the encoded capture, so it is its own fragment.
	decoded := append(clean, result.HTTPCall{Label: "userinfo", RespBody: `{"sub":"u","x":"` + strings.Repeat("Q", 40) + `"}`})
	if a := assertCaptureNotEchoed(decoded, []string{capture}); a.Passed || !strings.Contains(a.Actual, "userinfo") {
		t.Fatalf("a decoded bioValue in userinfo is an echo: %+v", a)
	}

	if a := assertCaptureNotEchoed(clean, nil); a.Passed {
		t.Fatal("with no capture to look for, the check must not pass vacuously")
	}
}

func TestScenarioConfigErrorForScripts(t *testing.T) {
	act := "SBI_ID"
	cases := map[string]Scenario{
		"steps with expect_login_failure": {AuthFactor: "bio", ExpectLoginFailure: true, Steps: []esignet.ScriptStep{{}}},
		"malformed script":                {AuthFactor: "bio", Steps: []esignet.ScriptStep{{Finish: true}, {}}},
		"echo check without a biometric":  {AuthFactor: "bio", ExpectCaptureNotEchoed: true, Steps: []esignet.ScriptStep{{Action: &act, Inputs: map[string]string{"username": "u"}}}},
		"id token check without finish":   {AuthFactor: "bio", ExpectIDTokenValues: map[string]string{"acr": "x"}, Steps: []esignet.ScriptStep{{Action: &act, Inputs: map[string]string{"username": "u"}}}},
		"introspect without finish":       {AuthFactor: "bio", Introspect: []IntrospectCase{{}}, Steps: []esignet.ScriptStep{{Action: &act, Inputs: map[string]string{"username": "u"}}}},
	}
	for name, sc := range cases {
		if scenarioConfigError(sc) == "" {
			t.Errorf("%s: accepted", name)
		}
	}
	ok := Scenario{AuthFactor: "bio", ExpectCaptureNotEchoed: true, Steps: []esignet.ScriptStep{
		{Capture: &esignet.CaptureSpec{As: "c"}, Action: &act, Inputs: map[string]string{"biometric": "{{capture.c}}"}},
		{Finish: true},
	}}
	if msg := scenarioConfigError(ok); msg != "" {
		t.Errorf("well-formed scripted scenario rejected: %s", msg)
	}
}
