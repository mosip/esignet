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
	"context"
	"flag"
	"fmt"
	"log"
	"os"
	"strings"

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

	// One options value for both the partial and the final report, so a crashed
	// run is written with exactly the same plans/config panels as a clean one.
	opts := report.Options{
		Dir:         cfg.Run.ReportDir,
		Plans:       cfg.PlanNames(),
		Provider:    cfg.Esignet.Provider,
		ConfigJSON:  cfg.Redacted(),
		PlanConfigs: planConfigs(cfg),
		ShowSecrets: cfg.Run.DebugShowSecrets,
	}

	orch := orchestrator.New(cfg, logf)
	run, err := orch.Run(context.Background())
	if err != nil {
		logger.Printf("run error: %v", err)
		// Still try to write whatever we have.
		if run != nil && len(run.Modules) > 0 {
			opts.Results = run.Modules
			// Log the write failure too: without it the operator sees only the
			// run error and cannot tell the partial report was lost as well.
			p, werr := report.Write(opts)
			if werr != nil {
				logger.Printf("partial report error: %v", werr)
			} else {
				logger.Printf("partial report: %s", p)
			}
		}
		os.Exit(2)
	}

	opts.Results = run.Modules
	htmlPath, err := report.Write(opts)
	if err != nil {
		logger.Printf("report error: %v", err)
		os.Exit(2)
	}

	summary := result.Summarize(run.Modules)
	fmt.Printf("\n== eSignet Conformance — %s ==\n", strings.Join(cfg.PlanNames(), " + "))
	fmt.Printf("total=%d passed=%d failed=%d warning=%d review=%d skipped=%d known=%d errored=%d\n",
		summary.Total, summary.Passed, summary.Failed, summary.Warning,
		summary.Review, summary.Skipped, summary.Known, summary.Errored)
	fmt.Printf("report: %s\n", htmlPath)

	if summary.HasFailures() {
		os.Exit(1)
	}
}

// planConfigs reads each plan's suite config for the report's configuration
// panel, redacted (private JWKS masked) by config.RedactedPlanConfig.
func planConfigs(cfg *config.Config) []report.PlanConfig {
	out := make([]report.PlanConfig, 0, len(cfg.Plans))
	for _, p := range cfg.Plans {
		out = append(out, report.PlanConfig{Plan: p.Name, JSON: config.RedactedPlanConfig(p.ConfigFile)})
	}
	return out
}
