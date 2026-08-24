package e2e

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"testing"

	"github.com/mosip/esignet/api-test/internal/result"
)

func TestIntrospectCaseResolveDefaults(t *testing.T) {
	got, err := IntrospectCase{}.resolve()
	if err != nil {
		t.Fatalf("resolve: %v", err)
	}
	if got.Token != introspectAccessToken {
		t.Errorf("token = %q, want %q", got.Token, introspectAccessToken)
	}
	if got.ClientAuth != introspectAuthPrivateKeyJWT {
		t.Errorf("client_auth = %q, want %q", got.ClientAuth, introspectAuthPrivateKeyJWT)
	}
	if got.ExpectStatus != http.StatusOK {
		t.Errorf("expect_status = %d, want 200", got.ExpectStatus)
	}
	// An unnamed case still has to be identifiable in the report.
	if got.Name == "" {
		t.Error("resolve left the case unnamed")
	}
}

func TestIntrospectCaseResolveRejectsUnknownSelectors(t *testing.T) {
	if _, err := (IntrospectCase{Token: "refresh_token"}).resolve(); err == nil {
		t.Error("unknown token selector was accepted")
	}
	if _, err := (IntrospectCase{ClientAuth: "client_secret_basic"}).resolve(); err == nil {
		t.Error("unknown client_auth mode was accepted")
	}
	// A mistyped selector must name the offending case, not just the field.
	_, err := resolveIntrospect([]IntrospectCase{{}, {Token: "nope"}})
	if err == nil || !strings.Contains(err.Error(), "case 2") {
		t.Errorf("resolveIntrospect error = %v, want it to name case 2", err)
	}
}

// A case must be able to assert against values only the run knows.
func TestIntrospectPlaceholderSubstitution(t *testing.T) {
	subs := introspectPlaceholders(
		&Runner{Issuer: "https://issuer.example"},
		&testClient{clientID: "cid-1"},
		tokenSet{sub: "sub-1", dpopJKT: "jkt-1"},
	)
	for in, want := range map[string]string{
		"{{client_id}}": "cid-1",
		"{{issuer}}":    "https://issuer.example",
		"{{sub}}":       "sub-1",
		"{{dpop_jkt}}":  "jkt-1",
		"literal":       "literal",
	} {
		if got := substitute(in, subs); got != want {
			t.Errorf("substitute(%q) = %q, want %q", in, got, want)
		}
	}
}

func TestLookupMemberDottedPath(t *testing.T) {
	var body map[string]any
	if err := json.Unmarshal([]byte(`{"active":true,"cnf":{"jkt":"abc"},"aud":["cid-1","other"]}`), &body); err != nil {
		t.Fatal(err)
	}
	if v, ok := lookupMember(body, "cnf.jkt"); !ok || v != "abc" {
		t.Errorf("cnf.jkt = %v (ok=%v), want abc", v, ok)
	}
	if _, ok := lookupMember(body, "cnf.missing"); ok {
		t.Error("cnf.missing resolved")
	}
	// A scalar is not an object, so a path through it must not resolve.
	if _, ok := lookupMember(body, "active.nested"); ok {
		t.Error("active.nested resolved")
	}
	// RFC 7662 lets aud be a string or a list; a case should not have to know which.
	aud, _ := lookupMember(body, "aud")
	if !matchesMember(aud, "cid-1") {
		t.Error("aud array did not match one of its elements")
	}
	if matchesMember(aud, "cid-2") {
		t.Error("aud array matched an element it does not contain")
	}
}

// exp and iat are JSON numbers; a report reading 1.7356896e+09 helps nobody.
func TestIntrospectValueRendersWholeNumbersAsIntegers(t *testing.T) {
	for in, want := range map[any]string{
		float64(1735689600): "1735689600",
		float64(1.5):        "1.5",
		true:                "true",
		"openid":            "openid",
		nil:                 "null",
	} {
		if got := introspectValue(in); got != want {
			t.Errorf("introspectValue(%v) = %q, want %q", in, got, want)
		}
	}
	if got := introspectValue([]any{"a", "b"}); got != `["a","b"]` {
		t.Errorf("introspectValue(array) = %q", got)
	}
}

