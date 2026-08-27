// Package result holds the outcome types every test surface produces and the report consumes.
package result

import "sort"

// HarnessOutcome distinguishes a harness-side disposition from the suite's own
// verdict (Result). See plan doc §5.
const (
	OutcomeOK               = "OK"
	OutcomeSkippedByHarness = "SKIPPED_BY_HARNESS"
	OutcomeKnownIssue       = "KNOWN_ISSUE"
	OutcomeUnsupportedProv  = "UNSUPPORTED_PROVIDER"
	OutcomeEnvNotReady      = "ENV_NOT_READY"
)

// Surface names the test surface a row came from, so one report can carry all three.
const (
	SurfaceConformance = "conformance"
	SurfaceClientMgmt  = "client-mgmt"
	SurfaceFlowExecute = "flow-execute"
	SurfaceE2E         = "e2e"
)

// ModuleResult is one row of the consolidated report.
type ModuleResult struct {
	Surface string // conformance | client-mgmt | flow-execute (which surface produced this row)
	Plugin  string // mock | mosip | sunbird (the run's target plugin)

	Plan    string
	Module  string
	TestID  string
	Variant map[string]any

	Status string // suite lifecycle: FINISHED / INTERRUPTED / WAITING(timeout)
	Result string // suite verdict: PASSED / FAILED / WARNING / REVIEW / SKIPPED

	HarnessOutcome string // OK | SKIPPED_BY_HARNESS | KNOWN_ISSUE | UNSUPPORTED_PROVIDER | ENV_NOT_READY
	OutcomeDetail  string

	DurationMs int64

	FailedConditions []Condition
	LogItems         []LogItem // full suite condition log, rendered UI-style
	FlowTrace        FlowTrace
	Calls            []HTTPCall // eSignet-thunder request/response trace for manual debugging
	HarnessError     string

	// Assertions is the full field-level validation trace for this case, one entry per check.
	Assertions []Assertion
}

// Assertion is one expected-vs-actual check within a test case.
type Assertion struct {
	Field    string // what was checked, e.g. "HTTP status" or "JSON errors.0.errorCode"
	Expected string
	Actual   string
	Passed   bool
}

// LogItem is one entry of the suite's per-test condition log, carried in full for the report.
type LogItem struct {
	Time        string
	Src         string
	Msg         string
	Kind        string // SUCCESS | FAILURE | WARNING | INFO | REQUEST | RESPONSE | ...
	Requirement string
	Block       bool        // suite "-START-BLOCK-" section header, rendered as a banner
	Details     []LogDetail // remaining fields, one per row (JSON values pretty-printed)
	MoreN       int
}

// LogDetail is one field of a log entry's expandable detail, with its value
// pretty-printed (JSON is re-indented and unescaped, matching the suite UI).
type LogDetail struct {
	Key   string
	Value string
}

// HTTPCall is a captured request/response pair (with headers and cookies) for
// one API call, so the report can be used to debug the exact wire traffic.
type HTTPCall struct {
	Seq         int
	At          int64 // capture time (unix nanos) for chronological ordering
	Repeat      int   // collapsed identical repeats (e.g. polled /api/info)
	Label       string
	Method      string
	URL         string
	ReqHeaders  map[string][]string
	ReqCookies  string
	ReqBody     string
	Status      int
	RespHeaders map[string][]string
	RespCookies string // Set-Cookie values
	RespBody    string
}

// CollapseCalls sorts calls chronologically and merges consecutive identical ones with a Repeat count.
func CollapseCalls(calls []HTTPCall) []HTTPCall {
	// Sort a copy: every other transform in this harness leaves the caller's slice untouched.
	sorted := append([]HTTPCall(nil), calls...)
	sort.SliceStable(sorted, func(i, j int) bool { return sorted[i].At < sorted[j].At })
	var out []HTTPCall
	for _, c := range sorted {
		if n := len(out); n > 0 {
			p := &out[n-1]
			if p.Method == c.Method && p.URL == c.URL && p.Status == c.Status &&
				p.ReqBody == c.ReqBody && p.RespBody == c.RespBody {
				p.Repeat++
				continue
			}
		}
		c.Repeat = 1
		out = append(out, c)
	}
	for i := range out {
		out[i].Seq = i + 1
	}
	return out
}

// Condition is a single FAILURE/WARNING entry from the suite's condition log.
type Condition struct {
	Src         string
	Msg         string
	Result      string // FAILURE / WARNING
	Requirement string
}

