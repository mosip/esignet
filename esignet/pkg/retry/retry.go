// Package retry provides a generic retry loop with exponential backoff and
// full jitter for transient-error recovery.
package retry

import (
	"context"
	"fmt"
	"math"
	"math/rand/v2"
	"time"
)

// Option configures the retry behavior.
type Option func(*config)

const (
	defaultMaxAttempts  = 3
	defaultInitialDelay = 1 * time.Second
	defaultMaxDelay     = 10 * time.Second
	defaultMultiplier   = 2.0
)

type config struct {
	maxAttempts  int
	initialDelay time.Duration
	maxDelay     time.Duration
	multiplier   float64
}

// WithMaxAttempts sets the maximum number of attempts (including the first).
// A value of 1 means no retries. Default: 3.
func WithMaxAttempts(n int) Option {
	return func(c *config) { c.maxAttempts = n }
}

// WithInitialDelay sets the base delay before the first retry. Default: 1s.
func WithInitialDelay(d time.Duration) Option {
	return func(c *config) { c.initialDelay = d }
}

// WithMaxDelay caps the backoff delay. Default: 10s.
func WithMaxDelay(d time.Duration) Option {
	return func(c *config) { c.maxDelay = d }
}

// WithMultiplier sets the exponential growth factor. Default: 2.0.
func WithMultiplier(m float64) Option {
	return func(c *config) { c.multiplier = m }
}

// Do calls fn until it returns nil, the context is cancelled, or maxAttempts
// is reached. Between attempts it sleeps for an exponentially increasing
// duration with full jitter: sleep = rand([0, min(initialDelay * multiplier^attempt, maxDelay))).
func Do(ctx context.Context, fn func(ctx context.Context) error, opts ...Option) error {
	cfg := config{
		maxAttempts:  defaultMaxAttempts,
		initialDelay: defaultInitialDelay,
		maxDelay:     defaultMaxDelay,
		multiplier:   defaultMultiplier,
	}
	for _, opt := range opts {
		opt(&cfg)
	}

	var lastErr error
	for attempt := range cfg.maxAttempts {
		lastErr = fn(ctx)
		if lastErr == nil {
			return nil
		}

		if attempt == cfg.maxAttempts-1 {
			break
		}

		timer := time.NewTimer(backoff(attempt, cfg))

		select {
		case <-ctx.Done():
			timer.Stop()
			return ctx.Err()
		case <-timer.C:
		}
	}

	return fmt.Errorf("after %d attempts: %w", cfg.maxAttempts, lastErr)
}

func backoff(attempt int, cfg config) time.Duration {
	delay := float64(cfg.initialDelay) * math.Pow(cfg.multiplier, float64(attempt))
	if delay > float64(cfg.maxDelay) {
		delay = float64(cfg.maxDelay)
	}

	return time.Duration(rand.Int64N(int64(delay) + 1))
}
