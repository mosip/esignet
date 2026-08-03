// Command e2e runs the end-to-end surface: register a throwaway OIDC client, then
// for each scenario drive authorize -> flow/execute login -> token
// (private_key_jwt) -> userinfo, and assert the returned claims. It writes a
// result.ModuleResult envelope (Surface=e2e) to out/e2e-envelope.json for the
// consolidation runner. Here the harness IS the relying party (unlike the
// conformance surface, where the suite is).
//
// Configuration comes from the same layered source as every other surface —
// config.<plugin>.json, config.local.json, then environment overrides (see
// internal/config). Which scenarios run is narrowed by e2e.auth_factors /
// e2e.include / e2e.exclude; by default every scenario in the spec runs, each
// driving the ACR it declares.
//
//	e2e -config config.mosip.json
package main

import (
	"context"
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
	"github.com/mosip/esignet/api-test/internal/wsotp"
)

func main() {
	configPath := flag.String("config", "config.json", "path to the harness config (env vars override its values)")
	specPath := flag.String("spec", "", "override e2e.spec from the config")
	outPath := flag.String("out", "out/e2e-envelope.json", "envelope output path")
	flag.Parse()

	logger := log.New(os.Stderr, "", log.LstdFlags)

	// The e2e surface is the only one selected here, so conformance-only
	// requirements (suite base URL, private plan config) are not enforced.
	cfgPath, mustExist := config.ResolvePath(*configPath, config.FlagExplicit("config"))
	cfg, err := config.Load(cfgPath, mustExist)
	if err != nil {
		logger.Printf("config error: %v", err)
		os.Exit(2)
	}
	// This binary IS the e2e surface, so enforce its requirements even when the
	// config's run.surfaces omits it (a direct single-surface run).
	if err := cfg.ValidateSurface(config.SurfaceE2E); err != nil {
		logger.Printf("config error: %v", err)
		os.Exit(2)
	}
	es := cfg.Esignet

	base := strings.TrimRight(es.BaseURL, "/")
	if base == "" {
		logger.Fatal("esignet.base_url (ESIGNET_BASE_URL) is required")
	}
	tlsVerify := cfg.Esignet.TLSVerify

	// Discovery -> endpoints.
	disco, err := fetchDiscovery(base+"/.well-known/openid-configuration", tlsVerify)
	if err != nil {
		logger.Fatalf("discovery: %v", err)
	}

	// Admin token for client registration.
	adminTok, err := keycloakToken(cfg.Keycloak, tlsVerify)
	if err != nil {
		logger.Fatalf("keycloak admin token: %v", err)
	}

	// Each scenario declares its own auth_factor (ACR) and may override the base
	// identity answers via its credentials map — there is no single global auth
	// factor here, since one run drives every ACR the spec covers.
	sp := cfg.E2E.Spec
	if *specPath != "" {
		sp = *specPath
	}
	var spec e2e.Spec
	if err := loadJSON(sp, &spec); err != nil {
		logger.Fatalf("load spec %s: %v", sp, err)
	}
	total := len(spec.Scenarios)
	spec, err = spec.Select(e2e.Filter{
		AuthFactors: cfg.E2E.AuthFactors,
		Include:     cfg.E2E.Include,
		Exclude:     cfg.E2E.Exclude,
	})
	if err != nil {
		logger.Fatalf("e2e scenario selection: %v", err)
	}
	if len(spec.Scenarios) != total {
		logger.Printf("scenario filter: %d of %d scenario(s) selected from %s", len(spec.Scenarios), total, sp)
	}

	// Dynamic OTP: connect the mock-SMTP listener once, shared across scenarios.
	var otpProvider esignet.OTPProvider
	if es.OTP.Source == "dynamic" {
		if es.OTP.WSURL == "" {
			logger.Fatal("OTP_SOURCE=dynamic requires OTP_WS_URL")
		}
		lst := wsotp.NewListener(es.OTP.WSURL, tlsVerify)
		if err := lst.Start(context.Background()); err != nil {
			logger.Fatalf("dynamic OTP: %v", err)
		}
		defer lst.Close()
		otpProvider = wsotp.NewOTPProvider(lst, es.OTP.RecipientEmail, 90*time.Second, time.Second)
		logger.Printf("dynamic OTP: listening on mock-SMTP for recipient %q", es.OTP.RecipientEmail)
	}

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

func keycloakToken(kc config.Keycloak, tlsVerify bool) (string, error) {
	tokenURL := kc.TokenURL
	if tokenURL == "" || kc.ClientID == "" || kc.ClientSecret == "" {
		return "", fmt.Errorf("keycloak.token_url/client_id/client_secret (KEYCLOAK_*) required")
	}
	form := url.Values{"grant_type": {"client_credentials"}, "client_id": {kc.ClientID}, "client_secret": {kc.ClientSecret}}
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
