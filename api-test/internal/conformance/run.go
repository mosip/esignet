package conformance

import (
	"context"
	"encoding/json"
	"fmt"
	"net/url"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/mosip/esignet/api-test/internal/config"
	"github.com/mosip/esignet/api-test/internal/esignet"
	"github.com/mosip/esignet/api-test/internal/result"
	"github.com/mosip/esignet/api-test/internal/textx"
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
}

// moduleBehavior describes the non-default driving a particular module needs.
// The zero value is the ordinary happy path: approve consent, authenticate on
// the first visit, drive each browser URL once.
type moduleBehavior struct {
	// consent answers the consent step; the zero value approves everything.
	consent esignet.ConsentPolicy
	// followRejection posts the flow's errorAssertion so the RP receives the
	// error redirect instead of the flow being reported as merely failed.
	followRejection bool
	// loadOnlyVisits is how many leading browser visits load the login page
	// without authenticating.
	loadOnlyVisits int
	// maxVisits is how many times the same browser URL may be driven; 0 means 1.
	maxVisits int
}

// moduleBehaviors maps a module-name substring to the behaviour it needs,
// matched the same way as unsupportedModuleHints.
var moduleBehaviors = map[string]moduleBehavior{
	// The suite waits for error=access_denied at the RP: "the tester MUST press
	// 'cancel' on the login screen or deny consent". Denying every consent
	// element ends the flow in ERROR with an errorAssertion, and following that
	// through to the callback is what turns it into the redirect the suite wants.
	"user-rejects-authentication": {
		consent:         esignet.ConsentPolicy{DenyAll: true},
		followRejection: true,
	},
	// Proves a request_uri can be reused before authentication completes. The
	// first visit must reach the login page and stop — authenticating there makes
	// the suite abort with "The user was authenticated on the initial visit to
	// login page" — and the second visit completes the login.
	"par-ensure-reused-request-uri-prior-to-auth-completion-succeeds": {
		loadOnlyVisits: 1,
		maxVisits:      2,
	},
}

// behaviorFor returns the behaviour configured for a module, or the zero value.
func behaviorFor(module string) moduleBehavior {
	for hint, b := range moduleBehaviors {
		if strings.Contains(module, hint) {
			return b
		}
	}
	return moduleBehavior{}
}

func (b moduleBehavior) visitBudget() int {
	if b.maxVisits < 1 {
		return 1
	}
	return b.maxVisits
}

type Orchestrator struct {
	cfg    *config.Config
	client *Client
	logf   func(string, ...any)
}

func New(cfg *config.Config, logf func(string, ...any)) *Orchestrator {
	httpTimeout := time.Duration(cfg.Run.TimeoutSeconds) * time.Second
	return &Orchestrator{
		cfg:    cfg,
		client: newClient(cfg.Conformance.BaseURL, cfg.Conformance.Token, cfg.Conformance.TLSVerify, httpTimeout),
		logf:   logf,
	}
}

// RunResult bundles the per-module results. Suite plumbing calls (available,
// create-plan, polls, deliver) are not reported — use the harness logs.
type RunResult struct {
	Modules []result.ModuleResult
}

// Run executes every configured plan and returns one ModuleResult per selected module.
func (o *Orchestrator) Run(ctx context.Context) (*RunResult, error) {
	out := &RunResult{}

	// Preflight: suite reachable.
	if err := o.client.Available(ctx); err != nil {
		o.client.TakeCalls()
		return out, fmt.Errorf("ENV_NOT_READY: %w", err)
	}
	o.logf("suite is available")
	o.client.TakeCalls() // discard the availability call

	answers := esignet.BuildAnswers(o.cfg.Esignet)
	// Preferred actions: the auth-factor (ACR) choice AND the login-ID-type
	// choice (uin/vid/phone), since eSignet asks for both in the OTP flow.
	preferred := append(esignet.AuthFactorTokens(o.cfg.Esignet.AuthFactor), esignet.IDTypeTokens(o.cfg.Esignet.Identity.IDType)...)
	// esignet.tls_verify, not conformance.tls_verify: this driver talks to the real deployment.
	driver := esignet.New(answers, preferred, o.cfg.Esignet.TLSVerify, time.Duration(o.cfg.Run.TimeoutSeconds)*time.Second)

	// Dynamic OTP: connect the mock-SMTP listener once and share it across all modules.
	if o.cfg.Esignet.OTP.Source == "dynamic" {
		lst := wsotp.NewListener(o.cfg.Esignet.OTP.WSURL, o.cfg.Esignet.TLSVerify)
		if err := lst.Start(ctx); err != nil {
			return out, fmt.Errorf("ENV_NOT_READY: %w", err)
		}
		defer lst.Close()
		timeout := time.Duration(o.cfg.Run.TimeoutSeconds) * time.Second
		poll := time.Duration(o.cfg.Run.PollIntervalSeconds) * time.Second
		driver.SetOTPProvider(wsotp.NewOTPProvider(lst, o.cfg.Esignet.OTP.RecipientEmail, timeout, poll))
		o.logf("dynamic OTP: listening on mock-SMTP for recipient %q", o.cfg.Esignet.OTP.RecipientEmail)
	}

	for i, p := range o.cfg.Plans {
		if len(o.cfg.Plans) > 1 {
			o.logf("== plan %d/%d: %s ==", i+1, len(o.cfg.Plans), p.Name)
		}
		stop, err := o.runPlan(ctx, p, driver, out)
		if err != nil {
			return out, err
		}
		if stop {
			if left := len(o.cfg.Plans) - i - 1; left > 0 {
				o.logf("fail-fast: %d plan(s) not run", left)
			}
			break
		}
	}
	return out, nil
}

