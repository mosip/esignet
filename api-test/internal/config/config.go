// Package config loads the harness configuration for all three surfaces
// (conformance, bdd, e2e) from JSON files and overlays environment variables on
// top, so the same binary/image runs both locally and in containers. See the
// plan doc §8b/§8f for the field-by-field mapping.
//
// Layering, lowest precedence first:
//
//	config.<plugin>.json   the tracked per-plugin file: which surfaces, which
//	                       modules/tags/scenarios, which auth factor. Committed
//	                       and reviewed — it defines what the test DOES.
//	config.local.json      gitignored overlay next to it: your credentials and
//	                       machine-specific URLs. Never tracked, so a secret can
//	                       never ride along in a commit to the file above.
//	environment            always wins; how containers (Rancher/compose) inject
//	                       secrets and per-deployment values.
//	defaults()             fills anything still empty.
//
// Each layer overrides only the keys it names, at any nesting depth, because
// json.Unmarshal leaves absent fields untouched — so the layers are just
// sequential unmarshals into the same struct. Slice fields (surfaces,
// auth_factors, modules) replace wholesale rather than append, which is the
// behaviour wanted for filters.
package config

import (
	"encoding/json"
	"flag"
	"fmt"
	"os"
	"path/filepath"
	"regexp"
	"strconv"
	"strings"
)

// Surface names a test surface that can be listed in run.surfaces.
const (
	SurfaceConformance = "conformance"
	SurfaceBDD         = "bdd"
	SurfaceE2E         = "e2e"
)

// LocalOverlayName is the gitignored per-developer overlay, looked up next to
// the selected config file. Absent is normal (containers use env instead).
const LocalOverlayName = "config.local.json"

type Config struct {
	Conformance Conformance `json:"conformance"`
	Plan        Plan        `json:"plan"`
	Esignet     Esignet     `json:"esignet"`
	Keycloak    Keycloak    `json:"keycloak"`
	BDD         BDD         `json:"bdd"`
	E2E         E2E         `json:"e2e"`
	Run         Run         `json:"run"`

	// Sources lists the layers that actually contributed, newest last, for the
	// --check output. Not part of the file format.
	Sources []string `json:"-"`
}

type Conformance struct {
	BaseURL   string `json:"base_url"`
	TLSVerify bool   `json:"tls_verify"`
	Token     string `json:"token"`
}

type Plan struct {
	Name       string         `json:"name"`
	Variant    map[string]any `json:"variant"`
	ConfigFile string         `json:"config_file"`
}

type Esignet struct {
	BaseURL     string      `json:"base_url"`
	Provider    string      `json:"provider"`
	AuthFactor  string      `json:"auth_factor"` // which ACR to select when the flow offers a choice: otp|password|bio|kbi
	Identity    Identity    `json:"identity"`
	Credentials Credentials `json:"credentials"`
	Knowledge   Knowledge   `json:"knowledge"`
	OTP         OTP         `json:"otp"`
	PMS         PMS         `json:"pms"`
}

// PMS holds partner-management-service settings used only by the mosip(id)
// plugin. For mosipid, test OIDC clients must be registered through PMS
// /oauth/client (not eSignet's client-mgmt directly) so IDA gets the
// partner+policy binding: PMS writes AuthPartnerID as the client's relying-party
// id and PolicyID governs the released KYC claims / allowed ACRs. Both the
// partner and policy are expected to already be onboarded/published on the
// target environment — we only reference their ids here. PMS reuses the same
// Keycloak credentials as client-mgmt (KEYCLOAK_*); for mosipid the operator
// supplies the partner-client secret in those.
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
	// RecipientEmail filters dynamic-OTP messages to a single recipient. Despite
	// the name it matches an email address OR a phone number (MOSIP OTPs to a UIN
	// arrive as SMS, whose recipient is the phone). Leave empty to take the newest
	// fresh 6-digit code from any recipient — reliable for a single-identity run.
	RecipientEmail string `json:"recipient_email"`
}

