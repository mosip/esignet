/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Command esignet runs the ThunderID embedder with MOSIP authentication support.
package main

import (
	"fmt"
	"net"
	"net/http"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	engineconfig "github.com/thunder-id/thunderid/pkg/thunderidengine/config"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/consentmgmt"
	"github.com/mosip/esignet/internal/engine"
	"github.com/mosip/esignet/internal/engine/executors"
	"github.com/mosip/esignet/internal/engine/runtimestores"
	"github.com/mosip/esignet/internal/httpmiddleware"
	applog "github.com/mosip/esignet/internal/log"
	"github.com/mosip/esignet/internal/security"
)

func main() {
	logger := applog.GetLogger()

	// Load application configurations
	appCfg, err := getAppConfig()
	if err != nil {
		logger.Fatal("failed to load app config", applog.Error(err))
	}

	// Setup DB connection
	pgConn, err := appCfg.DB.Open()
	if err != nil {
		logger.Fatal("postgres connection failed", applog.Error(err))
	}
	logger.Info("postgres connected",
		applog.Int("maxOpenConns", appCfg.DB.Pool.MaxOpenConns),
		applog.Int("maxIdleConns", appCfg.DB.Pool.MaxIdleConns))
	defer func() {
		if err := pgConn.Close(); err != nil {
			logger.Warn("close postgres", applog.Error(err))
		}
	}()

	// Setup Redis client. It is shared by the runtime store provider and the consent enforcer
	// (which reads the engine's authorization requests), so both resolve the same keys. Created
	// only when Redis is the configured runtime store; nil otherwise (e.g. in-memory store).
	var redisClient *redis.Client
	if appCfg.RuntimeDBType == "redis" {
		redisClient, err = appCfg.Redis.Open()
		if err != nil {
			logger.Fatal("connect redis", applog.Error(err))
		}
		defer func() {
			if err := redisClient.Close(); err != nil {
				logger.Warn("close redis", applog.Error(err))
			}
		}()
		logger.Info("redis connected",
			applog.String("key_prefix", appCfg.Redis.KeyPrefix),
		)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})

	// The runtime store is shared between the engine and the consent enforcer (which reads the
	// engine's stored authorization requests from it), so both resolve the same keys. It's also
	// used by clientSvc below to cache GetClient lookups.
	runtimeStore := runtimestores.Initialize(appCfg, redisClient)

	clientSvc := clientmgmt.NewService(pgConn, runtimeStore, appCfg.ClientCacheTTLSecs)
	clientHandler := clientmgmt.NewHandler(clientSvc, logger)
	clientHandler.RegisterRoutes(mux, getSecurityMiddleware(appCfg, logger))

	httpClient := newHTTPClient(appCfg.OutboundHTTPClient)

	authnProvider, observabilityProvider, err := engine.NewIDSystemProviders(appCfg, clientSvc, httpClient)
	if err != nil {
		logger.Fatal("plugin providers", applog.Error(err))
	}
	logger.Info("authn provider selected", applog.String("provider", appCfg.Provider))

	logLevel, err := applog.ConfiguredLevel()
	if err != nil {
		logger.Fatal("resolve log level", applog.Error(err))
	}

	_ = thunderidengine.New(mux,
		thunderidengine.WithLogConfig(engineconfig.LogConfig{Level: logLevel, Format: "json"}),
		thunderidengine.WithServerHome(appCfg.DataDir),
		thunderidengine.WithRuntimeTransientDBType("redis"),
		thunderidengine.WithKeyConfigs([]engineconfig.KeyConfig{appCfg.KeyConfig}),
		thunderidengine.WithEncryptionConfig(appCfg.EncryptionConfig),
		thunderidengine.WithServerConfig(appCfg.Server),
		thunderidengine.WithCacheConfig(appCfg.Cache),
		thunderidengine.WithOAuthConfig(appCfg.OAuth),
		thunderidengine.WithJWTConfig(appCfg.JWT),
		thunderidengine.WithGateClientConfig(appCfg.GateClient),
		thunderidengine.WithFlowConfig(appCfg.Flow),
		thunderidengine.WithObservabilityConfig(appCfg.Observability),
		thunderidengine.WithActorProvider(engine.NewActorProvider(clientSvc, appCfg)),
		thunderidengine.WithDefaultAuthnProvider(authnProvider),
		thunderidengine.WithAuthorizationProvider(engine.NewAuthorizationProvider(appCfg)),
		thunderidengine.WithConsentProvider(engine.NewConsentProvider(consentmgmt.NewService(pgConn), appCfg)),
		thunderidengine.WithDesignResolveProvider(engine.NewDesignProvider(appCfg, runtimeStore, appCfg.DesignCacheTTLSecs)),
		thunderidengine.WithFlowProvider(engine.NewFlowProvider(appCfg, runtimeStore, appCfg.FlowCacheTTLSecs)),
		thunderidengine.WithI18nProvider(engine.NewI18nProvider(appCfg)),
		thunderidengine.WithOUProvider(engine.NewOUProvider(appCfg)),
		thunderidengine.WithResourceProvider(engine.NewResourceProvider(appCfg)),
		thunderidengine.WithObservabilityProvider(observabilityProvider),
		thunderidengine.WithIDPProvider(engine.NewIDPProvider(appCfg)),
		thunderidengine.WithCustomExecutors(executors.Initialize(authnProvider)),
		thunderidengine.WithRuntimeStoreProvider(runtimeStore),
		thunderidengine.WithTransactioner(engine.NewNoOpTransactioner()),
		thunderidengine.WithAttestationProvider(engine.NewAttestationProvider(appCfg)),
		thunderidengine.WithCaptchaValidationProvider(engine.NewCaptchaProvider(&appCfg.CaptchaConfig, httpClient)),
	)

	addr := fmt.Sprintf(":%d", appCfg.Port)
	logger.Info("server listening", applog.String("addr", addr), applog.String("issuer", appCfg.Issuer))
	handler := httpmiddleware.CorrelationID(httpmiddleware.AccessLog(mux))
	if err := http.ListenAndServe(addr, handler); err != nil {
		logger.Fatal("server", applog.Error(err))
	}
}

