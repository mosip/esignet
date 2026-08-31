// Package config loads the harness configuration for all three surfaces and overlays environment variables.
package config

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"sort"
	"strconv"
	"strings"
)

// Surface names a test surface that can be listed in run.surfaces.
const (
	SurfaceConformance = "conformance"
	SurfaceAPI         = "api"
	SurfaceE2E         = "e2e"
)

// LocalOverlayName is the gitignored per-developer overlay, looked up next to
// the selected config file. Absent is normal (containers use env instead).
const LocalOverlayName = "config.local.json"

// defaultPlanName is the plan a config with no plans[] entry runs, and the only
// one whose variant is defaulted (see defaults()).
const defaultPlanName = "oidcc-test-plan"

type Config struct {
	Conformance Conformance `json:"conformance"`

	// Plan is the legacy single-plan form, folded into Plans at load time.
	Plan *Plan `json:"plan,omitempty"`

	// Plans lists the conformance plans one run executes, in order.
	Plans []Plan `json:"plans,omitempty"`

	Esignet  Esignet  `json:"esignet"`
	Keycloak Keycloak `json:"keycloak"`
	API      API      `json:"api"`
	E2E      E2E      `json:"e2e"`
	Run      Run      `json:"run"`

	// Sources lists the layers that actually contributed, newest last, for the
	// --check output. Not part of the file format.
	Sources []string `json:"-"`
}

type Conformance struct {
	BaseURL   string `json:"base_url"`
	TLSVerify bool   `json:"tls_verify"`
	Token     string `json:"token"`
}

// Plan is one conformance plan: which suite plan, which variant, which client/jwks file.
type Plan struct {
	Name       string         `json:"name"`
	Variant    map[string]any `json:"variant"`
	ConfigFile string         `json:"config_file"`

	Profile     string       `json:"profile,omitempty"`
	Modules     []string     `json:"modules,omitempty"`
	Filter      string       `json:"filter,omitempty"`
	Skip        []string     `json:"skip,omitempty"`
	KnownIssues []KnownIssue `json:"known_issues,omitempty"`
}

// Selection is the resolved module-selection for one plan: its own overrides where set, run.* otherwise.
type Selection struct {
	Modules     []string
	Profile     string
	Filter      string
	Skip        []string
	KnownIssues []KnownIssue
}

// Selection resolves p's module selection against the run-wide defaults.
func (c *Config) Selection(p Plan) Selection {
	s := Selection{
		Modules:     p.Modules,
		Profile:     p.Profile,
		Filter:      p.Filter,
		Skip:        p.Skip,
		KnownIssues: p.KnownIssues,
	}
	if len(s.Modules) == 0 {
		s.Modules = c.Run.Modules
	}
	if s.Profile == "" {
		s.Profile = c.Run.Profile
	}
	if s.Filter == "" {
		s.Filter = c.Run.Filter
	}
	if len(s.Skip) == 0 {
		s.Skip = c.Run.Skip
	}
	if len(s.KnownIssues) == 0 {
		s.KnownIssues = c.Run.KnownIssues
	}
	return s
}

// PlanNames lists the configured plan names in order, for report headers and
// log lines.
func (c *Config) PlanNames() []string {
	out := make([]string, 0, len(c.Plans))
	for _, p := range c.Plans {
		out = append(out, p.Name)
	}
	return out
}

type Esignet struct {
	BaseURL    string `json:"base_url"`
	Provider   string `json:"provider"`
	AuthFactor string `json:"auth_factor"` // which ACR to select when the flow offers a choice: otp|password|bio|kbi

	// TLSVerify governs certificate verification for every connection to the deployment under test.
	TLSVerify bool `json:"tls_verify"`

	Identity    Identity    `json:"identity"`
	Credentials Credentials `json:"credentials"`
	Knowledge   Knowledge   `json:"knowledge"`
	OTP         OTP         `json:"otp"`
	PMS         PMS         `json:"pms"`
}

// PMS holds partner-management-service settings used only by the mosip(id) plugin.
type PMS struct {
	BaseURL       string `json:"base_url"`        // PMS base, e.g. https://host/v1/partnermanagement; {base}/oauth/client is the create endpoint
	AuthPartnerID string `json:"auth_partner_id"` // onboarded Auth partner id -> becomes the client's relying-party id
	PolicyID      string `json:"policy_id"`       // published auth policy id -> governs allowed claims/ACRs
}

