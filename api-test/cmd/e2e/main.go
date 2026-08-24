// Command e2e registers a throwaway OIDC client, drives authorize/login/token/userinfo, and asserts claims.
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
	configPath := flag.String("config", "data/config/config.json", "path to the harness config (env vars override its values)")
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
		logger.Fatal("esignet.base_url (MOSIP_ESIGNET_BASE_URL) is required")
	}
	tlsVerify := cfg.Esignet.TLSVerify

	// One root context for the whole run, so every outbound call is cancellable
	// rather than bounded only by the per-client timeout.
	ctx := context.Background()

	// Discovery -> endpoints.
	disco, err := fetchDiscovery(ctx, base+"/.well-known/openid-configuration", tlsVerify)
	if err != nil {
		logger.Fatalf("discovery: %v", err)
	}

	// Admin token for client registration.
	adminTok, err := keycloakToken(ctx, cfg.Keycloak, tlsVerify)
	if err != nil {
		logger.Fatalf("keycloak admin token: %v", err)
	}

	// Each scenario declares its own auth_factor and may override the base identity answers.
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

	// run.timeout_seconds/poll_interval_seconds are shared with the conformance surface's HTTP client.
	timeout := time.Duration(cfg.Run.TimeoutSeconds) * time.Second
	poll := time.Duration(cfg.Run.PollIntervalSeconds) * time.Second
	if poll <= 0 {
		poll = time.Second
	}

	// Dynamic OTP: connect the mock-SMTP listener once, shared across scenarios.
	var otpProvider esignet.OTPProvider
	if es.OTP.Source == "dynamic" {
		if es.OTP.WSURL == "" {
			logger.Fatal("OTP_SOURCE=dynamic requires OTP_WS_URL")
		}
		lst := wsotp.NewListener(es.OTP.WSURL, tlsVerify)
		if err := lst.Start(ctx); err != nil {
			logger.Fatalf("dynamic OTP: %v", err)
		}
		defer lst.Close()
		otpProvider = wsotp.NewOTPProvider(lst, es.OTP.RecipientEmail, timeout, poll)
		logger.Printf("dynamic OTP: listening on mock-SMTP for recipient %q", es.OTP.RecipientEmail)
	}

	runner := &e2e.Runner{
		Base:               base,
		Issuer:             disco.Issuer,
		AuthEndpoint:       disco.AuthorizationEndpoint,
		TokenEndpoint:      disco.TokenEndpoint,
		UserinfoEndpoint:   disco.UserinfoEndpoint,
		JWKSURI:            disco.JWKSURI,
		PAREndpoint:        disco.PAREndpoint,
		IntrospectEndpoint: disco.IntrospectionEndpoint,
		DPoPAlgs:           disco.DPoPAlgs,
		AdminToken:         adminTok,
		Plugin:             es.Provider,
		Answers:            esignet.BuildAnswers(es),
		IDType:             es.Identity.IDType,
		TLSVerify:          tlsVerify,
		Timeout:            timeout,
		Logf:               logger.Printf,
		OTP:                otpProvider,
		PMSBaseURL:         es.PMS.BaseURL,
		AuthPartnerID:      es.PMS.AuthPartnerID,
		PolicyID:           es.PMS.PolicyID,
	}

	rows := runner.Run(ctx, spec)
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

	// PAR and DPoP support, for the scenarios that register clients requiring
	// them. Both are optional: a deployment advertising neither still runs
	// every scenario that does not ask for them.
	PAREndpoint string   `json:"pushed_authorization_request_endpoint"`
	DPoPAlgs    []string `json:"dpop_signing_alg_values_supported"`

	// IntrospectionEndpoint is RFC 7662 token introspection, likewise optional:
	// only the introspection scenarios need it.
	IntrospectionEndpoint string `json:"introspection_endpoint"`
}

func fetchDiscovery(ctx context.Context, url string, tlsVerify bool) (*discovery, error) {
	body, status, err := httpGet(ctx, url, tlsVerify)
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
	// An RP must not follow a discovery document to hosts it did not configure.
	if d.Issuer == "" {
		return nil, fmt.Errorf("discovery %s: no issuer", url)
	}
	if err := sameOrigin(url, d.Issuer, d.AuthorizationEndpoint, d.TokenEndpoint, d.UserinfoEndpoint, d.JWKSURI, d.PAREndpoint, d.IntrospectionEndpoint); err != nil {
		return nil, fmt.Errorf("discovery %s: %w", url, err)
	}
	return &d, nil
}

// sameOrigin reports whether every non-empty URL in others shares ref's scheme and host.
func sameOrigin(ref string, others ...string) error {
	base, err := url.Parse(ref)
	if err != nil {
		return fmt.Errorf("parse %q: %w", ref, err)
	}
	for _, o := range others {
		if o == "" {
			continue
		}
		u, err := url.Parse(o)
		if err != nil {
			return fmt.Errorf("parse %q: %w", o, err)
		}
		if !strings.EqualFold(u.Host, base.Host) {
			return fmt.Errorf("%q is on %q, not the discovery host %q", o, u.Host, base.Host)
		}
		if !strings.EqualFold(u.Scheme, base.Scheme) {
			return fmt.Errorf("%q uses scheme %q, not the discovery scheme %q — a downgrade would send the client assertion and access token in cleartext", o, u.Scheme, base.Scheme)
		}
	}
	return nil
}

func keycloakToken(ctx context.Context, kc config.Keycloak, tlsVerify bool) (string, error) {
	tokenURL := kc.TokenURL
	if tokenURL == "" || kc.ClientID == "" || kc.ClientSecret == "" {
		return "", fmt.Errorf("keycloak.token_url/client_id/client_secret (KEYCLOAK_*) required")
	}
	form := url.Values{"grant_type": {"client_credentials"}, "client_id": {kc.ClientID}, "client_secret": {kc.ClientSecret}}
	req, err := http.NewRequestWithContext(ctx, http.MethodPost, tokenURL, strings.NewReader(form.Encode()))
	if err != nil {
		return "", err
	}
	req.Header.Set("Content-Type", "application/x-www-form-urlencoded")
	resp, err := httpClient(tlsVerify).Do(req)
	if err != nil {
		return "", err
	}
	defer func() { _ = resp.Body.Close() }()
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
func httpGet(ctx context.Context, url string, tlsVerify bool) ([]byte, int, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return nil, 0, err
	}
	resp, err := httpClient(tlsVerify).Do(req)
	if err != nil {
		return nil, 0, err
	}
	defer func() { _ = resp.Body.Close() }()
	// A discovery document is a few KB. Cap the read so a misbehaving endpoint
	// cannot exhaust memory before the payload is even parsed.
	body, err := io.ReadAll(io.LimitReader(resp.Body, 1<<20))
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
	// Owner-only: this intermediate envelope holds the raw call trace (token and
	// userinfo bodies) before internal/report's redaction pass runs over it.
	if dir := filepath.Dir(path); dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0o750); err != nil {
			return err
		}
	}
	return os.WriteFile(path, data, 0o600)
}
