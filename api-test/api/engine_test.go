package api

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/cucumber/godog"
)

// TestFeatures is the godog entry point.
func TestFeatures(t *testing.T) {
	base := os.Getenv("ESIGNET_BASE_URL")
	if base == "" {
		t.Skip("ESIGNET_BASE_URL not set — skipping api surfaces")
	}

	// A missing features tree is a setup error, not an empty test run: godog
	// reports "0 scenarios" for it, suite.Run's status is intentionally
	// discarded below, and the envelope would then be written empty — passing
	// run-all.sh's exists-check and consolidating to a green report with no api
	// rows at all. Fail here instead, naming the path that was not found.
	if dir := featuresDir(); !isDir(dir) {
		t.Fatalf("features directory %q not found (set API_FEATURES_DIR to the data/features tree)", dir)
	}

	coll := &Collector{}
	tags := os.Getenv("GODOG_TAGS")
	if tags == "" {
		// Default: always run flow-execute; include client-mgmt only when the
		// Keycloak admin creds are configured, else it would fail on auth.
		tags = "@flow-execute"
		// Authorize-negative cases need a pre-registered client id; include them
		// only when FLOW_CLIENT_ID is set (else they'd have no client to drive).
		if os.Getenv("FLOW_CLIENT_ID") != "" {
			tags += ",@flow-authz-neg"
		}
		if os.Getenv("KEYCLOAK_CLIENT_SECRET") != "" {
			tags += ",@client-mgmt" // godog: comma = OR
			// The PMS-backed client-mgmt feature is mosipid-only: it needs an onboarded partner and policy.
			if strings.EqualFold(os.Getenv("AUTHN_PROVIDER"), "mosip") && os.Getenv("PMS_BASE_URL") != "" {
				tags += ",@client-mgmt-pms"
			}
		}
	}

	suite := godog.TestSuite{
		ScenarioInitializer: func(sc *godog.ScenarioContext) { InitScenario(sc, coll) },
		Options: &godog.Options{
			Format: "pretty",
			Paths:  []string{featuresDir()},
			Tags:   tags,
		},
	}
	_ = suite.Run() // capture results regardless of pass/fail

	rows := coll.Rows()
	// A gated-out surface becomes an explicit ENV_NOT_READY row, so the report never silently omits it.
	if os.Getenv("GODOG_TAGS") == "" {
		plugin := os.Getenv("AUTHN_PROVIDER")
		if plugin == "" {
			plugin = "mock"
		}
		if os.Getenv("KEYCLOAK_CLIENT_SECRET") == "" {
			rows = append(rows, Envelope{
				Surface:        "client-mgmt",
				Plugin:         plugin,
				Module:         "client-mgmt (not run)",
				HarnessOutcome: "ENV_NOT_READY",
				OutcomeDetail:  "KEYCLOAK_TOKEN_URL/CLIENT_ID/CLIENT_SECRET not set — admin auth unavailable",
			})
		}
		if os.Getenv("FLOW_CLIENT_ID") == "" {
			rows = append(rows, Envelope{
				Surface:        "flow-execute",
				Plugin:         plugin,
				Module:         "authorize-negative (not run)",
				HarnessOutcome: "ENV_NOT_READY",
				OutcomeDetail:  "FLOW_CLIENT_ID not set — authorize-endpoint negatives need a pre-registered client",
			})
		}
		if strings.EqualFold(plugin, "mosip") && os.Getenv("PMS_BASE_URL") == "" {
			rows = append(rows, Envelope{
				Surface:        "client-mgmt",
				Plugin:         plugin,
				Module:         "create-update-client-pms (not run)",
				HarnessOutcome: "ENV_NOT_READY",
				OutcomeDetail:  "PMS_BASE_URL not set — PMS-backed client-mgmt needs an onboarded partner+policy",
			})
		}
	}

	out := os.Getenv("API_ENVELOPE_OUT")
	if out == "" {
		out = filepath.Join("..", "out", "api-envelope.json")
	}
	if err := writeEnvelope(out, rows); err != nil {
		t.Fatalf("write envelope: %v", err)
	}
	t.Logf("api: wrote %d envelope row(s) to %s", len(rows), out)
}

// featuresDir locates the Gherkin tree. The feature files live in the shared
// data/ folder rather than inside this module, so the default is relative to the
// module directory this test runs from; API_FEATURES_DIR overrides it for a
// container layout that puts them elsewhere.
func featuresDir() string {
	if d := os.Getenv("API_FEATURES_DIR"); d != "" {
		return d
	}
	return filepath.Join("..", "data", "features")
}

// isDir reports whether path exists and is a directory.
func isDir(path string) bool {
	st, err := os.Stat(path)
	return err == nil && st.IsDir()
}

// writeEnvelope persists the collected rows for the consolidation runner.
func writeEnvelope(path string, rows []Envelope) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		return err
	}
	data, err := json.MarshalIndent(rows, "", "  ")
	if err != nil {
		return err
	}
	// os.WriteFile leaves an existing file's mode alone, so re-apply 0600 explicitly.
	f, err := os.OpenFile(path, os.O_WRONLY|os.O_CREATE|os.O_TRUNC, 0o600)
	if err != nil {
		return err
	}
	if err := f.Chmod(0o600); err != nil {
		_ = f.Close()
		return err
	}
	if _, err := f.Write(data); err != nil {
		_ = f.Close()
		return err
	}
	return f.Close()
}