type Identity struct {
	IndividualID string `json:"individual_id"`
	IDType       string `json:"id_type"`
}

type Credentials struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

type Knowledge struct {
	FullName string `json:"full_name"`
	DOB      string `json:"dob"`
}

type OTP struct {
	Source string `json:"source"`
	Value  string `json:"value"`
	WSURL  string `json:"ws_url"`
	// RecipientEmail filters dynamic-OTP messages to a single recipient.
	RecipientEmail string `json:"recipient_email"`
}

// Keycloak holds the client-credentials grant used for admin auth.
type Keycloak struct {
	TokenURL     string `json:"token_url"`
	ClientID     string `json:"client_id"`
	ClientSecret string `json:"client_secret"`
}

// API configures the godog API-surface run.
type API struct {
	// Tags is the godog tag expression (comma = OR). Empty lets the suite pick
	// its own default set based on which credentials are configured.
	Tags string `json:"tags"`
	// FlowClientID is a pre-registered client id the authorize-negative
	// scenarios drive; empty gates those cases out as ENV_NOT_READY.
	FlowClientID string `json:"flow_client_id"`
	TLSVerify    bool   `json:"tls_verify"`
}

// E2E configures the end-to-end surface: which scenario file to load and which scenarios to run.
type E2E struct {
	Spec string `json:"spec"`
	// AuthFactors keeps only scenarios whose auth_factor is listed (case
	// insensitive). Empty keeps every factor.
	AuthFactors []string `json:"auth_factors"`
	// Include keeps only scenarios whose name matches at least one regex.
	// Empty keeps everything (subject to the other filters).
	Include []string `json:"include"`
	// Exclude drops scenarios whose name matches any regex. Applied after
	// Include, so it always wins.
	Exclude []string `json:"exclude"`
}

type Run struct {
	// Surfaces selects which test surfaces this run executes: any of
	// conformance, api, e2e. Defaults to all three.
	Surfaces            []string     `json:"surfaces"`
	Modules             []string     `json:"modules"`
	Profile             string       `json:"profile"`
	Filter              string       `json:"filter"`
	Skip                []string     `json:"skip"`         // modules to not run at all -> Skipped bucket
	KnownIssues         []KnownIssue `json:"known_issues"` // known-failing modules to not run -> Known bucket
	PollIntervalSeconds int          `json:"poll_interval_seconds"`
	TimeoutSeconds      int          `json:"timeout_seconds"`
	FailFast            bool         `json:"fail_fast"`
	ReportDir           string       `json:"report_dir"`

	// DebugShowSecrets leaves the captured eSignet wire trace unredacted in the report and sidecar.
	DebugShowSecrets bool `json:"debug_show_secrets"`
}

// KnownIssue names a module already known to fail.
type KnownIssue struct {
	Module string `json:"module"`
	Reason string `json:"reason"`
}

// FlagExplicit reports whether the named flag was actually given on the command
// line (as opposed to sitting at its default).
func FlagExplicit(name string) bool {
	seen := false
	flag.Visit(func(f *flag.Flag) {
		if f.Name == name {
			seen = true
		}
	})
	return seen
}

// ResolvePath picks the config path and reports whether a missing file is fatal.
func ResolvePath(flagVal string, flagExplicit bool) (path string, mustExist bool) {
	if flagExplicit {
		return flagVal, true
	}
	if v := strings.TrimSpace(os.Getenv("CONFIG")); v != "" {
		return v, true
	}
	return flagVal, false
}

// legacyKeyRenames maps config keys this harness used to accept to their current
// names. json.Unmarshal ignores unknown keys, so without this an overlay still
// carrying the old name loses that whole block in silence — and api.tls_verify
// defaults back to true, which fails closed against a self-signed deployment and
// reads as a certificate problem rather than a stale config.
var legacyKeyRenames = map[string]string{"bdd": "api"}

