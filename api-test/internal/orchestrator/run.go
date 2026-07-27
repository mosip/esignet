// Package orchestrator ties the suite client and the eSignet driver together:
// preflight, module selection, per-module drive-and-poll, and result collection.
// See plan doc §2 and §8d.
package orchestrator

import (
	"encoding/json"
	"fmt"
	"net/url"
	"context"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"
	"unicode/utf8"

	"github.com/mosip/esignet/api-test/internal/config"
	"github.com/mosip/esignet/api-test/internal/conformance"
	"github.com/mosip/esignet/api-test/internal/esignet"
	"github.com/mosip/esignet/api-test/internal/result"
	"github.com/mosip/esignet/api-test/internal/wsotp"
)

// unsupportedModuleHints flag modules the harness can't drive in v1 (they need
// browser-fragment / QR / logout / reject handling). Matched as substrings.
var unsupportedModuleHints = map[string]string{
	"logout":       "logout",
	"rp-initiated": "logout",
	"qr":           "qr",
	"backchannel":  "backchannel",
	"ciba":         "ciba",
	"form-post":    "form_post_fragment",
}

type Orchestrator struct {
	cfg    *config.Config
	client *conformance.Client
	logf   func(string, ...any)
}

func New(cfg *config.Config, logf func(string, ...any)) *Orchestrator {
	httpTimeout := time.Duration(cfg.Run.TimeoutSeconds) * time.Second
	return &Orchestrator{
		cfg:    cfg,
		client: conformance.New(cfg.Conformance.BaseURL, cfg.Conformance.Token, cfg.Conformance.TLSVerify, httpTimeout),
		logf:   logf,
	}
}

// RunResult bundles the per-module results. Suite plumbing calls (available,
// create-plan, polls, deliver) are not reported — use the harness logs.
type RunResult struct {
	Modules []result.ModuleResult
}

// Run executes the plan and returns one ModuleResult per selected module.
func (o *Orchestrator) Run() (*RunResult, error) {
	out := &RunResult{}

	// Preflight: suite reachable.
	if err := o.client.Available(); err != nil {
		o.client.TakeCalls()
		return out, fmt.Errorf("ENV_NOT_READY: %w", err)
	}
	o.logf("suite is available")

	configBody, err := os.ReadFile(o.cfg.Plan.ConfigFile)
	if err != nil {
		return out, fmt.Errorf("read plan config %s: %w", o.cfg.Plan.ConfigFile, err)
	}

	plan, err := o.client.CreatePlan(o.cfg.Plan.Name, o.cfg.Plan.Variant, configBody)
	if err != nil {
		o.client.TakeCalls()
		return out, err
	}
	o.logf("created plan %s (id=%s, %d modules)", plan.Name, plan.ID, len(plan.Modules))

	o.client.TakeCalls() // discard setup calls (available + create plan)

	selected, err := o.selectModules(plan.Modules)
	if err != nil {
		return out, err
	}
	o.logf("selected %d module(s) for profile=%q filter=%q", len(selected), o.cfg.Run.Profile, o.cfg.Run.Filter)

	answers := esignet.BuildAnswers(o.cfg.Esignet)
	// Preferred actions: the auth-factor (ACR) choice AND the login-ID-type
	// choice (uin/vid/phone), since eSignet asks for both in the OTP flow.
	preferred := append(esignet.AuthFactorTokens(o.cfg.Esignet.AuthFactor), esignet.IDTypeTokens(o.cfg.Esignet.Identity.IDType)...)
	driver := esignet.New(answers, preferred, o.cfg.Conformance.TLSVerify, time.Duration(o.cfg.Run.TimeoutSeconds)*time.Second)

	// Dynamic OTP: connect the mock-SMTP listener once and share it across all
	// modules. Connecting before the module loop guarantees we are buffering
	// before any send-OTP is triggered.
	if o.cfg.Esignet.OTP.Source == "dynamic" {
		lst := wsotp.NewListener(o.cfg.Esignet.OTP.WSURL, o.cfg.Conformance.TLSVerify)
		if err := lst.Start(context.Background()); err != nil {
			return out, fmt.Errorf("ENV_NOT_READY: %w", err)
		}
		defer lst.Close()
		timeout := time.Duration(o.cfg.Run.TimeoutSeconds) * time.Second
		poll := time.Duration(o.cfg.Run.PollIntervalSeconds) * time.Second
		driver.SetOTPProvider(wsotp.NewOTPProvider(lst, o.cfg.Esignet.OTP.RecipientEmail, timeout, poll))
		o.logf("dynamic OTP: listening on mock-SMTP for recipient %q", o.cfg.Esignet.OTP.RecipientEmail)
	}

	// known_issues and skip modules are separated out before execution: they are
	// not run, they just get a report row in the Known / Skipped bucket.
	known := knownMap(o.cfg.Run.KnownIssues)
	skip := nameSet(o.cfg.Run.Skip)

	for _, m := range selected {
		if reason, ok := known[m.TestModule]; ok {
			res := gatedResult(plan.Name, o.cfg.Esignet.Provider, m, result.OutcomeKnownIssue, reason)
			out.Modules = append(out.Modules, res)
			o.logf("module %-45s -> KNOWN ISSUE%s", res.Module, reasonSuffix(reason))
			continue
		}
		if skip[m.TestModule] {
			res := gatedResult(plan.Name, o.cfg.Esignet.Provider, m, result.OutcomeSkippedByHarness, "skipped by config")
			res.Result = "SKIPPED"
			out.Modules = append(out.Modules, res)
			o.logf("module %-45s -> SKIPPED (config)", res.Module)
			continue
		}
		res := o.runModule(plan, m, driver)
		out.Modules = append(out.Modules, res)
		o.logf("module %-45s -> %s / %s%s", res.Module, dash(res.Result), res.HarnessOutcome, errSuffix(res))
		if o.cfg.Run.FailFast && (res.Result == "FAILED" || res.HarnessError != "") {
			o.logf("fail-fast: stopping after first failure")
			break
		}
	}
	return out, nil
}

