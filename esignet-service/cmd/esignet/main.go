/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Command esignet runs the ThunderID embedder with MOSIP authentication support.
package main

import (
	"context"
	"database/sql"
	"fmt"
	"net/http"
	"net/http/pprof"
	"os"
	"os/signal"
	"syscall"
	"time"

	"github.com/jmoiron/sqlx"
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
	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
	"github.com/mosip/esignet/internal/keymanager/db"
	"github.com/mosip/esignet/internal/keymanager/keystore"
	_ "github.com/mosip/esignet/internal/keymanager/keystore/pkcs11"
	_ "github.com/mosip/esignet/internal/keymanager/keystore/pkcs12"
	"github.com/mosip/esignet/internal/keymanager/signature"
	applog "github.com/mosip/esignet/internal/log"
	"github.com/mosip/esignet/internal/metrics"
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
	pgConn, closeDB, err := appCfg.DB.Open()
	if err != nil {
		logger.Fatal("postgres connection failed", applog.Error(err))
	}
	logger.Info(context.Background(), "postgres connected",
		applog.Int("maxOpenConns", appCfg.DB.Pool.MaxOpenConns),
		applog.Int("maxIdleConns", appCfg.DB.Pool.MaxIdleConns),
		applog.Int("connMaxLifetimeSecs", appCfg.DB.Pool.ConnMaxLifetimeSecs),
		applog.Int("connMaxIdleTimeSecs", appCfg.DB.Pool.ConnMaxIdleTimeSecs))
	defer func() {
		if err := closeDB(); err != nil {
			logger.Warn(context.Background(), "close postgres", applog.Error(err))
		}
	}()
	// keymanager's persistence layer is built on sqlx (for GetContext/SelectContext);
	// wrap the same *sql.DB rather than opening a second connection pool.
	sqlxConn := sqlx.NewDb(pgConn, "pgx")

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
				logger.Warn(context.Background(), "close redis", applog.Error(err))
			}
		}()
		logger.Info(context.Background(), "redis connected",
			applog.String("key_prefix", appCfg.Redis.KeyPrefix),
			applog.String("connMaxLifetime", appCfg.Redis.ConnMaxLifetime.String()),
		)
	}

	mux := http.NewServeMux()
	mux.HandleFunc("GET /health", func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte("ok"))
	})

	commonHTTPClient := config.NewHTTPClient(appCfg.OutboundHTTPClient)

	// The runtime store is shared between the engine and the consent enforcer (which reads the
	// engine's stored authorization requests from it), so both resolve the same keys. It's also
	// used by clientSvc below to cache GetClient lookups.
	runtimeStore := runtimestores.Initialize(appCfg, redisClient)

	// Security middleware wraps all engine routes, enforcing request time validation and (if enabled) Bearer token scope validation.
	// It is applied to the client management and key management endpoints below, so those endpoints are also protected.
	securityMiddleware := getSecurityMiddleware(appCfg, commonHTTPClient, logger)

	clientSvc := clientmgmt.NewService(pgConn, runtimeStore, appCfg.ClientCacheTTLSecs, appCfg.SupportedEncAlgorithms)
	clientHandler := clientmgmt.NewHandler(clientSvc, logger)
	clientHandler.RegisterRoutes(mux, securityMiddleware)
	keyMgrSvc, sigSvc, cryptoSvc, ks, err := initializeKeyManager(sqlxConn)
	if err != nil {
		logger.Fatal("failed to initialize key manager", applog.Error(err))
	}
	defer func() {
		if err := ks.Close(); err != nil {
			logger.Warn(context.Background(), "close keystore", applog.Error(err))
		}
	}()
	if err := provisionKeyHierarchy(keyMgrSvc); err != nil {
		logger.Fatal("failed to provision key hierarchy", applog.Error(err))
	}

	keyMgrHandler := keymanager.NewHandler(keyMgrSvc, logger)
	keyMgrHandler.RegisterRoutes(mux, securityMiddleware)

	authnProvider, observabilityProvider, err := engine.NewIDSystemProviders(appCfg, clientSvc, keyMgrSvc, sigSvc)
	if err != nil {
		logger.Fatal("plugin providers", applog.Error(err))
	}
	logger.Info(context.Background(), "authn provider selected", applog.String("provider", appCfg.Provider))

	logLevel, err := applog.ConfiguredLevel()
	if err != nil {
		logger.Fatal("resolve log level", applog.Error(err))
	}

	var originConfig engineconfig.OriginConfig
	if appCfg.AllowedOriginRegex != "" {
		originConfig = engineconfig.OriginConfig{
			AllowedOrigins: []engineconfig.OriginEntry{{Regex: appCfg.AllowedOriginRegex}},
		}
	}

	_ = thunderidengine.New(mux,
		thunderidengine.WithLogConfig(engineconfig.LogConfig{Level: logLevel, Format: "json"}),
		thunderidengine.WithServerHome(appCfg.DataDir),
		thunderidengine.WithRuntimeTransientDBType(appCfg.RuntimeDBType),
		thunderidengine.WithServerConfig(appCfg.Server),
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
		thunderidengine.WithI18nProvider(engine.NewI18nProvider(appCfg, clientSvc)),
		thunderidengine.WithOUProvider(engine.NewOUProvider(appCfg)),
		thunderidengine.WithResourceProvider(engine.NewResourceProvider(appCfg)),
		thunderidengine.WithObservabilityProvider(observabilityProvider),
		thunderidengine.WithIDPProvider(engine.NewIDPProvider(appCfg)),
		thunderidengine.WithCustomExecutors(executors.Initialize(authnProvider)),
		thunderidengine.WithRuntimeStoreProvider(runtimeStore),
		thunderidengine.WithTransactioner(engine.NewNoOpTransactioner()),
		thunderidengine.WithAttestationProvider(engine.NewAttestationProvider(appCfg)),
		thunderidengine.WithCaptchaValidationProvider(engine.NewCaptchaProvider(&appCfg.CaptchaConfig, commonHTTPClient)),
		thunderidengine.WithRuntimeCryptoProvider(engine.NewRuntimeCryptoProvider(appCfg, keyMgrSvc, sigSvc, cryptoSvc, authnProvider)),
		thunderidengine.WithOriginConfig(originConfig),
	)

	addr := fmt.Sprintf(":%d", appCfg.Port)
	handler := httpmiddleware.CorrelationID(httpmiddleware.AccessLog(mux))
	srv := &http.Server{
		Addr:              addr,
		Handler:           handler,
		ReadHeaderTimeout: time.Duration(appCfg.InboundHTTPServer.ReadHeaderTimeoutSecs) * time.Second,
		ReadTimeout:       time.Duration(appCfg.InboundHTTPServer.ReadTimeoutSecs) * time.Second,
		WriteTimeout:      time.Duration(appCfg.InboundHTTPServer.WriteTimeoutSecs) * time.Second,
		IdleTimeout:       time.Duration(appCfg.InboundHTTPServer.IdleTimeoutSecs) * time.Second,
	}

	go func() {
		logger.Info(context.Background(), "server listening", applog.String("addr", addr), applog.String("issuer", appCfg.Issuer))
		if err := srv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatal("server", applog.Error(err))
		}
	}()

	// Start a private metrics listener — not routed through the public ingress; only reachable within the cluster by Prometheus.
	metricsSrv := startMetricsServer(pgConn, redisClient, appCfg, logger)

	// Start a debug pprof listener if enabled.
	if appCfg.PProfConfig.Enabled {
		go startDebugServer(appCfg, logger)
	}

	// Block until an orchestrator (Docker/Kubernetes) asks us to stop, then
	// shut the HTTP server down gracefully — letting in-flight requests
	// finish — before this function returns and the deferred pgConn/
	// redisClient/keystore Close() calls above run. A bare os.Exit path
	// (logger.Fatal, an unhandled signal) skips all of those; this is the
	// only path that reaches them.
	quit := make(chan os.Signal, 1)
	signal.Notify(quit, os.Interrupt, syscall.SIGTERM)
	<-quit
	logger.Info(context.Background(), "shutdown signal received, draining in-flight requests")

	shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
	defer cancel()
	if err := srv.Shutdown(shutdownCtx); err != nil {
		logger.Warn(context.Background(), "graceful shutdown timed out, closing forcibly", applog.Error(err))
		_ = srv.Close()
	}
	if err := metricsSrv.Shutdown(shutdownCtx); err != nil {
		logger.Warn(context.Background(), "metrics server graceful shutdown timed out, closing forcibly", applog.Error(err))
		_ = metricsSrv.Close()
	}
}

