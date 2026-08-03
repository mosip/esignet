// Command consolidate merges the results of the three test surfaces into ONE
// per-plugin HTML report (plan doc §6). It reads:
//
//   - the bdd envelope JSON (client-mgmt + flow/execute), produced by the godog
//     module as a JSON array of result.ModuleResult-shaped rows, and
//   - optionally a conformance sidecar JSON (the {config, plan_config, modules}
//     dump a conformance run already writes),
//
// tags any surface-less/plugin-less rows with sane defaults, and renders them
// through the same report package the conformance run uses. The renderer only
// ever sees result.ModuleResult — it does not know godog exists.
//
// Usage:
//
//	consolidate -bdd out/bdd-envelope.json -conformance out/<run>.json -plugin mock -out out
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"strings"

	"github.com/mosip/esignet/api-test/internal/report"
	"github.com/mosip/esignet/api-test/internal/result"
)

func main() {
	bddPath := flag.String("bdd", "", "path to the bdd envelope JSON (godog surfaces)")
	e2ePath := flag.String("e2e", "", "path to the e2e envelope JSON (optional)")
	confPath := flag.String("conformance", "", "path to a conformance sidecar JSON (optional)")
	outDir := flag.String("out", "out", "report output directory")
	plan := flag.String("plan", "oidcc-test-plan", "plan name (report header/filename)")
	plugin := flag.String("plugin", "mock", "plugin/provider the run targeted")
	// This binary does not read the harness config (it merges envelopes produced
	// by binaries that did), so run.debug_show_secrets reaches it as a flag that
	// run-all.sh forwards. Default false keeps the safe behaviour for a direct run.
	showSecrets := flag.Bool("show-secrets", false, "leave the captured wire trace unredacted (run.debug_show_secrets)")
	flag.Parse()

	var (
		rows        []result.ModuleResult
		configJSON  string
		planConfigs []report.PlanConfig
	)

	if *confPath != "" {
		cr, cfg, pcfgs, err := loadConformance(*confPath)
		if err != nil {
			log.Fatalf("load conformance %s: %v", *confPath, err)
		}
		stampSurface(cr, result.SurfaceConformance)
		rows = append(rows, cr...)
		configJSON, planConfigs = cfg, pcfgs
	}

	if *bddPath != "" {
		br, err := loadEnvelope(*bddPath)
		if err != nil {
			log.Fatalf("load bdd envelope %s: %v", *bddPath, err)
		}
		stampSurface(br, result.SurfaceClientMgmt)
		rows = append(rows, br...)
	}

	if *e2ePath != "" {
		er, err := loadEnvelope(*e2ePath)
		if err != nil {
			log.Fatalf("load e2e envelope %s: %v", *e2ePath, err)
		}
		stampSurface(er, result.SurfaceE2E)
		rows = append(rows, er...)
	}

	if len(rows) == 0 {
		log.Fatalf("nothing to consolidate: pass -conformance, -bdd and/or -e2e")
	}

	// Stamp defaults so pre-existing/plain rows still group correctly.
	for i := range rows {
		if rows[i].Plugin == "" {
			rows[i].Plugin = *plugin
		}
	}

	// The plans a run covered are recorded on its rows, so a two-plan conformance
	// sidecar names both here without the caller having to repeat them; -plan is
	// the fallback for envelopes that carry none.
	plans := plansFromRows(rows)
	if len(plans) == 0 {
		plans = []string{*plan}
	}

	htmlPath, err := report.Write(report.Options{
		Dir:         *outDir,
		Plans:       plans,
		Provider:    *plugin,
		ConfigJSON:  configJSON,
		PlanConfigs: planConfigs,
		ShowSecrets: *showSecrets,
		Results:     rows,
	})
	if err != nil {
		log.Fatalf("write report: %v", err)
	}

	sum := result.Summarize(rows)
	fmt.Printf("\n== Consolidated report — %s · %s ==\n", strings.Join(plans, " + "), *plugin)

	// One label per group, plan-qualified only for a multi-plan run; the column is
	// widened to the longest so the counts stay aligned under a plan name that is
	// far longer than "conformance".
	groups := result.GroupBySurface(rows)
	labels := make([]string, len(groups))
	width := len("ALL")
	for i, g := range groups {
		labels[i] = g.Surface
		if g.Plan != "" && len(plans) > 1 {
			labels[i] = g.Surface + "/" + g.Plan
		}
		width = max(width, len(labels[i]))
	}
	line := func(label string, s result.Summary) {
		fmt.Printf("  %-*s total=%d passed=%d failed=%d skipped=%d errored=%d\n",
			width, label, s.Total, s.Passed, s.Failed, s.Skipped, s.Errored)
	}
	for i, g := range groups {
		line(labels[i], g.Summary)
	}
	line("ALL", sum)
	fmt.Printf("report: %s\n", htmlPath)

	if sum.HasFailures() {
		os.Exit(1)
	}
}

// stampSurface defaults any surface-less row to the surface of the source it was
// loaded from, so a partial writer can never land e2e/bdd rows in the
// conformance section of the report.
func stampSurface(rows []result.ModuleResult, surface string) {
	for i := range rows {
		if rows[i].Surface == "" {
			rows[i].Surface = surface
		}
	}
}

// loadEnvelope reads a JSON array of result.ModuleResult-shaped rows (the godog
// module writes field names that match, so this unmarshals directly).
func loadEnvelope(path string) ([]result.ModuleResult, error) {
	data, err := os.ReadFile(path)
	if err != nil {
		return nil, err
	}
	var rows []result.ModuleResult
	if err := json.Unmarshal(data, &rows); err != nil {
		return nil, err
	}
	return rows, nil
}

// plansFromRows lists the distinct plans the rows came from, in first-seen
// order. Rows from the bdd/e2e surfaces carry no plan and are ignored.
func plansFromRows(rows []result.ModuleResult) []string {
	seen := map[string]bool{}
	var out []string
	for _, r := range rows {
		if r.Plan == "" || seen[r.Plan] {
			continue
		}
		seen[r.Plan] = true
		out = append(out, r.Plan)
	}
	return out
}

// loadConformance reads a conformance sidecar dump: {config, plan_configs,
// modules:[...]}. Returns the module rows plus the config blobs (re-marshaled to
// strings) for the report's configuration panel.
//
// plan_config (singular) is the shape written before a run could cover several
// plans; it is still read so an older sidecar consolidates with its panel intact.
func loadConformance(path string) (rows []result.ModuleResult, configJSON string, planConfigs []report.PlanConfig, err error) {
	data, rerr := os.ReadFile(path)
	if rerr != nil {
		return nil, "", nil, rerr
	}
	var side struct {
		Config      json.RawMessage `json:"config"`
		PlanConfig  json.RawMessage `json:"plan_config"`
		PlanConfigs []struct {
			Plan   string          `json:"plan"`
			Config json.RawMessage `json:"config"`
		} `json:"plan_configs"`
		Modules []result.ModuleResult `json:"modules"`
	}
	if uerr := json.Unmarshal(data, &side); uerr != nil {
		return nil, "", nil, uerr
	}
	for _, pc := range side.PlanConfigs {
		planConfigs = append(planConfigs, report.PlanConfig{Plan: pc.Plan, JSON: string(pc.Config)})
	}
	if len(planConfigs) == 0 && len(side.PlanConfig) > 0 {
		planConfigs = append(planConfigs, report.PlanConfig{JSON: string(side.PlanConfig)})
	}
	return side.Modules, string(side.Config), planConfigs, nil
}
