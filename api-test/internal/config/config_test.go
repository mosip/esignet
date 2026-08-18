package config

import (
	"encoding/json"
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// writeConfig writes a minimal conformance-only config with extra fields spliced in and returns its path.
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

// A typo'd override must fail the run rather than leave verification wherever the file left it.
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

// ID_TYPE reaches the conformance surface only through applyEnv.
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

// The overlay must override only the keys it names, at any depth, and the environment must override both.
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

// A config named explicitly must not silently fall through to defaults.
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
	// e2e only: no conformance base_url and no plan config, which used to be hard requirements.
	spec := filepath.Join(t.TempDir(), "e2e-scenarios.json")
	if err := os.WriteFile(spec, []byte(`{"scenarios":[]}`), 0o600); err != nil {
		t.Fatal(err)
	}
	cfg := writeLayers(t,
		`{"esignet":{"provider":"mock","base_url":"https://esignet.example"},
		  "keycloak":{"token_url":"https://iam.example","client_id":"c","client_secret":"s"},
		  "e2e":{"spec":`+jsonString(spec)+`},
		  "run":{"surfaces":["e2e"]}}`, "")
	if _, err := Load(cfg, true); err != nil {
		t.Fatalf("e2e-only config rejected: %v", err)
	}

	// conformance only: no esignet.base_url, which e2e/api would have required.
	cfg = writeLayers(t,
		`{"conformance":{"base_url":"https://suite.example"},
		  "plan":{"config_file":$PLAN},
		  "run":{"surfaces":["conformance"]}}`, "")
	if _, err := Load(cfg, true); err != nil {
		t.Fatalf("conformance-only config rejected: %v", err)
	}

	// ...but the surface's OWN requirement still fires.
	cfg = writeLayers(t,
		`{"esignet":{"provider":"mock"},"run":{"surfaces":["api"]}}`, "")
	_, err := Load(cfg, true)
	if err == nil || !strings.Contains(err.Error(), "esignet.base_url") {
		t.Fatalf("err = %v, want one naming esignet.base_url", err)
	}
}

// A run against a real deployment must name the identity under test.
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