// rejectLegacyKeys reports a config still using a pre-rename top-level key.
func rejectLegacyKeys(path string, data []byte) error {
	var top map[string]json.RawMessage
	if err := json.Unmarshal(data, &top); err != nil {
		//nolint:nilerr // deliberate: malformed JSON is reported by the real decode in mergeFile, so a second message here would only duplicate it.
		return nil
	}
	for old, current := range legacyKeyRenames {
		if _, ok := top[old]; ok {
			return fmt.Errorf("%s uses the old %q block — rename it to %q (the surface was renamed; see the README)", path, old, current)
		}
	}
	return nil
}

// Load reads the plugin config, overlays config.local.json and the environment, defaults, and validates.
func Load(path string, mustExist bool) (*Config, error) {
	// Fail closed: TLS verification stays on unless a file or env explicitly disables it.
	c := &Config{
		Conformance: Conformance{TLSVerify: true},
		API:         API{TLSVerify: true},
		Esignet:     Esignet{TLSVerify: true},
	}

	if path != "" {
		found, err := c.mergeFile(path)
		if err != nil {
			return nil, err
		}
		if !found && mustExist {
			return nil, fmt.Errorf("config %q does not exist (it was named explicitly, so it is not skipped — check the path/spelling)", path)
		}
		if found {
			c.Sources = append(c.Sources, path)
		}
	}

	// The local overlay lives next to the selected config unless CONFIG_LOCAL points elsewhere.
	localPath, localExplicit := strings.TrimSpace(os.Getenv("CONFIG_LOCAL")), true
	if localPath == "" {
		localExplicit = false
		dir := "."
		if path != "" {
			dir = filepath.Dir(path)
		}
		localPath = filepath.Join(dir, LocalOverlayName)
	}
	found, err := c.mergeFile(localPath)
	if err != nil {
		return nil, err
	}
	if !found && localExplicit {
		return nil, fmt.Errorf("config overlay %q does not exist (named via CONFIG_LOCAL)", localPath)
	}
	if found {
		c.Sources = append(c.Sources, localPath)
	}

	// Folded before the environment is applied, so PLAN_<n>_* sees the legacy block as plans[0].
	if err := c.resolvePlans(); err != nil {
		return nil, err
	}

	n, err := c.applyEnv()
	if err != nil {
		return nil, err
	}
	if n > 0 {
		c.Sources = append(c.Sources, fmt.Sprintf("%d env override(s)", n))
	}

	c.defaults()
	if err := c.validate(); err != nil {
		// Return the fully-resolved config alongside the error, so -check can report what is missing.
		return c, err
	}
	return c, nil
}

// mergeFile overlays one JSON layer onto c, reporting whether the file existed.
func (c *Config) mergeFile(path string) (bool, error) {
	// A missing bind-mount source makes Docker create a directory at the target, so treat that as absent.
	if st, err := os.Stat(path); err == nil && st.IsDir() {
		return false, fmt.Errorf("config %s is a directory, not a file — if this is a container, the bind mount source does not exist on the host (create it, e.g. cp data/config/config.local.example.json data/config/config.local.json)", path)
	}
	//nolint:gosec // G304: operator-supplied by design — the path comes from -config, CONFIG or CONFIG_LOCAL, never from the deployment under test.
	data, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("read %s: %w", path, err)
	}
	if err := rejectLegacyKeys(path, data); err != nil {
		return false, err
	}
	if err := json.Unmarshal(data, c); err != nil {
		return false, fmt.Errorf("parse %s: %w", path, err)
	}
	return true, nil
}

// resolvePlans folds the legacy `plan` block into `plans` and rejects a config that sets both.
func (c *Config) resolvePlans() error {
	if c.Plan == nil {
		return nil
	}
	if len(c.Plans) > 0 {
		return fmt.Errorf("config sets both `plan` and `plans` — use `plans` alone (it takes the same fields, one entry per plan)")
	}
	c.Plans = []Plan{*c.Plan}
	c.Plan = nil
	return nil
}

