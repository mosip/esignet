// Command esignet runs the ThunderID embedder with MOSIP authentication support.
package main

import (
	"context"
	"errors"
	"io"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"

	"github.com/mosip/esignet/internal/catalog"
	"github.com/mosip/esignet/internal/config"
	embedhost "github.com/mosip/esignet/internal/host"
	applog "github.com/mosip/esignet/internal/log"
	"github.com/mosip/esignet/internal/runtimestore"
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

	runtimeStore, runtimeCloser := newRuntimeStore(logger)

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})

	thunderCfg := engineCfg.ThunderEngineConfig()
	thunderCfg.Runtime = runtimeStore
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
	srv := &http.Server{Addr: addr, Handler: mux}

	// Trap SIGINT/SIGTERM so a normal pod shutdown drains the server and runs
	// the runtime-store close path; ListenAndServe alone never returns on a
	// signal, so the close below would otherwise be dead code.
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	serveErr := make(chan error, 1)
	go func() {
		logger.Info("server listening", applog.String("addr", addr), applog.String("issuer", issuer))
		if err := srv.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serveErr <- err
			return
		}
		serveErr <- nil
	}()

	var exitErr error
	select {
	case exitErr = <-serveErr:
	case <-ctx.Done():
		stop() // restore default signal handling so a second signal can terminate immediately
		logger.Info("shutdown signal received, draining server")
		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()
		if err := srv.Shutdown(shutdownCtx); err != nil {
			logger.Error("server shutdown", applog.Error(err))
		}
	}

	if runtimeCloser != nil {
		if closeErr := runtimeCloser.Close(); closeErr != nil {
			logger.Error("close runtime store", applog.Error(closeErr))
		}
	}
	if exitErr != nil {
		logger.Fatal("server", applog.Error(exitErr))
	}
}

// newRuntimeStore selects the engine runtime store from configuration. The
// in-memory store is the default; "redis" connects with fail-fast on startup.
// The returned closer is nil for the in-memory store.
func newRuntimeStore(logger *applog.Logger) (runtime.Store, io.Closer) {
	cfg := config.LoadRuntimeStore()
	logger.Info("runtime store selected", applog.String("backend", cfg.Backend))

	switch cfg.Backend {
	case config.RuntimeStoreRedis:
		store, err := runtimestore.NewRedisStore(runtimestore.Config{
			Address:         cfg.Redis.Address,
			Username:        cfg.Redis.Username,
			Password:        cfg.Redis.Password,
			DB:              cfg.Redis.DB,
			DeploymentID:    cfg.Redis.DeploymentID,
			MaxRetries:      cfg.Redis.MaxRetries,
			MinRetryBackoff: cfg.Redis.MinRetryBackoff,
			MaxRetryBackoff: cfg.Redis.MaxRetryBackoff,
			DialTimeout:     cfg.Redis.DialTimeout,
			ReadTimeout:     cfg.Redis.ReadTimeout,
			WriteTimeout:    cfg.Redis.WriteTimeout,
		})
		if err != nil {
			logger.Fatal("redis runtime store", applog.Error(err))
		}
		return store, store
	case config.RuntimeStoreMemory:
		return runtime.NewMemoryRuntimeStore(), nil
	default:
		// Fail fast: an unknown/misspelled backend must not silently boot the
		// ephemeral in-memory store in production.
		logger.Fatal("unknown runtime store backend",
			applog.String("backend", cfg.Backend),
			applog.String("valid", config.RuntimeStoreMemory+", "+config.RuntimeStoreRedis))
		return nil, nil // unreachable: Fatal exits the process.
	}
}
