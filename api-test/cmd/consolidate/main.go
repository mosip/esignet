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
	flag.Parse()

	var (
		rows           []result.ModuleResult
		configJSON     string
		planConfigJSON string
	)

	if *confPath != "" {
		cr, cfg, pcfg, err := loadConformance(*confPath)
		if err != nil {
			log.Fatalf("load conformance %s: %v", *confPath, err)
		}
		stampSurface(cr, result.SurfaceConformance)
		rows = append(rows, cr...)
		configJSON, planConfigJSON = cfg, pcfg
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

	htmlPath, err := report.Write(*outDir, *plan, *plugin, configJSON, planConfigJSON, rows)
	if err != nil {
		log.Fatalf("write report: %v", err)
	}

	sum := result.Summarize(rows)
	fmt.Printf("\n== Consolidated report — %s · %s ==\n", *plan, *plugin)
	for _, g := range result.GroupBySurface(rows) {
		gs := g.Summary
		fmt.Printf("  %-14s total=%d passed=%d failed=%d skipped=%d errored=%d\n",
			g.Surface, gs.Total, gs.Passed, gs.Failed, gs.Skipped, gs.Errored)
	}
	fmt.Printf("  %-14s total=%d passed=%d failed=%d skipped=%d errored=%d\n",
		"ALL", sum.Total, sum.Passed, sum.Failed, sum.Skipped, sum.Errored)
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

// loadConformance reads a conformance sidecar dump: {config, plan_config,
// modules:[...]}. Returns the module rows plus the two config blobs (re-marshaled
// to strings) for the report's configuration panel.
func loadConformance(path string) (rows []result.ModuleResult, configJSON, planConfigJSON string, err error) {
	data, rerr := os.ReadFile(path)
	if rerr != nil {
		return nil, "", "", rerr
	}
	var side struct {
		Config     json.RawMessage       `json:"config"`
		PlanConfig json.RawMessage       `json:"plan_config"`
		Modules    []result.ModuleResult `json:"modules"`
	}
	if uerr := json.Unmarshal(data, &side); uerr != nil {
		return nil, "", "", uerr
	}
	return side.Modules, string(side.Config), string(side.PlanConfig), nil
}
