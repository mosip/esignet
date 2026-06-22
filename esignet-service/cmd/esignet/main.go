// Command esignet runs the ThunderID embedder with MOSIP authentication support.
package main

import (
	"context"
	"net/http"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine"
	applog "github.com/mosip/esignet/internal/log"
)

func main() {
	logger := applog.GetLogger()
	appCfg := config.LoadAppConfig()

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

	hostCfg := engine.LoadConfig()
	thunderCfg, executors, err := engine.BuildThunderConfig(appCfg)
	if err != nil {
		logger.Fatal("build thunder config", applog.Error(err))
	}
	certFile, keyFile, err := engine.PKIPaths(appCfg.DataDir)
	if err != nil {
		logger.Fatal("signing key paths", applog.Error(err))
	}
	actorProvider := engine.NewActorProvider(clientSvc, hostCfg)
	roleProvider := engine.NewRoleProvider()
	logger.Info("authn provider selected", applog.String("provider", hostCfg.Provider))
	authnProvider, err := engine.NewAuthnProviderFromConfig(hostCfg.Provider, clientSvc)
	if err != nil {
		logger.Fatal("authn provider", applog.Error(err))
	}

	eng, err := thunderidengine.New(
		thunderidengine.WithRedis(redisClient, appCfg.Redis.KeyPrefix),
		thunderidengine.WithConfig(appCfg.DataDir, thunderCfg),
		thunderidengine.WithPKIKey("default-key", certFile, keyFile),
		thunderidengine.WithHostActorProvider(actorProvider),
		thunderidengine.WithHostAuthnProvider(authnProvider),
		thunderidengine.WithHostRoleProvider(roleProvider),
		thunderidengine.WithExecutorDependencies(thunderidengine.ExecutorDependencies{
			ConsentEnforcer: engine.NewConsentEnforcer(),
		}),
		thunderidengine.WithEnabledExecutors(executors...),
		thunderidengine.WithCustomExecutors(engine.CustomExecutors(authnProvider, hostCfg.Provider)),
	)
	if err != nil {
		logger.Fatal("initialize engine", applog.Error(err))
	}
	defer func() { _ = eng.Shutdown(context.Background()) }()

	if err := eng.RegisterRoutes(mux); err != nil {
		logger.Fatal("register engine routes", applog.Error(err))
	}

	addr := ":" + appCfg.Port
	logger.Info("server listening", applog.String("addr", addr), applog.String("issuer", appCfg.Issuer))
	if err := http.ListenAndServe(addr, mux); err != nil {
		logger.Fatal("server", applog.Error(err))
	}
}