// writePlans builds a conformance-only config whose plans[] all point at a real (empty) plan config file.
func writePlans(t *testing.T, plans string) (cfgPath, planPath string) {
	t.Helper()
	dir := t.TempDir()
	planPath = filepath.Join(dir, "plan.json")
	if err := os.WriteFile(planPath, []byte(`{}`), 0o600); err != nil {
		t.Fatal(err)
	}
	body := `{"conformance":{"base_url":"https://suite.example"},` +
		`"plans":` + strings.ReplaceAll(plans, "$PLAN", jsonString(planPath)) + `,` +
		`"run":{"surfaces":["conformance"],"profile":"full"}}`
	cfgPath = filepath.Join(dir, "config.plugin.json")
	if err := os.WriteFile(cfgPath, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	return cfgPath, planPath
}

// Two plans in one run: each keeps its own name, variant and client/jwks file.
func TestPlansListAndLegacyBlock(t *testing.T) {
	cfg, planPath := writePlans(t, `[
		{"name":"oidcc-test-plan","config_file":$PLAN},
		{"name":"fapi2-security-profile-final-test-plan","config_file":$PLAN,
		 "variant":{"sender_constrain":"dpop","fapi_profile":"plain_fapi"}}]`)
	c, err := load(t, cfg)
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if got := c.PlanNames(); len(got) != 2 || got[1] != "fapi2-security-profile-final-test-plan" {
		t.Fatalf("PlanNames() = %v, want both plans in order", got)
	}
	if c.Plans[0].ConfigFile != planPath {
		t.Errorf("plans[0].config_file = %q, want %q", c.Plans[0].ConfigFile, planPath)
	}
	// The oidcc default variant must not leak onto a plan that named its own.
	if _, leaked := c.Plans[1].Variant["response_type"]; leaked {
		t.Errorf("fapi variant inherited oidcc defaults: %v", c.Plans[1].Variant)
	}
	if c.Plans[1].Variant["sender_constrain"] != "dpop" {
		t.Errorf("fapi variant lost its own values: %v", c.Plans[1].Variant)
	}
	// ...and the default plan still gets the default variant it always had.
	if c.Plans[0].Variant["response_type"] != "code" {
		t.Errorf("oidcc plan lost its default variant: %v", c.Plans[0].Variant)
	}

	// Legacy single-plan form folds into the same list.
	legacy := writeLayers(t, `{"conformance":{"base_url":"https://suite.example"},
		"plan":{"config_file":$PLAN},"run":{"surfaces":["conformance"]}}`, "")
	c, err = load(t, legacy)
	if err != nil {
		t.Fatalf("Load(legacy plan block): %v", err)
	}
	if len(c.Plans) != 1 || c.Plans[0].Name != "oidcc-test-plan" {
		t.Fatalf("legacy plan did not fold into plans: %+v", c.Plans)
	}
	if c.Plan != nil {
		t.Error("legacy plan block should be cleared once folded, so nothing downstream reads it")
	}
}

// A config answering "which plan runs" twice is a mistake, not a merge: picking
// one silently would run a plan the operator did not ask for.
func TestPlanAndPlansTogetherRejected(t *testing.T) {
	dir := t.TempDir()
	planPath := filepath.Join(dir, "plan.json")
	if err := os.WriteFile(planPath, []byte(`{}`), 0o600); err != nil {
		t.Fatal(err)
	}
	cfg := filepath.Join(dir, "config.json")
	body := `{"conformance":{"base_url":"https://suite.example"},
		"plan":{"config_file":` + jsonString(planPath) + `},
		"plans":[{"name":"oidcc-test-plan","config_file":` + jsonString(planPath) + `}],
		"run":{"surfaces":["conformance"]}}`
	if err := os.WriteFile(cfg, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	_, err := Load(cfg, true)
	if err == nil || !strings.Contains(err.Error(), "`plans`") {
		t.Fatalf("err = %v, want one telling the operator to use plans alone", err)
	}
}

// Plan names key the report sections and the smoke-profile files, so a duplicate
// would silently merge two plans' results into one section.
func TestDuplicatePlanNamesRejected(t *testing.T) {
	cfg, _ := writePlans(t, `[{"name":"oidcc-test-plan","config_file":$PLAN},
		{"name":"oidcc-test-plan","config_file":$PLAN}]`)
	_, err := Load(cfg, true)
	if err == nil || !strings.Contains(err.Error(), "duplicates") {
		t.Fatalf("err = %v, want one naming the duplicate plan", err)
	}
}

// Each plan narrows its own module set, because two plans in one run rarely want
// the same list; anything it leaves unset comes from run.*.
func TestPerPlanSelectionFallsBackToRun(t *testing.T) {
	dir := t.TempDir()
	planPath := filepath.Join(dir, "plan.json")
	if err := os.WriteFile(planPath, []byte(`{}`), 0o600); err != nil {
		t.Fatal(err)
	}
	cfg := filepath.Join(dir, "config.json")
	body := `{"conformance":{"base_url":"https://suite.example"},
		"plans":[{"name":"oidcc-test-plan","config_file":` + jsonString(planPath) + `},
		         {"name":"fapi2-security-profile-final-test-plan","config_file":` + jsonString(planPath) + `,
		          "profile":"full","filter":"^fapi2","skip":["fapi2-mtls"]}],
		"run":{"surfaces":["conformance"],"profile":"smoke","skip":["oidcc-logout"]}}`
	if err := os.WriteFile(cfg, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	c, err := Load(cfg, true)
	if err != nil {
		t.Fatalf("Load: %v", err)
	}

	oidcc := c.Selection(c.Plans[0])
	if oidcc.Profile != "smoke" || oidcc.Filter != "" || len(oidcc.Skip) != 1 || oidcc.Skip[0] != "oidcc-logout" {
		t.Errorf("plan without overrides = %+v, want run.* values", oidcc)
	}
	fapi := c.Selection(c.Plans[1])
	if fapi.Profile != "full" || fapi.Filter != "^fapi2" {
		t.Errorf("plan overrides ignored: %+v", fapi)
	}
	if len(fapi.Skip) != 1 || fapi.Skip[0] != "fapi2-mtls" {
		t.Errorf("plan skip = %v, want its own list rather than run.skip", fapi.Skip)
	}
}

// PLAN_CONFIG_PATH cannot say which plan it means once there are two.
func TestPlanEnvOverrides(t *testing.T) {
	cfg, planPath := writePlans(t, `[{"name":"oidcc-test-plan","config_file":$PLAN},
		{"name":"fapi2-security-profile-final-test-plan","config_file":$PLAN}]`)

	t.Run("unindexed is ambiguous", func(t *testing.T) {
		t.Setenv("PLAN_CONFIG_PATH", planPath)
		_, err := Load(cfg, true)
		if err == nil || !strings.Contains(err.Error(), "PLAN_1_CONFIG_PATH") {
			t.Fatalf("err = %v, want one pointing at the indexed form", err)
		}
	})

	t.Run("indexed addresses one plan", func(t *testing.T) {
		other := filepath.Join(filepath.Dir(planPath), "fapi.json")
		if err := os.WriteFile(other, []byte(`{}`), 0o600); err != nil {
			t.Fatal(err)
		}
		t.Setenv("PLAN_2_CONFIG_PATH", other)
		c, err := Load(cfg, true)
		if err != nil {
			t.Fatalf("Load: %v", err)
		}
		if c.Plans[0].ConfigFile != planPath {
			t.Errorf("plans[0].config_file = %q, want it untouched", c.Plans[0].ConfigFile)
		}
		if c.Plans[1].ConfigFile != other {
			t.Errorf("plans[1].config_file = %q, want the override %q", c.Plans[1].ConfigFile, other)
		}
	})

	t.Run("out of range is an error", func(t *testing.T) {
		t.Setenv("PLAN_3_CONFIG_PATH", planPath)
		_, err := Load(cfg, true)
		if err == nil || !strings.Contains(err.Error(), "PLAN_3_CONFIG_PATH") {
			t.Fatalf("err = %v, want one naming the out-of-range override", err)
		}
	})

	// `cfg -print-env` emits PLAN_1_* for every config, including the legacy singular `plan` block.
	t.Run("indexed reaches the legacy plan block", func(t *testing.T) {
		legacyCfg := writeLayers(t, `{"conformance":{"base_url":"https://suite.example"},
			"plan":{"config_file":$PLAN},"run":{"surfaces":["conformance"]}}`, "")

		other := filepath.Join(t.TempDir(), "override.json")
		if err := os.WriteFile(other, []byte(`{}`), 0o600); err != nil {
			t.Fatal(err)
		}
		t.Setenv("PLAN_1_NAME", "fapi2-security-profile-final-test-plan")
		t.Setenv("PLAN_1_CONFIG_PATH", other)

		c, err := Load(legacyCfg, true)
		if err != nil {
			t.Fatalf("Load(legacy plan + PLAN_1_*): %v", err)
		}
		if len(c.Plans) != 1 {
			t.Fatalf("plans = %+v, want the legacy block as the only entry", c.Plans)
		}
		if c.Plans[0].Name != "fapi2-security-profile-final-test-plan" || c.Plans[0].ConfigFile != other {
			t.Errorf("plans[0] = %+v, want both indexed overrides applied", c.Plans[0])
		}
	})
}

// json.Unmarshal merges a slice positionally and truncates to the incoming length.
func TestPlansOverlay(t *testing.T) {
	dir := t.TempDir()
	planPath := filepath.Join(dir, "plan.json")
	otherPath := filepath.Join(dir, "other.json")
	for _, p := range []string{planPath, otherPath} {
		if err := os.WriteFile(p, []byte(`{}`), 0o600); err != nil {
			t.Fatal(err)
		}
	}
	base := `{"conformance":{"base_url":"https://suite.example"},
		"plans":[{"name":"oidcc-test-plan","config_file":"missing.json"},
		         {"name":"fapi2-security-profile-final-test-plan","config_file":"missing.json"}],
		"run":{"surfaces":["conformance"],"profile":"full"}}`
	cfg := filepath.Join(dir, "config.plugin.json")
	if err := os.WriteFile(cfg, []byte(base), 0o600); err != nil {
		t.Fatal(err)
	}
	overlay := `{"plans":[{"config_file":` + jsonString(planPath) + `},
	                      {"config_file":` + jsonString(otherPath) + `}]}`
	if err := os.WriteFile(filepath.Join(dir, LocalOverlayName), []byte(overlay), 0o600); err != nil {
		t.Fatal(err)
	}

	c, err := Load(cfg, true)
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if len(c.Plans) != 2 {
		t.Fatalf("plans = %d, want the overlay to fill in both entries", len(c.Plans))
	}
	// Each entry keeps the name the tracked file gave it and takes the private
	// path from the overlay.
	if c.Plans[0].Name != "oidcc-test-plan" || c.Plans[0].ConfigFile != planPath {
		t.Errorf("plans[0] = %+v, want the base name with the overlay's file", c.Plans[0])
	}
	if c.Plans[1].Name != "fapi2-security-profile-final-test-plan" || c.Plans[1].ConfigFile != otherPath {
		t.Errorf("plans[1] = %+v, want the base name with the overlay's file", c.Plans[1])
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

// Every field the configuration panel must never carry, pinned in one place so a
// new secret or identity field cannot be added to Config without being masked.
func TestRedactedMasksEveryIdentityAndSecretField(t *testing.T) {
	c := &Config{}
	c.Conformance.Token = "tok-leak"
	c.Keycloak.ClientSecret = "kc-leak"
	c.Esignet.Credentials.Username = "user-leak"
	c.Esignet.Credentials.Password = "pw-leak"
	c.Esignet.OTP.Value = "otp-leak"
	c.Esignet.OTP.RecipientEmail = "mail-leak"
	c.Esignet.Identity.IndividualID = "id-leak"
	c.Esignet.Knowledge.FullName = "name-leak"
	c.Esignet.Knowledge.DOB = "dob-leak"

	out := c.Redacted()
	for _, want := range []string{
		"tok-leak", "kc-leak", "user-leak", "pw-leak",
		"otp-leak", "mail-leak", "id-leak", "name-leak", "dob-leak",
	} {
		if strings.Contains(out, want) {
			t.Errorf("Redacted() leaked %q:\n%s", want, out)
		}
	}
}

// RFC 7518 defines `oth` as an ARRAY of other-primes info on a multi-prime RSA
// key, so masking only string values would leave real key material in the report.
func TestRedactJWKMaterialMasksNonStringValues(t *testing.T) {
	var v any
	body := `{"kty":"RSA","n":"pub","d":"priv",
	          "oth":[{"r":"r1","d":"d1","t":"t1"}],
	          "client_secret":{"value":"nested-leak"}}`
	if err := json.Unmarshal([]byte(body), &v); err != nil {
		t.Fatal(err)
	}
	RedactJWKMaterial(v)
	out, err := json.Marshal(v)
	if err != nil {
		t.Fatal(err)
	}
	for _, want := range []string{`"priv"`, "d1", "r1", "nested-leak"} {
		if strings.Contains(string(out), want) {
			t.Errorf("RedactJWKMaterial left %s in:\n%s", want, out)
		}
	}
	// The public half stays readable — it is what the report is there to show.
	if !strings.Contains(string(out), `"pub"`) {
		t.Errorf("RedactJWKMaterial masked the public modulus:\n%s", out)
	}
}

// esignet.tls_verify is a separate knob from conformance.tls_verify and must fail closed on its own.
func TestEsignetTLSVerifyIsIndependentOfConformance(t *testing.T) {
	c, err := load(t, writeConfig(t, `,"tls_verify":false`))
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if c.Conformance.TLSVerify {
		t.Error("Conformance.TLSVerify = true, want the file's false")
	}
	if !c.Esignet.TLSVerify {
		t.Error("Esignet.TLSVerify = false, want true — conformance.tls_verify must not disable eSignet verification")
	}
}

func TestEsignetTLSVerifyEnvOverride(t *testing.T) {
	t.Setenv("ESIGNET_TLS_VERIFY", "false")
	c, err := load(t, writeConfig(t, ``))
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if c.Esignet.TLSVerify {
		t.Error("Esignet.TLSVerify = true, want the env override's false")
	}
}

// An env-only container declares its plan solely through the indexed PLAN_1_* variables.
func TestIndexedPlanEnvAllocatesWithNoConfigFile(t *testing.T) {
	plan := filepath.Join(t.TempDir(), "plan.json")
	if err := os.WriteFile(plan, []byte(`{}`), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("CONFORMANCE_BASE_URL", "https://suite.example")
	t.Setenv("SURFACES", "conformance")
	t.Setenv("PLAN_1_NAME", "oidcc-test-plan")
	t.Setenv("PLAN_1_CONFIG_PATH", plan)

	c, err := Load("", false)
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if len(c.Plans) != 1 {
		t.Fatalf("plans = %d, want 1 allocated from PLAN_1_*", len(c.Plans))
	}
	if c.Plans[0].Name != "oidcc-test-plan" || c.Plans[0].ConfigFile != plan {
		t.Errorf("plans[0] = %+v, want the env-supplied name and path", c.Plans[0])
	}
}

// The bound stays strict when the config really did declare plans: an index
// past the end is a typo, and inventing a plan for it would hide it.
func TestIndexedPlanEnvOutOfRangeStillRejected(t *testing.T) {
	t.Setenv("PLAN_2_CONFIG_PATH", "/nowhere/plan.json")
	_, err := load(t, writeConfig(t, ``))
	if err == nil {
		t.Fatal("Load succeeded, want an out-of-range plan index error")
	}
	if !strings.Contains(err.Error(), "PLAN_2_CONFIG_PATH") {
		t.Errorf("error = %v, want it to name PLAN_2_CONFIG_PATH", err)
	}
}

// A validation failure must still hand back the resolved config, since `cfg -check` reports from it.
func TestLoadReturnsConfigAlongsideValidationError(t *testing.T) {
	dir := t.TempDir()
	cfg := filepath.Join(dir, "config.json")
	// Names a plan config that is not mounted — the state of a fresh clone.
	body := `{"conformance":{"base_url":"https://suite.example"},` +
		`"esignet":{"provider":"mock"},` +
		`"plan":{"name":"oidcc-test-plan","config_file":"conformance-suite-private/esignet-config.json"},` +
		`"run":{"surfaces":["conformance"]}}`
	if err := os.WriteFile(cfg, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}

	c, err := Load(cfg, true)
	if err == nil {
		t.Fatal("Load succeeded, want a not-readable plan config error")
	}
	if c == nil {
		t.Fatal("Load returned a nil config with a validation error — -check cannot report on it")
	}
	if c.Esignet.Provider != "mock" || len(c.Plans) != 1 {
		t.Errorf("returned config is not fully resolved: %+v", c)
	}
}

// A layering/parse failure is different: there is no meaningful config to hand
// back, so nil is correct and -check must not try to print one.
func TestLoadReturnsNilOnParseError(t *testing.T) {
	cfg := filepath.Join(t.TempDir(), "config.json")
	if err := os.WriteFile(cfg, []byte(`{not json`), 0o600); err != nil {
		t.Fatal(err)
	}
	c, err := Load(cfg, true)
	if err == nil {
		t.Fatal("Load succeeded on malformed JSON")
	}
	if c != nil {
		t.Errorf("Load returned a config for malformed JSON: %+v", c)
	}
}

// Allocating up to a sparse highest index would invent empty plans for the gaps.
func TestSparseIndexedPlanEnvIsRejected(t *testing.T) {
	plan := filepath.Join(t.TempDir(), "plan.json")
	if err := os.WriteFile(plan, []byte(`{}`), 0o600); err != nil {
		t.Fatal(err)
	}
	t.Setenv("CONFORMANCE_BASE_URL", "https://suite.example")
	t.Setenv("SURFACES", "conformance")
	t.Setenv("PLAN_3_NAME", "oidcc-test-plan")
	t.Setenv("PLAN_3_CONFIG_PATH", plan)

	_, err := Load("", false)
	if err == nil {
		t.Fatal("Load succeeded with PLAN_3_* and no PLAN_1_*/PLAN_2_*")
	}
	for _, want := range []string{"PLAN_1_CONFIG_PATH", "PLAN_2_CONFIG_PATH", "contiguous"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("error = %v, want it to mention %q", err, want)
		}
	}
}
