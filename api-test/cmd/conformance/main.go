// Command conformance runs the eSignet ⇄ OpenID Conformance Suite pure-API test
// harness (Phase 1 / v1.0): drive the suite's oidcc-test-plan modules through
// eSignet's flow-execute login and produce a consolidated HTML report.
//
// Usage:
//
//	conformance -config config.json
//
// Config may come from the JSON file, environment variables (which override the
// file), or a mix. See README.md and plan doc §8b/§8f.
package main

import (
	"flag"
	"fmt"
	"log"
	"os"

	"github.com/mosip/esignet/api-test/internal/config"
	"github.com/mosip/esignet/api-test/internal/orchestrator"
	"github.com/mosip/esignet/api-test/internal/report"
	"github.com/mosip/esignet/api-test/internal/result"
)

func main() {
	configPath := flag.String("config", "config.json", "path to the harness config (env vars override its values)")
	flag.Parse()

	logger := log.New(os.Stderr, "", log.LstdFlags)
	logf := func(format string, args ...any) { logger.Printf(format, args...) }

	cfgPath, mustExist := config.ResolvePath(*configPath, config.FlagExplicit("config"))
	cfg, err := config.Load(cfgPath, mustExist)
	if err != nil {
		logger.Printf("config error: %v", err)
		os.Exit(2)
	}
	// This binary IS the conformance surface, so enforce its requirements even
	// when the config's run.surfaces omits it (a direct single-surface run).
	if err := cfg.ValidateSurface(config.SurfaceConformance); err != nil {
		logger.Printf("config error: %v", err)
		os.Exit(2)
	}

	orch := orchestrator.New(cfg, logf)
	run, err := orch.Run()
	if err != nil {
		logger.Printf("run error: %v", err)
		// Still try to write whatever we have.
		if run != nil && len(run.Modules) > 0 {
			if p, werr := report.Write(cfg.Run.ReportDir, cfg.Plan.Name, cfg.Esignet.Provider, cfg.Redacted(), config.RedactedPlanConfig(cfg.Plan.ConfigFile), run.Modules); werr == nil {
				logger.Printf("partial report: %s", p)
			}
		}
		os.Exit(2)
	}

	htmlPath, err := report.Write(cfg.Run.ReportDir, cfg.Plan.Name, cfg.Esignet.Provider, cfg.Redacted(), config.RedactedPlanConfig(cfg.Plan.ConfigFile), run.Modules)
	if err != nil {
		logger.Printf("report error: %v", err)
		os.Exit(2)
	}

	summary := result.Summarize(run.Modules)
	fmt.Printf("\n== eSignet Conformance — %s ==\n", cfg.Plan.Name)
	fmt.Printf("total=%d passed=%d failed=%d warning=%d review=%d skipped=%d known=%d errored=%d\n",
		summary.Total, summary.Passed, summary.Failed, summary.Warning,
		summary.Review, summary.Skipped, summary.Known, summary.Errored)
	fmt.Printf("report: %s\n", htmlPath)

	if summary.HasFailures() {
		os.Exit(1)
	}
}
