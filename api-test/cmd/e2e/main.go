// Command e2e runs the end-to-end surface: register a throwaway OIDC client, then
// for each scenario drive authorize -> flow/execute login -> token
// (private_key_jwt) -> userinfo, and assert the returned claims. It writes a
// result.ModuleResult envelope (Surface=e2e) to out/e2e-envelope.json for the
// consolidation runner. Here the harness IS the relying party (unlike the
// conformance surface, where the suite is).
//
// Required env: ESIGNET_BASE_URL, KEYCLOAK_TOKEN_URL/CLIENT_ID/CLIENT_SECRET.
// Optional env: AUTHN_PROVIDER, INDIVIDUAL_ID, ID_TYPE, TEST_OTP, AUTH_FACTOR,
// E2E_SPEC (default e2e-scenarios.json), BDD_TLS_VERIFY.
// For AUTHN_PROVIDER=mosip the client is registered via PMS, which also needs
// PMS_BASE_URL, AUTH_PARTNER_ID, AUTH_POLICY_ID.
package main

import (
	"crypto/tls"
	"encoding/json"
	"flag"
	"fmt"
	"io"
	"log"
	"net/http"
	"net/url"
	"os"
	"path/filepath"
	"strings"
	"time"

	"github.com/mosip/esignet/api-test/internal/config"
	"github.com/mosip/esignet/api-test/internal/e2e"
	"github.com/mosip/esignet/api-test/internal/esignet"
	"github.com/mosip/esignet/api-test/internal/result"
)

func main() {
	specPath := flag.String("spec", env("E2E_SPEC", "e2e-scenarios.json"), "path to the scenarios JSON")
	outPath := flag.String("out", "out/e2e-envelope.json", "envelope output path")
	flag.Parse()

	logger := log.New(os.Stderr, "", log.LstdFlags)

	base := strings.TrimRight(os.Getenv("ESIGNET_BASE_URL"), "/")
	if base == "" {
		logger.Fatal("ESIGNET_BASE_URL is required")
	}
	// Fail closed: verification is only skipped when explicitly opted out, so a
	// run against a real deployment never sends the client secret unverified.
	tlsVerify := !strings.EqualFold(os.Getenv("BDD_TLS_VERIFY"), "false")

	// Discovery -> endpoints.
	disco, err := fetchDiscovery(base+"/.well-known/openid-configuration", tlsVerify)
	if err != nil {
		logger.Fatalf("discovery: %v", err)
	}

	// Admin token for client registration.
	adminTok, err := keycloakToken(tlsVerify)
	if err != nil {
		logger.Fatalf("keycloak admin token: %v", err)
	}

	// Base login inputs (reuse the same resolver the conformance surface uses).
	// Each scenario declares its own auth_factor (ACR) and may override any of
	// these via its credentials map — there is no single global auth factor
	// anymore, since one run now drives every ACR the plugin supports.
	provider := env("AUTHN_PROVIDER", "mock")
	// The seeded identity is a default only for the mock plugin, whose data is
	// synthetic. Against any real deployment an unset INDIVIDUAL_ID must fail
	// rather than silently authenticate as (and report claims for) whoever owns
	// that identifier.
	individualID := os.Getenv("INDIVIDUAL_ID")
	if individualID == "" {
		if !strings.EqualFold(provider, "mock") {
			logger.Fatalf("INDIVIDUAL_ID is required for the %q plugin", provider)
		}
		individualID = "+912532509749" // mock plugin's seeded test identity
	}

	// Same rule for the password ACR: the mock plugin's seeded login is synthetic
	// and lives here rather than in the committed scenario file, so no scenario
	// carries a credential pattern. Any other plugin must supply TEST_USERNAME /
	// TEST_PASSWORD; without them the password scenarios report FAILED, which is
	// the documented behaviour for an ACR with no configured answer.
	username, password := os.Getenv("TEST_USERNAME"), os.Getenv("TEST_PASSWORD")
	if strings.EqualFold(provider, "mock") {
		if username == "" {
			username = "decl-user-1"
		}
		if password == "" {
			password = "Mosip@123"
		}
	}

	es := config.Esignet{
		Provider: provider,
		Identity: config.Identity{IndividualID: individualID, IDType: env("ID_TYPE", "phone")},
		Credentials: config.Credentials{
			Username: username,
			Password: password,
		},
		Knowledge: config.Knowledge{
			FullName: env("KBI_FULL_NAME", ""),
			DOB:      env("KBI_DOB", ""),
		},
		OTP: config.OTP{
			Source:         env("OTP_SOURCE", "static"),
			Value:          env("TEST_OTP", "111111"),
			WSURL:          os.Getenv("OTP_WS_URL"),
			RecipientEmail: os.Getenv("OTP_RECIPIENT_EMAIL"),
		},
		PMS: config.PMS{
			BaseURL:       os.Getenv("PMS_BASE_URL"),
			AuthPartnerID: os.Getenv("AUTH_PARTNER_ID"),
			PolicyID:      os.Getenv("AUTH_POLICY_ID"),
		},
	}

	var spec e2e.Spec
	if err := loadJSON(*specPath, &spec); err != nil {
		logger.Fatalf("load spec %s: %v", *specPath, err)
	}

	// Dynamic OTP (mock-SMTP listener) lands with the mosipid plugin; this build
	// only drives the static OTP source. This binary assembles config.Esignet
	// itself and never goes through config.Load, so validate() does not run here —
	// reject it explicitly, or BuildAnswers omits the otp answer and every OTP
	// scenario dies deep in the driver as "no configured answer for flow input(s)".
	if strings.EqualFold(es.OTP.Source, "dynamic") {
		logger.Fatal("OTP_SOURCE=dynamic is not supported yet — the mock-SMTP listener lands with the mosipid plugin; use OTP_SOURCE=static")
	}
	var otpProvider esignet.OTPProvider

	runner := &e2e.Runner{
		Base:             base,
		Issuer:           disco.Issuer,
		AuthEndpoint:     disco.AuthorizationEndpoint,
		TokenEndpoint:    disco.TokenEndpoint,
		UserinfoEndpoint: disco.UserinfoEndpoint,
		JWKSURI:          disco.JWKSURI,
		AdminToken:       adminTok,
		Plugin:           es.Provider,
		Answers:          esignet.BuildAnswers(es),
		IDType:           es.Identity.IDType,
		TLSVerify:        tlsVerify,
		Timeout:          90 * time.Second,
		Logf:             logger.Printf,
		OTP:              otpProvider,
		PMSBaseURL:       es.PMS.BaseURL,
		AuthPartnerID:    es.PMS.AuthPartnerID,
		PolicyID:         es.PMS.PolicyID,
	}

	rows := runner.Run(spec)
	if err := writeEnvelope(*outPath, rows); err != nil {
		logger.Fatalf("write envelope: %v", err)
	}

	sum := result.Summarize(rows)
	fmt.Printf("\n== E2E — %s ==\n", es.Provider)
	fmt.Printf("total=%d passed=%d failed=%d errored=%d\n", sum.Total, sum.Passed, sum.Failed, sum.Errored)
	fmt.Printf("envelope: %s\n", *outPath)
	if sum.HasFailures() {
		os.Exit(1)
	}
}

