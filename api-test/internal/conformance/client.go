// Package conformance is a thin client over the OpenID Conformance Suite's container API.
package conformance

import (
	"bytes"
	"context"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"net/url"
	"regexp"
	"strings"
	"time"

	"github.com/mosip/esignet/api-test/internal/httpx"
	"github.com/mosip/esignet/api-test/internal/result"
	"github.com/mosip/esignet/api-test/internal/textx"
)

// maxRespBytes caps a single response read. Sized for the suite's /api/log
// dumps, which are the largest thing this client legitimately fetches.
const maxRespBytes = 64 << 20

type Client struct {
	baseURL string
	token   string
	http    *http.Client
	calls   []result.HTTPCall // accumulated call trace; drained via TakeCalls
}

func New(baseURL, token string, tlsVerify bool, timeout time.Duration) *Client {
	return &Client{
		baseURL: strings.TrimRight(baseURL, "/"),
		token:   token,
		http:    httpx.NewClient(tlsVerify, timeout),
	}
}

// TakeCalls returns and clears the accumulated call trace (so the orchestrator
// can attach run-level calls to a setup section and per-module calls to a module).
func (c *Client) TakeCalls() []result.HTTPCall {
	out := c.calls
	c.calls = nil
	return out
}

// ----- API response shapes (accessed defensively; schema drifts across suite releases) -----

type Module struct {
	TestModule string         `json:"testModule"`
	Variant    map[string]any `json:"variant"`
}

type PlanResponse struct {
	ID      string   `json:"id"`
	Name    string   `json:"name"`
	Modules []Module `json:"modules"`
}

type TestResponse struct {
	Name string `json:"name"`
	ID   string `json:"id"`
	URL  string `json:"url"`
}

type Info struct {
	Status string `json:"status"`
	Result string `json:"result"`
}

type Runner struct {
	Exposed map[string]any `json:"exposed"`
	Browser Browser        `json:"browser"`
}

type Browser struct {
	URLs    []string `json:"urls"`
	Visited []string `json:"visited"`
}

// Available polls GET /api/runner/available (200 => suite ready).
func (c *Client) Available(ctx context.Context) error {
	body, status, err := c.do(ctx, "suite /api/runner/available", http.MethodGet, "/api/runner/available", nil, "")
	if err != nil {
		return err
	}
	if status != http.StatusOK {
		return fmt.Errorf("suite not available: HTTP %d: %s", status, snippet(body))
	}
	return nil
}

// CreatePlan POSTs the exported suite config as the body and the variant as a
// url-encoded query param, returning the planId + module list.
func (c *Client) CreatePlan(ctx context.Context, planName string, variant map[string]any, configBody []byte) (*PlanResponse, error) {
	variantJSON, err := json.Marshal(variant)
	if err != nil {
		return nil, fmt.Errorf("marshal variant: %w", err)
	}
	q := url.Values{}
	q.Set("planName", planName)
	q.Set("variant", string(variantJSON))
	path := "/api/plan?" + q.Encode()

	body, status, err := c.do(ctx, "suite /api/plan (create plan)", http.MethodPost, path, configBody, "application/json")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK && status != http.StatusCreated {
		return nil, fmt.Errorf("create plan HTTP %d: %s", status, snippet(body))
	}
	var pr PlanResponse
	if err := json.Unmarshal(body, &pr); err != nil {
		return nil, fmt.Errorf("parse plan response: %w (%s)", err, snippet(body))
	}
	if pr.ID == "" {
		return nil, fmt.Errorf("create plan: empty planId in response: %s", snippet(body))
	}
	return &pr, nil
}

// CreateTest starts one module (no body) and returns its testId.
func (c *Client) CreateTest(ctx context.Context, module, planID string) (*TestResponse, error) {
	q := url.Values{}
	q.Set("test", module)
	q.Set("plan", planID)
	path := "/api/runner?" + q.Encode()

	body, status, err := c.do(ctx, "suite /api/runner (start "+module+")", http.MethodPost, path, nil, "")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK && status != http.StatusCreated {
		return nil, fmt.Errorf("create test %s HTTP %d: %s", module, status, snippet(body))
	}
	var tr TestResponse
	if err := json.Unmarshal(body, &tr); err != nil {
		return nil, fmt.Errorf("parse test response: %w (%s)", err, snippet(body))
	}
	if tr.ID == "" {
		return nil, fmt.Errorf("create test %s: empty testId: %s", module, snippet(body))
	}
	return &tr, nil
}

func (c *Client) GetInfo(ctx context.Context, testID string) (*Info, error) {
	body, status, err := c.do(ctx, "suite /api/info", http.MethodGet, "/api/info/"+testID, nil, "")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, fmt.Errorf("get info HTTP %d: %s", status, snippet(body))
	}
	var i Info
	if err := json.Unmarshal(body, &i); err != nil {
		return nil, fmt.Errorf("parse info: %w (%s)", err, snippet(body))
	}
	return &i, nil
}

func (c *Client) GetRunner(ctx context.Context, testID string) (*Runner, error) {
	body, status, err := c.do(ctx, "suite /api/runner (browser urls)", http.MethodGet, "/api/runner/"+testID, nil, "")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, fmt.Errorf("get runner HTTP %d: %s", status, snippet(body))
	}
	var r Runner
	if err := json.Unmarshal(body, &r); err != nil {
		return nil, fmt.Errorf("parse runner: %w (%s)", err, snippet(body))
	}
	return &r, nil
}

