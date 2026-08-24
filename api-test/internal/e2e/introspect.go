package e2e

import (
	"context"
	"crypto/rand"
	"encoding/json"
	"fmt"
	"math"
	"net/http"
	"net/url"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/mosip/esignet/api-test/internal/result"
)

// RFC 7662 token introspection, as eSignet exposes it at /oauth2/introspect.
//
// The endpoint is client-authenticated with private_key_jwt exactly like token,
// takes the token to inspect as a form parameter, and answers 200 with an
// "active" member either way: a token it never issued is reported inactive
// rather than as an error (RFC 7662 s2.2), and an inactive answer carries no
// other metadata (s4, so introspection is not an oracle for guessed tokens).
// Errors are reserved for a malformed request (400) and a failed client
// authentication (401).
//
// A case therefore has two independent axes — which token is submitted, and how
// the call authenticates — and the negatives are written by pointing one axis
// away from the positive: a well-formed request whose assertion is signed with
// an unregistered key must be refused before the token is ever looked at.

// Token selectors: what a case submits in the "token" parameter.
const (
	introspectAccessToken = "access_token"
	introspectIDToken     = "id_token"
	introspectUnissued    = "unissued"
	introspectNoToken     = "none"
)

// Client-authentication modes for the introspection call.
const (
	introspectAuthPrivateKeyJWT = "private_key_jwt"
	introspectAuthNoAssertion   = "no_assertion"
	introspectAuthWrongKey      = "wrong_key"
	introspectAuthWrongAudience = "wrong_audience"
)

// wrongAudience is an issuer this deployment is not, for the assertion whose
// audience is deliberately somebody else's.
const wrongAudience = "https://not-this-issuer.example.org"

// IntrospectCase is one introspection request and what is expected back.
type IntrospectCase struct {
	// Name labels the case in the report, so a scenario running several of them
	// says which one failed rather than only that introspection failed.
	Name string `json:"name"`

	// Token selects what is submitted: access_token (default), id_token,
	// unissued (a token this deployment never minted), or none (the parameter
	// is left out entirely).
	Token string `json:"token"`

	// Hint sets token_type_hint. Omitted when empty, which a case uses to prove
	// the endpoint resolves the token without being told its type.
	Hint string `json:"hint"`

	// ClientAuth is how the call authenticates: private_key_jwt (default),
	// no_assertion (client_id alone), wrong_key (an assertion signed with a key
	// the client never registered), or wrong_audience (a correctly signed
	// assertion made out to somebody else).
	ClientAuth string `json:"client_auth"`

	// ExpectStatus is the HTTP status the call must answer with. Defaults to 200.
	ExpectStatus int `json:"expect_status"`

	// ExpectActive asserts the "active" member. Left unset, nothing is asserted
	// about it — which is what an error case wants, since a 400/401 body has none.
	ExpectActive *bool `json:"expect_active"`

	// ExpectPresent, ExpectValues and ExpectAbsent are checked against the
	// response members. Names may be dotted paths ("cnf.jkt"), and an expected
	// value may be one of the {{client_id}}, {{issuer}}, {{sub}} or
	// {{dpop_jkt}} placeholders, which a spec file cannot know ahead of the run.
	ExpectPresent []string          `json:"expect_present"`
	ExpectValues  map[string]string `json:"expect_values"`
	ExpectAbsent  []string          `json:"expect_absent"`

	// ExpectError is the OAuth "error" member a rejection must carry.
	ExpectError string `json:"expect_error"`
}

// resolve validates the case and fills in its defaults.
func (c IntrospectCase) resolve() (IntrospectCase, error) {
	out := c
	switch strings.ToLower(strings.TrimSpace(c.Token)) {
	case "", introspectAccessToken:
		out.Token = introspectAccessToken
	case introspectIDToken:
		out.Token = introspectIDToken
	case introspectUnissued:
		out.Token = introspectUnissued
	case introspectNoToken:
		out.Token = introspectNoToken
	default:
		return out, fmt.Errorf("introspect.token %q is not one of access_token, id_token, unissued, none", c.Token)
	}
	switch strings.ToLower(strings.TrimSpace(c.ClientAuth)) {
	case "", introspectAuthPrivateKeyJWT:
		out.ClientAuth = introspectAuthPrivateKeyJWT
	case introspectAuthNoAssertion:
		out.ClientAuth = introspectAuthNoAssertion
	case introspectAuthWrongKey:
		out.ClientAuth = introspectAuthWrongKey
	case introspectAuthWrongAudience:
		out.ClientAuth = introspectAuthWrongAudience
	default:
		return out, fmt.Errorf("introspect.client_auth %q is not one of private_key_jwt, no_assertion, wrong_key, wrong_audience", c.ClientAuth)
	}
	if out.ExpectStatus == 0 {
		out.ExpectStatus = http.StatusOK
	}
	if strings.TrimSpace(out.Name) == "" {
		out.Name = out.Token + "/" + out.ClientAuth
	}
	return out, nil
}

