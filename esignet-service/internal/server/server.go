// Package server builds and owns the HTTP server: router, middleware stack,
// route registration, and graceful shutdown.
package server

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"

	"github.com/go-chi/chi/v5"
	chimw "github.com/go-chi/chi/v5/middleware"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/handler"
	"github.com/mosip/esignet/internal/middleware"
)

// Dependencies bundles the optional pluggable dependencies exposed via /health.
// Nil fields are skipped in the health check.
type Dependencies struct {
	DB    handler.Pinger // *db.Postgres
	Cache handler.Pinger // *cache.Redis
}

// Server owns the http.Server and provides lifecycle methods.
type Server struct {
	http            *http.Server
	log             *slog.Logger
	shutdownTimeout config.ServerConfig
}

// New wires the chi router, attaches middleware, registers routes, and returns
// a *Server ready to call Start on.
func New(cfg config.ServerConfig, deps Dependencies, log *slog.Logger) *Server {
	r := chi.NewRouter()

	// ── Middleware stack (applied to every request) ─────────────────────────
	r.Use(middleware.RequestID)             // inject/echo X-Request-ID
	r.Use(middleware.Logger(log))           // structured access log
	r.Use(middleware.Recoverer(log))        // panic → 500 + stack log
	r.Use(chimw.StripSlashes)               // /health/ → /health
	r.Use(chimw.Compress(5))               // gzip response bodies
	r.Use(chimw.Timeout(cfg.WriteTimeout)) // per-request deadline

	// ── Routes ───────────────────────────────────────────────────────────────

	// GET /ping – ultra-lightweight liveness probe (no DB/Redis calls).
	// Useful for load-balancer health checks that fire every second.
	r.Get("/ping", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "text/plain")
		_, _ = w.Write([]byte("pong"))
	})

	// Build dependency map for deep health check.
	pingers := make(map[string]handler.Pinger)
	if deps.DB != nil {
		pingers["postgres"] = deps.DB
	}
	if deps.Cache != nil {
		pingers["redis"] = deps.Cache
	}

	// GET /health – deep readiness probe (pings DB + Redis concurrently).
	r.Get("/health", handler.HealthHandler(log, pingers))

	// ── HTTP server ───────────────────────────────────────────────────────────
	addr := fmt.Sprintf(":%d", cfg.Port)
	httpSrv := &http.Server{
		Addr:         addr,
		Handler:      r,
		ReadTimeout:  cfg.ReadTimeout,
		WriteTimeout: cfg.WriteTimeout,
		IdleTimeout:  cfg.IdleTimeout,
	}

	return &Server{
		http:            httpSrv,
		log:             log,
		shutdownTimeout: cfg,
	}
}

// Start begins accepting connections. It blocks until the server is stopped.
// Returns nil when stopped via Shutdown; returns an error for unexpected failures.
func (s *Server) Start() error {
	s.log.Info("http server listening", slog.String("addr", s.http.Addr))
	if err := s.http.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		return fmt.Errorf("http server: %w", err)
	}
	return nil
}

// Shutdown gracefully drains in-flight requests within the configured timeout.
func (s *Server) Shutdown(ctx context.Context) error {
	s.log.Info("graceful shutdown initiated")
	shutCtx, cancel := context.WithTimeout(ctx, s.shutdownTimeout.ShutdownTimeout)
	defer cancel()
	if err := s.http.Shutdown(shutCtx); err != nil {
		return fmt.Errorf("shutdown: %w", err)
	}
	s.log.Info("http server stopped")
	return nil
}
