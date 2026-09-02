package api

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/cucumber/godog"
)

// adminAuthAvailable reports whether the client-mgmt scenarios have a way to
// present an admin bearer: either all three Keycloak client-credentials
// fields to mint one (iAuthenticateAsAdmin in steps.go requires the same
// three, so checking fewer here would tag @client-mgmt in only to fail every
// scenario on that missing field), or an ADMIN_TOKEN supplied directly for a
// target that does not enforce scope (a locally started server installs no
// scope middleware). Without either, the scenarios would fail on auth rather
// than on anything they are meant to test, so they are gated out.
func adminAuthAvailable() bool {
	return os.Getenv("ADMIN_TOKEN") != "" ||
		(os.Getenv("KEYCLOAK_TOKEN_URL") != "" &&
			os.Getenv("KEYCLOAK_CLIENT_ID") != "" &&
			os.Getenv("KEYCLOAK_CLIENT_SECRET") != "")
}

// TestFeatures is the godog entry point.
func TestFeatures(t *testing.T) {
	base := os.Getenv("MOSIP_ESIGNET_BASE_URL")
	if base == "" {
		t.Skip("MOSIP_ESIGNET_BASE_URL not set — skipping api surfaces")
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
		if adminAuthAvailable() {
			tags += ",@client-mgmt" // godog: comma = OR
			// Enforcement of a client's INACTIVE status at the authorize
			// endpoint. Grouped with admin auth rather than with FLOW_CLIENT_ID
			// above: the scenarios deactivate the client they drive, so they
			// register one of their own instead of using the shared id.
			tags += ",@inactive-client"
			// The PMS-backed client-mgmt feature is mosipid-only: it needs an onboarded partner and policy.
			if strings.EqualFold(os.Getenv("MOSIP_ESIGNET_AUTHN_PROVIDER"), "mosip") && os.Getenv("PMS_BASE_URL") != "" {
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
		plugin := os.Getenv("MOSIP_ESIGNET_AUTHN_PROVIDER")
		if plugin == "" {
			plugin = "mock"
		}
		if !adminAuthAvailable() {
			rows = append(rows, Envelope{
				Surface:        "client-mgmt",
				Plugin:         plugin,
				Module:         "client-mgmt (not run)",
				HarnessOutcome: "ENV_NOT_READY",
				OutcomeDetail:  "KEYCLOAK_TOKEN_URL/CLIENT_ID/CLIENT_SECRET not set and no ADMIN_TOKEN — admin auth unavailable",
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
		if !adminAuthAvailable() {
			rows = append(rows, Envelope{
				Surface:        "flow-execute",
				Plugin:         plugin,
				Module:         "inactive-client (not run)",
				HarnessOutcome: "ENV_NOT_READY",
				OutcomeDetail:  "no admin auth — deactivating a client to drive authorize with it needs client-mgmt write access",
			})
		}
		// Either half missing hides this surface: the tag is only added inside the
		// admin-auth branch above, so missing admin auth drops it just as
		// silently as an unset PMS_BASE_URL would.
		if strings.EqualFold(plugin, "mosip") &&
			(os.Getenv("PMS_BASE_URL") == "" || !adminAuthAvailable()) {
			rows = append(rows, Envelope{
				Surface:        "client-mgmt",
				Plugin:         plugin,
				Module:         "create-update-client-pms (not run)",
				HarnessOutcome: "ENV_NOT_READY",
				OutcomeDetail:  "PMS_BASE_URL/KEYCLOAK_CLIENT_SECRET not set — PMS-backed client-mgmt needs admin auth plus an onboarded partner+policy",
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