// Keycloak holds the client-credentials grant used for admin auth: eSignet
// client-mgmt (bdd) and test-client registration (e2e). Previously env-only
// (KEYCLOAK_*); the secret belongs in config.local.json or the environment, not
// in a tracked per-plugin file.
type Keycloak struct {
	TokenURL     string `json:"token_url"`
	ClientID     string `json:"client_id"`
	ClientSecret string `json:"client_secret"`
}

// BDD configures the godog surfaces. The bdd/ tree is a separate Go module and
// cannot import this package, so these reach it as environment variables — see
// cmd/cfg, which renders a Config back out as shell exports.
type BDD struct {
	// Tags is the godog tag expression (comma = OR). Empty lets the suite pick
	// its own default set based on which credentials are configured.
	Tags string `json:"tags"`
	// FlowClientID is a pre-registered client id the authorize-negative
	// scenarios drive; empty gates those cases out as ENV_NOT_READY.
	FlowClientID string `json:"flow_client_id"`
	TLSVerify    bool   `json:"tls_verify"`
}

// E2E configures the end-to-end surface: which scenario file to load and which
// of its scenarios to actually run. The spec files each carry scenarios across
// several ACRs (otp/password/bio/kbi), and one run drives them all by default —
// these fields narrow that set for a focused manual run.
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
	// conformance, bdd, e2e. Defaults to all three.
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
}

// KnownIssue names a module we already know fails (bug filed, upstream gap, etc.).
// It is not executed; it is reported in the Known bucket with its reason so it
// stays visible without polluting the Failed count or the exit code.
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
// Precedence: an explicit -config flag, then the CONFIG environment variable,
// then the flag's default.
//
// A path the operator explicitly chose MUST exist. Falling through to defaults
// on `CONFIG=config.msoip.json` would run the mock plugin while the operator
// believes mosip ran, and a green report would be read as mosip passing.
func ResolvePath(flagVal string, flagExplicit bool) (path string, mustExist bool) {
	if flagExplicit {
		return flagVal, true
	}
	if v := strings.TrimSpace(os.Getenv("CONFIG")); v != "" {
		return v, true
	}
	return flagVal, false
}