// resolveIntrospect validates every case up front, so a mistyped selector fails
// the scenario before a login flow is driven for it.
func resolveIntrospect(cases []IntrospectCase) ([]IntrospectCase, error) {
	out := make([]IntrospectCase, 0, len(cases))
	for i, c := range cases {
		rc, err := c.resolve()
		if err != nil {
			return nil, fmt.Errorf("introspect case %d: %w", i+1, err)
		}
		out = append(out, rc)
	}
	return out, nil
}

// tokenSet is what a completed flow obtained, and the values an introspection
// response is cross-checked against.
type tokenSet struct {
	accessToken string
	idToken     string
	sub         string // sub of the id_token; "" when the flow returned none
	dpopJKT     string // thumbprint the access token is bound to; "" when unbound
}

// introspect runs every case against the introspection endpoint and returns the
// assertions they produced. It never returns an error: a transport failure or a
// missing endpoint is itself a failed assertion, so the row still reports that
// the login succeeded and names introspection as the part that did not.
func (r *Runner) introspect(ctx context.Context, calls *[]result.HTTPCall, cl *testClient, cases []IntrospectCase, tk tokenSet) []result.Assertion {
	if r.IntrospectEndpoint == "" {
		return []result.Assertion{{
			Field: "introspection endpoint", Expected: "advertised in discovery",
			Actual: "(absent) — this deployment publishes no introspection_endpoint", Passed: false,
		}}
	}
	var out []result.Assertion
	for _, c := range cases {
		out = append(out, r.introspectOne(ctx, calls, cl, c, tk)...)
	}
	return out
}

func (r *Runner) introspectOne(ctx context.Context, calls *[]result.HTTPCall, cl *testClient, c IntrospectCase, tk tokenSet) []result.Assertion {
	field := func(what string) string { return fmt.Sprintf("introspection [%s] %s", c.Name, what) }

	form, err := r.introspectForm(cl, c, tk)
	if err != nil {
		return []result.Assertion{{Field: field("request"), Expected: "built", Actual: err.Error(), Passed: false}}
	}
	headers := map[string]string{"Content-Type": "application/x-www-form-urlencoded"}
	status, rb, err := r.do(ctx, calls, "introspect ("+c.Name+")", http.MethodPost, r.IntrospectEndpoint, headers, form.Encode())
	if err != nil {
		return []result.Assertion{{Field: field("request"), Expected: "completed", Actual: err.Error(), Passed: false}}
	}

	out := []result.Assertion{{
		Field: field("HTTP status"), Expected: strconv.Itoa(c.ExpectStatus),
		Actual: strconv.Itoa(status), Passed: status == c.ExpectStatus,
	}}

	var body map[string]any
	if uerr := json.Unmarshal(rb, &body); uerr != nil {
		return append(out, result.Assertion{
			Field: field("response body"), Expected: "a JSON object",
			Actual: snippet(rb), Passed: false,
		})
	}

	if c.ExpectActive != nil {
		want := *c.ExpectActive
		got, isBool := body["active"].(bool)
		out = append(out, result.Assertion{
			Field: field("active"), Expected: strconv.FormatBool(want),
			Actual: memberString(body, "active"), Passed: isBool && got == want,
		})
	}
	if c.ExpectError != "" {
		got, _ := body["error"].(string)
		out = append(out, result.Assertion{
			Field: field("error"), Expected: c.ExpectError,
			Actual: firstNonEmpty(got, "(absent)"), Passed: got == c.ExpectError,
		})
	}

	subs := introspectPlaceholders(r, cl, tk)
	for _, path := range c.ExpectPresent {
		_, present := lookupMember(body, path)
		out = append(out, result.Assertion{
			Field: field(path), Expected: "present", Actual: memberString(body, path), Passed: present,
		})
	}
	for _, path := range sortedKeys(c.ExpectValues) {
		expected := substitute(c.ExpectValues[path], subs)
		v, present := lookupMember(body, path)
		out = append(out, result.Assertion{
			Field: field(path), Expected: expected, Actual: memberString(body, path),
			Passed: present && matchesMember(v, expected),
		})
	}
	for _, path := range c.ExpectAbsent {
		_, present := lookupMember(body, path)
		out = append(out, result.Assertion{
			Field: field(path), Expected: "absent", Actual: memberString(body, path), Passed: !present,
		})
	}

	// An active token must not already be expired: introspection reporting
	// active=true alongside a past exp would let a resource server accept a
	// token the authorization server itself considers dead.
	if c.ExpectActive != nil && *c.ExpectActive {
		if exp, ok := lookupMember(body, "exp"); ok {
			if secs, isNum := exp.(float64); isNum {
				at := time.Unix(int64(secs), 0).UTC()
				out = append(out, result.Assertion{
					Field: field("exp"), Expected: "in the future",
					Actual: at.Format(time.RFC3339), Passed: at.After(time.Now()),
				})
			}
		}
	}
	return out
}

