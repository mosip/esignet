// Command cfg renders the layered harness config for consumers that cannot load
// it themselves:
//
//	-print-env   shell `export` lines, for `eval` by run-all.sh. The bdd/ tree is
//	             a separate Go module and cannot import internal/config, so this
//	             is how the file reaches the godog surfaces.
//	-get KEY     one resolved value (dotted path), for shell branching.
//	-check       human-readable summary of what a run would do, secrets masked.
//
// Usage:
//
//	cfg -config config.mosip.json -check
//	eval "$(cfg -config config.mosip.json -print-env)"
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"strconv"
	"strings"

	"github.com/mosip/esignet/api-test/internal/config"
	"github.com/mosip/esignet/api-test/internal/e2e"
)

func main() {
	configPath := flag.String("config", "config.json", "path to the harness config")
	printEnv := flag.Bool("print-env", false, "emit shell export lines for the resolved config")
	get := flag.String("get", "", "print one resolved value by dotted key (e.g. run.surfaces)")
	check := flag.Bool("check", false, "print what a run would do, then exit")
	flag.Parse()

	logger := log.New(os.Stderr, "", 0)

	cfgPath, mustExist := config.ResolvePath(*configPath, config.FlagExplicit("config"))
	cfg, err := config.Load(cfgPath, mustExist)
	if err != nil {
		logger.Printf("config error: %v", err)
		os.Exit(2)
	}

	switch {
	case *get != "":
		v, ok := lookup(cfg, *get)
		if !ok {
			logger.Printf("unknown config key %q", *get)
			os.Exit(2)
		}
		fmt.Println(v)
	case *printEnv:
		fmt.Print(exports(cfg))
	case *check:
		if err := printCheck(cfg, cfgPath); err != nil {
			logger.Printf("%v", err)
			os.Exit(2)
		}
	default:
		fmt.Println(cfg.Redacted())
	}
}

// lookup resolves the dotted keys run-all.sh branches on. Deliberately a small
// explicit set rather than reflection — these are a shell contract, so adding
// one should be a visible change here.
func lookup(c *config.Config, key string) (string, bool) {
	switch key {
	case "run.surfaces":
		return strings.Join(c.Run.Surfaces, ","), true
	case "run.profile":
		return c.Run.Profile, true
	case "run.report_dir":
		return c.Run.ReportDir, true
	case "esignet.provider":
		return c.Esignet.Provider, true
	case "esignet.auth_factor":
		return c.Esignet.AuthFactor, true
	case "plan.name":
		return c.Plan.Name, true
	case "e2e.spec":
		return c.E2E.Spec, true
	}
	return "", false
}

// exports renders the resolved config as shell exports. This is the ONLY path by
// which the config reaches the bdd module, so every variable bdd/steps.go and
// bdd/engine_test.go read must appear here.
//
// The values are already fully resolved (file + overlay + env), so re-exporting
// them is idempotent: an operator's own env override survives, because it was
// folded in before this ran.
func exports(c *config.Config) string {
	var b strings.Builder
	kv := func(k, v string) {
		fmt.Fprintf(&b, "export %s=%s\n", k, shellQuote(v))
	}

	kv("ESIGNET_BASE_URL", c.Esignet.BaseURL)
	kv("AUTHN_PROVIDER", c.Esignet.Provider)
	kv("AUTH_FACTOR", c.Esignet.AuthFactor)
	kv("ID_TYPE", c.Esignet.Identity.IDType)
	kv("INDIVIDUAL_ID", c.Esignet.Identity.IndividualID)
	kv("TEST_USERNAME", c.Esignet.Credentials.Username)
	kv("TEST_PASSWORD", c.Esignet.Credentials.Password)
	kv("KBI_FULL_NAME", c.Esignet.Knowledge.FullName)
	kv("KBI_DOB", c.Esignet.Knowledge.DOB)
	kv("OTP_SOURCE", c.Esignet.OTP.Source)
	kv("TEST_OTP", c.Esignet.OTP.Value)
	kv("OTP_WS_URL", c.Esignet.OTP.WSURL)
	kv("OTP_RECIPIENT_EMAIL", c.Esignet.OTP.RecipientEmail)
	kv("PMS_BASE_URL", c.Esignet.PMS.BaseURL)
	kv("AUTH_PARTNER_ID", c.Esignet.PMS.AuthPartnerID)
	kv("AUTH_POLICY_ID", c.Esignet.PMS.PolicyID)

	kv("KEYCLOAK_TOKEN_URL", c.Keycloak.TokenURL)
	kv("KEYCLOAK_CLIENT_ID", c.Keycloak.ClientID)
	kv("KEYCLOAK_CLIENT_SECRET", c.Keycloak.ClientSecret)

	kv("GODOG_TAGS", c.BDD.Tags)
	kv("FLOW_CLIENT_ID", c.BDD.FlowClientID)
	kv("BDD_TLS_VERIFY", strconv.FormatBool(c.BDD.TLSVerify))

	kv("CONFORMANCE_BASE_URL", c.Conformance.BaseURL)
	kv("CONFORMANCE_TLS_VERIFY", strconv.FormatBool(c.Conformance.TLSVerify))
	kv("PLAN_CONFIG_PATH", c.Plan.ConfigFile)
	kv("REPORT_DIR", c.Run.ReportDir)

	// run-all.sh branches on these. Emitting them here keeps the whole script on
	// ONE config load: a second `-get` call could fail on its own and leave the
	// surface list empty, which would run nothing and still consolidate green.
	kv("SURFACES", strings.Join(c.Run.Surfaces, ","))
	kv("PLUGIN", c.Esignet.Provider)
	kv("TEST_PROFILE", c.Run.Profile)
	return b.String()
}