func getAppConfig() (*config.AppConfig, error) {
	appCfg, err := config.LoadAppConfig()
	if err != nil {
		return nil, err
	}

	if err := config.ApplyEnvOverrides(appCfg); err != nil {
		return nil, err
	}
	return appCfg, err
}

func getSecurityMiddleware(appCfg *config.AppConfig, logger *applog.Logger) func(http.Handler) http.Handler {
	var scopeMW func(http.Handler) http.Handler
	if scopeEnforcementEnabled(appCfg) {
		logger.Info("Scope enforcement enabled",
			applog.String("jwks_endpoint", appCfg.SecurityConfig.JwksURL),
			applog.String("issuer", appCfg.SecurityConfig.IssuerURL),
		)
		jwksCache := security.NewJWKSCache(appCfg.SecurityConfig.JwksURL, time.Duration(appCfg.SecurityConfig.JwksCacheTTL))
		scopeMW = security.ScopeMiddleware(jwksCache, appCfg.SecurityConfig)
	} else {
		logger.Warn("Scope enforcement disabled; set ISSUER_URL and JWKS_URL in security_config to enable")
	}

	requestTimeLeeway := time.Duration(appCfg.SecurityConfig.RequestTimeLeewaySecs) * time.Second
	logger.Info("Request time validation enabled", applog.String("leeway", requestTimeLeeway.String()))
	requestTimeMW := security.RequestTimeMiddleware(requestTimeLeeway)

	if scopeMW != nil {
		return func(next http.Handler) http.Handler {
			return scopeMW(requestTimeMW(next))
		}
	}
	return requestTimeMW
}

// ScopeEnforcementEnabled reports whether Bearer token scope enforcement should
// be applied. Both Issuer and JWKSEndpoint must be set.
func scopeEnforcementEnabled(appCfg *config.AppConfig) bool {
	return appCfg.SecurityConfig.IssuerURL != "" && appCfg.SecurityConfig.JwksURL != ""
}

// newHTTPClient returns a tuned HTTP client for outbound calls, configured
// from appCfg.OutboundHTTPClient.
func newHTTPClient(cfg config.HTTPClientConfig) *http.Client {
	return &http.Client{
		Timeout: time.Duration(cfg.TimeoutSecs) * time.Second,
		Transport: &http.Transport{
			DialContext: (&net.Dialer{
				Timeout:   time.Duration(cfg.DialTimeoutSecs) * time.Second,
				KeepAlive: time.Duration(cfg.DialKeepAliveSecs) * time.Second,
			}).DialContext,
			TLSHandshakeTimeout:   time.Duration(cfg.TLSHandshakeTimeoutSecs) * time.Second,
			ResponseHeaderTimeout: time.Duration(cfg.ResponseHeaderTimeoutSecs) * time.Second,
			IdleConnTimeout:       time.Duration(cfg.IdleConnTimeoutSecs) * time.Second,
			MaxConnsPerHost:       cfg.MaxConnsPerHost,
		},
	}
}