// ValidateSurface enforces the requirements of one surface.
func (c *Config) ValidateSurface(name string) error {
	switch name {
	case SurfaceConformance:
		if c.Conformance.BaseURL == "" {
			return fmt.Errorf("conformance.base_url (CONFORMANCE_BASE_URL) is required for the conformance surface")
		}
		if len(c.Plans) == 0 {
			return fmt.Errorf("no conformance plan configured — set plans[] (or the legacy plan block)")
		}
		seen := map[string]int{}
		for i, p := range c.Plans {
			label := fmt.Sprintf("plans[%d]", i)
			if p.Name == "" {
				return fmt.Errorf("%s.name is required (e.g. oidcc-test-plan)", label)
			}
			// Plan names key the report sections, anchors and smoke-profile files,
			// so a duplicate would silently merge two plans' results into one.
			if j, dup := seen[p.Name]; dup {
				return fmt.Errorf("%s.name %q duplicates plans[%d] — one run cannot execute the same plan twice", label, p.Name, j)
			}
			seen[p.Name] = i
			if p.ConfigFile == "" {
				return fmt.Errorf("%s.config_file (PLAN_%d_CONFIG_PATH) is required — path to the suite plan config with client jwks for %q", label, i+1, p.Name)
			}
			// Deliberately never defaulted: this file holds the private JWKS and is
			// mounted, never baked into the image or committed.
			if _, err := os.Stat(p.ConfigFile); err != nil {
				return fmt.Errorf("%s.config_file %q not readable: %w (mount the private plan config there)", label, p.ConfigFile, err)
			}
		}

	case SurfaceAPI:
		if c.Esignet.BaseURL == "" {
			return fmt.Errorf("esignet.base_url (MOSIP_ESIGNET_BASE_URL) is required for the api surface")
		}
		// Keycloak is deliberately not required: client-mgmt degrades to an ENV_NOT_READY row without it.

	case SurfaceE2E:
		if c.Esignet.BaseURL == "" {
			return fmt.Errorf("esignet.base_url (MOSIP_ESIGNET_BASE_URL) is required for the e2e surface")
		}
		// Unlike api, e2e cannot degrade: it registers a throwaway OIDC client
		// before it can drive anything, and that needs the admin grant.
		//
		// ADMIN_TOKEN satisfies that too. A target that does not enforce scope
		// never inspects the bearer — scope middleware is only installed when
		// both ISSUER_URL and JWKS_URL are set — so against such a server the
		// Keycloak round-trip would exist only to obtain a value the server
		// ignores, and a deployed IAM credential would decide whether the
		// surface runs at all. Checked here as well as at the call site so the
		// failure names the missing setting instead of surfacing as a 401 after
		// the run has started.
		if os.Getenv("ADMIN_TOKEN") == "" &&
			(c.Keycloak.TokenURL == "" || c.Keycloak.ClientID == "" || c.Keycloak.ClientSecret == "") {
			return fmt.Errorf("keycloak.token_url/client_id/client_secret (KEYCLOAK_*) are required for the e2e surface — it registers a test client before running (or set ADMIN_TOKEN for a target that does not enforce scope)")
		}
		if c.E2E.Spec == "" {
			return fmt.Errorf("e2e.spec (E2E_SPEC) is required for the e2e surface — no default known for provider %q", c.Esignet.Provider)
		}
		if _, err := os.Stat(c.E2E.Spec); err != nil {
			return fmt.Errorf("e2e.spec %q not readable: %w", c.E2E.Spec, err)
		}
		for _, group := range [][]string{c.E2E.Include, c.E2E.Exclude} {
			for _, expr := range group {
				if _, err := regexp.Compile(expr); err != nil {
					return fmt.Errorf("invalid e2e scenario regex %q: %w", expr, err)
				}
			}
		}

	default:
		return fmt.Errorf("unknown surface %q (want conformance|api|e2e)", name)
	}

	// The seeded mock identity is synthetic; a real deployment must name the identity under test.
	if name != SurfaceAPI && c.Esignet.Provider != "mock" && c.Esignet.Identity.IndividualID == "" {
		return fmt.Errorf("esignet.identity.individual_id (INDIVIDUAL_ID) is required for the %q plugin", c.Esignet.Provider)
	}
	return nil
}

// HasSurface reports whether the named surface is selected for this run.
func (c *Config) HasSurface(name string) bool {
	for _, s := range c.Run.Surfaces {
		if strings.EqualFold(strings.TrimSpace(s), name) {
			return true
		}
	}
	return false
}