// Load reads the per-plugin config file, overlays config.local.json from the
// same directory, overlays the environment, fills defaults, and validates. See
// the package doc for the layering rules.
//
// mustExist makes a missing path an error; when false a missing file is fine,
// which is how a container run supplies everything through the environment.
func Load(path string, mustExist bool) (*Config, error) {
	// Fail closed: TLS verification stays on unless a file or env explicitly
	// disables it. json.Unmarshal leaves absent fields untouched, so seeding true
	// here makes omission mean "verify".
	c := &Config{
		Conformance: Conformance{TLSVerify: true},
		BDD:         BDD{TLSVerify: true},
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

	// The local overlay lives next to the selected config unless CONFIG_LOCAL
	// points elsewhere. An explicitly named overlay must exist, for the same
	// reason an explicitly named config must.
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

	n, err := c.applyEnv()
	if err != nil {
		return nil, err
	}
	if n > 0 {
		c.Sources = append(c.Sources, fmt.Sprintf("%d env override(s)", n))
	}

	c.defaults()
	if err := c.validate(); err != nil {
		return nil, err
	}
	return c, nil
}

// mergeFile overlays one JSON layer onto c, reporting whether the file existed.
// Absent keys leave the existing value alone, which is what makes the layering
// work without any explicit merge logic.
func (c *Config) mergeFile(path string) (bool, error) {
	// A bind mount whose source is missing makes Docker create a DIRECTORY at the
	// target, so this is the shape a forgotten `cp config.local.example.json
	// config.local.json` takes inside a container. Name it rather than surfacing
	// a bare "is a directory" read error.
	if st, err := os.Stat(path); err == nil && st.IsDir() {
		return false, fmt.Errorf("config %s is a directory, not a file — if this is a container, the bind mount source does not exist on the host (create it, e.g. cp config.local.example.json config.local.json)", path)
	}
	data, err := os.ReadFile(path)
	if os.IsNotExist(err) {
		return false, nil
	}
	if err != nil {
		return false, fmt.Errorf("read %s: %w", path, err)
	}
	if err := json.Unmarshal(data, c); err != nil {
		return false, fmt.Errorf("parse %s: %w", path, err)
	}
	return true, nil
}

// ValidateSurface enforces the requirements of one surface. Load applies it to
// every entry in run.surfaces; a single-surface binary invoked directly calls it
// for its own surface, so `e2e -config config.mosip.json` still checks the
// Keycloak grant even when the file's run.surfaces lists only conformance.
func (c *Config) ValidateSurface(name string) error {
	switch name {
	case SurfaceConformance:
		if c.Conformance.BaseURL == "" {
			return fmt.Errorf("conformance.base_url (CONFORMANCE_BASE_URL) is required for the conformance surface")
		}
		if c.Plan.ConfigFile == "" {
			return fmt.Errorf("plan.config_file (PLAN_CONFIG_PATH) is required — path to the suite plan config with client jwks")
		}
		// Deliberately never defaulted: this file holds the private JWKS and is
		// mounted, never baked into the image or committed.
		if _, err := os.Stat(c.Plan.ConfigFile); err != nil {
			return fmt.Errorf("plan.config_file %q not readable: %w (mount the private plan config there)", c.Plan.ConfigFile, err)
		}

	case SurfaceBDD:
		if c.Esignet.BaseURL == "" {
			return fmt.Errorf("esignet.base_url (ESIGNET_BASE_URL) is required for the bdd surface")
		}
		// Keycloak is deliberately NOT required here: the client-mgmt features
		// degrade to an explicit ENV_NOT_READY row when the admin grant is
		// missing, which keeps the section visible in the report.

	case SurfaceE2E:
		if c.Esignet.BaseURL == "" {
			return fmt.Errorf("esignet.base_url (ESIGNET_BASE_URL) is required for the e2e surface")
		}
		// Unlike bdd, e2e cannot degrade: it registers a throwaway OIDC client
		// before it can drive anything, and that needs the admin grant.
		if c.Keycloak.TokenURL == "" || c.Keycloak.ClientID == "" || c.Keycloak.ClientSecret == "" {
			return fmt.Errorf("keycloak.token_url/client_id/client_secret (KEYCLOAK_*) are required for the e2e surface — it registers a test client before running")
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
		return fmt.Errorf("unknown surface %q (want conformance|bdd|e2e)", name)
	}

	// The seeded mock identity is synthetic; any real deployment must name the
	// identity under test explicitly. Without this, a run against a live
	// environment authenticates as — and reports claims for — whoever owns
	// whatever identifier happened to be left in the config.
	if name != SurfaceBDD && !strings.EqualFold(c.Esignet.Provider, "mock") && c.Esignet.Identity.IndividualID == "" {
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

// applyEnv overlays environment overrides on the loaded files and returns how
// many were applied (shown by --check, so an unexpected value can be traced to
// the environment rather than the file). A malformed bool/int override is a hard
// error rather than a silent no-op: swallowing CONFORMANCE_TLS_VERIFY=ture would
// leave verification wherever the file left it, so a typo could silently accept
// forged certificates.
func (c *Config) applyEnv() (int, error) {
	var bad []string
	n := 0

	envStr(&c.Conformance.BaseURL, "CONFORMANCE_BASE_URL", &n)
	envStr(&c.Conformance.Token, "CONFORMANCE_TOKEN", &n)
	envBool(&c.Conformance.TLSVerify, "CONFORMANCE_TLS_VERIFY", &n, &bad)

	envStr(&c.Plan.Name, "PLAN_NAME", &n)
	envStr(&c.Plan.ConfigFile, "PLAN_CONFIG_PATH", &n)

	envStr(&c.Esignet.BaseURL, "ESIGNET_BASE_URL", &n)
	envStr(&c.Esignet.Provider, "AUTHN_PROVIDER", &n)
	envStr(&c.Esignet.AuthFactor, "AUTH_FACTOR", &n)
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

	envStr(&c.BDD.Tags, "GODOG_TAGS", &n)
	envStr(&c.BDD.FlowClientID, "FLOW_CLIENT_ID", &n)
	envBool(&c.BDD.TLSVerify, "BDD_TLS_VERIFY", &n, &bad)

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

	if len(bad) > 0 {
		return n, fmt.Errorf("invalid environment override: %s", strings.Join(bad, "; "))
	}
	return n, nil
}

// e2eSpecByProvider is the scenario file each plugin runs when e2e.spec is not
// set explicitly. Keeps the per-plugin config files from having to restate the
// obvious while still allowing an override to a mounted spec.
var e2eSpecByProvider = map[string]string{
	"mock":    "e2e-scenarios.json",
	"mosip":   "e2e-scenarios-mosip.json",
	"sunbird": "e2e-scenarios-sunbird.json",
}

func (c *Config) defaults() {
	if len(c.Run.Surfaces) == 0 {
		c.Run.Surfaces = []string{SurfaceConformance, SurfaceBDD, SurfaceE2E}
	}
	if c.Plan.Name == "" {
		c.Plan.Name = "oidcc-test-plan"
	}
	if len(c.Plan.Variant) == 0 {
		c.Plan.Variant = map[string]any{
			"client_auth_type":    "private_key_jwt",
			"response_type":       "code",
			"response_mode":       "default",
			"client_registration": "static_client",
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
		c.E2E.Spec = e2eSpecByProvider[strings.ToLower(c.Esignet.Provider)]
	}
}

// validate enforces the supported scope: mock/mosip/sunbird providers with a
// static or dynamic OTP source. sunbird runs like the others (plan doc §8e); its
// environment prerequisites (reachable KBI flow, seeded registry) surface as a
// runtime ENV_NOT_READY / failure, not a hard config rejection here. dynamic OTP
// is the mock-SMTP listener the mosipid plugin uses to read a live OTP.
//
// Requirements are scoped to the selected surfaces: one config file now drives
// all three, so an e2e-only run must not be rejected for missing a conformance
// field it never touches.
func (c *Config) validate() error {
	for _, s := range c.Run.Surfaces {
		switch strings.ToLower(strings.TrimSpace(s)) {
		case SurfaceConformance, SurfaceBDD, SurfaceE2E:
		default:
			return fmt.Errorf("unknown run.surfaces entry %q (want conformance|bdd|e2e)", s)
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
		// The real OTP is read from the mock-SMTP WebSocket (mosipid sends a
		// live OTP). Requires a reachable ws_url; recipient is optional (when
		// unset, the newest fresh 6-digit message is used).
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
//
// An empty value counts and applies: a container that passes FOO="" to blank a
// field set by the config file must be able to. That is why these check
// LookupEnv rather than treating "" as unset.
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
// The slice is replaced wholesale, not appended to — a filter set in the file
// must be fully overridable from the environment.
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

// RedactedPlanConfig reads the suite plan-config file (the one POSTed to
// /api/plan, holding the client id + jwks) and returns it as pretty JSON with
// private JWK key material and any *secret* fields masked, for the report's
// configuration panel. Returns a short note (not an error) on read/parse issues
// so the report still renders.
func RedactedPlanConfig(path string) string {
	if path == "" {
		return ""
	}
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

// RedactJWKMaterial walks parsed JSON and masks private JWK params (only inside
// JWK objects, identified by a "kty" member) and any key containing "secret", in
// place. Exported so the report can scrub the suite's condition log too — the
// suite echoes the POSTed client config (jwks and all) back into its log.
func RedactJWKMaterial(v any) {
	switch t := v.(type) {
	case map[string]any:
		isJWK := t["kty"] != nil
		for k, val := range t {
			if (isJWK && privateJWKParams[k]) || strings.Contains(strings.ToLower(k), "secret") {
				if s, ok := val.(string); ok && s != "" {
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