// FlowTrace records the harness-side steps so a harness failure is visibly
// distinct from a genuine eSignet conformance failure.
type FlowTrace struct {
	AuthorizeStatus       int
	Steps                 []FlowStep
	EsignetCallbackStatus int // POST /oauth2/auth/callback -> redirect_uri
	SuiteCallbackStatus   int // GET  {redirect_uri}?code&state -> implicitCallback HTML
	ImplicitSubmitStatus  int // POST {implicitSubmitUrl} -> 204
}

// FlowStep is one /flow/execute round-trip.
type FlowStep struct {
	FlowStatus string
	Inputs     []string
	Action     string
}

// Summary is the aggregate roll-up for the report tiles.
type Summary struct {
	Total, Passed, Failed, Warning, Review, Skipped, Known, Errored int
}

// Summarize computes the tile counts.
func Summarize(rs []ModuleResult) Summary {
	var s Summary
	for _, r := range rs {
		s.Total++
		switch {
		case r.HarnessOutcome == OutcomeKnownIssue:
			s.Known++
		case r.HarnessOutcome == OutcomeSkippedByHarness || r.Result == "SKIPPED":
			s.Skipped++
		case r.HarnessError != "" || r.HarnessOutcome == OutcomeEnvNotReady || r.HarnessOutcome == OutcomeUnsupportedProv:
			s.Errored++
		case r.Result == "PASSED":
			s.Passed++
		case r.Result == "FAILED":
			s.Failed++
		case r.Result == "WARNING":
			s.Warning++
		case r.Result == "REVIEW":
			s.Review++
		default:
			s.Errored++
		}
	}
	return s
}

// HasFailures reports whether the run should exit non-zero: any suite FAILED or
// any harness error.
func (s Summary) HasFailures() bool { return s.Failed > 0 || s.Errored > 0 }

// NotPassed is every module that did not pass, was not deliberately skipped and
// is not a tracked known issue — so Passed + NotPassed + Skipped + Known is
// always Total, by construction rather than by keeping a list of buckets in
// step. It folds in Errored, Warning and Review.
//
// This is what the report filename carries as its failure count. The filename
// used to print Failed alone, which silently dropped the Errored modules: a run
// of 170 with 71 errored was named t-170_p-68_f-28_sk-3_ki-0, whose parts sum
// to 99 and leave a reader hunting for the other 71. An errored module is still
// a test that did not pass. The tiles in the report keep the buckets separate,
// which is where the failed/errored distinction actually helps.
func (s Summary) NotPassed() int { return s.Total - s.Passed - s.Skipped - s.Known }

// SurfaceGroup is the rows of one surface plus that surface's own tile counts.
type SurfaceGroup struct {
	Surface string
	Plan    string
	Summary Summary
	Rows    []ModuleResult
}

// surfaceOrder fixes the display order; unknown/blank surfaces sort last.
var surfaceOrder = map[string]int{
	SurfaceConformance: 0,
	SurfaceClientMgmt:  1,
	SurfaceFlowExecute: 2,
	SurfaceE2E:         3,
}

// GroupBySurface splits results into ordered groups, one per (surface, plan).
func GroupBySurface(rs []ModuleResult) []SurfaceGroup {
	type key struct{ surface, plan string }
	byKey := map[key][]ModuleResult{}
	var order []key
	for _, r := range rs {
		k := key{surface: r.Surface, plan: r.Plan}
		if k.surface == "" {
			k.surface = SurfaceConformance
		}
		if _, seen := byKey[k]; !seen {
			order = append(order, k)
		}
		byKey[k] = append(byKey[k], r)
	}
	sort.SliceStable(order, func(i, j int) bool {
		oi, iok := surfaceOrder[order[i].surface]
		oj, jok := surfaceOrder[order[j].surface]
		if iok && jok && oi != oj {
			return oi < oj
		}
		if iok != jok {
			return iok // known surfaces before unknown
		}
		if !iok && !jok && order[i].surface != order[j].surface {
			return order[i].surface < order[j].surface
		}
		return false // same surface: keep the plans in run order
	})
	out := make([]SurfaceGroup, 0, len(order))
	for _, k := range order {
		out = append(out, SurfaceGroup{Surface: k.surface, Plan: k.plan, Summary: Summarize(byKey[k]), Rows: byKey[k]})
	}
	return out
}