// applyEnv overlays environment overrides on the loaded files and returns how many were applied.
func (c *Config) applyEnv() (int, error) {
	var bad []string
	n := 0

	envStr(&c.Conformance.BaseURL, "CONFORMANCE_BASE_URL", &n)
	envStr(&c.Conformance.Token, "CONFORMANCE_TOKEN", &n)
	envBool(&c.Conformance.TLSVerify, "CONFORMANCE_TLS_VERIFY", &n, &bad)

	if err := c.applyPlanEnv(&n); err != nil {
		return n, err
	}

	envStr(&c.Esignet.BaseURL, "MOSIP_ESIGNET_BASE_URL", &n)
	envStr(&c.Esignet.Provider, "MOSIP_ESIGNET_AUTHN_PROVIDER", &n)
	envStr(&c.Esignet.AuthFactor, "AUTH_FACTOR", &n)
	envBool(&c.Esignet.TLSVerify, "ESIGNET_TLS_VERIFY", &n, &bad)
	envStr(&c.Esignet.Identity.IndividualID, "INDIVIDUAL_ID", &n)
	envStr(&c.Esignet.Identity.IDType, "ID_TYPE", &n)
	envStr(&c.Esignet.Credentials.Username, "TEST_USERNAME", &n)
	envStr(&c.Esignet.Credentials.Password, "TEST_PASSWORD", &n)
	envStr(&c.Esignet.Knowledge.FullName, "KBI_FULL_NAME", &n)
	envStr(&c.Esignet.Knowledge.DOB, "KBI_DOB", &n)
	envStr(&c.Esignet.OTP.Source, "OTP_SOURCE", &n)
	envStr(&c.Esignet.OTP.Value, "TEST_OTP", &n)
	envStr(&c.Esignet.OTP.WSURL, "OTP_WS_URL", &n)
	envStr(&c.Esignet.OTP.RecipientEmail, "OTP_RECIPIENT_EMAIL", &n)
	envStr(&c.Esignet.PMS.BaseURL, "PMS_BASE_URL", &n)
	envStr(&c.Esignet.PMS.AuthPartnerID, "AUTH_PARTNER_ID", &n)
	envStr(&c.Esignet.PMS.PolicyID, "AUTH_POLICY_ID", &n)

	envStr(&c.Keycloak.TokenURL, "KEYCLOAK_TOKEN_URL", &n)
	envStr(&c.Keycloak.ClientID, "KEYCLOAK_CLIENT_ID", &n)
	envStr(&c.Keycloak.ClientSecret, "KEYCLOAK_CLIENT_SECRET", &n)

	envStr(&c.API.Tags, "GODOG_TAGS", &n)
	envStr(&c.API.FlowClientID, "FLOW_CLIENT_ID", &n)
	envBool(&c.API.TLSVerify, "API_TLS_VERIFY", &n, &bad)

	envStr(&c.E2E.Spec, "E2E_SPEC", &n)
	envList(&c.E2E.AuthFactors, "E2E_AUTH_FACTORS", &n)
	envList(&c.E2E.Include, "E2E_INCLUDE", &n)
	envList(&c.E2E.Exclude, "E2E_EXCLUDE", &n)

	envList(&c.Run.Surfaces, "SURFACES", &n)
	envStr(&c.Run.Profile, "TEST_PROFILE", &n)
	envStr(&c.Run.Filter, "TEST_RUN", &n)
	envList(&c.Run.Skip, "SKIP_MODULES", &n)
	envStr(&c.Run.ReportDir, "REPORT_DIR", &n)
	envInt(&c.Run.PollIntervalSeconds, "POLL_INTERVAL_SECONDS", &n, &bad)
	envInt(&c.Run.TimeoutSeconds, "TIMEOUT_SECONDS", &n, &bad)
	envBool(&c.Run.FailFast, "FAIL_FAST", &n, &bad)
	envBool(&c.Run.DebugShowSecrets, "DEBUG_SHOW_SECRETS", &n, &bad)

	if len(bad) > 0 {
		return n, fmt.Errorf("invalid environment override: %s", strings.Join(bad, "; "))
	}
	return n, nil
}

// planEnvRE matches the per-plan environment overrides, PLAN_<n>_NAME and
// PLAN_<n>_CONFIG_PATH, where <n> is the 1-based position in `plans`.
var planEnvRE = regexp.MustCompile(`^PLAN_([0-9]+)_(NAME|CONFIG_PATH)$`)

