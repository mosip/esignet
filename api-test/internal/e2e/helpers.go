package e2e

import (
	"encoding/json"
	"fmt"
	"net/url"
	"sort"
	"strings"
	"unicode/utf8"

	"github.com/mosip/esignet/api-test/internal/result"
)

// parseUserinfo turns a userinfo response into a claims map. eSignet may return
// it as plain JSON or as a signed JWT (JWS RS256); handle both. When it is a JWS,
// verify the signature against the published JWKS (a real E2E check) but don't
// fail claim extraction if verification can't run — record it as a claim instead.
func parseUserinfo(body []byte, jwksURI string, tlsVerify bool) (map[string]any, error) {
	s := strings.TrimSpace(string(body))
	if strings.HasPrefix(s, "{") {
		var m map[string]any
		if err := json.Unmarshal(body, &m); err != nil {
			return nil, err
		}
		return m, nil
	}
	// Treat as compact JWS.
	claims, err := decodeJWTPayload(s)
	if err != nil {
		return nil, fmt.Errorf("userinfo is neither JSON nor JWT: %w", err)
	}
	if jwksURI != "" {
		if verr := verifyJWS(s, jwksURI, tlsVerify); verr != nil {
			claims["_jws_verified"] = false
			claims["_jws_error"] = verr.Error()
		} else {
			claims["_jws_verified"] = true
		}
	}
	return claims, nil
}

// assertClaims checks the scenario's expectations against the userinfo claims
// and returns the full expected-vs-actual trace — one Assertion per check,
// whether it passed or failed — for the report's Validation tab. Field
// validation, not just the HTTP/status-code level.
func assertClaims(sc Scenario, claims map[string]any) []result.Assertion {
	var out []result.Assertion
	// A signed userinfo response is the in-transit integrity evidence this
	// surface exists to produce, so the verification result is a first-class
	// assertion — recording it only as a claim would let a JWT signed by an
	// unknown key still report PASSED.
	if v, ok := claims["_jws_verified"]; ok {
		verified, _ := v.(bool)
		actual := "signature verified against JWKS"
		if !verified {
			actual = fmt.Sprintf("verification failed: %v", claims["_jws_error"])
		}
		out = append(out, result.Assertion{
			Field: "userinfo JWS signature", Expected: "verified against JWKS",
			Actual: actual, Passed: verified,
		})
	}
	actualOf := func(k string) string {
		if !claimPresent(claims, k) {
			return "(absent)"
		}
		return fmt.Sprintf("%v", claims[k])
	}
	for _, k := range sc.ExpectPresent {
		present := claimPresent(claims, k)
		actual := actualOf(k)
		if !present {
			actual = fmt.Sprintf("(absent) — userinfo returned: [%s]", presentKeys(claims))
		}
		out = append(out, result.Assertion{
			Field: "userinfo claim " + k, Expected: "present", Actual: actual, Passed: present,
		})
	}
	for k, want := range sc.ExpectValues {
		got := actualOf(k)
		out = append(out, result.Assertion{
			Field: "userinfo claim " + k, Expected: want, Actual: got, Passed: claimPresent(claims, k) && got == want,
		})
	}
	for _, k := range sc.ExpectAbsent {
		absent := !claimPresent(claims, k)
		out = append(out, result.Assertion{
			Field: "userinfo claim " + k, Expected: "absent", Actual: actualOf(k), Passed: absent,
		})
	}
	return out
}

// failedConditions converts the failing entries of an Assertion trace into
// Conditions, for the legacy "failure finding(s)" summary alongside the new
// per-field Validation tab.
func failedConditions(assertions []result.Assertion) []result.Condition {
	var conds []result.Condition
	for _, a := range assertions {
		if !a.Passed {
			conds = append(conds, result.Condition{
				Src: "userinfo", Result: "FAILURE",
				Msg: fmt.Sprintf("%s: expected %q, got %q", a.Field, a.Expected, a.Actual),
			})
		}
	}
	return conds
}

func claimPresent(claims map[string]any, k string) bool {
	v, ok := claims[k]
	if !ok {
		return false
	}
	if s, isStr := v.(string); isStr && s == "" {
		return false
	}
	return v != nil
}

func presentKeys(claims map[string]any) string {
	keys := make([]string, 0, len(claims))
	for k := range claims {
		if strings.HasPrefix(k, "_") { // internal markers
			continue
		}
		keys = append(keys, k)
	}
	sort.Strings(keys)
	return strings.Join(keys, ", ")
}

// codeFromRedirect extracts the authorization code from the callback redirect_uri
// eSignet returns (…redirect_uri?code=…&state=…).
func codeFromRedirect(redirectURI string) (string, error) {
	u, err := url.Parse(redirectURI)
	if err != nil {
		return "", fmt.Errorf("parse redirect_uri: %w", err)
	}
	if e := u.Query().Get("error"); e != "" {
		return "", fmt.Errorf("authorize returned error=%s %s", e, u.Query().Get("error_description"))
	}
	code := u.Query().Get("code")
	if code == "" {
		return "", fmt.Errorf("no code in redirect_uri %q", redirectURI)
	}
	return code, nil
}

// firstErrorCode returns the first MOSIP errors[].errorCode in a response body,
// or "" if there is none (i.e. the call succeeded).
func firstErrorCode(body []byte) string {
	var env struct {
		Errors []struct {
			ErrorCode string `json:"errorCode"`
		} `json:"errors"`
	}
	if json.Unmarshal(body, &env) == nil && len(env.Errors) > 0 {
		return env.Errors[0].ErrorCode
	}
	return ""
}

func firstNonEmpty(vals ...string) string {
	for _, v := range vals {
		if strings.TrimSpace(v) != "" {
			return strings.TrimSpace(v)
		}
	}
	return ""
}

func snippet(b []byte) string {
	const n = 300
	if len(b) <= n {
		return string(b)
	}
	cut := n
	for cut > 0 && !utf8.RuneStart(b[cut]) {
		cut--
	}
	return string(b[:cut]) + "…"
}