// runPlan creates one suite plan and runs its selected modules, appending each result to out.
func (o *Orchestrator) runPlan(ctx context.Context, p config.Plan, driver *esignet.Driver, out *RunResult) (bool, error) {
	configBody, err := os.ReadFile(p.ConfigFile)
	if err != nil {
		out.Modules = append(out.Modules, planErrorResult(p.Name, o.cfg.Esignet.Provider, fmt.Errorf("read plan config %s: %w", p.ConfigFile, err)))
		o.logf("plan %-45s -> ERROR [read plan config %s: %v]", p.Name, p.ConfigFile, err)
		return o.cfg.Run.FailFast, nil
	}

	plan, err := o.client.CreatePlan(ctx, p.Name, p.Variant, configBody)
	o.client.TakeCalls() // discard setup calls (create plan)
	if err != nil {
		out.Modules = append(out.Modules, planErrorResult(p.Name, o.cfg.Esignet.Provider, err))
		o.logf("plan %-45s -> ERROR [%v]", p.Name, err)
		return o.cfg.Run.FailFast, nil
	}
	o.logf("created plan %s (id=%s, %d modules)", plan.Name, plan.ID, len(plan.Modules))

	sel := o.cfg.Selection(p)
	selected, err := o.selectModules(p.Name, sel, plan.Modules)
	if err != nil {
		out.Modules = append(out.Modules, planErrorResult(p.Name, o.cfg.Esignet.Provider, err))
		o.logf("plan %-45s -> ERROR [%v]", p.Name, err)
		return o.cfg.Run.FailFast, nil
	}
	o.logf("selected %d module(s) for profile=%q filter=%q", len(selected), sel.Profile, sel.Filter)

	// known_issues and skip modules are separated out before execution: they are
	// not run, they just get a report row in the Known / Skipped bucket.
	known := knownMap(sel.KnownIssues)
	skip := nameSet(sel.Skip)

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
		res := o.runModule(ctx, plan, m, driver)
		out.Modules = append(out.Modules, res)
		o.logf("module %-45s -> %s / %s%s", res.Module, dash(res.Result), res.HarnessOutcome, errSuffix(res))
		if o.cfg.Run.FailFast && (res.Result == "FAILED" || res.HarnessError != "") {
			o.logf("fail-fast: stopping after first failure")
			return true, nil
		}
	}
	return false, nil
}

// planErrorResult is the report row for a plan that never got as far as running a module.
func planErrorResult(planName, provider string, err error) result.ModuleResult {
	return result.ModuleResult{
		Surface:        result.SurfaceConformance,
		Plugin:         provider,
		Plan:           planName,
		Module:         "(plan setup)",
		HarnessOutcome: result.OutcomeOK,
		HarnessError:   err.Error(),
		Status:         "NOT_STARTED",
	}
}

