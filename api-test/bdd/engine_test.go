package bdd

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"

	"github.com/cucumber/godog"
)

// TestFeatures is the godog entry point. It runs the .feature files against the
// live eSignet base (ESIGNET_BASE_URL), collects one Envelope per scenario, and
// writes them to BDD_ENVELOPE_OUT (default ../out/bdd-envelope.json) for the
// consolidation runner to fold into the report. Scenario failures do NOT fail
// the test binary — the report is the source of truth for pass/fail.
func TestFeatures(t *testing.T) {
	base := os.Getenv("ESIGNET_BASE_URL")
	if base == "" {
		t.Skip("ESIGNET_BASE_URL not set — skipping bdd surfaces")
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
			// The PMS-backed client-mgmt feature is mosipid-only (needs an
			// onboarded partner+policy); include it only for the mosip provider
			// with a PMS base configured, so it never runs for mock/sunbird.
			if strings.EqualFold(os.Getenv("AUTHN_PROVIDER"), "mosip") && os.Getenv("PMS_BASE_URL") != "" {
				tags += ",@client-mgmt-pms"
			}
		}
	}

	suite := godog.TestSuite{
		ScenarioInitializer: func(sc *godog.ScenarioContext) { InitScenario(sc, coll) },
		Options: &godog.Options{
			Format: "pretty",
			Paths:  []string{"features"},
			Tags:   tags,
		},
	}
	_ = suite.Run() // capture results regardless of pass/fail

	rows := coll.Rows()
	// If a surface was gated out, surface it as an explicit ENV_NOT_READY row so
	// the report shows the section instead of silently omitting it — an omitted
	// section reads as a false green during conformance sign-off.
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
	}

	out := os.Getenv("BDD_ENVELOPE_OUT")
	if out == "" {
		out = filepath.Join("..", "out", "bdd-envelope.json")
	}
	if err := writeEnvelope(out, rows); err != nil {
		t.Fatalf("write envelope: %v", err)
	}
	t.Logf("bdd: wrote %d envelope row(s) to %s", len(rows), out)
}

// writeEnvelope persists the collected rows for the consolidation runner. The
// envelope carries the raw request/response trace, so it is owner-only: it is an
// intermediate artifact that the report's own redaction pass has not run over
// yet (that happens in internal/report on the consolidated results).
func writeEnvelope(path string, rows []Envelope) error {
	if err := os.MkdirAll(filepath.Dir(path), 0o750); err != nil {
		return err
	}
	data, err := json.MarshalIndent(rows, "", "  ")
	if err != nil {
		return err
	}
	return os.WriteFile(path, data, 0o600)
}
