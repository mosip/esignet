// Command esignet is the esignet HTTP service entrypoint.
package main

import (
	"context"
	"errors"
	"log/slog"
	"os"
	"os/signal"
	"strings"
	"syscall"

	"github.com/mosip/esignet/internal/cache"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/db"
	"github.com/mosip/esignet/internal/handler"
	"github.com/mosip/esignet/internal/server"
	"github.com/mosip/esignet/internal/thunderembed"
	"github.com/mosip/esignet/pkg/logger"
)

func main() {
	os.Exit(run())
}

func run() int {
	// ── 1. Configuration ─────────────────────────────────────────────────────
	cfg, err := config.Load()
	if err != nil {
		// slog default is not yet initialised; fall back to stdlib.
		slog.Error("failed to load config", slog.String("error", err.Error()))
		return 1
	}

	// ── 2. Logger ────────────────────────────────────────────────────────────
	log := logger.New(cfg.Log.Level, cfg.Log.Format)
	slog.SetDefault(log) // make slog.Info/Error etc. route to our logger

	log.Info("esignet service starting",
		slog.String("log_level", cfg.Log.Level),
		slog.String("log_format", cfg.Log.Format),
	)

	// ── 3. Root context – cancelled on SIGINT / SIGTERM ──────────────────────
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// ── 4. Postgres ──────────────────────────────────────────────────────────
	postgres, err := db.NewPostgres(ctx, cfg.Postgres, log)
	if err != nil {
		log.Error("postgres init failed", slog.String("error", err.Error()))
		return 1
	}
	defer postgres.Close()

	// ── 5. Redis ─────────────────────────────────────────────────────────────
	redisClient, err := cache.NewRedis(ctx, cfg.Redis, log)
	if err != nil {
		log.Error("redis init failed", slog.String("error", err.Error()))
		return 1
	}
	defer func() {
		if err := redisClient.Close(); err != nil {
			log.Warn("redis close error", slog.String("error", err.Error()))
		}
	}()

	pingers := map[string]handler.Pinger{
		"postgres": postgres,
		"redis":    redisClient,
	}

	// ── 6. HTTP Server ───────────────────────────────────────────────────────
	thunderHome := strings.TrimSpace(cfg.Thunder.Home)
	var (
		srvStd *server.Server
		srvEmb *thunderembed.Server
	)
	if thunderHome != "" {
		srvEmb, err = thunderembed.NewServer(cfg, log, pingers)
		if err != nil {
			log.Error("thunder embed init failed", slog.String("error", err.Error()))
			return 1
		}
	} else {
		srvStd = server.New(cfg.Server, server.Dependencies{
			DB:    postgres,
			Cache: redisClient,
		}, log)
	}

	srvErr := make(chan error, 1)
	go func() {
		if srvEmb != nil {
			srvErr <- srvEmb.Start()
			return
		}
		srvErr <- srvStd.Start()
	}()

	// ── 7. Block until signal or server error ────────────────────────────────
	select {
	case err := <-srvErr:
		if err != nil && !errors.Is(err, context.Canceled) {
			log.Error("server exited with error", slog.String("error", err.Error()))
			return 1
		}
	case <-ctx.Done():
		log.Info("shutdown signal received", slog.String("signal", ctx.Err().Error()))
	}

	// ── 8. Graceful shutdown ─────────────────────────────────────────────────
	shutCtx := context.Background()
	if srvEmb != nil {
		if err := srvEmb.Shutdown(shutCtx); err != nil {
			log.Error("graceful shutdown failed", slog.String("error", err.Error()))
			return 1
		}
	} else {
		if err := srvStd.Shutdown(shutCtx); err != nil {
			log.Error("graceful shutdown failed", slog.String("error", err.Error()))
			return 1
		}
	}

	log.Info("esignet service stopped cleanly")
	return 0
}
