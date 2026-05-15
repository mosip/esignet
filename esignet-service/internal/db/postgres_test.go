package db

import (
	"context"
	"errors"
	"log/slog"
	"os"
	"testing"
	"time"

	"github.com/jackc/pgx/v5/pgxpool"
	"github.com/mosip/esignet/internal/config"
)

// nopLog silences log output in tests.
var nopLog = slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError + 100}))

// ── mock pool ─────────────────────────────────────────────────────────────────

type mockPool struct {
	pingErr error
	closed  bool
}

func (m *mockPool) Ping(_ context.Context) error { return m.pingErr }
func (m *mockPool) Close()                       { m.closed = true }

// ── helpers ───────────────────────────────────────────────────────────────────

func minimalCfg() config.PostgresConfig {
	return config.PostgresConfig{
		URL:             "postgres://user:pass@localhost:5432/db?sslmode=disable",
		MaxConns:        5,
		MinConns:        1,
		MaxConnLifetime: time.Hour,
		MaxConnIdleTime: 30 * time.Minute,
		HealthTimeout:   5 * time.Second,
	}
}

// ── NewPostgres ───────────────────────────────────────────────────────────────

func TestNewPostgres_InvalidDSN(t *testing.T) {
	cfg := minimalCfg()
	cfg.URL = "not-a-valid-dsn://???"

	_, err := NewPostgres(context.Background(), cfg, nopLog)
	if err == nil {
		t.Fatal("expected error for invalid DSN, got nil")
	}
}

func TestNewPostgres_PoolConstructionError(t *testing.T) {
	// Override pool constructor to return an error.
	orig := newPool
	t.Cleanup(func() { newPool = orig })
	newPool = func(_ context.Context, _ *pgxpool.Config) (pgxPool, error) {
		return nil, errors.New("pool creation failed")
	}

	_, err := NewPostgres(context.Background(), minimalCfg(), nopLog)
	if err == nil {
		t.Fatal("expected error when pool constructor fails, got nil")
	}
}

func TestNewPostgres_PingFails(t *testing.T) {
	orig := newPool
	t.Cleanup(func() { newPool = orig })
	mock := &mockPool{pingErr: errors.New("connection refused")}
	newPool = func(_ context.Context, _ *pgxpool.Config) (pgxPool, error) {
		return mock, nil
	}

	_, err := NewPostgres(context.Background(), minimalCfg(), nopLog)
	if err == nil {
		t.Fatal("expected error when ping fails, got nil")
	}
	if !mock.closed {
		t.Error("pool.Close() should be called when ping fails")
	}
}

func TestNewPostgres_Success(t *testing.T) {
	orig := newPool
	t.Cleanup(func() { newPool = orig })
	mock := &mockPool{}
	newPool = func(_ context.Context, _ *pgxpool.Config) (pgxPool, error) {
		return mock, nil
	}

	pg, err := NewPostgres(context.Background(), minimalCfg(), nopLog)
	if err != nil {
		t.Fatalf("NewPostgres() unexpected error: %v", err)
	}
	if pg == nil {
		t.Fatal("NewPostgres() returned nil Postgres")
	}
}

// ── Ping ──────────────────────────────────────────────────────────────────────

func TestPostgres_Ping_Success(t *testing.T) {
	mock := &mockPool{}
	pg := &Postgres{pool: mock, log: nopLog}

	if err := pg.Ping(context.Background()); err != nil {
		t.Errorf("Ping() unexpected error: %v", err)
	}
}

func TestPostgres_Ping_Error(t *testing.T) {
	mock := &mockPool{pingErr: errors.New("db gone")}
	pg := &Postgres{pool: mock, log: nopLog}

	err := pg.Ping(context.Background())
	if err == nil {
		t.Fatal("expected error from Ping(), got nil")
	}
}

// ── Close ─────────────────────────────────────────────────────────────────────

func TestPostgres_Close(t *testing.T) {
	mock := &mockPool{}
	pg := &Postgres{pool: mock, log: nopLog}

	pg.Close()

	if !mock.closed {
		t.Error("Close() did not call pool.Close()")
	}
}