// applyPlanEnv overlays the plan environment overrides.
func (c *Config) applyPlanEnv(n *int) error {
	_, hasName := os.LookupEnv("PLAN_NAME")
	_, hasPath := os.LookupEnv("PLAN_CONFIG_PATH")
	switch {
	case (hasName || hasPath) && len(c.Plans) > 1:
		return fmt.Errorf("PLAN_NAME/PLAN_CONFIG_PATH are ambiguous with %d plans configured — use PLAN_1_CONFIG_PATH … PLAN_%d_CONFIG_PATH", len(c.Plans), len(c.Plans))
	case hasName || hasPath:
		// Zero or one plan so far: the sole plan is the one being addressed.
		// Allocate it if the file did not declare one at all (env-only container).
		if len(c.Plans) == 0 {
			c.Plans = append(c.Plans, Plan{})
		}
		envStr(&c.Plans[0].Name, "PLAN_NAME", n)
		envStr(&c.Plans[0].ConfigFile, "PLAN_CONFIG_PATH", n)
	}

	// Collect the indexed overrides sorted, so the errors below are deterministic.
	var keys []string
	seenIdx := map[int]bool{}
	highest := 0
	for _, kv := range os.Environ() {
		key, _, ok := strings.Cut(kv, "=")
		if !ok {
			continue
		}
		m := planEnvRE.FindStringSubmatch(key)
		if m == nil {
			continue
		}
		keys = append(keys, key)
		if idx, err := strconv.Atoi(m[1]); err == nil {
			seenIdx[idx] = true
			if idx > highest {
				highest = idx
			}
		}
	}
	sort.Strings(keys)

	// With no plan declared anywhere, the indexed variables are the plan list; allocate what they address.
	if len(c.Plans) == 0 && highest > 0 {
		// Only a contiguous 1..n defines a plan list.
		var missing []string
		for i := 1; i <= highest; i++ {
			if !seenIdx[i] {
				missing = append(missing, "PLAN_"+strconv.Itoa(i)+"_CONFIG_PATH")
			}
		}
		if len(missing) > 0 {
			return fmt.Errorf("PLAN_%d_* is set without %s — with no config file the indexed plan variables define the plan list, so they must start at 1 and be contiguous", highest, strings.Join(missing, ", "))
		}
		c.Plans = make([]Plan, highest)
	}

	for _, key := range keys {
		m := planEnvRE.FindStringSubmatch(key)
		idx, err := strconv.Atoi(m[1])
		if err != nil || idx < 1 || idx > len(c.Plans) {
			return fmt.Errorf("%s names plan %s but the config has %d plan(s) (indexes are 1-based)", key, m[1], len(c.Plans))
		}
		if m[2] == "NAME" {
			envStr(&c.Plans[idx-1].Name, key, n)
		} else {
			envStr(&c.Plans[idx-1].ConfigFile, key, n)
		}
	}
	return nil
}

// e2eSpecByProvider is the scenario file each plugin runs when e2e.spec is not set explicitly.
var e2eSpecByProvider = map[string]string{
	"mock":    "data/scenarios/e2e-scenarios.json",
	"mosip":   "data/scenarios/e2e-scenarios-mosip.json",
	"sunbird": "data/scenarios/e2e-scenarios-sunbird.json",
}

