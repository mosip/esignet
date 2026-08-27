// Package report renders the consolidated, self-contained HTML report plus a sidecar JSON dump.
package report

import (
	"encoding/json"
	"fmt"
	"html/template"
	"net/url"
	"os"
	"path/filepath"
	"slices"
	"sort"
	"strings"
	"time"

	"github.com/mosip/esignet/api-test/internal/result"
	"github.com/mosip/esignet/api-test/internal/textx"
)

type view struct {
	Plan        string
	Provider    string
	Generated   string
	ConfigJSON  string
	PlanConfigs []PlanConfig
	ShowSecrets bool           // wire trace left unredacted (run.debug_show_secrets)
	Summary     result.Summary // overall roll-up across every surface
	Surfaces    []surfaceView  // one section per surface/plan (plan doc §6)
}

// surfaceView is one surface's section: its label, own tile counts, and rows.
type surfaceView struct {
	Name    string // conformance | client-mgmt | flow-execute
	Label   string // human label for the section heading
	Anchor  string // in-page anchor id
	Summary result.Summary
	Rows    []rowView
}

// surfaceLabels gives each surface a readable section heading.
var surfaceLabels = map[string]string{
	result.SurfaceConformance: "Conformance (OpenID suite)",
	result.SurfaceClientMgmt:  "Client management (godog)",
	result.SurfaceFlowExecute: "flow/execute — negative/edge (godog)",
	result.SurfaceE2E:         "End-to-end (create client → userinfo claims)",
}

type rowView struct {
	Index          int
	Module         string
	TestID         string
	Variant        string
	Result         string
	ResultClass    string
	OutcomeDetail  string
	Duration       string
	FailedCount    int
	CallCount      int
	LogCount       int
	Conditions     []result.Condition
	Log            []logView
	Flow           result.FlowTrace
	Calls          []callView
	HarnessError   string
	Assertions     []assertionView
	AssertionCount int
	AssertionFails int
}

// assertionView is one expected-vs-actual check, rendered in the per-testcase Validation tab.
type assertionView struct {
	Field       string
	Expected    string
	Actual      string
	Passed      bool
	ResultLabel string // PASS | FAIL
	ResultClass string // pass | fail
}

type logView struct {
	Time        string
	Src         string
	Msg         string
	Kind        string
	KindClass   string
	Requirement string
	Block       bool
	Details     []detailView
	MoreN       int
}

type detailView struct {
	Key   string
	Value string
}

type callView struct {
	Seq          int
	Repeat       int
	Label        string
	Method       string
	URL          string
	QueryDecoded string // query params with percent-encoding undone, one "key = value" per line; empty when the URL has no query
	Status       int
	StatusClass  string
	ReqHeaders   string
	ReqCookies   string
	ReqBody      string
	RespHeaders  string
	RespCookies  string
	RespBody     string
}

// PlanConfig is one plan's redacted suite config, shown in the report's configuration panel.
type PlanConfig struct {
	Plan string
	JSON string
}

// Options is one report's input.
type Options struct {
	Dir         string
	Plans       []string
	Provider    string
	ConfigJSON  string // redacted effective harness config
	PlanConfigs []PlanConfig
	// ShowSecrets leaves the captured eSignet wire trace unredacted for local debugging.
	ShowSecrets bool
	Results     []result.ModuleResult
}

// Write emits <dir>/<plan>_<provider>_<ts>_t-<n>_p-<n>_f-<n>_sk-<n>_ki-<n>.{html,json}
// and returns the HTML path. f is every module that did not pass — errored
// included — so p+f+sk+ki always sums to t.
func Write(o Options) (string, error) {
	dir := o.Dir
	// Owner-only: the report and its sidecar carry the userinfo claims this
	// surface retains as evidence, and ShowSecrets adds the unredacted wire trace.
	if err := os.MkdirAll(dir, 0o750); err != nil {
		return "", fmt.Errorf("mkdir %s: %w", dir, err)
	}
	results := o.Results
	showSecrets := o.ShowSecrets
	if !showSecrets {
		results = redactCallBodies(results)
	}
	sum := result.Summarize(results)
	provider := o.Provider
	if provider == "" {
		provider = "na"
	}
	plan := planLabel(o.Plans)
	ts := time.Now().Format("20060102-150405")
	// p/f/sk/ki always account for the whole run: f is Summary.NotPassed, which
	// carries the errored modules too rather than leaving them unnamed.
	base := fmt.Sprintf("%s_%s_%s_t-%d_p-%d_f-%d_sk-%d_ki-%d", surfaceSlug(results), provider, ts, sum.Total, sum.Passed, sum.NotPassed(), sum.Skipped, sum.Known)
	// Never overwrite an existing report: if a same-second run produced the same
	// counts, add a numeric suffix so both reports are kept.
	stem := base
	for i := 2; fileExists(filepath.Join(dir, base+".html")); i++ {
		base = fmt.Sprintf("%s-%d", stem, i)
	}

	// plan_configs is a list; the old single-plan key is still written when there is exactly one.
	planConfigs := make([]map[string]any, 0, len(o.PlanConfigs))
	for _, pc := range o.PlanConfigs {
		planConfigs = append(planConfigs, map[string]any{
			"plan":   pc.Plan,
			"config": json.RawMessage(safeJSON(pc.JSON)),
		})
	}
	sidecar := map[string]any{
		"config":       json.RawMessage(safeJSON(o.ConfigJSON)),
		"plan_configs": planConfigs,
		"modules":      results,
	}
	if len(o.PlanConfigs) == 1 {
		sidecar["plan_config"] = json.RawMessage(safeJSON(o.PlanConfigs[0].JSON))
	}
	// The consolidation runner derives this sidecar's path from the HTML name, so report a write failure.
	data, err := json.MarshalIndent(sidecar, "", "  ")
	if err != nil {
		return "", fmt.Errorf("marshal sidecar: %w", err)
	}
	if err := os.WriteFile(filepath.Join(dir, base+".json"), data, 0o600); err != nil {
		return "", fmt.Errorf("write sidecar: %w", err)
	}

	v := view{
		Plan:        plan,
		Provider:    provider,
		Generated:   time.Now().Format("2006-01-02 15:04:05 MST"),
		ConfigJSON:  o.ConfigJSON,
		PlanConfigs: o.PlanConfigs,
		ShowSecrets: showSecrets,
		Summary:     sum,
	}
	// Name the plan in a section heading only when the run covered more than one:
	// a single-plan report already says which plan in its header.
	multiPlan := len(o.Plans) > 1
	for _, g := range result.GroupBySurface(results) {
		label := surfaceLabels[g.Surface]
		if label == "" {
			label = g.Surface
		}
		anchor := "surface-" + g.Surface
		if multiPlan && g.Plan != "" {
			label += " — " + g.Plan
			anchor += "-" + slug(g.Plan)
		}
		v.Surfaces = append(v.Surfaces, surfaceView{
			Name:    g.Surface,
			Label:   label,
			Anchor:  anchor,
			Summary: g.Summary,
			Rows:    toRowViews(g.Rows),
		})
	}

	htmlPath := filepath.Join(dir, base+".html")
	f, err := os.OpenFile(htmlPath, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o600)
	if err != nil {
		return "", fmt.Errorf("create %s: %w", htmlPath, err)
	}
	if err := tmpl.Execute(f, v); err != nil {
		_ = f.Close()
		return "", fmt.Errorf("render report: %w", err)
	}
	// Closed explicitly: a disk-full or buffered-write failure only surfaces
	// here, and a truncated report must not be returned as a good one.
	if err := f.Close(); err != nil {
		return "", fmt.Errorf("close %s: %w", htmlPath, err)
	}
	return htmlPath, nil
}