// gatedResult builds a not-run report row for a module excluded by config
// (known_issues -> Known bucket, skip -> Skipped bucket).
func gatedResult(plan, provider string, m conformance.Module, outcome, detail string) result.ModuleResult {
	return result.ModuleResult{
		Surface:        result.SurfaceConformance,
		Plugin:         provider,
		Plan:           plan,
		Module:         m.TestModule,
		Variant:        m.Variant,
		HarnessOutcome: outcome,
		OutcomeDetail:  detail,
		Status:         "NOT_RUN",
	}
}

func knownMap(items []config.KnownIssue) map[string]string {
	m := map[string]string{}
	for _, k := range items {
		if k.Module != "" {
			m[k.Module] = k.Reason
		}
	}
	return m
}

func nameSet(names []string) map[string]bool {
	m := map[string]bool{}
	for _, n := range names {
		if n != "" {
			m[n] = true
		}
	}
	return m
}

func reasonSuffix(reason string) string {
	if reason == "" {
		return ""
	}
	return " (" + reason + ")"
}

func (o *Orchestrator) runModule(plan *conformance.PlanResponse, m conformance.Module, driver *esignet.Driver) result.ModuleResult {
	res := result.ModuleResult{
		Surface:        result.SurfaceConformance,
		Plugin:         o.cfg.Esignet.Provider,
		Plan:           plan.Name,
		Module:         m.TestModule,
		Variant:        m.Variant,
		HarnessOutcome: result.OutcomeOK,
	}

	if reason := unsupportedReason(m.TestModule); reason != "" {
		res.HarnessOutcome = result.OutcomeSkippedByHarness
		res.OutcomeDetail = "UNSUPPORTED_INTERACTION, detail: " + reason
		res.Result = "SKIPPED"
		res.Status = "NOT_STARTED"
		return res
	}

	o.client.TakeCalls() // discard any stray calls before this module

	start := time.Now()
	test, err := o.client.CreateTest(m.TestModule, plan.ID)
	if err != nil {
		res.HarnessError = err.Error()
		o.client.TakeCalls() // discard suite plumbing calls (not shown in report)
		res.DurationMs = time.Since(start).Milliseconds()
		return res
	}
	res.TestID = test.ID

	deadline := time.Now().Add(time.Duration(o.cfg.Run.TimeoutSeconds) * time.Second)
	poll := time.Duration(o.cfg.Run.PollIntervalSeconds) * time.Second
	handled := map[string]bool{}

	for {
		info, err := o.client.GetInfo(test.ID)
		if err != nil {
			res.HarnessError = err.Error()
			break
		}
		res.Status = info.Status
		if info.Status == "FINISHED" || info.Status == "INTERRUPTED" {
			res.Result = info.Result
			break
		}

		runner, err := o.client.GetRunner(test.ID)
		if err != nil {
			res.HarnessError = err.Error()
			break
		}
		pending := pendingURLs(runner.Browser, handled)
		if len(pending) > 0 {
			for _, u := range pending {
				o.driveOne(driver, u, &res)
				handled[u] = true
				if res.HarnessError != "" {
					break
				}
			}
			if res.HarnessError != "" {
				break
			}
		} else {
			time.Sleep(poll)
		}

		if time.Now().After(deadline) {
			if res.HarnessError == "" {
				res.HarnessError = fmt.Sprintf("timeout after %ds (last status %s)", o.cfg.Run.TimeoutSeconds, res.Status)
			}
			break
		}
	}
	res.DurationMs = time.Since(start).Milliseconds()

	// Final verdict: re-fetch in case the loop exited before reaching a terminal
	// status (deadline, or a harness error mid-drive). Falls back to whatever the
	// loop already captured rather than blanking it out on a transient failure.
	if info, err := o.client.GetInfo(test.ID); err == nil {
		// Only overwrite with a populated field: a transient/partial payload
		// would otherwise blank an already-captured verdict into a report dash.
		if info.Status != "" {
			res.Status = info.Status
		}
		if info.Result != "" {
			res.Result = info.Result
		}
	} else if res.HarnessError == "" {
		res.HarnessError = fmt.Sprintf("final GetInfo: %v", err)
	}
	// Full condition log (best effort): rendered UI-style in the report, and
	// distilled into the FAILURE/WARNING summary.
	if raw, err := o.client.GetRawLog(test.ID); err == nil {
		res.LogItems = buildLogItems(raw)
		res.FailedConditions = failedFromItems(res.LogItems)
	}

	// The report shows only eSignet-thunder traffic; discard the suite plumbing
	// calls (create_test, polls, info, log, deliver) captured on the client.
	o.client.TakeCalls()
	res.Calls = result.CollapseCalls(res.Calls)
	return res
}