// introspectForm is where a negative case is actually expressed, so each mode
// has to produce a materially different request.
func TestIntrospectFormPerClientAuthMode(t *testing.T) {
	priv, err := generateRSA()
	if err != nil {
		t.Fatal(err)
	}
	cl := &testClient{priv: priv, kid: "k1", clientID: "cid-1"}
	r := &Runner{Issuer: "https://issuer.example", IntrospectEndpoint: "https://issuer.example/oauth2/introspect"}
	tk := tokenSet{accessToken: "at-1", idToken: "it-1"}

	mustForm := func(c IntrospectCase) url.Values {
		t.Helper()
		rc, rerr := c.resolve()
		if rerr != nil {
			t.Fatalf("resolve: %v", rerr)
		}
		f, ferr := r.introspectForm(cl, rc, tk)
		if ferr != nil {
			t.Fatalf("introspectForm: %v", ferr)
		}
		return f
	}

	good := mustForm(IntrospectCase{Hint: "access_token"})
	if good.Get("token") != "at-1" || good.Get("token_type_hint") != "access_token" {
		t.Errorf("default case sent token=%q hint=%q", good.Get("token"), good.Get("token_type_hint"))
	}
	if good.Get("client_assertion_type") != clientAssertionTypeJWTBearer {
		t.Errorf("client_assertion_type = %q", good.Get("client_assertion_type"))
	}
	if aud := audienceOf(t, good.Get("client_assertion")); aud != r.Issuer {
		t.Errorf("assertion aud = %q, want the issuer %q", aud, r.Issuer)
	}

	// No hint at all, so the endpoint has to resolve the token unaided.
	if _, sent := mustForm(IntrospectCase{})["token_type_hint"]; sent {
		t.Error("a case with no hint still sent token_type_hint")
	}

	if got := mustForm(IntrospectCase{Token: introspectIDToken}).Get("token"); got != "it-1" {
		t.Errorf("id_token case sent token=%q, want it-1", got)
	}
	if got := mustForm(IntrospectCase{Token: introspectUnissued}).Get("token"); got == "" || got == "at-1" || got == "it-1" {
		t.Errorf("unissued case sent token=%q, want a value this deployment never issued", got)
	}
	if _, sent := mustForm(IntrospectCase{Token: introspectNoToken})["token"]; sent {
		t.Error("the no-token case still sent a token parameter")
	}

	// client_id alone is not client authentication.
	noAssertion := mustForm(IntrospectCase{ClientAuth: introspectAuthNoAssertion})
	if noAssertion.Get("client_id") != "cid-1" || noAssertion.Get("client_assertion") != "" {
		t.Errorf("no_assertion case sent client_id=%q assertion=%q", noAssertion.Get("client_id"), noAssertion.Get("client_assertion"))
	}

	// Signed by a key the client never registered: well-formed, right audience,
	// unverifiable. It must not accidentally be the registered key.
	wrongKey := mustForm(IntrospectCase{ClientAuth: introspectAuthWrongKey})
	if wrongKey.Get("client_assertion") == good.Get("client_assertion") {
		t.Error("wrong_key reused the registered key's assertion")
	}
	if aud := audienceOf(t, wrongKey.Get("client_assertion")); aud != r.Issuer {
		t.Errorf("wrong_key assertion aud = %q, want the issuer (only the key is wrong)", aud)
	}

	if aud := audienceOf(t, mustForm(IntrospectCase{ClientAuth: introspectAuthWrongAudience}).Get("client_assertion")); aud != wrongAudience {
		t.Errorf("wrong_audience assertion aud = %q, want %q", aud, wrongAudience)
	}

	// A case asking for an id_token the flow never returned is a spec error, not
	// a silent introspection of nothing.
	rc, _ := IntrospectCase{Token: introspectIDToken}.resolve()
	if _, err := r.introspectForm(cl, rc, tokenSet{accessToken: "at-1"}); err == nil {
		t.Error("id_token case with no id_token was accepted")
	}
}

func audienceOf(t *testing.T, assertion string) string {
	t.Helper()
	claims, err := decodeJWTPayload(assertion)
	if err != nil {
		t.Fatalf("decode assertion: %v", err)
	}
	aud, _ := claims["aud"].(string)
	return aud
}

// A deployment that advertises no introspection_endpoint must produce a failed
// assertion naming that, rather than a silent pass or a nil-URL request.
func TestIntrospectWithoutEndpointFailsExplicitly(t *testing.T) {
	r := &Runner{}
	got := r.introspect(context.Background(), &[]result.HTTPCall{}, &testClient{}, []IntrospectCase{{}}, tokenSet{})
	if len(got) != 1 || got[0].Passed {
		t.Fatalf("assertions = %+v, want one failure", got)
	}
	if !strings.Contains(got[0].Field, "introspection endpoint") {
		t.Errorf("assertion field = %q", got[0].Field)
	}
}