func fileExists(p string) bool {
	_, err := os.Stat(p)
	return err == nil
}

// planLabel is the header text for the plans a run covered: all of them, joined,
// so the report says up front that it holds two plans' results.
func planLabel(plans []string) string {
	switch len(plans) {
	case 0:
		return "no plan"
	case 1:
		return plans[0]
	default:
		return strings.Join(plans, " + ")
	}
}

// filenameSurfaces maps a row's surface to its filename name, in the order surfaces run.
var filenameSurfaces = []struct{ surface, part string }{
	{result.SurfaceConformance, "conformance"},
	{result.SurfaceClientMgmt, "api"},
	{result.SurfaceFlowExecute, "api"},
	{result.SurfaceE2E, "e2e"},
}

// surfaceSlug is the leading filename part: the surfaces this report actually covers.
func surfaceSlug(results []result.ModuleResult) string {
	present := map[string]bool{}
	for _, r := range results {
		s := r.Surface
		if s == "" {
			s = result.SurfaceConformance
		}
		present[s] = true
	}

	var parts []string
	seen := map[string]bool{}
	for _, fs := range filenameSurfaces {
		if present[fs.surface] && !seen[fs.part] {
			seen[fs.part] = true
			parts = append(parts, fs.part)
		}
		delete(present, fs.surface)
	}
	// Anything unrecognised still gets named rather than silently dropped, so an
	// added surface cannot produce two reports that differ only by timestamp.
	var extra []string
	for s := range present {
		extra = append(extra, slug(s))
	}
	sort.Strings(extra)
	parts = append(parts, extra...)

	if len(parts) == 0 {
		return "run"
	}
	return strings.Join(parts, "_")
}

// slug reduces a plan name to filename/anchor-safe characters.
func slug(s string) string {
	var b strings.Builder
	for _, r := range s {
		switch {
		case r >= 'a' && r <= 'z', r >= 'A' && r <= 'Z', r >= '0' && r <= '9', r == '-', r == '_':
			b.WriteRune(r)
		default:
			b.WriteByte('-')
		}
	}
	out := strings.Trim(b.String(), "-")
	if out == "" {
		return "plan"
	}
	return out
}

// safeJSON returns s if it is valid JSON, else a JSON string wrapping it, so the
// sidecar always marshals.
func safeJSON(s string) []byte {
	if json.Valid([]byte(s)) {
		return []byte(s)
	}
	b, _ := json.Marshal(s) //nolint:errchkjson // marshalling a string cannot fail
	return b
}

// toRowViews builds the per-module row views for one surface's rows.
func toRowViews(rows []result.ModuleResult) []rowView {
	var out []rowView
	for i, r := range rows {
		rv := rowView{
			Index:         i + 1,
			Module:        r.Module,
			TestID:        r.TestID,
			Variant:       variantStr(r.Variant),
			Result:        displayResult(r),
			ResultClass:   resultClass(r),
			OutcomeDetail: r.OutcomeDetail,
			Duration:      fmt.Sprintf("%.1fs", float64(r.DurationMs)/1000),
			FailedCount:   len(r.FailedConditions),
			CallCount:     len(r.Calls),
			LogCount:      len(r.LogItems),
			Conditions:    r.FailedConditions,
			Log:           toLogViews(r.LogItems),
			Flow:          r.FlowTrace,
			HarnessError:  r.HarnessError,
		}
		rv.Calls = toCallViews(r.Calls)
		rv.Assertions, rv.AssertionFails = toAssertionViews(r.Assertions)
		rv.AssertionCount = len(rv.Assertions)
		out = append(out, rv)
	}
	return out
}

// toAssertionViews builds the Validation-tab rows and counts how many failed.
func toAssertionViews(items []result.Assertion) ([]assertionView, int) {
	var out []assertionView
	fails := 0
	for _, a := range items {
		av := assertionView{Field: a.Field, Expected: a.Expected, Actual: a.Actual, Passed: a.Passed}
		if a.Passed {
			av.ResultLabel, av.ResultClass = "PASS", "pass"
		} else {
			av.ResultLabel, av.ResultClass = "FAIL", "fail"
			fails++
		}
		out = append(out, av)
	}
	return out, fails
}