// gatedResult builds a not-run report row for a module excluded by config
// (known_issues -> Known bucket, skip -> Skipped bucket).
func gatedResult(plan, provider string, m Module, outcome, detail string) result.ModuleResult {
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

func (o *Orchestrator) runModule(ctx context.Context, plan *PlanResponse, m Module, driver *esignet.Driver) result.ModuleResult {
	res := result.ModuleResult{
		Surface:        result.SurfaceConformance,
		Plugin:         o.cfg.Esignet.Provider,
		Plan:           plan.Name,
		Module:         m.TestModule,
		Variant:        m.Variant,
		HarnessOutcome: result.OutcomeOK,
	}

	if reason := unsupportedReason(m.TestModule, m.Variant); reason != "" {
		res.HarnessOutcome = result.OutcomeSkippedByHarness
		res.OutcomeDetail = "UNSUPPORTED_INTERACTION, detail: " + reason
		res.Result = "SKIPPED"
		res.Status = "NOT_STARTED"
		return res
	}

	o.client.TakeCalls() // discard any stray calls before this module

	start := time.Now()
	test, err := o.client.CreateTest(ctx, m.TestModule, plan.ID)
	if err != nil {
		res.HarnessError = err.Error()
		o.client.TakeCalls() // discard suite plumbing calls (not shown in report)
		res.DurationMs = time.Since(start).Milliseconds()
		return res
	}
	res.TestID = test.ID

	deadline := time.Now().Add(time.Duration(o.cfg.Run.TimeoutSeconds) * time.Second)
	poll := time.Duration(o.cfg.Run.PollIntervalSeconds) * time.Second
	// A zero interval would turn the wait below into a hot loop that hammers the
	// suite for the whole timeout window (wsotp.WaitOTP applies the same floor).
	if poll <= 0 {
		poll = time.Second
	}
	// Per-module driving. One Driver is shared across every module, so both
	// switches are restored before returning — leaving DenyAll on would silently
	// deny consent for every module that follows.
	behavior := behaviorFor(m.TestModule)
	driver.SetConsentPolicy(behavior.consent)
	driver.SetFollowRejection(behavior.followRejection)
	defer func() {
		driver.SetConsentPolicy(esignet.ConsentPolicy{})
		driver.SetFollowRejection(false)
	}()

	visits := map[string]int{}
	visitCount := 0

	for {
		info, err := o.client.GetInfo(ctx, test.ID)
		if err != nil {
			res.HarnessError = err.Error()
			break
		}
		res.Status = info.Status
		if info.Status == "FINISHED" || info.Status == "INTERRUPTED" {
			res.Result = info.Result
			break
		}

		runner, err := o.client.GetRunner(ctx, test.ID)
		if err != nil {
			res.HarnessError = err.Error()
			break
		}
		pending := pendingURLs(runner.Browser, visits, behavior.visitBudget())
		if len(pending) > 0 {
			for _, u := range pending {
				o.driveOne(ctx, driver, u, &res, visitCount < behavior.loadOnlyVisits)
				visits[u]++
				visitCount++
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

	// Final verdict: re-fetch in case the loop exited before reaching a terminal status.
	if info, err := o.client.GetInfo(ctx, test.ID); err == nil {
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
	if raw, err := o.client.GetRawLog(ctx, test.ID); err == nil {
		res.LogItems = buildLogItems(raw)
		res.FailedConditions = failedFromItems(res.LogItems)
	}

	// The report shows only eSignet-thunder traffic; discard the suite plumbing
	// calls (create_test, polls, info, log, deliver) captured on the client.
	o.client.TakeCalls()
	res.Calls = result.CollapseCalls(res.Calls)
	return res
}

// driveOne drives a single authorize URL through eSignet and hands the code back to the suite.
func (o *Orchestrator) driveOne(ctx context.Context, driver *esignet.Driver, authorizeURL string,
	res *result.ModuleResult, loadOnly bool) {
	base, err := o.esignetBase(authorizeURL)
	if err != nil {
		res.HarnessError = err.Error()
		return
	}

	// A load-only visit stops at the login page on purpose, so there is no
	// assertion and no redirect to deliver — the suite drives the next visit.
	if loadOnly {
		flow := driver.RunToLogin(ctx, base, authorizeURL)
		res.FlowTrace.AuthorizeStatus = flow.AuthorizeStatus
		res.FlowTrace.Steps = append(res.FlowTrace.Steps, flow.Steps...)
		res.Calls = append(res.Calls, flow.Calls...)
		if flow.Error != "" {
			res.HarnessError = "eSignet flow: " + flow.Error
		} else if !flow.LoginReached {
			res.HarnessError = "eSignet flow: login page not reached on the load-only visit"
		}
		return
	}

	flow := driver.Run(ctx, base, authorizeURL)
	res.FlowTrace.AuthorizeStatus = flow.AuthorizeStatus
	res.FlowTrace.Steps = append(res.FlowTrace.Steps, flow.Steps...)
	res.FlowTrace.EsignetCallbackStatus = flow.CallbackStatus
	res.Calls = append(res.Calls, flow.Calls...)
	if !flow.OK() {
		res.HarnessError = "eSignet flow: " + flow.Error
		return
	}

	deliver, err := o.client.DeliverCallback(ctx, flow.RedirectURI)
	res.FlowTrace.SuiteCallbackStatus = deliver.SuiteCallbackStatus
	res.FlowTrace.ImplicitSubmitStatus = deliver.ImplicitSubmitStatus
	// deliver's HTTP calls land on the client trace and are discarded at the end of runModule.
	if err != nil {
		res.HarnessError = "deliver callback: " + err.Error()
	}
}

// esignetBase returns the configured eSignet base URL, or derives it from the authorize URL.
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
		// Exact match only.
		if derived != configured {
			return "", fmt.Errorf("ESIGNET_BASE_URL_MISMATCH: configured %q but suite authorize URL implies %q", configured, derived)
		}
		return configured, nil
	}
	return derived, nil
}

// selectModules applies precedence: explicit modules, then profile subset, then filter.
func (o *Orchestrator) selectModules(planName string, sel config.Selection, all []Module) ([]Module, error) {
	byName := map[string]Module{}
	var order []string
	for _, m := range all {
		if _, seen := byName[m.TestModule]; !seen {
			order = append(order, m.TestModule)
		}
		byName[m.TestModule] = m
	}

	var names []string
	switch {
	case len(sel.Modules) > 0:
		names = sel.Modules
	case sel.Profile == "smoke":
		smoke, err := loadSmokeProfile(planName)
		if err != nil {
			return nil, err
		}
		names = smoke
	default: // full
		names = order
	}

	// Apply the filter regex (if any).
	var re *regexp.Regexp
	if sel.Filter != "" {
		var err error
		re, err = regexp.Compile(sel.Filter)
		if err != nil {
			return nil, fmt.Errorf("invalid filter regex %q: %w", sel.Filter, err)
		}
	}

	var out []Module
	for _, n := range names {
		if re != nil && !re.MatchString(n) {
			continue
		}
		if m, ok := byName[n]; ok {
			out = append(out, m)
		} else {
			// Named module not in the plan — keep it so the operator sees the miss.
			out = append(out, Module{TestModule: n})
		}
	}
	if len(out) == 0 {
		return nil, fmt.Errorf("no modules selected for plan %s (profile=%q filter=%q) — plan has %d modules", planName, sel.Profile, sel.Filter, len(all))
	}
	return out, nil
}

type smokeFile struct {
	SuiteVersion string   `json:"suite_version"`
	Plan         string   `json:"plan"`
	Modules      []string `json:"modules"`
}

// loadSmokeProfile reads the smoke module list for one plan. The file is keyed
// by plan name, so a multi-plan run needs one per plan that uses profile=smoke.
func loadSmokeProfile(planName string) ([]string, error) {
	path := filepath.Join("data", "conformance", planName+".smoke.json")
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

func unsupportedReason(module string, variant map[string]any) string {
	// form_post is not a module name: the suite runs the ordinary modules and
	// carries the mode in the plan variant, so a name-substring hint would never
	// fire. The harness reads the code from the redirect query, which a form-post
	// response body does not have.
	if mode, ok := variant["response_mode"].(string); ok && strings.EqualFold(mode, "form_post") {
		return "form_post"
	}
	lm := strings.ToLower(module)
	for hint, reason := range unsupportedModuleHints {
		if strings.Contains(lm, hint) {
			return reason
		}
	}
	return ""
}

// pendingURLs returns the browser URLs still to be driven. visits counts how
// many times this run has already driven each URL and budget caps that count —
// with the default budget of 1 this is the original "drive each URL once"
// behaviour. A budget above 1 lets a module be sent to the same authorize URL
// again, which par-ensure-reused-request-uri needs: its first visit deliberately
// does not authenticate, so the same request_uri must be drivable a second time.
func pendingURLs(b Browser, visits map[string]int, budget int) []string {
	if budget < 1 {
		budget = 1
	}
	visited := map[string]bool{}
	for _, v := range b.Visited {
		visited[v] = true
	}
	var out []string
	for _, u := range b.URLs {
		if !visited[u] && visits[u] < budget {
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

// buildLogItems converts the raw suite condition log into report LogItems.
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

// prettyValue re-indents a JSON string or object and shows other values as-is.
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

// truncateStr cuts s to at most n bytes for a log detail value.
func truncateStr(s string, n int) string {
	return textx.Truncate(s, n, "\n…(truncated)")
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