// GetRawLog returns the per-test condition log as raw objects, preserving every field for the report.
func (c *Client) GetRawLog(ctx context.Context, testID string) ([]map[string]any, error) {
	body, status, err := c.do(ctx, "suite /api/log", http.MethodGet, "/api/log/"+testID, nil, "")
	if err != nil {
		return nil, err
	}
	if status != http.StatusOK {
		return nil, fmt.Errorf("get log HTTP %d: %s", status, snippet(body))
	}
	var entries []map[string]any
	if err := json.Unmarshal(body, &entries); err != nil {
		return nil, fmt.Errorf("parse log: %w (%s)", err, snippet(body))
	}
	return entries, nil
}

// DeliverResult reports the two-step hand-off back to the suite. (The captured
// HTTP calls flow through the client's shared trace, drained via TakeCalls.)
type DeliverResult struct {
	SuiteCallbackStatus  int
	ImplicitSubmitStatus int
	ImplicitSubmitURL    string
}

var implicitSubmitRe = regexp.MustCompile(`"implicitSubmitUrl"\s*:\s*"([^"]+)"`)
var implicitPathRe = regexp.MustCompile(`https?://[^"'\s]+/implicit/[^"'\s]+`)

// DeliverCallback performs the suite's two-request hand-off: GET the redirect_uri, then POST implicitSubmitUrl.
func (c *Client) DeliverCallback(ctx context.Context, redirectURI string) (DeliverResult, error) {
	var res DeliverResult

	body, status, err := c.doAbs(ctx, "suite callback (deliver code)", http.MethodGet, redirectURI, nil, "")
	res.SuiteCallbackStatus = status
	if err != nil {
		return res, fmt.Errorf("deliver code (GET callback): %w", err)
	}

	submitURL := parseImplicitSubmitURL(string(body))
	res.ImplicitSubmitURL = submitURL
	if submitURL == "" {
		return res, fmt.Errorf("could not find implicitSubmitUrl in callback response (status %d)", status)
	}

	sbody, sstatus, err := c.doAbs(ctx, "suite implicit-submit", http.MethodPost, submitURL, []byte{}, "text/plain")
	res.ImplicitSubmitStatus = sstatus
	if err != nil {
		return res, fmt.Errorf("implicit submit POST: %w", err)
	}
	if sstatus != http.StatusNoContent && sstatus != http.StatusOK {
		return res, fmt.Errorf("implicit submit HTTP %d: %s", sstatus, snippet(sbody))
	}
	return res, nil
}

func parseImplicitSubmitURL(html string) string {
	// Older suites: a JSON field "implicitSubmitUrl": "https://…".
	if m := implicitSubmitRe.FindStringSubmatch(html); len(m) == 2 {
		return strings.ReplaceAll(m[1], "\\/", "/")
	}
	// Current suite: the URL is a JS string in an xhr.open("POST", …) call with backslash-escaped slashes.
	un := strings.ReplaceAll(html, "\\/", "/")
	if m := implicitPathRe.FindString(un); m != "" {
		return m
	}
	return ""
}

// ----- low-level helpers -----

func (c *Client) do(ctx context.Context, label, method, path string, body []byte, contentType string) ([]byte, int, error) {
	return c.doAbs(ctx, label, method, c.baseURL+path, body, contentType)
}

// isSuiteHost reports whether u addresses the configured conformance suite.
func (c *Client) isSuiteHost(u *url.URL) bool {
	base, err := url.Parse(c.baseURL)
	if err != nil {
		return false
	}
	return strings.EqualFold(u.Scheme, base.Scheme) && strings.EqualFold(u.Host, base.Host)
}

// doAbs performs the request and records it (headers/cookies/bodies) into the
// client's call trace for the debug report.
func (c *Client) doAbs(ctx context.Context, label, method, absURL string, body []byte, contentType string) ([]byte, int, error) {
	var rdr io.Reader
	if body != nil {
		rdr = bytes.NewReader(body)
	}
	// Seq is assigned up front so the error paths below record an ordered row too.
	call := result.HTTPCall{Seq: len(c.calls) + 1, At: time.Now().UnixNano(), Label: label, Method: method, URL: absURL, ReqBody: string(body)}
	req, err := http.NewRequestWithContext(ctx, method, absURL, rdr)
	if err != nil {
		call.RespBody = "ERROR: " + err.Error()
		c.calls = append(c.calls, call)
		return nil, 0, err
	}
	if contentType != "" {
		req.Header.Set("Content-Type", contentType)
	}
	// The token authenticates us to the conformance suite only.
	if c.token != "" && c.isSuiteHost(req.URL) {
		req.Header.Set("Authorization", "Bearer "+c.token)
	}
	call.ReqHeaders = httpx.CloneHeader(req.Header)
	// Redact at the capture point, not downstream: these calls are dropped from
	// the report today, but the suite admin token must never depend on that.
	if _, ok := call.ReqHeaders["Authorization"]; ok {
		call.ReqHeaders["Authorization"] = []string{"***redacted***"}
	}
	resp, err := c.http.Do(req)
	if err != nil {
		call.RespBody = "ERROR: " + err.Error()
		c.calls = append(c.calls, call)
		return nil, 0, err
	}
	defer func() { _ = resp.Body.Close() }()
	// Generous, because the suite's /api/log dumps are large — but not unbounded:
	// DeliverCallback sends this at absolute URLs taken from the deployment under
	// test, and a host on that path could otherwise stream until the harness dies.
	rb, _ := io.ReadAll(io.LimitReader(resp.Body, maxRespBytes))
	call.Status = resp.StatusCode
	call.RespHeaders = httpx.CloneHeader(resp.Header)
	call.RespCookies = strings.Join(resp.Header["Set-Cookie"], "\n")
	call.RespBody = string(rb)
	c.calls = append(c.calls, call)
	return rb, resp.StatusCode, nil
}

// snippet cuts to at most 400 bytes for an error message.
func snippet(b []byte) string {
	return textx.Truncate(strings.TrimSpace(string(b)), 400, "…")
}
