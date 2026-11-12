package e2e

import (
	"encoding/base64"
	"encoding/json"
	"fmt"
	"regexp"
	"strings"

	"github.com/mosip/esignet/api-test/internal/result"
)

// mdsCallPrefix marks the trace entries of the mock MDS itself, whose responses are the captures and so are not evidence of an echo.
const mdsCallPrefix = "mock MDS"

// minFragment is the shortest piece of a capture searched for: long enough that a match cannot be chance.
const minFragment = 32

var jwtRe = regexp.MustCompile(`eyJ[A-Za-z0-9_-]+\.[A-Za-z0-9_-]+\.[A-Za-z0-9_-]*`)

// assertCaptureNotEchoed checks that no fragment of any submitted capture appears in any eSignet response, including inside the JWTs those responses carry (flow assertion, ID token, access token, signed userinfo).
func assertCaptureNotEchoed(calls []result.HTTPCall, captures []string) result.Assertion {
	a := result.Assertion{Field: "biometric capture echo", Expected: "no part of the capture in any response, token or userinfo"}
	fragments := captureFragments(captures)
	if len(fragments) == 0 {
		a.Actual = "no capture was submitted to check"
		return a
	}
	for _, c := range calls {
		if strings.HasPrefix(c.Label, mdsCallPrefix) {
			continue
		}
		haystacks := []string{c.RespBody}
		for _, tok := range jwtRe.FindAllString(c.RespBody, -1) {
			if payload, err := base64.RawURLEncoding.DecodeString(strings.Split(tok, ".")[1]); err == nil {
				haystacks = append(haystacks, string(payload))
			}
		}
		for _, h := range haystacks {
			for _, f := range fragments {
				if strings.Contains(h, f) {
					a.Actual = fmt.Sprintf("found in the %q response", c.Label)
					return a
				}
			}
		}
	}
	a.Actual = fmt.Sprintf("absent (%d fragments checked across %d calls)", len(fragments), len(calls))
	a.Passed = true
	return a
}

// captureFragments returns searchable pieces of each capture: its encoded form and each record's hash, session key and signed-data segments.
func captureFragments(captures []string) []string {
	var out []string
	add := func(s string) {
		if len(s) >= minFragment {
			out = append(out, s[:minFragment])
		}
	}
	for _, c := range captures {
		add(c)
		raw, err := base64.StdEncoding.DecodeString(c)
		if err != nil {
			continue
		}
		var records []map[string]any
		if json.Unmarshal(raw, &records) != nil {
			continue
		}
		for _, rec := range records {
			for _, k := range []string{"hash", "sessionKey"} {
				if s, ok := rec[k].(string); ok {
					add(s)
				}
			}
			// Payload and signature only: the JWS header is shared with every other RS256 JWT carrying an x5c, so it would match innocently.
			if s, ok := rec["data"].(string); ok {
				if segs := strings.Split(s, "."); len(segs) == 3 {
					add(segs[1])
					add(segs[2])
					// The biometric itself, in case a response carries it decoded from the payload rather than as the encoded segment.
					var payload struct {
						BioValue string `json:"bioValue"`
					}
					if p, err := base64.RawURLEncoding.DecodeString(segs[1]); err == nil && json.Unmarshal(p, &payload) == nil {
						add(payload.BioValue)
					}
				}
			}
		}
	}
	return out
}
