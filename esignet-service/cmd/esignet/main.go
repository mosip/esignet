// Command esignet runs the ThunderID embedder with MOSIP authentication support.
package main

import (
	"fmt"
	"net/http"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine"
	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

func main() {
	logger := applog.GetLogger()
	appCfg, err := config.LoadAppConfig()
	if err != nil {
		logger.Fatal("failed to load app config", applog.Error(err))
	}
	if err := config.ApplyEnvOverrides(appCfg); err != nil {
		logger.Fatal("failed to apply env overrides", applog.Error(err))
	}

	pgConn, err := appCfg.DB.Open()
	if err != nil {
		logger.Fatal("connect postgres", applog.Error(err))
	}
	defer func() {
		if err := pgConn.Close(); err != nil {
			logger.Warn("close postgres", applog.Error(err))
		}
	}()

	redisClient, err := appCfg.Redis.Open()
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

	clientMgmtCfg := clientmgmt.LoadConfig()
	var scopeMW func(http.Handler) http.Handler
	if clientMgmtCfg.ScopeEnforcementEnabled() {
		logger.Info("client mgmt scope enforcement enabled",
			applog.String("required_scope", clientMgmtCfg.RequiredScope),
			applog.String("jwks_endpoint", clientMgmtCfg.JWKSEndpoint),
			applog.String("issuer", clientMgmtCfg.Issuer),
		)
		jwksCache := clientmgmt.NewJWKSCache(clientMgmtCfg.JWKSEndpoint, clientMgmtCfg.JWKSCacheTTL)
		scopeMW = clientmgmt.ScopeMiddleware(jwksCache, clientMgmtCfg.Issuer, clientMgmtCfg.RequiredScope)
	} else {
		logger.Warn("client mgmt scope enforcement disabled; set CLIENT_MGMT_ISSUER_URL and CLIENT_MGMT_JWKS_ENDPOINT to enable")
	}
	clientSvc := clientmgmt.NewService(pgConn)
	clientHandler := clientmgmt.NewHandler(clientSvc, logger)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	clientHandler.RegisterRoutes(mux, scopeMW)

	authnProvider, err := engine.NewAuthnProvider(appCfg.Provider, appCfg, clientSvc)
	if err != nil {
		logger.Fatal("authn provider", applog.Error(err))
	}
	logger.Info("authn provider selected", applog.String("provider", appCfg.Provider))

	_ = thunderidengine.New(mux,
		thunderidengine.WithServerHome(appCfg.DataDir),
		thunderidengine.WithServerConfig(appCfg.Server),
		thunderidengine.WithCacheConfig(appCfg.Cache),
		thunderidengine.WithOAuthConfig(appCfg.OAuth),
		thunderidengine.WithJWTConfig(appCfg.JWT),
		thunderidengine.WithFlowConfig(appCfg.Flow),
		thunderidengine.WithObservabilityConfig(appCfg.Observability),
		thunderidengine.WithActorProvider(engine.NewActorProvider(clientSvc, appCfg)),
		thunderidengine.WithAuthnProvider(authnProvider),
		thunderidengine.WithAuthorizationProvider(engine.NewAuthorizationProvider(appCfg)),
		thunderidengine.WithConsentProvider(engine.NewConsentEnforcer()),
		thunderidengine.WithDesignResolveProvider(engine.NewDesignProvider(appCfg)),
		thunderidengine.WithFlowProvider(engine.NewFlowProvider(appCfg)),
		thunderidengine.WithI18nProvider(engine.NewI18nProvider(appCfg)),
		thunderidengine.WithOUProvider(engine.NewOUProvider(appCfg)),
		thunderidengine.WithResourceProvider(engine.NewResourceProvider(appCfg)),
		thunderidengine.WithObservabilityProvider(engine.NewObservabilityProvider(appCfg.Observability)),
		thunderidengine.WithCustomExecutors(getCustomExecutors(authnProvider)),
	)

	addr := fmt.Sprintf(":%d", appCfg.Port)
	logger.Info("server listening", applog.String("addr", addr), applog.String("issuer", appCfg.Issuer))
	if err := http.ListenAndServe(addr, mux); err != nil {
		logger.Fatal("server", applog.Error(err))
	}
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
