// Command esignet runs the ThunderID embedder with MOSIP authentication support.
package main

import (
	"context"
	"net/http"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"

	"github.com/mosip/esignet/internal/catalog"
	"github.com/mosip/esignet/internal/client"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/database"
	embedhost "github.com/mosip/esignet/internal/host"
	applog "github.com/mosip/esignet/internal/log"
)

func main() {
	logger := applog.GetLogger()
	ctx := context.Background()

	clientCfg, err := client.LoadConfig()
	if err != nil {
		logger.Fatal("client config", applog.Error(err))
	}
	dbPool, err := database.NewPool(ctx, clientCfg.Postgres, logger)
	if err != nil {
		logger.Fatal("postgres", applog.Error(err))
	}
	defer dbPool.Close()

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

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})

	thunderCfg := engineCfg.ThunderEngineConfig()
	thunderCfg.Runtime = runtime.NewMemoryRuntimeStore()
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

	clientMod, err := client.NewModule(ctx, clientCfg, dbPool, logger)
	if err != nil {
		logger.Fatal("client module", applog.Error(err))
	}
	clientMod.Initialize(mux)

	addr := ":" + engineCfg.Port
	logger.Info("server listening", applog.String("addr", addr), applog.String("issuer", issuer))
	if err := http.ListenAndServe(addr, mux); err != nil {
		logger.Fatal("server", applog.Error(err))
	}
}