// driveOne drives a single authorize URL through eSignet and hands the code back
// to the suite. It derives the eSignet base from the authorize URL (or validates
// a configured base against it).
func (o *Orchestrator) driveOne(driver *esignet.Driver, authorizeURL string, res *result.ModuleResult) {
	base, err := o.esignetBase(authorizeURL)
	if err != nil {
		res.HarnessError = err.Error()
		return
	}

	flow := driver.Run(base, authorizeURL)
	res.FlowTrace.AuthorizeStatus = flow.AuthorizeStatus
	res.FlowTrace.Steps = append(res.FlowTrace.Steps, flow.Steps...)
	res.FlowTrace.EsignetCallbackStatus = flow.CallbackStatus
	res.Calls = append(res.Calls, flow.Calls...)
	if !flow.OK() {
		res.HarnessError = "eSignet flow: " + flow.Error
		return
	}

	deliver, err := o.client.DeliverCallback(flow.RedirectURI)
	res.FlowTrace.SuiteCallbackStatus = deliver.SuiteCallbackStatus
	res.FlowTrace.ImplicitSubmitStatus = deliver.ImplicitSubmitStatus
	// (deliver's HTTP calls land on the client trace and are discarded at the end
	// of runModule — see the TakeCalls() comment there; only eSignet-thunder
	// traffic reaches the report)
	if err != nil {
		res.HarnessError = "deliver callback: " + err.Error()
	}
}

// esignetBase returns the eSignet base URL: the configured one if set (validated
// against the authorize URL's scheme+host+prefix), otherwise derived from the
// authorize URL by trimming /oauth2/authorize.
func (o *Orchestrator) esignetBase(authorizeURL string) (string, error) {
	au, err := url.Parse(authorizeURL)
	if err != nil {
		return "", fmt.Errorf("parse authorize URL: %w", err)
	}
	idx := strings.Index(au.Path, "/oauth2/authorize")
	if idx < 0 {
		return "", fmt.Errorf("authorize URL path %q has no /oauth2/authorize segment", au.Path)
	}
	derived := au.Scheme + "://" + au.Host + au.Path[:idx]

	if o.cfg.Esignet.BaseURL != "" {
		configured := strings.TrimRight(o.cfg.Esignet.BaseURL, "/")
		if !strings.HasPrefix(derived+"/", configured+"/") && derived != configured {
			return "", fmt.Errorf("ESIGNET_BASE_URL_MISMATCH: configured %q but suite authorize URL implies %q", configured, derived)
		}
		return configured, nil
	}
	return derived, nil
}

