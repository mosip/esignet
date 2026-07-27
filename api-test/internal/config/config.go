// Package config loads the harness configuration from a JSON file and overlays
// environment variables on top, so the same binary/image runs both locally
// (config.json) and in containers (env + mounted secrets). See the plan doc
// §8b/§8f for the field-by-field mapping.
package config

import (
	"encoding/json"
	"fmt"
	"os"
	"strconv"
	"strings"
)

type Config struct {
	Conformance Conformance `json:"conformance"`
	Plan        Plan        `json:"plan"`
	Esignet     Esignet     `json:"esignet"`
	Run         Run         `json:"run"`
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

type Run struct {
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

// Load reads the JSON config file (if present), applies env overrides, fills
// defaults, and validates. A missing file is allowed when the full config is
// supplied through the environment.
func Load(path string) (*Config, error) {
	// Fail closed: TLS verification stays on unless the file or env explicitly
	// disables it. json.Unmarshal leaves absent fields untouched, so seeding true
	// here makes omission mean "verify".
	c := &Config{Conformance: Conformance{TLSVerify: true}}
	if path != "" {
		data, err := os.ReadFile(path)
		if err == nil {
			if err := json.Unmarshal(data, c); err != nil {
				return nil, fmt.Errorf("parse %s: %w", path, err)
			}
		} else if !os.IsNotExist(err) {
			return nil, fmt.Errorf("read %s: %w", path, err)
		}
	}

	if err := c.applyEnv(); err != nil {
		return nil, err
	}
	c.defaults()
	if err := c.validate(); err != nil {
		return nil, err
	}
	return c, nil
}

// applyEnv overlays environment overrides on the loaded file. A malformed
// bool/int override is a hard error rather than a silent no-op: swallowing
// CONFORMANCE_TLS_VERIFY=ture would leave verification wherever the file left
// it, so a typo could silently accept forged certificates.
func (c *Config) applyEnv() error {
	var bad []string

	envStr(&c.Conformance.BaseURL, "CONFORMANCE_BASE_URL")
	envStr(&c.Conformance.Token, "CONFORMANCE_TOKEN")
	envBool(&c.Conformance.TLSVerify, "CONFORMANCE_TLS_VERIFY", &bad)

	envStr(&c.Plan.Name, "PLAN_NAME")
	envStr(&c.Plan.ConfigFile, "PLAN_CONFIG_PATH")

	envStr(&c.Esignet.BaseURL, "ESIGNET_BASE_URL")
	envStr(&c.Esignet.Provider, "AUTHN_PROVIDER")
	envStr(&c.Esignet.AuthFactor, "AUTH_FACTOR")
	envStr(&c.Esignet.Identity.IndividualID, "INDIVIDUAL_ID")
	envStr(&c.Esignet.Identity.IDType, "ID_TYPE")
	envStr(&c.Esignet.Credentials.Username, "TEST_USERNAME")
	envStr(&c.Esignet.Credentials.Password, "TEST_PASSWORD")
	envStr(&c.Esignet.Knowledge.FullName, "KBI_FULL_NAME")
	envStr(&c.Esignet.Knowledge.DOB, "KBI_DOB")
	envStr(&c.Esignet.OTP.Source, "OTP_SOURCE")
	envStr(&c.Esignet.OTP.Value, "TEST_OTP")
	envStr(&c.Esignet.OTP.WSURL, "OTP_WS_URL")
	envStr(&c.Esignet.OTP.RecipientEmail, "OTP_RECIPIENT_EMAIL")
	envStr(&c.Esignet.PMS.BaseURL, "PMS_BASE_URL")
	envStr(&c.Esignet.PMS.AuthPartnerID, "AUTH_PARTNER_ID")
	envStr(&c.Esignet.PMS.PolicyID, "AUTH_POLICY_ID")

	envStr(&c.Run.Profile, "TEST_PROFILE")
	envStr(&c.Run.Filter, "TEST_RUN")
	envList(&c.Run.Skip, "SKIP_MODULES")
	envStr(&c.Run.ReportDir, "REPORT_DIR")
	envInt(&c.Run.PollIntervalSeconds, "POLL_INTERVAL_SECONDS", &bad)
	envInt(&c.Run.TimeoutSeconds, "TIMEOUT_SECONDS", &bad)
	envBool(&c.Run.FailFast, "FAIL_FAST", &bad)

	if len(bad) > 0 {
		return fmt.Errorf("invalid environment override: %s", strings.Join(bad, "; "))
	}
	return nil
}

func (c *Config) defaults() {
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
}

// validate enforces the supported scope: mock/mosip/sunbird providers with a
// static OTP source. sunbird runs like the others (plan doc §8e); its
// environment prerequisites (reachable KBI flow, seeded registry) surface as a
// runtime ENV_NOT_READY / failure, not a hard config rejection here. dynamic OTP
// is the mock-SMTP listener the mosipid plugin uses to read a live OTP.
func (c *Config) validate() error {
	if c.Conformance.BaseURL == "" {
		return fmt.Errorf("conformance.base_url (CONFORMANCE_BASE_URL) is required")
	}
	if c.Plan.ConfigFile == "" {
		return fmt.Errorf("plan.config_file (PLAN_CONFIG_PATH) is required — path to the suite plan config with client jwks")
	}
	if _, err := os.Stat(c.Plan.ConfigFile); err != nil {
		return fmt.Errorf("plan.config_file %q not readable: %w", c.Plan.ConfigFile, err)
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

func envStr(dst *string, key string) {
	if v, ok := os.LookupEnv(key); ok {
		*dst = v
	}
}

// envBool overrides a bool from the environment, collecting unparseable values
// into bad instead of ignoring them (see applyEnv).
func envBool(dst *bool, key string, bad *[]string) {
	if v, ok := os.LookupEnv(key); ok {
		b, err := strconv.ParseBool(strings.TrimSpace(v))
		if err != nil {
			*bad = append(*bad, fmt.Sprintf("%s=%q is not a boolean (want true|false)", key, v))
			return
		}
		*dst = b
	}
}

// envInt overrides an int from the environment, collecting unparseable values
// into bad instead of ignoring them (see applyEnv).
func envInt(dst *int, key string, bad *[]string) {
	if v, ok := os.LookupEnv(key); ok {
		n, err := strconv.Atoi(strings.TrimSpace(v))
		if err != nil {
			*bad = append(*bad, fmt.Sprintf("%s=%q is not an integer", key, v))
			return
		}
		*dst = n
	}
}

// envList overrides a string slice from a comma-separated environment variable.
func envList(dst *[]string, key string) {
	if v, ok := os.LookupEnv(key); ok {
		var out []string
		for _, part := range strings.Split(v, ",") {
			if p := strings.TrimSpace(part); p != "" {
				out = append(out, p)
			}
		}
		*dst = out
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