// introspectForm builds the request body for one case.
func (r *Runner) introspectForm(cl *testClient, c IntrospectCase, tk tokenSet) (url.Values, error) {
	form := url.Values{}
	switch c.Token {
	case introspectAccessToken:
		form.Set("token", tk.accessToken)
	case introspectIDToken:
		if tk.idToken == "" {
			return nil, fmt.Errorf("case needs an id_token, but the token response carried none")
		}
		form.Set("token", tk.idToken)
	case introspectUnissued:
		form.Set("token", unissuedToken())
	case introspectNoToken:
		// The parameter is deliberately left out.
	}
	if c.Hint != "" {
		form.Set("token_type_hint", c.Hint)
	}

	// aud is the issuer, matching what the token call sends and what eSignet
	// validates a client assertion against.
	aud := r.Issuer
	if aud == "" {
		aud = r.IntrospectEndpoint
	}
	switch c.ClientAuth {
	case introspectAuthNoAssertion:
		form.Set("client_id", cl.clientID)
	case introspectAuthPrivateKeyJWT, introspectAuthWrongAudience:
		key := cl.priv
		if c.ClientAuth == introspectAuthWrongAudience {
			aud = wrongAudience
		}
		assertion, err := clientAssertion(key, cl.kid, cl.clientID, aud)
		if err != nil {
			return nil, fmt.Errorf("client assertion: %w", err)
		}
		setClientAuth(form, cl.clientID, assertion)
	case introspectAuthWrongKey:
		// A key the client never registered: correctly formed, correctly
		// audienced, and unverifiable against the registered JWK.
		other, err := generateRSA()
		if err != nil {
			return nil, err
		}
		assertion, err := clientAssertion(other, cl.kid, cl.clientID, aud)
		if err != nil {
			return nil, fmt.Errorf("client assertion: %w", err)
		}
		setClientAuth(form, cl.clientID, assertion)
	}
	return form, nil
}

// setClientAuth writes the private_key_jwt members onto a form.
func setClientAuth(form url.Values, clientID, assertion string) {
	form.Set("client_id", clientID)
	form.Set("client_assertion_type", clientAssertionTypeJWTBearer)
	form.Set("client_assertion", assertion)
}

// unissuedToken is a random opaque value this deployment cannot have minted.
// Opaque rather than a forged JWT on purpose: RFC 7662 s2.2 requires
// "active": false for any token the server does not recognise, whatever its shape.
func unissuedToken() string {
	raw := make([]byte, 32)
	_, _ = rand.Read(raw)
	return "unissued-" + b64(raw)
}

// introspectPlaceholders are the run-time values a spec file cannot know, so a
// case can assert "client_id is this flow's client" without naming a generated id.
func introspectPlaceholders(r *Runner, cl *testClient, tk tokenSet) map[string]string {
	return map[string]string{
		"{{client_id}}": cl.clientID,
		"{{issuer}}":    r.Issuer,
		"{{sub}}":       tk.sub,
		"{{dpop_jkt}}":  tk.dpopJKT,
	}
}

func substitute(s string, subs map[string]string) string {
	for k, v := range subs {
		s = strings.ReplaceAll(s, k, v)
	}
	return s
}

// lookupMember resolves a dotted path against a decoded JSON object.
func lookupMember(m map[string]any, path string) (any, bool) {
	var cur any = m
	for _, seg := range strings.Split(path, ".") {
		obj, ok := cur.(map[string]any)
		if !ok {
			return nil, false
		}
		cur, ok = obj[seg]
		if !ok {
			return nil, false
		}
	}
	return cur, true
}

// memberString renders a member for the report, or "(absent)" when the path
// resolves to nothing.
func memberString(m map[string]any, path string) string {
	v, ok := lookupMember(m, path)
	if !ok {
		return "(absent)"
	}
	return introspectValue(v)
}

// matchesMember compares a response member against an expected string. An array
// matches when any element does, because RFC 7662 lets aud be either a single
// string or a list and a case should not have to know which one it gets.
func matchesMember(v any, expected string) bool {
	if arr, ok := v.([]any); ok {
		for _, e := range arr {
			if introspectValue(e) == expected {
				return true
			}
		}
		return false
	}
	return introspectValue(v) == expected
}

// introspectValue renders a JSON member for the report. Numbers are printed as
// integers when they are whole, so an exp reads 1735689600 rather than 1.7356896e+09.
func introspectValue(v any) string {
	switch t := v.(type) {
	case nil:
		return "null"
	case string:
		return t
	case bool:
		return strconv.FormatBool(t)
	case float64:
		if t == math.Trunc(t) && math.Abs(t) < 1e15 {
			return strconv.FormatInt(int64(t), 10)
		}
		return strconv.FormatFloat(t, 'f', -1, 64)
	default:
		b, err := json.Marshal(t)
		if err != nil {
			return fmt.Sprintf("%v", t)
		}
		return string(b)
	}
}

// sortedKeys orders a map's keys, so the assertion trace comes out in the same
// sequence on every run rather than in Go's randomized map order.
func sortedKeys(m map[string]string) []string {
	keys := make([]string, 0, len(m))
	for k := range m {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return keys
}
