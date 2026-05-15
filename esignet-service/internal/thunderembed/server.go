// Package thunderembed wires ThunderID OAuth and flow execution into this process
// when THUNDER_HOME is set.
package thunderembed

import (
	"context"
	"fmt"
	"log/slog"
	"net/http"
	"strings"

	"github.com/mosip/esignet/internal/authn"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/handler"
	"github.com/thunder-id/thunderid/pkg/embed"
)

// Server is a thin wrapper around [http.Server] for the Thunder-combined mux.
type Server struct {
	http            *http.Server
	log             *slog.Logger
	shutdownTimeout config.ServerConfig
}

// NewServer builds a [http.ServeMux] with esignet probes, then registers Thunder
// routes via [github.com/thunder-id/thunderid/pkg/embed.WireThunder].
func NewServer(cfg *config.Config, log *slog.Logger, pingers map[string]handler.Pinger) (*Server, error) {
	mux := http.NewServeMux()

	mux.HandleFunc("GET /ping", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "text/plain")
		_, _ = w.Write([]byte("pong"))
	})

	mux.HandleFunc("GET /health", handler.HealthHandler(log, pingers))

	if err := embed.WireThunder(mux, strings.TrimSpace(cfg.Thunder.Home), authn.New()); err != nil {
		return nil, fmt.Errorf("thunder embed: %w", err)
	}

	addr := fmt.Sprintf(":%d", cfg.Server.Port)
	httpSrv := &http.Server{
		Addr:         addr,
		Handler:      mux,
		ReadTimeout:  cfg.Server.ReadTimeout,
		WriteTimeout: cfg.Server.WriteTimeout,
		IdleTimeout:  cfg.Server.IdleTimeout,
	}

	return &Server{
		http:            httpSrv,
		log:             log,
		shutdownTimeout: cfg.Server,
	}, nil
}

// Start begins accepting connections.
func (s *Server) Start() error {
	s.log.Info("http server listening (thunder embed)", slog.String("addr", s.http.Addr))
	if err := s.http.ListenAndServe(); err != nil && err != http.ErrServerClosed {
		return fmt.Errorf("http server: %w", err)
	}
	return nil
}

// Shutdown drains in-flight requests.
func (s *Server) Shutdown(ctx context.Context) error {
	s.log.Info("graceful shutdown initiated")
	shutCtx, cancel := context.WithTimeout(ctx, s.shutdownTimeout.ShutdownTimeout)
	defer cancel()
	if err := s.http.Shutdown(shutCtx); err != nil {
		return fmt.Errorf("shutdown: %w", err)
	}
	embed.ShutdownThunder()
	s.log.Info("http server stopped")
	return nil
}