func (c *Config) defaults() {
	// Normalize the enum-valued fields once, so every downstream comparison agrees about casing.
	c.Esignet.Provider = strings.ToLower(strings.TrimSpace(c.Esignet.Provider))
	c.Esignet.OTP.Source = strings.ToLower(strings.TrimSpace(c.Esignet.OTP.Source))
	c.Run.Profile = strings.ToLower(strings.TrimSpace(c.Run.Profile))
	if len(c.Run.Surfaces) == 0 {
		c.Run.Surfaces = []string{SurfaceConformance, SurfaceAPI, SurfaceE2E}
	}
	// A config that names no plan at all still gets one entry, so the conformance
	// surface reports "config_file is required" rather than "no plan configured".
	if len(c.Plans) == 0 {
		c.Plans = []Plan{{}}
	}
	for i := range c.Plans {
		if c.Plans[i].Name == "" {
			c.Plans[i].Name = defaultPlanName
		}
		if len(c.Plans[i].Variant) == 0 {
			// Only the default plan gets a default variant; every other plan's variants are its own.
			if c.Plans[i].Name == defaultPlanName {
				c.Plans[i].Variant = map[string]any{
					"client_auth_type":    "private_key_jwt",
					"response_type":       "code",
					"response_mode":       "default",
					"client_registration": "static_client",
				}
			} else {
				c.Plans[i].Variant = map[string]any{}
			}
		}
	}
	if c.Esignet.Provider == "" {
		c.Esignet.Provider = "mock"
	}
	if c.Esignet.AuthFactor == "" {
		// Default the ACR selection from the provider: mock/mosip use OTP;
		// sunbird (gated) would use KBI. Override with auth_factor / AUTH_FACTOR.
		if c.Esignet.Provider == "sunbird" {
			c.Esignet.AuthFactor = "kbi"
		} else {
			c.Esignet.AuthFactor = "otp"
		}
	}
	if c.Esignet.OTP.Source == "" {
		c.Esignet.OTP.Source = "static"
	}
	if c.Esignet.OTP.Value == "" {
		c.Esignet.OTP.Value = "111111"
	}
	if c.Run.Profile == "" {
		c.Run.Profile = "smoke"
	}
	if c.Run.PollIntervalSeconds == 0 {
		c.Run.PollIntervalSeconds = 2
	}
	if c.Run.TimeoutSeconds == 0 {
		c.Run.TimeoutSeconds = 120
	}
	if c.Run.ReportDir == "" {
		c.Run.ReportDir = "out"
	}
	if c.E2E.Spec == "" {
		c.E2E.Spec = e2eSpecByProvider[c.Esignet.Provider]
	}
}

// validate enforces the supported scope: mock/mosip/sunbird with a static or dynamic OTP source.
func (c *Config) validate() error {
	for _, s := range c.Run.Surfaces {
		switch strings.ToLower(strings.TrimSpace(s)) {
		case SurfaceConformance, SurfaceAPI, SurfaceE2E:
		default:
			return fmt.Errorf("unknown run.surfaces entry %q (want conformance|api|e2e)", s)
		}
	}

	for _, s := range c.Run.Surfaces {
		if err := c.ValidateSurface(strings.ToLower(strings.TrimSpace(s))); err != nil {
			return err
		}
	}

	switch c.Esignet.Provider {
	case "mock", "mosip", "sunbird":
		// supported
	default:
		return fmt.Errorf("unknown provider %q (want mock|mosip|sunbird)", c.Esignet.Provider)
	}

	switch c.Esignet.OTP.Source {
	case "static":
		// supported: the OTP value is taken from otp.value (default 111111).
	case "dynamic":
		// The real OTP is read from the mock-SMTP WebSocket (mosipid sends a live OTP).
		if c.Esignet.OTP.WSURL == "" {
			return fmt.Errorf("otp.source=dynamic needs otp.ws_url (OTP_WS_URL), e.g. https://smtp.<env>.mosip.net/ or wss://smtp.<env>.mosip.net/mocksmtp/websocket")
		}
	default:
		return fmt.Errorf("unknown otp.source %q (want static|dynamic)", c.Esignet.OTP.Source)
	}

	switch c.Run.Profile {
	case "smoke", "full":
	default:
		return fmt.Errorf("unknown run.profile %q (want smoke|full)", c.Run.Profile)
	}

	// A non-positive interval reaches time.Sleep, which returns immediately and
	// turns the readiness poll into a tight request loop against the suite.
	if c.Run.PollIntervalSeconds <= 0 {
		return fmt.Errorf("run.poll_interval_seconds must be > 0 (got %d)", c.Run.PollIntervalSeconds)
	}
	if c.Run.TimeoutSeconds <= 0 {
		return fmt.Errorf("run.timeout_seconds must be > 0 (got %d)", c.Run.TimeoutSeconds)
	}
	return nil
}

// envStr overrides a string from the environment, counting the override in n.
func envStr(dst *string, key string, n *int) {
	if v, ok := os.LookupEnv(key); ok {
		*dst = v
		*n++
	}
}

// envBool overrides a bool from the environment, collecting unparseable values
// into bad instead of ignoring them (see applyEnv).
func envBool(dst *bool, key string, n *int, bad *[]string) {
	if v, ok := os.LookupEnv(key); ok {
		b, err := strconv.ParseBool(strings.TrimSpace(v))
		if err != nil {
			*bad = append(*bad, fmt.Sprintf("%s=%q is not a boolean (want true|false)", key, v))
			return
		}
		*dst = b
		*n++
	}
}