// selectModules applies precedence: explicit modules -> profile subset -> filter.
func (o *Orchestrator) selectModules(all []conformance.Module) ([]conformance.Module, error) {
	byName := map[string]conformance.Module{}
	var order []string
	for _, m := range all {
		if _, seen := byName[m.TestModule]; !seen {
			order = append(order, m.TestModule)
		}
		byName[m.TestModule] = m
	}

	var names []string
	switch {
	case len(o.cfg.Run.Modules) > 0:
		names = o.cfg.Run.Modules
	case o.cfg.Run.Profile == "smoke":
		smoke, err := o.loadSmokeProfile()
		if err != nil {
			return nil, err
		}
		names = smoke
	default: // full
		names = order
	}

	// Apply the filter regex (if any).
	var re *regexp.Regexp
	if o.cfg.Run.Filter != "" {
		var err error
		re, err = regexp.Compile(o.cfg.Run.Filter)
		if err != nil {
			return nil, fmt.Errorf("invalid run.filter regex %q: %w", o.cfg.Run.Filter, err)
		}
	}

	var out []conformance.Module
	for _, n := range names {
		if re != nil && !re.MatchString(n) {
			continue
		}
		if m, ok := byName[n]; ok {
			out = append(out, m)
		} else {
			// Named module not in the plan — keep it so the operator sees the miss.
			out = append(out, conformance.Module{TestModule: n})
		}
	}
	if len(out) == 0 {
		return nil, fmt.Errorf("no modules selected (profile=%q filter=%q) — plan has %d modules", o.cfg.Run.Profile, o.cfg.Run.Filter, len(all))
	}
	return out, nil
}

type smokeFile struct {
	SuiteVersion string   `json:"suite_version"`
	Plan         string   `json:"plan"`
	Modules      []string `json:"modules"`
}

func (o *Orchestrator) loadSmokeProfile() ([]string, error) {
	path := filepath.Join("profiles", o.cfg.Plan.Name+".smoke.json")
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, fmt.Errorf("read smoke profile %s: %w", path, err)
	}
	var sf smokeFile
	if err := json.Unmarshal(data, &sf); err != nil {
		return nil, fmt.Errorf("parse smoke profile %s: %w", path, err)
	}
	if len(sf.Modules) == 0 {
		return nil, fmt.Errorf("smoke profile %s lists no modules", path)
	}
	return sf.Modules, nil
}

func unsupportedReason(module string) string {
	lm := strings.ToLower(module)
	for hint, reason := range unsupportedModuleHints {
		if strings.Contains(lm, hint) {
			return reason
		}
	}
	return ""
}

func pendingURLs(b conformance.Browser, handled map[string]bool) []string {
	visited := map[string]bool{}
	for _, v := range b.Visited {
		visited[v] = true
	}
	var out []string
	for _, u := range b.URLs {
		if !visited[u] && !handled[u] {
			out = append(out, u)
		}
	}
	return out
}

// primaryLogKeys are the log-entry fields rendered as columns (or dropped as
// internal noise); everything else becomes an expandable detail row.
var primaryLogKeys = map[string]bool{
	"time": true, "src": true, "msg": true, "result": true, "requirements": true,
	"_id": true, "testId": true, "testOwner": true, "seq": true,
	"blockId": true, "startBlock": true, // internal block markers
}

// detailOrder ranks the common HTTP detail fields so the report shows them in
// the same order as the suite UI (status, headers, body) rather than alphabetically.
var detailOrder = map[string]int{
	"http": 0, "request_method": 1, "request_url": 2, "request_headers": 3, "request_body": 4,
	"response_status_code": 5, "response_status_text": 6, "response_headers": 7, "response_body": 8,
}

