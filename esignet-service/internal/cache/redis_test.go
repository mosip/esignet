package cache

import (
	"context"
	"log/slog"
	"os"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/mosip/esignet/internal/config"
)

// nopLog silences log output in tests.
var nopLog = slog.New(slog.NewTextHandler(os.Stderr, &slog.HandlerOptions{Level: slog.LevelError + 100}))

func redisCfg(addr string) config.RedisConfig {
	return config.RedisConfig{
		Addr:          addr,
		Password:      "",
		DB:            0,
		DialTimeout:   2 * time.Second,
		ReadTimeout:   2 * time.Second,
		WriteTimeout:  2 * time.Second,
		PoolSize:      5,
		HealthTimeout: 2 * time.Second,
	}
}

// ── NewRedis ──────────────────────────────────────────────────────────────────

func TestNewRedis_Success(t *testing.T) {
	mr := miniredis.RunT(t) // starts and auto-closes with the test

	r, err := NewRedis(context.Background(), redisCfg(mr.Addr()), nopLog)
	if err != nil {
		t.Fatalf("NewRedis() unexpected error: %v", err)
	}
	if r == nil {
		t.Fatal("NewRedis() returned nil")
	}
	if r.Client == nil {
		t.Error("Client should not be nil")
	}
	_ = r.Close()
}

func TestNewRedis_PingFails(t *testing.T) {
	// Point at a port with nothing listening.
	cfg := redisCfg("127.0.0.1:19999")
	cfg.DialTimeout = 200 * time.Millisecond
	cfg.HealthTimeout = 300 * time.Millisecond

	_, err := NewRedis(context.Background(), cfg, nopLog)
	if err == nil {
		t.Fatal("expected error when redis is unreachable, got nil")
	}
}

// ── Ping ──────────────────────────────────────────────────────────────────────

func TestRedis_Ping_Success(t *testing.T) {
	mr := miniredis.RunT(t)

	r, err := NewRedis(context.Background(), redisCfg(mr.Addr()), nopLog)
	if err != nil {
		t.Fatalf("setup: %v", err)
	}
	defer func() { _ = r.Close() }()

	if err := r.Ping(context.Background()); err != nil {
		t.Errorf("Ping() unexpected error: %v", err)
	}
}

func TestRedis_Ping_AfterServerDown(t *testing.T) {
	mr := miniredis.RunT(t)

	r, err := NewRedis(context.Background(), redisCfg(mr.Addr()), nopLog)
	if err != nil {
		t.Fatalf("setup: %v", err)
	}
	defer func() { _ = r.Close() }()

	mr.Close() // kill the server mid-flight

	if err := r.Ping(context.Background()); err == nil {
		t.Error("expected Ping to fail after server is stopped")
	}
}

// ── Close ─────────────────────────────────────────────────────────────────────

func TestRedis_Close(t *testing.T) {
	mr := miniredis.RunT(t)

	r, err := NewRedis(context.Background(), redisCfg(mr.Addr()), nopLog)
	if err != nil {
		t.Fatalf("setup: %v", err)
	}

	if err := r.Close(); err != nil {
		t.Errorf("Close() unexpected error: %v", err)
	}
}