func TestIntrospectOneAssertsAgainstTheResponse(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		body, _ := io.ReadAll(req.Body)
		form, _ := url.ParseQuery(string(body))
		w.Header().Set("Content-Type", "application/json")
		if form.Get("token") != "at-1" {
			_, _ = io.WriteString(w, `{"active":false}`)
			return
		}
		_, _ = io.WriteString(w, `{"active":true,"client_id":"cid-1","sub":"sub-1","scope":"openid","exp":4102444800,"cnf":{"jkt":"jkt-1"}}`)
	}))
	defer srv.Close()

	priv, err := generateRSA()
	if err != nil {
		t.Fatal(err)
	}
	cl := &testClient{priv: priv, kid: "k1", clientID: "cid-1"}
	r := &Runner{Issuer: srv.URL, IntrospectEndpoint: srv.URL, TLSVerify: false}
	tk := tokenSet{accessToken: "at-1", sub: "sub-1", dpopJKT: "jkt-1"}

	run := func(c IntrospectCase) []result.Assertion {
		t.Helper()
		rc, rerr := c.resolve()
		if rerr != nil {
			t.Fatalf("resolve: %v", rerr)
		}
		var calls []result.HTTPCall
		return r.introspectOne(context.Background(), &calls, cl, rc, tk)
	}

	active := run(IntrospectCase{
		Name: "active", ExpectActive: boolPtr(true),
		ExpectPresent: []string{"scope"},
		ExpectValues:  map[string]string{"client_id": "{{client_id}}", "sub": "{{sub}}", "cnf.jkt": "{{dpop_jkt}}"},
	})
	for _, a := range active {
		if !a.Passed {
			t.Errorf("assertion failed on a matching response: %s: want %q, got %q", a.Field, a.Expected, a.Actual)
		}
	}
	// The exp cross-check is added for an active token, not declared by the case.
	if !hasField(active, "exp") {
		t.Error("no exp assertion was produced for an active token")
	}

	// RFC 7662 s4: an inactive answer must not leak metadata. The stub sends
	// nothing but "active" for a token it did not issue, so this is the
	// passing direction; the failing one is exercised below.
	inactive := run(IntrospectCase{
		Name: "unissued", Token: introspectUnissued, ExpectActive: boolPtr(false),
		ExpectAbsent: []string{"sub"},
	})
	for _, a := range inactive {
		if !a.Passed {
			t.Errorf("assertion failed on the inactive response: %s: want %q, got %q", a.Field, a.Expected, a.Actual)
		}
	}

	// A case whose expectation is wrong must fail rather than pass vacuously.
	mismatch := run(IntrospectCase{Name: "mismatch", ExpectValues: map[string]string{"sub": "somebody-else"}})
	if !anyFailed(mismatch) {
		t.Error("a mismatched expected value passed")
	}
	if !anyFailed(run(IntrospectCase{Name: "wrong-status", ExpectStatus: http.StatusUnauthorized})) {
		t.Error("a wrong expected status passed")
	}
	if !anyFailed(run(IntrospectCase{Name: "absent-member", ExpectPresent: []string{"username"}})) {
		t.Error("expect_present passed for a member the response omits")
	}
	// The other direction for expect_absent: the active response does carry
	// sub, so a case demanding its absence has to fail. Without this an
	// expect_absent that always passed would keep the s4 leak check green.
	if !anyFailed(run(IntrospectCase{Name: "leaked-member", ExpectAbsent: []string{"sub"}})) {
		t.Error("expect_absent passed for a member the response returns")
	}
}

func hasField(as []result.Assertion, suffix string) bool {
	for _, a := range as {
		if strings.HasSuffix(a.Field, suffix) {
			return true
		}
	}
	return false
}

func anyFailed(as []result.Assertion) bool {
	for _, a := range as {
		if !a.Passed {
			return true
		}
	}
	return false
}

// A scenario that both expects rejection and asks for introspection would be
// reported PASSED on the expected-rejection branch, having introspected
// nothing — so it has to be refused as a config error instead.
func TestScenarioConfigErrorCatchesUnreachableIntrospection(t *testing.T) {
	ok := Scenario{AuthFactor: "otp", Introspect: []IntrospectCase{{}}}
	if msg := scenarioConfigError(ok); msg != "" {
		t.Errorf("well-formed scenario rejected: %s", msg)
	}
	bad := Scenario{AuthFactor: "otp", ExpectLoginFailure: true, Introspect: []IntrospectCase{{}}}
	if msg := scenarioConfigError(bad); !strings.Contains(msg, "expect_login_failure") {
		t.Errorf("scenarioConfigError = %q, want it to name expect_login_failure", msg)
	}
	if msg := scenarioConfigError(Scenario{}); !strings.Contains(msg, "auth_factor") {
		t.Errorf("scenarioConfigError = %q, want it to name auth_factor", msg)
	}
}
