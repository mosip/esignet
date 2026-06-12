// Command esignet runs the ThunderID embedder with MOSIP authentication support.
package main

import (
	"net/http"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"

	"github.com/mosip/esignet/internal/catalog"
	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	embedhost "github.com/mosip/esignet/internal/host"
	applog "github.com/mosip/esignet/internal/log"
	"github.com/mosip/esignet/internal/store"
)

func main() {
	logger := applog.GetLogger()

	engineCfg := config.LoadEngine()
	issuer := engineCfg.Issuer

	cat, err := catalog.Load(engineCfg.DataDir)
	if err != nil {
		logger.Fatal("load catalog", applog.Error(err))
	}

	authnCfg := config.LoadAuthn()
	logger.Info("authn provider selected", applog.String("provider", authnCfg.Provider))
	authnProvider, err := embedhost.NewAuthnProviderFromConfig(authnCfg, cat)
	if err != nil {
		logger.Fatal("authn provider", applog.Error(err))
	}

	dbCfg := config.LoadDB()
	pgConn, err := dbCfg.Open()
	if err != nil {
		logger.Fatal("connect postgres", applog.Error(err))
	}
	defer func() {
		if err := pgConn.Close(); err != nil {
			logger.Warn("close postgres", applog.Error(err))
		}
	}()

	redisCfg := config.LoadRedis()
	redisClient, err := redisCfg.Open()
	if err != nil {
		logger.Fatal("connect redis", applog.Error(err))
	}
	defer func() {
		if err := redisClient.Close(); err != nil {
			logger.Warn("close redis", applog.Error(err))
		}
	}()
	logger.Info("redis connected",
		applog.String("key_prefix", redisCfg.KeyPrefix),
	)

	clientMgmtCfg := config.LoadClientMgmt(engineCfg.Issuer)
	logger.Info("client mgmt scope enforcement",
		applog.String("required_scope", clientMgmtCfg.RequiredScope),
		applog.String("jwks_endpoint", clientMgmtCfg.JWKSEndpoint),
	)

	jwksCache := clientmgmt.NewJWKSCache(clientMgmtCfg.JWKSEndpoint, clientMgmtCfg.JWKSCacheTTL)
	scopeMW := clientmgmt.ScopeMiddleware(jwksCache, clientMgmtCfg.Issuer, clientMgmtCfg.RequiredScope)

	clientSvc := clientmgmt.NewService(pgConn)
	clientHandler := clientmgmt.NewHandler(clientSvc, logger)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})
	clientHandler.RegisterRoutes(mux, scopeMW)

	thunderCfg := engineCfg.ThunderEngineConfig()
	thunderCfg.Runtime = store.NewRedisStore(redisClient, redisCfg.KeyPrefix)
	thunderCfg.Flow.RegisterCustom = func(reg thunderidengine.ExecutorRegistry, factory thunderidengine.FlowFactory) error {
		return embedhost.RegisterCustomExecutors(reg, factory, embedhost.CustomExecutorDeps{
			Authn:    authnProvider,
			AuthnCfg: authnCfg,
		})
	}
	thunderCfg.Actors = embedhost.NewActorProvider(cat)
	thunderCfg.Authn = authnProvider
	thunderCfg.Authorization = embedhost.NewAuthorizationProvider()
	thunderCfg.Consent = embedhost.NewConsentEnforcer()

	_, err = thunderidengine.Initialize(mux, thunderCfg)
	if err != nil {
		logger.Fatal("initialize engine", applog.Error(err))
	}

	addr := ":" + engineCfg.Port
	logger.Info("server listening", applog.String("addr", addr), applog.String("issuer", issuer))
	if err := http.ListenAndServe(addr, mux); err != nil {
		logger.Fatal("server", applog.Error(err))
	}
}