// startMetricsServer starts a private metrics listener — not routed through
// the public ingress; only reachable within the cluster by Prometheus.
// Keeping it on a separate port means no authentication middleware is needed
// and no scrape traffic reaches the main application mux.
func startMetricsServer(pgConn *sql.DB, redisClient *redis.Client, appCfg *config.AppConfig,
	logger *applog.Logger) *http.Server {
	metrics.RegisterDBStats(pgConn)
	if redisClient != nil {
		metrics.RegisterRedisStats(redisClient)
	}

	metricsMux := http.NewServeMux()
	metricsMux.Handle("GET /metrics", metrics.Handler())
	metricsAddr := fmt.Sprintf(":%d", appCfg.MetricsPort)
	metricsSrv := &http.Server{
		Addr:              metricsAddr,
		Handler:           metricsMux,
		ReadHeaderTimeout: 5 * time.Second,
		ReadTimeout:       10 * time.Second,
		WriteTimeout:      30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}
	go func() {
		logger.Info(context.Background(), "metrics listener", applog.String("addr", metricsAddr))
		if err := metricsSrv.ListenAndServe(); err != nil && err != http.ErrServerClosed {
			logger.Fatal("metrics server", applog.Error(err))
		}
	}()
	return metricsSrv
}

func startDebugServer(appCfg *config.AppConfig, logger *applog.Logger) {
	addr := fmt.Sprintf("127.0.0.1:%d", appCfg.PProfConfig.Port)
	logger.Info(context.Background(), "starting debug pprof server", applog.String("addr", addr))
	if err := http.ListenAndServe(addr, newDebugMux()); err != nil {
		logger.Warn(context.Background(), "debug pprof server stopped", applog.Error(err))
	}
}