type discovery struct {
	Issuer                string `json:"issuer"`
	AuthorizationEndpoint string `json:"authorization_endpoint"`
	TokenEndpoint         string `json:"token_endpoint"`
	UserinfoEndpoint      string `json:"userinfo_endpoint"`
	JWKSURI               string `json:"jwks_uri"`
}

func fetchDiscovery(url string, tlsVerify bool) (*discovery, error) {
	body, status, err := httpGet(url, tlsVerify)
	if err != nil {
		return nil, err
	}
	// Without this a 404/502 HTML page surfaces as an opaque JSON parse error.
	if status < 200 || status > 299 {
		return nil, fmt.Errorf("discovery %s: HTTP %d", url, status)
	}
	var d discovery
	if err := json.Unmarshal(body, &d); err != nil {
		return nil, err
	}
	if d.TokenEndpoint == "" || d.AuthorizationEndpoint == "" {
		return nil, fmt.Errorf("discovery missing endpoints")
	}
	return &d, nil
}

func keycloakToken(tlsVerify bool) (string, error) {
	tokenURL := os.Getenv("KEYCLOAK_TOKEN_URL")
	clientID := os.Getenv("KEYCLOAK_CLIENT_ID")
	secret := os.Getenv("KEYCLOAK_CLIENT_SECRET")
	if tokenURL == "" || clientID == "" || secret == "" {
		return "", fmt.Errorf("KEYCLOAK_TOKEN_URL/CLIENT_ID/CLIENT_SECRET required")
	}
	form := url.Values{"grant_type": {"client_credentials"}, "client_id": {clientID}, "client_secret": {secret}}
	client := httpClient(tlsVerify)
	resp, err := client.Post(tokenURL, "application/x-www-form-urlencoded", strings.NewReader(form.Encode()))
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	// Status first, like fetchDiscovery: a proxy error page would otherwise fail
	// as an opaque JSON-decode error instead of naming the HTTP status.
	if resp.StatusCode < 200 || resp.StatusCode > 299 {
		body, _ := io.ReadAll(io.LimitReader(resp.Body, 500))
		return "", fmt.Errorf("keycloak token %s: HTTP %d: %s", tokenURL, resp.StatusCode, strings.TrimSpace(string(body)))
	}
	var t struct {
		AccessToken string `json:"access_token"`
	}
	if err := json.NewDecoder(resp.Body).Decode(&t); err != nil {
		return "", err
	}
	if t.AccessToken == "" {
		return "", fmt.Errorf("no access_token (HTTP %d)", resp.StatusCode)
	}
	return t.AccessToken, nil
}

func httpClient(tlsVerify bool) *http.Client {
	return &http.Client{Timeout: 30 * time.Second, Transport: &http.Transport{TLSClientConfig: &tls.Config{
		MinVersion:         tls.VersionTLS12,
		InsecureSkipVerify: !tlsVerify,
	}}}
}

// httpGet returns the body along with the status code so callers can tell an
// error page apart from a malformed payload.
func httpGet(url string, tlsVerify bool) ([]byte, int, error) {
	resp, err := httpClient(tlsVerify).Get(url)
	if err != nil {
		return nil, 0, err
	}
	defer resp.Body.Close()
	body, err := io.ReadAll(resp.Body)
	return body, resp.StatusCode, err
}

func loadJSON(path string, v any) error {
	data, err := os.ReadFile(path)
	if err != nil {
		return err
	}
	return json.Unmarshal(data, v)
}

func writeEnvelope(path string, rows []result.ModuleResult) error {
	data, err := json.MarshalIndent(rows, "", "  ")
	if err != nil {
		return err
	}
	if dir := filepath.Dir(path); dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return err
		}
	}
	return os.WriteFile(path, data, 0o644)
}

func env(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}
