// Package main is the entry point for the template application.
package main

import (
	"context"
	"errors"
	"fmt"
	"log/slog"
	"net/http" // [server]
	"os"
	"os/signal"
	"syscall"
	"time" // [server]

	"github.com/go-chi/chi/v5" // [server]

	"github.com/mosip/esignet/pkg/db" // [postgres]
	"github.com/mosip/esignet/pkg/env"
	"github.com/mosip/esignet/pkg/handler"    // [server] [postgres]
	"github.com/mosip/esignet/pkg/middleware" // [server]
	"github.com/mosip/esignet/pkg/service"    // [server] [postgres]
	"github.com/mosip/esignet/pkg/template"   // [client] [postgres]

	_ "github.com/mosip/esignet/pkg/log" // structured logger init
)

// Config holds environment variables for this process.
type Config struct {
	// [postgres]
	DatabaseURL string `env:"DATABASE_URL,required"`
	// [/postgres]
	// [server]
	Port         string        `env:"PORT,default=8080"`
	ReadTimeout  time.Duration `env:"READ_TIMEOUT,default=10s"`
	WriteTimeout time.Duration `env:"WRITE_TIMEOUT,default=10s"`
	// [/server]
}

func main() {
	if err := run(); err != nil {
		slog.Error("fatal error", slog.Any("error", err))
		os.Exit(1)
	}
}

func run() error {
	ctx, stop := signal.NotifyContext(context.Background(), os.Interrupt, syscall.SIGTERM)
	defer stop()

	config, err := env.New[Config]()
	if err != nil {
		return fmt.Errorf("loading config: %w", err)
	}

	// [server]
	if err := serve(ctx, config); err != nil {
		if cause := context.Cause(ctx); cause != nil {
			slog.ErrorContext(ctx, "shutting down", slog.Any("cause", cause))

			return nil
		}

		return err
	}
	// [/server]
	// [client]
	if err := scrape(ctx, config); err != nil {
		if cause := context.Cause(ctx); cause != nil {
			slog.ErrorContext(ctx, "shutting down", slog.Any("cause", cause))

			return nil
		}

		return err
	}
	// [/client]

	return nil
}

// [server]

// serve starts the HTTP server with graceful shutdown.
func serve(ctx context.Context, config *Config) error {
	// [postgres]
	database, err := db.New(ctx, config.DatabaseURL)
	if err != nil {
		return fmt.Errorf("create database: %w", err)
	}
	defer database.Close()

	svc := service.New(database)
	h := handler.New(svc)
	// [/postgres]

	r := chi.NewRouter()
	r.Use(middleware.Recover)
	r.Use(middleware.RequestID)
	r.Use(middleware.Logger)

	// [postgres]
	r.Get("/health", handler.Health(database))
	r.Route("/api", func(r chi.Router) {
		r.Get("/example", h.GetExample)
	})
	// [/postgres]

	srv := &http.Server{
		Addr:         ":" + config.Port,
		Handler:      r,
		ReadTimeout:  config.ReadTimeout,
		WriteTimeout: config.WriteTimeout,
	}

	errCh := make(chan error, 1)
	go func() {
		slog.InfoContext(ctx, "server listening", slog.String("addr", srv.Addr))
		errCh <- srv.ListenAndServe()
	}()

	select {
	case err := <-errCh:
		if err != nil && !errors.Is(err, http.ErrServerClosed) {
			return fmt.Errorf("listen: %w", err)
		}
	case <-ctx.Done():
		slog.InfoContext(ctx, "shutting down server")

		shutdownCtx, cancel := context.WithTimeout(context.Background(), 10*time.Second)
		defer cancel()

		if err := srv.Shutdown(shutdownCtx); err != nil {
			return fmt.Errorf("shutdown: %w", err)
		}
	}

	return nil
}

// [/server]

// [client]

// scrape runs the client-side scraper logic.
func scrape(ctx context.Context, config *Config) error {
	slog.InfoContext(ctx, "template application started")

	// [postgres]
	database, err := db.New(ctx, config.DatabaseURL)
	if err != nil {
		return fmt.Errorf("create database: %w", err)
	}
	defer database.Close()

	client, err := template.New(database, nil)
	if err != nil {
		return fmt.Errorf("create template: %w", err)
	}

	resp, err := client.Example(ctx)
	if err != nil {
		return fmt.Errorf("example: %w", err)
	}

	slog.InfoContext(ctx, "example response",
		slog.String("peetprint", resp.TLS.PeetPrint),
		slog.String("peetprint_hash", resp.TLS.PeetPrintHash),
	)
	// [/postgres]

	return nil
}

// [/client]