// envInt overrides an int from the environment, collecting unparseable values
// into bad instead of ignoring them (see applyEnv).
func envInt(dst *int, key string, n *int, bad *[]string) {
	if v, ok := os.LookupEnv(key); ok {
		i, err := strconv.Atoi(strings.TrimSpace(v))
		if err != nil {
			*bad = append(*bad, fmt.Sprintf("%s=%q is not an integer", key, v))
			return
		}
		*dst = i
		*n++
	}
}

// envList overrides a string slice from a comma-separated environment variable.
func envList(dst *[]string, key string, n *int) {
	if v, ok := os.LookupEnv(key); ok {
		var out []string
		for _, part := range strings.Split(v, ",") {
			if p := strings.TrimSpace(part); p != "" {
				out = append(out, p)
			}
		}
		*dst = out
		*n++
	}
}

// Redacted returns the effective config as pretty JSON with secrets masked, for
// display in the report's configuration panel.
func (c *Config) Redacted() string {
	clone := *c
	mask := func(s string) string {
		if s == "" {
			return ""
		}
		return "***redacted***"
	}
	clone.Conformance.Token = mask(clone.Conformance.Token)
	clone.Keycloak.ClientSecret = mask(clone.Keycloak.ClientSecret)
	// The username is a login identifier on every plugin but mock, and the wire trace already masks it.
	clone.Esignet.Credentials.Username = mask(clone.Esignet.Credentials.Username)
	clone.Esignet.Credentials.Password = mask(clone.Esignet.Credentials.Password)
	// Authenticator + personal data: reports are archived as CI artifacts.
	clone.Esignet.OTP.Value = mask(clone.Esignet.OTP.Value)
	clone.Esignet.OTP.RecipientEmail = mask(clone.Esignet.OTP.RecipientEmail)
	clone.Esignet.Identity.IndividualID = mask(clone.Esignet.Identity.IndividualID)
	clone.Esignet.Knowledge.FullName = mask(clone.Esignet.Knowledge.FullName)
	clone.Esignet.Knowledge.DOB = mask(clone.Esignet.Knowledge.DOB)
	data, err := json.MarshalIndent(clone, "", "  ")
	if err != nil {
		return fmt.Sprintf("(config marshal error: %v)", err)
	}
	return string(data)
}

// privateJWKParams are the JWK members that carry private key material and must
// never appear in the report.
var privateJWKParams = map[string]bool{
	"d": true, "p": true, "q": true, "dp": true, "dq": true, "qi": true, "k": true, "oth": true,
}

// RedactedPlanConfig returns the suite plan-config file as pretty JSON with private key material masked.
func RedactedPlanConfig(path string) string {
	if path == "" {
		return ""
	}
	//nolint:gosec // G304: operator-supplied by design — the path comes from plans[].config_file, never from the deployment under test.
	data, err := os.ReadFile(path)
	if err != nil {
		return fmt.Sprintf("(could not read plan config %s: %v)", path, err)
	}
	var v any
	if err := json.Unmarshal(data, &v); err != nil {
		return fmt.Sprintf("(plan config %s is not valid JSON: %v)", path, err)
	}
	RedactJWKMaterial(v)
	out, err := json.MarshalIndent(v, "", "  ")
	if err != nil {
		return fmt.Sprintf("(plan config marshal error: %v)", err)
	}
	return string(out)
}

// RedactJWKMaterial walks parsed JSON in place, masking private JWK params and any "secret" key.
func RedactJWKMaterial(v any) {
	switch t := v.(type) {
	case map[string]any:
		isJWK := t["kty"] != nil
		for k, val := range t {
			if (isJWK && privateJWKParams[k]) || strings.Contains(strings.ToLower(k), "secret") {
				// Any non-nil value, not just a string: RFC 7518 defines `oth` as an array.
				if val != nil {
					t[k] = "***redacted***"
				}
				continue
			}
			RedactJWKMaterial(val)
		}
	case []any:
		for _, val := range t {
			RedactJWKMaterial(val)
		}
	}
}
