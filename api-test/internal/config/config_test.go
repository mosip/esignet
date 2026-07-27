package config

import (
	"os"
	"path/filepath"
	"strings"
	"testing"
)

// writeConfig writes a minimal config file that passes validate(), with extra
// conformance fields spliced in, and returns its path.
func writeConfig(t *testing.T, conformance string) string {
	t.Helper()
	dir := t.TempDir()
	plan := filepath.Join(dir, "plan.json")
	if err := os.WriteFile(plan, []byte(`{}`), 0o600); err != nil {
		t.Fatal(err)
	}
	cfg := filepath.Join(dir, "config.json")
	body := `{"conformance":{"base_url":"https://suite.example"` + conformance + `},` +
		`"plan":{"config_file":` + jsonString(plan) + `}}`
	if err := os.WriteFile(cfg, []byte(body), 0o600); err != nil {
		t.Fatal(err)
	}
	return cfg
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
			c, err := Load(writeConfig(t, tc.conformance))
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
			_, err := Load(writeConfig(t, `,"tls_verify":false`))
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
	if _, err := Load(writeConfig(t, ``)); err == nil {
		t.Fatal("Load accepted a negative poll interval")
	}
}

// ID_TYPE reaches the conformance surface only through applyEnv; without the
// mapping IDTypeTokens("") returns nil and the login-id-type preference is
// silently dropped on that surface while cmd/e2e still honours the same var.
func TestIDTypeEnvOverride(t *testing.T) {
	t.Setenv("ID_TYPE", "uin")
	c, err := Load(writeConfig(t, ``))
	if err != nil {
		t.Fatalf("Load: %v", err)
	}
	if c.Esignet.Identity.IDType != "uin" {
		t.Errorf("IDType = %q, want uin", c.Esignet.Identity.IDType)
	}
}
