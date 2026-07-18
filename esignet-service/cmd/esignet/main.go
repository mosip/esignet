/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Command esignet runs the ThunderID embedder with MOSIP authentication support.
package main

import (
	"fmt"
	"net/http"
	"time"

	"github.com/redis/go-redis/v9"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	engineconfig "github.com/thunder-id/thunderid/pkg/thunderidengine/config"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/consentmgmt"
	"github.com/mosip/esignet/internal/engine"
	"github.com/mosip/esignet/internal/engine/runtimestores/inmemory"
	"github.com/mosip/esignet/internal/engine/runtimestores/redisstore"
	"github.com/mosip/esignet/internal/engine/shared"
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
	runtimeStore := getRuntimeStoreProvider(appCfg, redisClient, logger)

	clientSvc := clientmgmt.NewService(pgConn, runtimeStore, appCfg.ClientCacheTTLSecs)
	clientHandler := clientmgmt.NewHandler(clientSvc, logger)
	clientHandler.RegisterRoutes(mux, getSecurityMiddleware(appCfg, logger))

	authnProvider, observabilityProvider, err := engine.NewIDSystemProviders(appCfg, clientSvc)
	if err != nil {
		logger.Fatal("plugin providers", applog.Error(err))
	}
	logger.Info("authn provider selected", applog.String("provider", appCfg.Provider))

	_ = thunderidengine.New(mux,
		thunderidengine.WithServerHome(appCfg.DataDir),
		thunderidengine.WithKeyConfigs([]engineconfig.KeyConfig{appCfg.KeyConfig}),
		thunderidengine.WithEncryptionConfig(appCfg.EncryptionConfig),
		thunderidengine.WithRuntimeDBType("redis"),
		thunderidengine.WithServerConfig(appCfg.Server),
		thunderidengine.WithCacheConfig(appCfg.Cache),
		thunderidengine.WithOAuthConfig(appCfg.OAuth),
		thunderidengine.WithJWTConfig(appCfg.JWT),
		thunderidengine.WithGateClientConfig(appCfg.GateClient),
		thunderidengine.WithFlowConfig(appCfg.Flow),
		thunderidengine.WithObservabilityConfig(appCfg.Observability),
		thunderidengine.WithActorProvider(engine.NewActorProvider(clientSvc, appCfg)),
		thunderidengine.WithAuthnProvider(authnProvider),
		thunderidengine.WithAuthorizationProvider(engine.NewAuthorizationProvider(appCfg)),
		thunderidengine.WithConsentProvider(engine.NewConsentProvider(consentmgmt.NewService(pgConn), appCfg, runtimeStore)),
		thunderidengine.WithDesignResolveProvider(engine.NewDesignProvider(appCfg)),
		thunderidengine.WithFlowProvider(engine.NewFlowProvider(appCfg)),
		thunderidengine.WithI18nProvider(engine.NewI18nProvider(appCfg)),
		thunderidengine.WithOUProvider(engine.NewOUProvider(appCfg)),
		thunderidengine.WithResourceProvider(engine.NewResourceProvider(appCfg)),
		thunderidengine.WithObservabilityProvider(observabilityProvider),
		thunderidengine.WithIDPProvider(engine.NewIDPProvider(appCfg)),
		thunderidengine.WithCustomExecutors(getCustomExecutors(authnProvider)),
		thunderidengine.WithRuntimeStoreProvider(runtimeStore),
	)

	addr := fmt.Sprintf(":%d", appCfg.Port)
	logger.Info("server listening", applog.String("addr", addr), applog.String("issuer", appCfg.Issuer))
	if err := http.ListenAndServe(addr, mux); err != nil {
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

// CustomExecutors returns embedder-supplied flow executors keyed by executor name.
func getCustomExecutors(authn providers.AuthnProviderManager) map[string]providers.Executor {
	executors := map[string]providers.Executor{
		engine.ExecutorNameClearInputs: engine.NewClearInputsExecutor(),
	}
	if otpAuthn, ok := authn.(shared.ConsolidatedAuthnProvider); ok {
		executors[engine.ExecutorNameMosipOTP] = engine.NewMosipOtpExecutor(otpAuthn)
	}
	return executors
}

func getRuntimeStoreProvider(appCfg *config.AppConfig, redisClient *redis.Client, logger *applog.Logger) providers.RuntimeStoreProvider {
	if appCfg.RuntimeDBType == "redis" {
		store, err := redisstore.Initialize(appCfg.Identifier, appCfg.Redis.KeyPrefix, redisClient)
		if err != nil {
			logger.Fatal("Failed to initialize redis store", applog.Error(err))
		}
		return store
	}

	return inmemory.Initialize(appCfg.Identifier)
}