// newDebugMux builds the debug ServeMux with all pprof routes. Separated
// from startDebugServer so it can be exercised in tests without binding a port.
func newDebugMux() *http.ServeMux {
	dbg := http.NewServeMux()
	dbg.HandleFunc("/debug/pprof/", pprof.Index)
	dbg.HandleFunc("/debug/pprof/cmdline", pprof.Cmdline)
	dbg.HandleFunc("/debug/pprof/profile", pprof.Profile)
	dbg.HandleFunc("/debug/pprof/symbol", pprof.Symbol)
	dbg.HandleFunc("/debug/pprof/trace", pprof.Trace)
	dbg.Handle("/debug/pprof/goroutine", pprof.Handler("goroutine"))
	dbg.Handle("/debug/pprof/heap", pprof.Handler("heap"))
	return dbg
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

func getSecurityMiddleware(appCfg *config.AppConfig, commonHTTPClient *http.Client,
	logger *applog.Logger) func(http.Handler) http.Handler {
	var scopeMW func(http.Handler) http.Handler
	if scopeEnforcementEnabled(appCfg) {
		logger.Info(context.Background(), "Scope enforcement enabled",
			applog.String("jwks_endpoint", appCfg.SecurityConfig.JwksURL),
			applog.String("issuer", appCfg.SecurityConfig.IssuerURL),
		)
		jwksCache := security.NewJWKSCache(appCfg.SecurityConfig.JwksURL,
			time.Duration(appCfg.SecurityConfig.JwksCacheTTL), commonHTTPClient)
		scopeMW = security.ScopeMiddleware(jwksCache, appCfg.SecurityConfig)
	} else {
		logger.Warn(context.Background(), "Scope enforcement disabled; set ISSUER_URL and JWKS_URL in security_config to enable")
	}

	requestTimeLeeway := time.Duration(appCfg.SecurityConfig.RequestTimeLeewaySecs) * time.Second
	logger.Info(context.Background(), "Request time validation enabled", applog.String("leeway", requestTimeLeeway.String()))
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

func initializeKeyManager(conn *sqlx.DB) (*keymanager.Service, *signature.Service, *cryptomanager.Service, keystore.KeyStore, error) {
	kmCfg := keymanager.LoadConfig()
	ks, err := keystore.New(kmCfg.KeystoreType, kmCfg.KeystoreParams)
	if err != nil {
		applog.GetLogger().Fatal("initialize keystore", applog.Error(err))
	}
	svc := keymanager.NewService(conn, ks, kmCfg)
	sigSvc := signature.NewService(svc)
	cryptoSvc := cryptomanager.NewService(db.New(conn, kmCfg.DBSchema), svc, cryptomanager.LoadConfig())
	return svc, sigSvc, cryptoSvc, ks, nil
}

// provisionKeyHierarchy provisions the ROOT key, the OIDC_SERVICE Component
// Master Key (intermediate, signed by ROOT), and its EC sign key (leaf,
// signed directly by ROOT). Each GenerateMasterKey call is idempotent — it
// returns the existing key unless it's missing or past its pre-expiry cutoff
// (see ensureCurrentKey) — so running this on every startup is safe and
// requires no separate "has this run before" tracking; the key hierarchy
// itself, persisted in keymgr.key_alias, is the source of truth.
func provisionKeyHierarchy(svc *keymanager.Service) error {
	if err := generateKey(svc, keymanager.AppIDRoot, "", "MOSIP Root CA"); err != nil {
		return fmt.Errorf("provision ROOT key: %w", err)
	}
	if err := generateKey(svc, config.OIDCServiceAppID, keymanager.RefIDRSA2048, ""); err != nil {
		return fmt.Errorf("provision %s component master key: %w", config.OIDCServiceAppID, err)
	}
	if err := generateKey(svc, config.OIDCServiceAppID, keymanager.RefIDECSECP256R1Sign, ""); err != nil {
		return fmt.Errorf("provision %s EC sign key: %w", config.OIDCServiceAppID, err)
	}
	if _, err := svc.GenerateSymmetricKey(context.Background(), keymanager.SymmetricKeyRequest{
		ApplicationID: config.OIDCServiceAppID,
		ReferenceID:   keymanager.RefIDCacheEncrypt,
		Force:         false,
	}); err != nil {
		return fmt.Errorf("provision %s symmetric key: %w", config.OIDCServiceAppID, err)
	}
	if err := generateKey(svc, config.OIDCPartnerAppID, keymanager.RefIDRSA2048, ""); err != nil {
		return fmt.Errorf("provision %s component master key: %w", config.OIDCPartnerAppID, err)
	}
	return nil
}

// generateKey provisions the master/intermediate/leaf key for
// (appID, refID), naming it commonName if given or the configured default
// otherwise (see Config.CertCommonName / applyCertSubjectDefaults).
func generateKey(svc *keymanager.Service, appID, refID, commonName string) error {
	_, err := svc.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: appID,
		ReferenceID:   refID,
		ObjectType:    keymanager.ObjectTypeCertificate,
		CommonName:    commonName,
	})
	return err
}