// buildLogItems converts the raw suite condition log into report LogItems,
// reproducing the suite UI's log view: section banners ("-START-BLOCK-"), a
// per-entry badge, and expandable detail fields with JSON values pretty-printed
// (re-indented and unescaped) rather than dumped as one escaped blob.
func buildLogItems(raw []map[string]any) []result.LogItem {
	var out []result.LogItem
	for _, e := range raw {
		it := result.LogItem{
			Time:        formatLogTime(e["time"]),
			Src:         asString(e["src"]),
			Msg:         asString(e["msg"]),
			Kind:        logKind(e),
			Requirement: reqFromAny(e["requirements"]),
			Block:       isTrue(e["startBlock"]) || asString(e["src"]) == "-START-BLOCK-",
		}
		var keys []string
		for k := range e {
			if !primaryLogKeys[k] {
				keys = append(keys, k)
			}
		}
		sort.Slice(keys, func(i, j int) bool {
			ri, oki := detailOrder[keys[i]]
			rj, okj := detailOrder[keys[j]]
			if oki != okj {
				return oki // ranked keys come before unranked
			}
			if oki && ri != rj {
				return ri < rj
			}
			return keys[i] < keys[j]
		})
		for _, k := range keys {
			it.Details = append(it.Details, result.LogDetail{Key: k, Value: truncateStr(prettyValue(e[k]), 8000)})
		}
		it.MoreN = len(it.Details)
		out = append(out, it)
	}
	return out
}

// prettyValue renders a detail value: a JSON string/object is re-indented (and
// its escaped quotes unescaped), other values are shown as-is. Private JWK
// material is redacted here too — the suite echoes the POSTed client config
// (jwks and all) back into its condition log.
func prettyValue(v any) string {
	switch t := v.(type) {
	case string:
		if p, ok := prettyJSONString(t); ok {
			return p
		}
		return t
	default:
		config.RedactJWKMaterial(v)
		if b, err := json.MarshalIndent(v, "", "  "); err == nil {
			return string(b)
		}
		return asString(v)
	}
}

// prettyJSONString parses a JSON string, redacts private JWK material, and
// re-indents it. Returns ok=false when s is not a JSON object/array.
func prettyJSONString(s string) (string, bool) {
	s = strings.TrimSpace(s)
	if s == "" || (s[0] != '{' && s[0] != '[') || !json.Valid([]byte(s)) {
		return "", false
	}
	var v any
	if err := json.Unmarshal([]byte(s), &v); err != nil {
		return "", false
	}
	config.RedactJWKMaterial(v)
	b, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return "", false
	}
	return string(b), true
}

func isTrue(v any) bool {
	b, ok := v.(bool)
	return ok && b
}

func failedFromItems(items []result.LogItem) []result.Condition {
	var out []result.Condition
	for _, it := range items {
		if it.Kind == "FAILURE" || it.Kind == "WARNING" {
			out = append(out, result.Condition{
				Src: it.Src, Msg: it.Msg, Result: it.Kind, Requirement: it.Requirement,
			})
		}
	}
	return out
}

// logKind derives the badge label: the condition result if present, else the
// HTTP direction (REQUEST/RESPONSE), else INFO.
func logKind(e map[string]any) string {
	if r := strings.ToUpper(asString(e["result"])); r != "" {
		return r
	}
	if h := strings.ToUpper(asString(e["http"])); h != "" {
		return h
	}
	return "INFO"
}

func formatLogTime(v any) string {
	switch t := v.(type) {
	case float64:
		return time.UnixMilli(int64(t)).Format("15:04:05")
	case json.Number:
		if ms, err := t.Int64(); err == nil {
			return time.UnixMilli(ms).Format("15:04:05")
		}
	case string:
		return t
	}
	return ""
}

func reqFromAny(v any) string {
	switch r := v.(type) {
	case string:
		return r
	case []any:
		var parts []string
		for _, x := range r {
			parts = append(parts, asString(x))
		}
		return strings.Join(parts, ", ")
	}
	return ""
}

func asString(v any) string {
	switch s := v.(type) {
	case nil:
		return ""
	case string:
		return s
	case float64:
		return strconv.FormatFloat(s, 'f', -1, 64)
	case bool:
		return strconv.FormatBool(s)
	default:
		return fmt.Sprintf("%v", s)
	}
}

// truncateStr cuts s to at most n bytes, backing off to the last full rune so a
// multi-byte UTF-8 character is never split.
func truncateStr(s string, n int) string {
	if len(s) <= n {
		return s
	}
	cut := n
	for cut > 0 && !utf8.RuneStart(s[cut]) {
		cut--
	}
	return s[:cut] + "\n…(truncated)"
}

func dash(s string) string {
	if s == "" {
		return "-"
	}
	return s
}

func errSuffix(r result.ModuleResult) string {
	if r.HarnessError != "" {
		return " [" + r.HarnessError + "]"
	}
	return ""
}