func toLogViews(items []result.LogItem) []logView {
	var out []logView
	for _, it := range items {
		lv := logView{
			Time:        it.Time,
			Src:         it.Src,
			Msg:         truncate(it.Msg, 2000),
			Kind:        it.Kind,
			KindClass:   kindClass(it.Kind),
			Requirement: it.Requirement,
			Block:       it.Block,
			MoreN:       it.MoreN,
		}
		for _, d := range it.Details {
			lv.Details = append(lv.Details, detailView{Key: d.Key, Value: d.Value})
		}
		out = append(out, lv)
	}
	return out
}

func toCallViews(calls []result.HTTPCall) []callView {
	var out []callView
	for j, c := range calls {
		out = append(out, callView{
			Seq:          j + 1,
			Repeat:       c.Repeat,
			Label:        c.Label,
			Method:       c.Method,
			URL:          c.URL,
			QueryDecoded: decodedQuery(c.URL),
			Status:       c.Status,
			StatusClass:  statusClass(c.Status),
			ReqHeaders:   headerStr(c.ReqHeaders),
			ReqCookies:   c.ReqCookies,
			ReqBody:      truncate(c.ReqBody, 4000),
			RespHeaders:  headerStr(c.RespHeaders),
			RespCookies:  c.RespCookies,
			RespBody:     truncate(c.RespBody, 8000),
		})
	}
	return out
}

func displayResult(r result.ModuleResult) string {
	if r.HarnessError != "" {
		return "HARNESS ERROR"
	}
	if r.HarnessOutcome == result.OutcomeKnownIssue {
		return "KNOWN ISSUE"
	}
	if r.HarnessOutcome == result.OutcomeSkippedByHarness {
		return "SKIPPED (harness)"
	}
	if r.HarnessOutcome == result.OutcomeEnvNotReady {
		return "ENV_NOT_READY"
	}
	if r.Result == "" {
		return r.Status
	}
	return r.Result
}

func resultClass(r result.ModuleResult) string {
	if r.HarnessError != "" {
		return "err"
	}
	if r.HarnessOutcome == result.OutcomeKnownIssue {
		return "known"
	}
	switch r.Result {
	case "PASSED":
		return "pass"
	case "FAILED":
		return "fail"
	case "WARNING":
		return "warn"
	case "REVIEW":
		return "review"
	case "SKIPPED":
		return "skip"
	}
	if r.HarnessOutcome == result.OutcomeSkippedByHarness {
		return "skip"
	}
	return "err"
}

func statusClass(code int) string {
	switch {
	case code == 0:
		return "err"
	case code >= 200 && code < 300:
		return "pass"
	case code >= 300 && code < 400:
		return "warn"
	default:
		return "fail"
	}
}

// kindClass maps a suite log entry's kind to a colour class.
func kindClass(kind string) string {
	switch strings.ToUpper(kind) {
	case "SUCCESS":
		return "pass"
	case "FAILURE":
		return "fail"
	case "WARNING":
		return "warn"
	case "REVIEW":
		return "review"
	case "REQUEST", "RESPONSE":
		return "req"
	default:
		return "info"
	}
}

// sensitiveHeaders carry credentials or session identifiers. Reports are archived
// as CI artifacts, so their values are masked rather than shown for debugging.
var sensitiveHeaders = []string{"Authorization", "Proxy-Authorization", "Cookie", "Set-Cookie", "X-API-KEY", "Api-Key"}

func isSensitiveHeader(k string) bool {
	for _, s := range sensitiveHeaders {
		if strings.EqualFold(k, s) {
			return true
		}
	}
	return false
}

// redactHeaderMap copies a captured header map with credential- and session-bearing values masked.
func redactHeaderMap(h map[string][]string) map[string][]string {
	if len(h) == 0 {
		return h
	}
	out := make(map[string][]string, len(h))
	for k, v := range h {
		if isSensitiveHeader(k) {
			out[k] = []string{"***redacted***"}
			continue
		}
		out[k] = slices.Clone(v)
	}
	return out
}

