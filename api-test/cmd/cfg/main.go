// Command cfg renders the layered harness config for consumers that cannot load it themselves.
package main

import (
	"encoding/json"
	"flag"
	"fmt"
	"log"
	"os"
	"sort"
	"strconv"
	"strings"

	"github.com/mosip/esignet/api-test/internal/config"
	"github.com/mosip/esignet/api-test/internal/e2e"
)

func main() {
	configPath := flag.String("config", "data/config/config.json", "path to the harness config")
	printEnv := flag.Bool("print-env", false, "emit shell export lines for the resolved config")
	get := flag.String("get", "", "print one resolved value by dotted key (e.g. run.surfaces)")
	check := flag.Bool("check", false, "print what a run would do, then exit")
	flag.Parse()

	logger := log.New(os.Stderr, "", 0)

	cfgPath, mustExist := config.ResolvePath(*configPath, config.FlagExplicit("config"))
	cfg, err := config.Load(cfgPath, mustExist)
	// -check is a dry run: it prints what a run would do even when the config is not yet runnable.
	if err != nil && !(*check && cfg != nil) {
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
		printCheck(cfg, cfgPath)
		// Still a failing check — the summary above says what a run would attempt,
		// this says why it cannot start yet.
		if err != nil {
			fmt.Printf("\nNOT RUNNABLE YET\n  %v\n", err)
			os.Exit(2)
		}
	default:
		fmt.Println(cfg.Redacted())
	}
}

// lookup resolves the dotted keys run-all.sh branches on.
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
		return strings.Join(c.PlanNames(), ","), true
	case "e2e.spec":
		return c.E2E.Spec, true
	}
	return "", false
}

// exports renders the resolved config as shell exports, the only path by which it reaches the api module.
func exports(c *config.Config) string {
	var b strings.Builder
	kv := func(k, v string) {
		fmt.Fprintf(&b, "export %s=%s\n", k, shellQuote(v))
	}

	kv("MOSIP_ESIGNET_BASE_URL", c.Esignet.BaseURL)
	kv("MOSIP_ESIGNET_AUTHN_PROVIDER", c.Esignet.Provider)
	kv("AUTH_FACTOR", c.Esignet.AuthFactor)
	kv("ESIGNET_TLS_VERIFY", strconv.FormatBool(c.Esignet.TLSVerify))
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

	kv("GODOG_TAGS", c.API.Tags)
	kv("FLOW_CLIENT_ID", c.API.FlowClientID)
	kv("API_TLS_VERIFY", strconv.FormatBool(c.API.TLSVerify))

	kv("CONFORMANCE_BASE_URL", c.Conformance.BaseURL)
	kv("CONFORMANCE_TLS_VERIFY", strconv.FormatBool(c.Conformance.TLSVerify))
	// Plans are exported in the indexed form, the only one that can address more than one.
	for i, p := range c.Plans {
		kv(fmt.Sprintf("PLAN_%d_NAME", i+1), p.Name)
		kv(fmt.Sprintf("PLAN_%d_CONFIG_PATH", i+1), p.ConfigFile)
	}
	if len(c.Plans) == 1 {
		kv("PLAN_NAME", c.Plans[0].Name)
		kv("PLAN_CONFIG_PATH", c.Plans[0].ConfigFile)
	}
	kv("REPORT_DIR", c.Run.ReportDir)

	// run-all.sh branches on these.
	kv("SURFACES", strings.Join(c.Run.Surfaces, ","))
	kv("PLUGIN", c.Esignet.Provider)
	kv("TEST_PROFILE", c.Run.Profile)
	// consolidate takes this as a flag rather than reading the config; run-all.sh
	// forwards it from here.
	kv("DEBUG_SHOW_SECRETS", strconv.FormatBool(c.Run.DebugShowSecrets))
	return b.String()
}

// shellQuote single-quotes a value and escapes embedded quotes by closing and reopening.
func shellQuote(s string) string {
	return "'" + strings.ReplaceAll(s, "'", `'\''`) + "'"
}

