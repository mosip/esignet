package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// writeConfig writes a minimal conformance-only config that passes validate(),
// with extra conformance fields spliced in, and returns its path. Scoping it to
// the conformance surface keeps these cases off the bdd/e2e requirements.
func writeConfig(t *testing.T, conformance string) string {
	t.Helper()
	dir := t.TempDir()
	plan := filepath.Join(dir, "plan.json")
	if err := os.WriteFile(plan, []byte(`{}`), 0o600); err != nil {
		t.Fatal(err)
	}
	cfg := filepath.Join(dir, "config.json")
	body := `{"conformance":{"base_url":"https://suite.example"` + conformance + `},` +
		`"plan":{"config_file":` + jsonString(plan) + `},` +
		`"run":{"surfaces":["conformance"]}}`
	if err := os.WriteFile(cfg, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	return cfg
}

// load is the common call for these tests: the file exists, so mustExist is
// irrelevant to what they are checking.
func load(t *testing.T, path string) (*Config, error) {
	t.Helper()
	return Load(path, false)
}

func jsonString(s string) string { return `"` + strings.ReplaceAll(s, `\`, `\\`) + `"` }

// TLS verification must be on unless something explicitly turns it off, so an
// omitted tls_verify cannot silently accept forged certificates.
func TestTLSVerifyFailsClosed(t *testing.T) {
	cases := []struct {
		name        string
		conformance string
		want        bool
	}{
		{"omitted", ``, true},
		{"explicit false", `,"tls_verify":false`, false},
		{"explicit true", `,"tls_verify":true`, true},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			c, err := load(t, writeConfig(t, tc.conformance))
			if err != nil {
				t.Fatalf("Load: %v", err)
			}
			if c.Conformance.TLSVerify != tc.want {
				t.Errorf("TLSVerify = %v, want %v", c.Conformance.TLSVerify, tc.want)
			}
		})
	}
}

// A typo'd override must fail the run rather than leave verification wherever
// the file left it — the shipped example config disables it for the suite's
// self-signed localhost cert.
func TestMalformedEnvOverrideIsRejected(t *testing.T) {
	for _, tc := range []struct{ key, val string }{
		{"CONFORMANCE_TLS_VERIFY", "ture"},
		{"POLL_INTERVAL_SECONDS", "2s"},
	} {
		t.Run(tc.key, func(t *testing.T) {
			t.Setenv(tc.key, tc.val)
			_, err := load(t, writeConfig(t, `,"tls_verify":false`))
			if err == nil || !strings.Contains(err.Error(), tc.key) {
				t.Fatalf("Load err = %v, want one naming %s", err, tc.key)
			}
		})
	}
}

// Non-positive polling values reach time.Sleep and turn the readiness poll into
// a tight request loop.
func TestNonPositivePollingRejected(t *testing.T) {
	t.Setenv("POLL_INTERVAL_SECONDS", "-1")
	if _, err := load(t, writeConfig(t, ``)); err == nil {
		t.Fatal("Load accepted a negative poll interval")
	}
}

// ID_TYPE reaches the conformance surface only through applyEnv; without the
// mapping IDTypeTokens("") returns nil and the login-id-type preference is
// silently dropped on that surface while cmd/e2e still honours the same var.
func TestIDTypeEnvOverride(t *testing.T) {
	t.Setenv("ID_TYPE", "uin")
	c, err := load(t, writeConfig(t, ``))
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if c.Esignet.Identity.IDType != "uin" {
		t.Errorf("IDType = %q, want uin", c.Esignet.Identity.IDType)
	}
}

// writeLayers builds a temp dir holding a plugin config plus (optionally) the
// config.local.json overlay beside it, and returns the plugin config's path.
func writeLayers(t *testing.T, plugin, overlay string) string {
	t.Helper()
	dir := t.TempDir()
	plan := filepath.Join(dir, "plan.json")
	if err := os.WriteFile(plan, []byte(`{}`), 0o600); err != nil {
		t.Fatal(err)
	}
	plugin = strings.ReplaceAll(plugin, "$PLAN", jsonString(plan))
	cfg := filepath.Join(dir, "config.plugin.json")
	if err := os.WriteFile(cfg, []byte(plugin), 0o600); err != nil {
		t.Fatal(err)
	}
	if overlay != "" {
		if err := os.WriteFile(filepath.Join(dir, LocalOverlayName), []byte(overlay), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	return cfg
}

// The overlay must override only the keys it names, at any depth, and the
// environment must override both. This is the whole contract that lets a tracked
// per-plugin file hold the test definition while credentials stay untracked.
func TestLayerPrecedence(t *testing.T) {
	cfg := writeLayers(t,
		`{"conformance":{"base_url":"https://suite.example"},
		  "plan":{"config_file":$PLAN},
		  "esignet":{"provider":"mosip","auth_factor":"otp",
		             "identity":{"individual_id":"from-plugin","id_type":"uin"}},
		  "run":{"surfaces":["conformance"],"profile":"smoke"}}`,
		`{"esignet":{"identity":{"individual_id":"from-overlay"}}}`)

	c, err := load(t, cfg)
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if c.Esignet.Identity.IndividualID != "from-overlay" {
		t.Errorf("individual_id = %q, want the overlay's value", c.Esignet.Identity.IndividualID)
	}
	// A sibling key the overlay never mentions must survive it.
	if c.Esignet.Identity.IDType != "uin" {
		t.Errorf("id_type = %q, want uin — the overlay must not blank unmentioned siblings", c.Esignet.Identity.IDType)
	}
	if c.Esignet.Provider != "mosip" {
		t.Errorf("provider = %q, want mosip", c.Esignet.Provider)
	}

	t.Setenv("INDIVIDUAL_ID", "from-env")
	c, err = load(t, cfg)
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if c.Esignet.Identity.IndividualID != "from-env" {
		t.Errorf("individual_id = %q, want the env value to beat both files", c.Esignet.Identity.IndividualID)
	}
}

// A config named explicitly must not silently fall through to defaults: a typo'd
// CONFIG would otherwise run mock while the operator believes mosip ran, and the
// green report would be read as mosip passing.
func TestExplicitMissingConfigIsFatal(t *testing.T) {
	missing := filepath.Join(t.TempDir(), "config.msoip.json")

	if _, err := Load(missing, true); err == nil {
		t.Fatal("Load accepted an explicitly named config that does not exist")
	}

	// The same absent path is fine when it was only the default — that is how a
	// container supplies everything through the environment.
	t.Setenv("CONFORMANCE_BASE_URL", "https://suite.example")
	t.Setenv("SURFACES", "conformance")
	plan := filepath.Join(t.TempDir(), "plan.json")
	if err := os.WriteFile(plan, []byte(`{}`), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("PLAN_CONFIG_PATH", plan)
	if _, err := Load(missing, false); err != nil {
		t.Fatalf("Load(mustExist=false) on a missing file: %v", err)
	}
}

// One config now drives all three surfaces, so a run must only be held to the
// requirements of the surfaces it actually selected.
func TestValidationIsScopedToSelectedSurfaces(t *testing.T) {
	// e2e only: no conformance base_url and no plan config, which would have been
	// hard requirements before.
	cfg := writeLayers(t,
		`{"esignet":{"provider":"mock","base_url":"https://esignet.example"},
		  "keycloak":{"token_url":"https://iam.example","client_id":"c","client_secret":"s"},
		  "e2e":{"spec":"../../e2e-scenarios.json"},
		  "run":{"surfaces":["e2e"]}}`, "")
	if _, err := Load(cfg, true); err != nil {
		t.Fatalf("e2e-only config rejected: %v", err)
	}

	// conformance only: no esignet.base_url, which e2e/bdd would have required.
	cfg = writeLayers(t,
		`{"conformance":{"base_url":"https://suite.example"},
		  "plan":{"config_file":$PLAN},
		  "run":{"surfaces":["conformance"]}}`, "")
	if _, err := Load(cfg, true); err != nil {
		t.Fatalf("conformance-only config rejected: %v", err)
	}

	// ...but the surface's OWN requirement still fires.
	cfg = writeLayers(t,
		`{"esignet":{"provider":"mock"},"run":{"surfaces":["bdd"]}}`, "")
	_, err := Load(cfg, true)
	if err == nil || !strings.Contains(err.Error(), "esignet.base_url") {
		t.Fatalf("err = %v, want one naming esignet.base_url", err)
	}
}

// A run against a real deployment must name the identity under test, or it
// authenticates as — and reports claims for — whoever owns whatever identifier
// was left in the config.
func TestNonMockProviderRequiresIdentity(t *testing.T) {
	cfg := writeLayers(t,
		`{"conformance":{"base_url":"https://suite.example"},
		  "plan":{"config_file":$PLAN},
		  "esignet":{"provider":"mosip"},
		  "run":{"surfaces":["conformance"]}}`, "")
	_, err := Load(cfg, true)
	if err == nil || !strings.Contains(err.Error(), "individual_id") {
		t.Fatalf("err = %v, want one naming individual_id", err)
	}
}

func TestUnknownSurfaceRejected(t *testing.T) {
	cfg := writeLayers(t, `{"run":{"surfaces":["conformance","smoke"]}}`, "")
	_, err := Load(cfg, true)
	if err == nil || !strings.Contains(err.Error(), "smoke") {
		t.Fatalf("err = %v, want one naming the unknown surface", err)
	}
}

// Reports are archived as CI artifacts, so the configuration panel must never
// carry the admin grant that can create OIDC clients.
func TestRedactedMasksKeycloakSecret(t *testing.T) {
	c := &Config{}
	c.Keycloak.ClientSecret = "super-secret-value"
	if out := c.Redacted(); strings.Contains(out, "super-secret-value") {
		t.Errorf("Redacted() leaked the keycloak client secret:\n%s", out)
	}
}
