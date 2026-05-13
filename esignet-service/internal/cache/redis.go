// Package cache provides a thin wrapper around a [redis.Client] with
// pool configuration and a [Ping] method for health checks.
package cache

import (
	"context"
	"fmt"
	"log/slog"

	"github.com/redis/go-redis/v9"
	"github.com/mosip/esignet/internal/config"
)

// Redis wraps a go-redis Client.
type Redis struct {
	Client *redis.Client
	log    *slog.Logger
}

// NewRedis creates and validates a go-redis client.
// A first Ping is issued before returning to confirm connectivity.
// The caller owns the returned *Redis and must call Close when done.
func NewRedis(ctx context.Context, cfg config.RedisConfig, log *slog.Logger) (*Redis, error) {
	client := redis.NewClient(&redis.Options{
		Addr:         cfg.Addr,
		Password:     cfg.Password,
		DB:           cfg.DB,
		DialTimeout:  cfg.DialTimeout,
		ReadTimeout:  cfg.ReadTimeout,
		WriteTimeout: cfg.WriteTimeout,
		PoolSize:     cfg.PoolSize,
	})

	// Fail fast: verify connectivity before the server starts serving traffic.
	pingCtx, cancel := context.WithTimeout(ctx, cfg.HealthTimeout)
	defer cancel()

	if err := client.Ping(pingCtx).Err(); err != nil {
		_ = client.Close()
		return nil, fmt.Errorf("initial redis ping: %w", err)
	}

	log.Info("redis connected",
		slog.String("addr", cfg.Addr),
		slog.Int("db", cfg.DB),
		slog.Int("pool_size", cfg.PoolSize),
	)

	return &Redis{Client: client, log: log}, nil
}

// Ping sends a PING command to verify the Redis server is reachable.
// It is safe for concurrent use and intended for health-check endpoints.
func (r *Redis) Ping(ctx context.Context) error {
	if err := r.Client.Ping(ctx).Err(); err != nil {
		return fmt.Errorf("redis ping: %w", err)
	}
	return nil
}

// Close closes the underlying connection pool.
// It should be deferred in main() after NewRedis succeeds.
func (r *Redis) Close() error {
	r.log.Info("redis client closed")
	return r.Client.Close()
}
