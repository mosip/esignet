// Command esignet runs the ThunderID embedder with MOSIP authentication support.
package main

import (
	"fmt"
	"net/http"
	"os"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"

	"github.com/mosip/esignet/internal/catalog"
	"github.com/mosip/esignet/internal/config"
	embedhost "github.com/mosip/esignet/internal/host"
	applog "github.com/mosip/esignet/internal/log"
)

func main() {
	logger := applog.GetLogger()

	port := envOrDefault("PORT", "8080")
	issuer := envOrDefault("ISSUER_URL", fmt.Sprintf("http://127.0.0.1:%s", port))
	dataDir := envOrDefault("DATA_DIR", "./data")
	signingKey := envOrDefault("SIGNING_KEY_PATH", "./keys/signing.key")

	cat, err := catalog.Load(dataDir)
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

	_, err = thunderidengine.Initialize(mux, thunderidengine.EngineConfig{
		Issuer:  issuer,
		DataDir: dataDir,
		Runtime: runtime.NewMemoryRuntimeStore(),
		Crypto: thunderidengine.CryptoConfig{
			SigningKeyPath: signingKey,
		},
		FlowStore: thunderidengine.FlowProviderConfig{
			StoreMode: thunderidengine.StoreModeDeclarative,
		},
		Flow: thunderidengine.FlowConfig{
			Executors: []string{
				"BasicAuthExecutor",
				"AuthorizationExecutor",
				"AuthAssertExecutor",
				"ConsentExecutor",
			},
			RegisterCustom: func(reg thunderidengine.ExecutorRegistry, factory thunderidengine.FlowFactory) error {
				return embedhost.RegisterCustomExecutors(reg, factory, embedhost.CustomExecutorDeps{
					Authn:    authnProvider,
					AuthnCfg: authnCfg,
				})
			},
		},
		Actors:        embedhost.NewActorProvider(cat),
		Authn:         authnProvider,
		Authorization: embedhost.NewAuthorizationProvider(),
		Consent:       embedhost.NewConsentEnforcer(),
	})
	if err != nil {
		logger.Fatal("initialize engine", applog.Error(err))
	}

	addr := ":" + port
	logger.Info("server listening", applog.String("addr", addr), applog.String("issuer", issuer))
	if err := http.ListenAndServe(addr, mux); err != nil {
		logger.Fatal("server", applog.Error(err))
	}
}

func envOrDefault(key, fallback string) string {
	if value := os.Getenv(key); value != "" {
		return value
	}
	return fallback
}