// shellQuote wraps a value in single quotes, which suppress every form of shell
// expansion, and escapes any embedded single quote by closing/reopening. Without
// this a password containing $ or a backtick would be expanded by the `eval`.
func shellQuote(s string) string {
	return "'" + strings.ReplaceAll(s, "'", `'\''`) + "'"
}

// printCheck reports what a run would actually do — which layers were read,
// which surfaces are selected, and which e2e scenarios survive the filter — so a
// long run can be confirmed before it starts rather than after it finishes.
func printCheck(c *config.Config, path string) error {
	fmt.Printf("config    %s\n", strings.Join(c.Sources, " + "))
	if len(c.Sources) == 0 {
		fmt.Printf("          (no file found at %s — using environment + defaults)\n", path)
	}
	fmt.Printf("plugin    %s\n", c.Esignet.Provider)
	fmt.Printf("surfaces  %s\n", strings.Join(c.Run.Surfaces, ", "))
	fmt.Printf("esignet   %s\n", orDash(c.Esignet.BaseURL))

	if c.HasSurface(config.SurfaceConformance) {
		fmt.Printf("\nconformance\n")
		fmt.Printf("  suite       %s (tls_verify=%v)\n", c.Conformance.BaseURL, c.Conformance.TLSVerify)
		fmt.Printf("  plan        %s\n", c.Plan.Name)
		fmt.Printf("  plan config %s\n", c.Plan.ConfigFile)
		fmt.Printf("  auth factor %s\n", c.Esignet.AuthFactor)
		if len(c.Run.Modules) > 0 {
			fmt.Printf("  modules     %d explicitly listed (profile ignored)\n", len(c.Run.Modules))
			for _, m := range c.Run.Modules {
				fmt.Printf("                %s\n", m)
			}
		} else {
			fmt.Printf("  modules     profile=%s", c.Run.Profile)
			if c.Run.Filter != "" {
				fmt.Printf(" filter=%q", c.Run.Filter)
			}
			fmt.Println()
		}
		for _, s := range c.Run.Skip {
			fmt.Printf("  skip        %s\n", s)
		}
		for _, k := range c.Run.KnownIssues {
			fmt.Printf("  known      %s (%s)\n", k.Module, k.Reason)
		}
	}

	if c.HasSurface(config.SurfaceBDD) {
		fmt.Printf("\nbdd\n")
		fmt.Printf("  tags        %s\n", orDefault(c.BDD.Tags, "(auto — chosen from configured credentials)"))
		fmt.Printf("  client-mgmt %s\n", readiness(c.Keycloak.ClientSecret != "", "ENV_NOT_READY: no keycloak.client_secret"))
		fmt.Printf("  authz-neg   %s\n", readiness(c.BDD.FlowClientID != "", "ENV_NOT_READY: no bdd.flow_client_id"))
	}

	if c.HasSurface(config.SurfaceE2E) {
		fmt.Printf("\ne2e\n")
		fmt.Printf("  spec        %s\n", c.E2E.Spec)
		names, total, err := selectedScenarios(c)
		if err != nil {
			return err
		}
		fmt.Printf("  scenarios   %d of %d\n", len(names), total)
		for _, n := range names {
			fmt.Printf("                %s\n", n)
		}
	}
	return nil
}

// selectedScenarios loads the spec and applies the configured filter, so --check
// reports the scenarios that would really run rather than restating the filter.
func selectedScenarios(c *config.Config) (names []string, total int, err error) {
	data, err := os.ReadFile(c.E2E.Spec)
	if err != nil {
		return nil, 0, fmt.Errorf("read e2e.spec %s: %w", c.E2E.Spec, err)
	}
	var spec e2e.Spec
	if err := json.Unmarshal(data, &spec); err != nil {
		return nil, 0, fmt.Errorf("parse e2e.spec %s: %w", c.E2E.Spec, err)
	}
	total = len(spec.Scenarios)
	sel, err := spec.Select(e2e.Filter{
		AuthFactors: c.E2E.AuthFactors,
		Include:     c.E2E.Include,
		Exclude:     c.E2E.Exclude,
	})
	if err != nil {
		return nil, total, err
	}
	for _, s := range sel.Scenarios {
		names = append(names, s.Name)
	}
	return names, total, nil
}

func readiness(ok bool, why string) string {
	if ok {
		return "will run"
	}
	return why
}

func orDash(s string) string {
	if s == "" {
		return "-"
	}
	return s
}

func orDefault(s, def string) string {
	if s == "" {
		return def
	}
	return s
}