// printCheck reports what a run would actually do: layers read, surfaces selected, scenarios kept.
func printCheck(c *config.Config, path string) {
	fmt.Printf("config    %s\n", strings.Join(c.Sources, " + "))
	if len(c.Sources) == 0 {
		fmt.Printf("          (no file found at %s — using environment + defaults)\n", path)
	}
	fmt.Printf("plugin    %s\n", c.Esignet.Provider)
	fmt.Printf("surfaces  %s\n", strings.Join(c.Run.Surfaces, ", "))
	fmt.Printf("esignet   %s (tls_verify=%v)\n", orDash(c.Esignet.BaseURL), c.Esignet.TLSVerify)
	if !c.Esignet.TLSVerify {
		fmt.Printf("\n!! esignet.tls_verify is OFF — identities, OTPs and passwords will be sent\n")
		fmt.Printf("!! to %s without certificate verification.\n", orDash(c.Esignet.BaseURL))
	}
	if c.Run.DebugShowSecrets {
		fmt.Printf("\n!! run.debug_show_secrets is ON — the report will contain unredacted\n")
		fmt.Printf("!! OTPs, passwords, identity values and bearer tokens. Do not share it.\n")
	}

	if c.HasSurface(config.SurfaceConformance) {
		fmt.Printf("\nconformance\n")
		fmt.Printf("  suite       %s (tls_verify=%v)\n", orDash(c.Conformance.BaseURL), c.Conformance.TLSVerify)
		fmt.Printf("  auth factor %s\n", c.Esignet.AuthFactor)
		fmt.Printf("  plans       %d, run in this order\n", len(c.Plans))
		for i, p := range c.Plans {
			sel := c.Selection(p)
			fmt.Printf("\n  %d. plan      %s\n", i+1, p.Name)
			// The plan config holds the private JWKS and is always mounted, so name it as the likely missing piece.
			fmt.Printf("     config    %s%s\n", orDash(p.ConfigFile), planConfigNote(p.ConfigFile))
			fmt.Printf("     variant   %s\n", variantStr(p.Variant))
			if len(sel.Modules) > 0 {
				fmt.Printf("     modules   %d explicitly listed (profile ignored)\n", len(sel.Modules))
				for _, m := range sel.Modules {
					fmt.Printf("                 %s\n", m)
				}
			} else {
				fmt.Printf("     modules   profile=%s", sel.Profile)
				if sel.Filter != "" {
					fmt.Printf(" filter=%q", sel.Filter)
				}
				fmt.Println()
			}
			for _, s := range sel.Skip {
				fmt.Printf("     skip      %s\n", s)
			}
			for _, k := range sel.KnownIssues {
				fmt.Printf("     known     %s (%s)\n", k.Module, k.Reason)
			}
		}
	}

	if c.HasSurface(config.SurfaceAPI) {
		fmt.Printf("\napi\n")
		fmt.Printf("  tags        %s\n", orDefault(c.API.Tags, "(auto — chosen from configured credentials)"))
		// ADMIN_TOKEN stands in for the Keycloak round-trip against a target that
		// does not enforce scope, so it counts as admin auth here too — otherwise
		// --check would report client-mgmt gated out on a run where it executes.
		// All three Keycloak fields are required together: iAuthenticateAsAdmin
		// needs all three, so accepting just the secret would report "will run"
		// for a config that fails every scenario on a missing token_url/client_id.
		adminAuth := os.Getenv("ADMIN_TOKEN") != "" ||
			(c.Keycloak.TokenURL != "" && c.Keycloak.ClientID != "" && c.Keycloak.ClientSecret != "")
		fmt.Printf("  client-mgmt %s\n", readiness(adminAuth, "ENV_NOT_READY: no keycloak.token_url/client_id/client_secret and no ADMIN_TOKEN"))
		fmt.Printf("  authz-neg   %s\n", readiness(c.API.FlowClientID != "", "ENV_NOT_READY: no api.flow_client_id"))
	}

	if c.HasSurface(config.SurfaceE2E) {
		fmt.Printf("\ne2e\n")
		fmt.Printf("  spec        %s\n", orDash(c.E2E.Spec))
		// An unreadable/unparseable spec is reported in place, not returned: this
		// is a dry run, and the surrounding summary is still worth printing.
		names, total, err := selectedScenarios(c)
		if err != nil {
			fmt.Printf("  scenarios   ENV_NOT_READY: %v\n", err)
		} else {
			fmt.Printf("  scenarios   %d of %d\n", len(names), total)
			for _, n := range names {
				fmt.Printf("                %s\n", n)
			}
		}
	}
}

// planConfigNote annotates a plan's config_file with why it is not usable yet,
// so --check answers "what would run" on a fresh clone instead of only erroring.
func planConfigNote(path string) string {
	if strings.TrimSpace(path) == "" {
		return "  <- ENV_NOT_READY: not set"
	}
	if _, err := os.Stat(path); err != nil {
		return "  <- ENV_NOT_READY: not readable (mount the private plan config there)"
	}
	return ""
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

// variantStr renders a plan's variant as sorted k=v pairs, matching how the suite UI prints it.
func variantStr(v map[string]any) string {
	if len(v) == 0 {
		return "(none)"
	}
	keys := make([]string, 0, len(v))
	for k := range v {
		keys = append(keys, k)
	}
	sort.Strings(keys)
	parts := make([]string, 0, len(keys))
	for _, k := range keys {
		parts = append(parts, fmt.Sprintf("%s=%v", k, v[k]))
	}
	return strings.Join(parts, ", ")
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