// headerStr renders a header map as sorted "Key: value" lines, masking the
// credential- and session-bearing headers.
func headerStr(h map[string][]string) string {
	if len(h) == 0 {
		return ""
	}
	keys := make([]string, 0, len(h))
	for k := range h {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	var b strings.Builder
	for _, k := range keys {
		vals := h[k]
		if isSensitiveHeader(k) {
			vals = []string{"***redacted***"}
		}
		for _, v := range vals {
			fmt.Fprintf(&b, "%s: %s\n", k, v)
		}
	}
	return strings.TrimRight(b.String(), "\n")
}

// sensitiveBodyKeys are JSON field names scrubbed from captured bodies before they reach the report.
var sensitiveBodyKeys = []string{"password", "otp", "assertion", "secret", "token"}

// identityKeys are the login inputs the driver posts to /flow/execute.
var identityKeys = []string{"individualid", "individual_id", "username", "fullname", "full_name", "name", "dob"}

func isIdentityKey(k string) bool {
	lk := strings.ToLower(strings.TrimSpace(k))
	for _, s := range identityKeys {
		if lk == s {
			return true
		}
	}
	return false
}

// redactCallBodies copies results with sensitive fields scrubbed from every body and log item.
func redactCallBodies(results []result.ModuleResult) []result.ModuleResult {
	out := make([]result.ModuleResult, len(results))
	for i, r := range results {
		r.LogItems = redactLogItems(r.LogItems)
		if len(r.Calls) > 0 {
			calls := make([]result.HTTPCall, len(r.Calls))
			for j, c := range r.Calls {
				c.ReqBody = redactBodyMode(c.ReqBody, true)
				c.RespBody = redactBodyMode(c.RespBody, false)
				c.URL = redactURL(c.URL)
				c.ReqCookies = redactCookies(c.ReqCookies)
				c.RespCookies = redactCookies(c.RespCookies)
				c.ReqHeaders = redactHeaderMap(c.ReqHeaders)
				c.RespHeaders = redactHeaderMap(c.RespHeaders)
				calls[j] = c
			}
			r.Calls = calls
		}
		out[i] = r
	}
	return out
}

// redactLogItems scrubs the conformance suite's /api/log entries.
func redactLogItems(items []result.LogItem) []result.LogItem {
	if len(items) == 0 {
		return items
	}
	out := make([]result.LogItem, len(items))
	for i, it := range items {
		it.Msg = redactBody(it.Msg)
		if len(it.Details) > 0 {
			details := make([]result.LogDetail, len(it.Details))
			for j, d := range it.Details {
				details[j] = result.LogDetail{Key: d.Key, Value: redactLogValue(d.Key, d.Value)}
			}
			it.Details = details
		}
		out[i] = it
	}
	return out
}

// redactLogValue masks a log detail outright by key, else runs the body and URL scrubbers over it.
func redactLogValue(key, value string) string {
	if strings.TrimSpace(value) == "" {
		return value
	}
	if containsSensitiveKey(key) || isSensitiveParam(key) {
		return "***redacted***"
	}
	value = redactBody(value)
	if strings.Contains(value, "://") {
		value = redactURL(value)
	}
	return value
}

// redactURL masks credential-bearing values in both the query string and the fragment.
func redactURL(raw string) string {
	// Fragment first: url.ParseQuery does not treat '#' as a delimiter, so a
	// fragment left attached would ride along inside the last query value.
	frag := ""
	if h := strings.IndexByte(raw, '#'); h >= 0 {
		raw, frag = raw[:h], raw[h+1:]
	}
	if i := strings.IndexByte(raw, '?'); i >= 0 {
		if q, ok := redactQuery(raw[i+1:]); ok {
			raw = raw[:i+1] + q
		}
	}
	if frag != "" {
		if q, ok := redactQuery(frag); ok {
			frag = q
		}
		raw += "#" + frag
	}
	return raw
}

// decodedQuery renders a call's query string with the percent-encoding undone, one line per parameter.
func decodedQuery(raw string) string {
	i := strings.IndexByte(raw, '?')
	if i < 0 {
		return ""
	}
	qs := raw[i+1:]
	if h := strings.IndexByte(qs, '#'); h >= 0 {
		qs = qs[:h]
	}
	q, err := url.ParseQuery(qs)
	if err != nil || len(q) == 0 {
		return ""
	}
	keys := make([]string, 0, len(q))
	for k := range q {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	var b strings.Builder
	for _, k := range keys {
		for _, v := range q[k] {
			if p, ok := prettyJSONString(v); ok {
				fmt.Fprintf(&b, "%s =\n%s\n", k, p)
			} else {
				fmt.Fprintf(&b, "%s = %s\n", k, v)
			}
		}
	}
	return strings.TrimRight(b.String(), "\n")
}

// prettyJSONString re-indents s if it parses as a JSON object or array.
func prettyJSONString(s string) (string, bool) {
	t := strings.TrimSpace(s)
	if t == "" || (t[0] != '{' && t[0] != '[') || !json.Valid([]byte(t)) {
		return "", false
	}
	var v any
	if err := json.Unmarshal([]byte(t), &v); err != nil {
		return "", false
	}
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return "", false
	}
	return string(b), true
}

// redactQuery masks sensitive values in an encoded query/fragment string,
// reporting whether anything changed so callers can keep the original bytes.
func redactQuery(s string) (string, bool) {
	q, err := url.ParseQuery(s)
	if err != nil {
		return s, false
	}
	changed := false
	for k, vals := range q {
		if !containsSensitiveKey(k) && !isSensitiveParam(k) {
			continue
		}
		for n, v := range vals {
			if v != "" {
				vals[n] = "***redacted***"
				changed = true
			}
		}
	}
	if !changed {
		return s, false
	}
	return q.Encode(), true
}

// sensitiveParams are query/fragment/form keys that sensitiveBodyKeys does not match by substring.
var sensitiveParams = []string{"code", "code_verifier", "id_token", "access_token", "refresh_token"}

func isSensitiveParam(k string) bool {
	for _, s := range sensitiveParams {
		if strings.EqualFold(k, s) {
			return true
		}
	}
	return false
}

// redactCookies masks cookie values while keeping the names visible.
func redactCookies(s string) string {
	if strings.TrimSpace(s) == "" {
		return s
	}
	lines := strings.Split(s, "\n")
	for i, line := range lines {
		parts := strings.Split(line, ";")
		for j, p := range parts {
			eq := strings.IndexByte(p, '=')
			if eq < 0 {
				continue
			}
			name := strings.TrimSpace(p[:eq])
			// Set-Cookie attributes (Path, Domain, Expires, Max-Age, SameSite)
			// carry no secrets and stay readable; only j==0 is the actual pair.
			if j > 0 && !isCookieValueField(name) {
				continue
			}
			if strings.TrimSpace(p[eq+1:]) == "" {
				continue
			}
			parts[j] = p[:eq+1] + "***redacted***"
		}
		lines[i] = strings.Join(parts, ";")
	}
	return strings.Join(lines, "\n")
}

// isCookieValueField reports whether a semicolon-separated Set-Cookie segment is
// a cookie pair rather than a well-known attribute.
func isCookieValueField(name string) bool {
	switch strings.ToLower(name) {
	case "path", "domain", "expires", "max-age", "samesite", "version", "comment":
		return false
	}
	return true
}

// redactBody masks sensitiveBodyKeys values in a JSON or x-www-form-urlencoded body.
func redactBody(s string) string { return redactBodyMode(s, false) }

// redactBodyMode is redactBody with the request/response distinction that scopes
// identityKeys to request bodies (see identityKeys).
func redactBodyMode(s string, isRequest bool) string {
	trimmed := strings.TrimSpace(s)
	if trimmed == "" {
		return s
	}
	if trimmed[0] != '{' && trimmed[0] != '[' {
		return redactFormBody(trimmed, s)
	}
	if !json.Valid([]byte(trimmed)) {
		return s
	}
	var v any
	if err := json.Unmarshal([]byte(trimmed), &v); err != nil {
		return s
	}
	redactSensitiveMode(v, isRequest)
	b, err := json.Marshal(v)
	if err != nil {
		return s
	}
	return string(b)
}

// redactFormBody masks sensitive values in an x-www-form-urlencoded body.
func redactFormBody(trimmed, orig string) string {
	if !strings.Contains(trimmed, "=") || strings.ContainsAny(trimmed, " \n\t") {
		return orig
	}
	q, err := url.ParseQuery(trimmed)
	if err != nil {
		return orig
	}
	changed := false
	for k, vals := range q {
		// Same matcher as the URL path so a key never leaks in the token POST
		// body while being masked in the authorize URL.
		if !containsSensitiveKey(k) && !isSensitiveParam(k) {
			continue
		}
		for n, v := range vals {
			if v != "" {
				vals[n] = "***redacted***"
				changed = true
			}
		}
	}
	if !changed {
		return orig
	}
	return q.Encode()
}

func redactSensitiveMode(v any, isRequest bool) {
	switch t := v.(type) {
	case map[string]any:
		for k, val := range t {
			// Any scalar under a sensitive key, not just a string: a numeric
			// {"otp":111111} would otherwise reach the report in the clear.
			if containsSensitiveKey(k) || (isRequest && isIdentityKey(k)) {
				switch s := val.(type) {
				case string:
					if s != "" {
						t[k] = "***redacted***"
					}
					continue
				case float64, bool:
					t[k] = "***redacted***"
					continue
				case map[string]any, []any:
					// A composite under a sensitive key must be masked whole; recursing would only re-test inner keys.
					t[k] = "***redacted***"
					continue
				}
			}
			redactSensitiveMode(val, isRequest)
		}
	case []any:
		for _, val := range t {
			redactSensitiveMode(val, isRequest)
		}
	}
}

func containsSensitiveKey(k string) bool {
	lk := strings.ToLower(k)
	for _, s := range sensitiveBodyKeys {
		if strings.Contains(lk, s) {
			return true
		}
	}
	return false
}

func variantStr(v map[string]any) string {
	if len(v) == 0 {
		return ""
	}
	var parts []string
	for k, val := range v {
		parts = append(parts, fmt.Sprintf("%s=%v", k, val))
	}
	sort.Strings(parts)
	return strings.Join(parts, ", ")
}

// truncate cuts s to at most n bytes for a report detail value.
func truncate(s string, n int) string { return textx.Truncate(s, n, "\n…(truncated)") }

var tmpl = template.Must(template.New("report").Parse(reportHTML))

const reportHTML = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>eSignet Conformance — {{.Plan}} ({{.Provider}})</title>
<style>
  :root { --bg:#f6f7f9; --card:#fff; --fg:#1a1d21; --muted:#5b6570; --line:#e3e7eb;
          --pass:#1a7f37; --fail:#c1121f; --warn:#b26a00; --review:#6b3fa0; --skip:#5b6570; --known:#0969da; --info:#8a6d3b; --err:#8a1f2b; }
  @media (prefers-color-scheme: dark) {
    :root { --bg:#0f1216; --card:#171b21; --fg:#e6e9ee; --muted:#9aa4b0; --line:#2a3038;
            --pass:#3fb950; --fail:#f85149; --warn:#d29922; --review:#bc8cff; --skip:#8b949e; --known:#58a6ff; --info:#c9a227; --err:#ff7b72; } }
  :root[data-theme="dark"] { --bg:#0f1216; --card:#171b21; --fg:#e6e9ee; --muted:#9aa4b0; --line:#2a3038;
            --pass:#3fb950; --fail:#f85149; --warn:#d29922; --review:#bc8cff; --skip:#8b949e; --known:#58a6ff; --info:#c9a227; --err:#ff7b72; }
  :root[data-theme="light"] { --bg:#f6f7f9; --card:#fff; --fg:#1a1d21; --muted:#5b6570; --line:#e3e7eb;
            --pass:#1a7f37; --fail:#c1121f; --warn:#b26a00; --review:#6b3fa0; --skip:#5b6570; --known:#0969da; --info:#8a6d3b; --err:#8a1f2b; }
  * { box-sizing:border-box; }
  body { margin:0; background:var(--bg); color:var(--fg); font:14px/1.5 -apple-system,Segoe UI,Roboto,Helvetica,Arial,sans-serif; }
  .wrap { max-width:1100px; margin:0 auto; padding:24px 16px 64px; }
  h1 { font-size:20px; margin:0 0 4px; }
  .sub { color:var(--muted); margin:0 0 20px; }
  .tiles { display:flex; flex-wrap:wrap; gap:10px; margin-bottom:20px; }
  .tile { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:12px 16px; min-width:88px; }
  .tile .n { font-size:22px; font-weight:700; }
  .tile .l { color:var(--muted); font-size:12px; text-transform:uppercase; letter-spacing:.04em; }
  .tile.pass .n{color:var(--pass)} .tile.fail .n{color:var(--fail)} .tile.warn .n{color:var(--warn)}
  .tile.review .n{color:var(--review)} .tile.skip .n{color:var(--skip)} .tile.known .n{color:var(--known)} .tile.err .n{color:var(--err)}
  details.surface-section { margin:0; }
  summary.surface { font-size:16px; font-weight:700; margin:28px 0 10px; padding-top:14px; border-top:2px solid var(--line);
                    display:flex; align-items:baseline; gap:10px; flex-wrap:wrap; cursor:pointer; list-style:none; }
  summary.surface::-webkit-details-marker { display:none; }
  summary.surface::before { content:"▸"; color:var(--muted); font-weight:400; font-size:13px; transition:transform .12s ease; }
  details.surface-section[open] > summary.surface::before { transform:rotate(90deg); }
  summary.surface:hover { color:var(--fg); }
  .toolbar-sep { width:1px; align-self:stretch; background:var(--line); margin:0 4px; }
  .tiles.small { gap:8px; margin-bottom:12px; }
  .tiles.small .tile { min-width:66px; padding:8px 12px; }
  .tiles.small .tile .n { font-size:18px; }
  .valtable td, .valtable th { padding:6px 10px; font-size:12.5px; }
  .valtable tr.valfail { background:color-mix(in srgb, var(--fail) 10%, transparent); }
  .expand-btn { cursor:pointer; border:1px solid var(--line); background:var(--card); color:var(--muted);
                border-radius:6px; padding:2px 9px; font-size:11px; }
  .expand-btn:hover { color:var(--fg); }
  .toolbar { display:flex; gap:8px; margin:0 0 16px; flex-wrap:wrap; }
  .tablewrap { overflow-x:auto; }
  table { width:100%; border-collapse:collapse; background:var(--card); border:1px solid var(--line); border-radius:10px; overflow:hidden; }
  th,td { text-align:left; padding:10px 12px; border-bottom:1px solid var(--line); vertical-align:top; }
  th { font-size:12px; text-transform:uppercase; letter-spacing:.04em; color:var(--muted); }
  tr:last-child td { border-bottom:none; }
  .badge { display:inline-block; padding:2px 8px; border-radius:20px; font-size:12px; font-weight:600; border:1px solid currentColor; }
  .badge.pass{color:var(--pass)} .badge.fail{color:var(--fail)} .badge.warn{color:var(--warn)}
  .badge.review{color:var(--review)} .badge.skip{color:var(--skip)} .badge.known{color:var(--known)} .badge.err{color:var(--err)}
  code,.mono { font-family:ui-monospace,SFMono-Regular,Menlo,Consolas,monospace; font-size:12px; }
  details { margin-top:6px; }
  summary { cursor:pointer; color:var(--muted); font-size:12px; }
  .panel { background:var(--card); border:1px solid var(--line); border-radius:10px; padding:10px 14px; margin-bottom:20px; }
  .unredacted { border:2px solid var(--fail); border-left-width:6px; border-radius:10px;
                padding:10px 14px; margin-bottom:20px; color:var(--fail); line-height:1.5; }
  .panel > summary { font-size:13px; color:var(--fg); font-weight:600; }
  .drill { margin:8px 0 4px; padding:10px 12px; background:var(--bg); border:1px solid var(--line); border-radius:8px; }
  .cond { margin:4px 0; }
  .cond .r { font-weight:600; }
  .cond .r.FAILURE{color:var(--fail)} .cond .r.WARNING{color:var(--warn)}
  .req { color:var(--muted); }
  .flow { color:var(--muted); font-size:12px; }
  .muted { color:var(--muted); }
  /* Suite-UI-style condition log */
  .log { border:1px solid var(--line); border-radius:8px; overflow:hidden; margin:4px 0; }
  .logblock { background:#0b3d91; color:#fff; font-weight:600; padding:8px 12px;
              border-bottom:1px solid var(--line); }
  @media (prefers-color-scheme: dark) { .logblock { background:#12305c; } }
  :root[data-theme="dark"] .logblock { background:#12305c; }
  :root[data-theme="light"] .logblock { background:#0b3d91; }
  .logrow { display:grid; grid-template-columns:74px 96px 1fr; gap:10px; align-items:start;
            padding:8px 10px; border-bottom:1px solid var(--line); }
  .logrow:last-child { border-bottom:none; }
  .copy { cursor:pointer; border:1px solid var(--line); background:var(--card); color:var(--muted);
          border-radius:5px; padding:0 7px; font-size:10px; line-height:16px; margin-left:6px; }
  .copy:hover { color:var(--fg); }
  .gotop { position:fixed; right:16px; bottom:16px; z-index:10; text-decoration:none;
           background:var(--card); color:var(--fg); border:1px solid var(--line); border-radius:8px;
           padding:6px 12px; font-size:12px; box-shadow:0 1px 4px rgba(0,0,0,.15); }
  .logrow .t { color:var(--muted); font:12px/1.6 ui-monospace,Consolas,monospace; }
  .logrow .src { font-weight:600; }
  .logrow .msg { color:var(--muted); }
  .kbadge { display:inline-block; padding:1px 8px; border-radius:6px; font-size:11px; font-weight:700; letter-spacing:.03em;
            border:1px solid currentColor; text-transform:uppercase; }
  .kbadge.pass{color:var(--pass)} .kbadge.fail{color:var(--fail)} .kbadge.warn{color:var(--warn)}
  .kbadge.review{color:var(--review)} .kbadge.req{color:var(--known)} .kbadge.info{color:var(--info)}
  .call { border:1px solid var(--line); border-radius:8px; margin:8px 0; }
  .call > summary { padding:8px 10px; color:var(--fg); font-size:13px; }
  .call .body { padding:0 10px 10px; }
  .kv { font-size:11px; color:var(--muted); text-transform:uppercase; letter-spacing:.04em; margin:8px 0 2px; }
  pre { margin:0; padding:8px 10px; background:var(--card); border:1px solid var(--line); border-radius:6px;
        overflow-x:auto; white-space:pre-wrap; word-break:break-word; font:12px/1.5 ui-monospace,Consolas,monospace; max-height:340px; overflow-y:auto; }
  .s.pass{color:var(--pass)} .s.fail{color:var(--fail)} .s.warn{color:var(--warn)} .s.err{color:var(--err)}
  .toggle { float:right; cursor:pointer; border:1px solid var(--line); background:var(--card); color:var(--fg);
            border-radius:8px; padding:4px 10px; font-size:12px; }
</style>
</head>
<body>
<a id="top"></a>
<div class="wrap">
  <button class="toggle" onclick="var r=document.documentElement;r.dataset.theme=(r.dataset.theme==='dark'?'light':'dark')">◐ theme</button>
  <h1>eSignet ⇄ OpenID Conformance — {{.Plan}} · {{.Provider}}</h1>
  <p class="sub">Generated {{.Generated}} · {{.Summary.Total}} module(s)</p>

  <div class="toolbar">
    <button class="expand-btn" onclick="toggleAllSections(false)">▸ Collapse all sections</button>
    <button class="expand-btn" onclick="toggleAllSections(true)">▾ Expand all sections</button>
    <span class="toolbar-sep"></span>
    <button class="expand-btn" onclick="toggleAll(true)">⤢ Expand all row details</button>
    <button class="expand-btn" onclick="toggleAll(false)">⤡ Collapse all row details</button>
  </div>

  <div class="tiles">
    <div class="tile"><div class="n">{{.Summary.Total}}</div><div class="l">Total</div></div>
    <div class="tile pass"><div class="n">{{.Summary.Passed}}</div><div class="l">Passed</div></div>
    <div class="tile fail"><div class="n">{{.Summary.Failed}}</div><div class="l">Failed</div></div>
    <div class="tile warn"><div class="n">{{.Summary.Warning}}</div><div class="l">Warning</div></div>
    <div class="tile review"><div class="n">{{.Summary.Review}}</div><div class="l">Review</div></div>
    <div class="tile skip"><div class="n">{{.Summary.Skipped}}</div><div class="l">Skipped</div></div>
    <div class="tile known"><div class="n">{{.Summary.Known}}</div><div class="l">Known</div></div>
    <div class="tile err"><div class="n">{{.Summary.Errored}}</div><div class="l">Errored</div></div>
  </div>

  {{if .ShowSecrets}}
  <div class="unredacted">
    <strong>Unredacted report.</strong> run.debug_show_secrets was on, so the captured
    request/response bodies, URLs, cookies and headers below are shown exactly as they went
    over the wire — including OTPs, passwords, identity values and bearer tokens. Do not
    attach this file to a ticket, archive it in CI, or share it. Re-run without the flag to
    produce a redacted report. (The configuration panel and plan config are masked either way.)
  </div>
  {{end}}

  {{if or .ConfigJSON .PlanConfigs}}
  <details class="panel">
    <summary>Configuration used (secrets redacted)</summary>
    {{if .ConfigJSON}}
      <div class="kv">Harness config <button class="copy" onclick="copyPre(this)">copy</button></div>
      <pre>{{.ConfigJSON}}</pre>
    {{end}}
    {{range .PlanConfigs}}
      {{if .JSON}}
      <div class="kv">Plan config{{if .Plan}} · {{.Plan}}{{end}} — sent to the suite (client + jwks, private keys redacted) <button class="copy" onclick="copyPre(this)">copy</button></div>
      <pre>{{.JSON}}</pre>
      {{end}}
    {{end}}
  </details>
  {{end}}

  {{range .Surfaces}}
  <details class="surface-section" open>
  <summary id="{{.Anchor}}" class="surface">{{.Label}} <span class="muted">· {{.Summary.Total}} case(s)</span>
    {{if .Rows}}
    <span class="toolbar" style="margin:0">
      <button class="expand-btn" onclick="event.stopPropagation(); toggleSection('{{.Anchor}}', true)">⤢ Expand rows</button>
      <button class="expand-btn" onclick="event.stopPropagation(); toggleSection('{{.Anchor}}', false)">⤡ Collapse rows</button>
    </span>
    {{end}}
  </summary>
  <div class="tiles small">
    <div class="tile pass"><div class="n">{{.Summary.Passed}}</div><div class="l">Passed</div></div>
    <div class="tile fail"><div class="n">{{.Summary.Failed}}</div><div class="l">Failed</div></div>
    <div class="tile warn"><div class="n">{{.Summary.Warning}}</div><div class="l">Warning</div></div>
    <div class="tile skip"><div class="n">{{.Summary.Skipped}}</div><div class="l">Skipped</div></div>
    <div class="tile known"><div class="n">{{.Summary.Known}}</div><div class="l">Known</div></div>
    <div class="tile err"><div class="n">{{.Summary.Errored}}</div><div class="l">Errored</div></div>
  </div>
  {{if .Rows}}
  <div class="tablewrap" id="{{.Anchor}}-body">
  <table>
    <thead><tr><th>#</th><th>Module</th><th>Result</th><th>Duration</th><th>Details</th></tr></thead>
    <tbody>
    {{range .Rows}}
      <tr>
        <td class="muted">{{.Index}}</td>
        <td>
          <div class="mono">{{.Module}}</div>
          {{if .Variant}}<div class="muted mono" style="font-size:11px">{{.Variant}}</div>{{end}}
          {{if .TestID}}<div class="muted mono" style="font-size:11px">test: {{.TestID}}</div>{{end}}
        </td>
        <td>
          <span class="badge {{.ResultClass}}">{{.Result}}</span>
          {{if .OutcomeDetail}}<div class="muted" style="font-size:11px">{{.OutcomeDetail}}</div>{{end}}
        </td>
        <td class="muted">{{.Duration}}</td>
        <td>
          {{if .HarnessError}}<div class="cond"><span class="r FAILURE">harness</span>: <span class="mono">{{.HarnessError}}</span></div>{{end}}

          {{if .Assertions}}
          <details{{if gt .AssertionFails 0}} open{{end}}>
            <summary>Validation — {{.AssertionCount}} check(s){{if gt .AssertionFails 0}}, <span class="fail">{{.AssertionFails}} failed</span>{{end}}</summary>
            <div class="drill">
            <div class="tablewrap">
            <table class="valtable">
              <thead><tr><th>Field</th><th>Expected</th><th>Actual</th><th>Result</th></tr></thead>
              <tbody>
              {{range .Assertions}}
                <tr class="{{if not .Passed}}valfail{{end}}">
                  <td class="mono">{{.Field}}</td>
                  <td class="mono">{{.Expected}}</td>
                  <td class="mono">{{.Actual}}</td>
                  <td><span class="badge {{.ResultClass}}">{{.ResultLabel}}</span></td>
                </tr>
              {{end}}
              </tbody>
            </table>
            </div>
            </div>
          </details>
          {{end}}

          {{if .Conditions}}
          <details><summary>{{.FailedCount}} failure/warning finding(s)</summary>
            <div class="drill">
              {{range .Conditions}}
                <div class="cond"><span class="r {{.Result}}">{{.Result}}</span>
                  <span class="mono">{{.Src}}</span> — {{.Msg}}
                  {{if .Requirement}}<div class="req">↳ {{.Requirement}}</div>{{end}}
                </div>
              {{end}}
            </div>
          </details>
          {{end}}

          {{if .Log}}
          <details><summary>test log — {{.LogCount}} entr(y/ies)</summary>
            <div class="log">
            {{range .Log}}
              {{if .Block}}
              <div class="logblock">{{.Time}} · {{.Msg}}</div>
              {{else}}
              <div class="logrow">
                <div class="t">{{.Time}}</div>
                <div><span class="kbadge {{.KindClass}}">{{.Kind}}</span></div>
                <div>
                  {{if .Src}}<span class="src mono">{{.Src}}</span>{{end}}
                  {{if .Msg}}<div class="msg">{{.Msg}}</div>{{end}}
                  {{if .Requirement}}<div class="req">↳ {{.Requirement}}</div>{{end}}
                  {{if .Details}}<details><summary>+{{.MoreN}} more</summary>
                    {{range .Details}}
                      <div class="kv">{{.Key}} <button class="copy" onclick="copyPre(this)">copy</button></div>
                      <pre>{{.Value}}</pre>
                    {{end}}
                  </details>{{end}}
                </div>
              </div>
              {{end}}
            {{end}}
            </div>
          </details>
          {{end}}

          {{if or .Flow.AuthorizeStatus .Flow.Steps}}
          <details><summary>eSignet flow trace</summary>
            <div class="drill flow">
              <div>authorize HTTP {{.Flow.AuthorizeStatus}} · esignet-callback {{.Flow.EsignetCallbackStatus}} · suite-callback {{.Flow.SuiteCallbackStatus}} · implicit-submit {{.Flow.ImplicitSubmitStatus}}</div>
              {{range .Flow.Steps}}<div>flow: {{.FlowStatus}} {{if .Action}}action={{.Action}} {{end}}{{if .Inputs}}inputs={{.Inputs}}{{end}}</div>{{end}}
            </div>
          </details>
          {{end}}

          {{if .Calls}}
          <details><summary>{{.CallCount}} eSignet API call(s) — full request/response</summary>
            <div class="drill">
            {{range .Calls}}{{template "call" .}}{{end}}
            </div>
          </details>
          {{end}}
        </td>
      </tr>
    {{end}}
    </tbody>
  </table>
  </div>
  {{end}}
  </details>
  {{end}}
</div>
<a href="#top" class="gotop">↑ Go to Top</a>
<script>
function copyPre(btn){
  var p=btn.parentElement.nextElementSibling;
  while(p && p.tagName!=='PRE'){ p=p.nextElementSibling; }
  if(!p){ return; }
  var t=p.textContent, done=function(){ var o=btn.textContent; btn.textContent='copied'; setTimeout(function(){btn.textContent=o;},1200); };
  if(navigator.clipboard && navigator.clipboard.writeText){ navigator.clipboard.writeText(t).then(done, function(){fallback(t);done();}); }
  else { fallback(t); done(); }
}
function fallback(t){ var a=document.createElement('textarea'); a.value=t; document.body.appendChild(a); a.select(); try{document.execCommand('copy');}catch(e){} document.body.removeChild(a); }
function toggleSection(anchorId, open){
  var el = document.getElementById(anchorId+'-body');
  if(!el) return;
  el.querySelectorAll('details').forEach(function(d){ d.open = open; });
}
function toggleAll(open){
  // Row-level drill-downs only — leave the section wrappers as they are.
  document.querySelectorAll('.wrap details:not(.surface-section)').forEach(function(d){ d.open = open; });
}
function toggleAllSections(open){
  document.querySelectorAll('details.surface-section').forEach(function(d){ d.open = open; });
}
</script>
</body>
</html>

{{define "call"}}
<details class="call">
  <summary><span class="mono">#{{.Seq}} {{.Method}}</span> {{.Label}} <span class="s {{.StatusClass}}">→ {{.Status}}</span>{{if gt .Repeat 1}} <span class="muted">×{{.Repeat}}</span>{{end}}</summary>
  <div class="body">
    <div class="kv">URL</div><pre>{{.URL}}</pre>
    {{if .QueryDecoded}}<div class="kv">Query params (decoded) <button class="copy" onclick="copyPre(this)">copy</button></div><pre>{{.QueryDecoded}}</pre>{{end}}
    {{if .ReqHeaders}}<div class="kv">Request headers <button class="copy" onclick="copyPre(this)">copy</button></div><pre>{{.ReqHeaders}}</pre>{{end}}
    {{if .ReqCookies}}<div class="kv">Request cookies</div><pre>{{.ReqCookies}}</pre>{{end}}
    {{if .ReqBody}}<div class="kv">Request body <button class="copy" onclick="copyPre(this)">copy</button></div><pre>{{.ReqBody}}</pre>{{end}}
    {{if .RespHeaders}}<div class="kv">Response headers <button class="copy" onclick="copyPre(this)">copy</button></div><pre>{{.RespHeaders}}</pre>{{end}}
    {{if .RespCookies}}<div class="kv">Response Set-Cookie</div><pre>{{.RespCookies}}</pre>{{end}}
    {{if .RespBody}}<div class="kv">Response body <button class="copy" onclick="copyPre(this)">copy</button></div><pre>{{.RespBody}}</pre>{{end}}
  </div>
</details>
{{end}}`
