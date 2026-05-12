// Package worker provides generic bounded-concurrency primitives for
// processing slices of items in parallel with context cancellation.
package worker

import (
	"context"
	"iter"
	"sync"

	"golang.org/x/sync/errgroup"
)

// Run processes items concurrently with at most concurrency goroutines.
// It returns the first error encountered, cancelling remaining work.
func Run[T any](ctx context.Context, items []T, concurrency int, fn func(ctx context.Context, item T) error) error {
	g, ctx := errgroup.WithContext(ctx)
	g.SetLimit(concurrency)

	for _, item := range items {
		g.Go(func() error {
			return fn(ctx, item)
		})
	}

	return g.Wait()
}

// Map processes items concurrently with at most concurrency goroutines and
// collects results in input order. It returns the first error encountered,
// cancelling remaining work. On success the returned slice has the same
// length and order as items.
func Map[T, R any](ctx context.Context, items []T, concurrency int, fn func(ctx context.Context, item T) (R, error)) ([]R, error) {
	g, ctx := errgroup.WithContext(ctx)
	g.SetLimit(concurrency)

	results := make([]R, len(items))

	for i, item := range items {
		g.Go(func() error {
			r, err := fn(ctx, item)
			if err != nil {
				return err
			}
			results[i] = r

			return nil
		})
	}

	if err := g.Wait(); err != nil {
		return nil, err
	}

	return results, nil
}

// RunIter processes items from an iterator concurrently with at most
// concurrency goroutines. It returns the first error encountered,
// cancelling remaining work.
func RunIter[T any](ctx context.Context, seq iter.Seq[T], concurrency int, fn func(ctx context.Context, item T) error) error {
	g, ctx := errgroup.WithContext(ctx)
	g.SetLimit(concurrency)

	for item := range seq {
		g.Go(func() error {
			return fn(ctx, item)
		})
	}

	return g.Wait()
}

// MapIter processes items from an iterator concurrently with at most
// concurrency goroutines and collects results. Unlike Map, input order
// is not guaranteed because iterator length is unknown upfront.
func MapIter[T, R any](ctx context.Context, seq iter.Seq[T], concurrency int, fn func(ctx context.Context, item T) (R, error)) ([]R, error) {
	g, ctx := errgroup.WithContext(ctx)
	g.SetLimit(concurrency)

	var (
		mu      sync.Mutex
		results []R
	)

	for item := range seq {
		g.Go(func() error {
			r, err := fn(ctx, item)
			if err != nil {
				return err
			}

			mu.Lock()
			results = append(results, r)
			mu.Unlock()

			return nil
		})
	}

	if err := g.Wait(); err != nil {
		return nil, err
	}

	return results, nil
}
