package runtimestore

import (
	"context"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/stretchr/testify/require"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"
)

// runStoreContract exercises the behaviors both the in-memory and Redis stores
// must share, guarding against drift between the two implementations.
func runStoreContract(t *testing.T, newStore func(t *testing.T) runtime.Store) {
	ctx := context.Background()
	future := func() time.Time { return time.Now().Add(time.Hour) }

	t.Run("AuthCodeRoundTripAndDelete", func(t *testing.T) {
		s := newStore(t)
		require.NoError(t, s.StoreAuthCode(ctx, "code", []byte("payload"), future()))

		got, err := s.GetAuthCode(ctx, "code")
		require.NoError(t, err)
		require.Equal(t, []byte("payload"), got)

		require.NoError(t, s.DeleteAuthCode(ctx, "code"))
		_, err = s.GetAuthCode(ctx, "code")
		require.ErrorIs(t, err, runtime.ErrNotFound)

		// Deleting an absent key is not an error.
		require.NoError(t, s.DeleteAuthCode(ctx, "code"))
	})

	t.Run("GetAbsentReturnsNotFound", func(t *testing.T) {
		s := newStore(t)
		_, err := s.GetPAR(ctx, "missing")
		require.ErrorIs(t, err, runtime.ErrNotFound)
		_, err = s.GetAuthRequest(ctx, "missing")
		require.ErrorIs(t, err, runtime.ErrNotFound)
	})

	t.Run("ExpiredAtWriteReturnsNotFound", func(t *testing.T) {
		s := newStore(t)
		require.NoError(t, s.StoreAuthRequest(ctx, "req", []byte("v"), time.Now().Add(-time.Second)))
		_, err := s.GetAuthRequest(ctx, "req")
		require.ErrorIs(t, err, runtime.ErrNotFound)
	})

	t.Run("ReStoreWithPastExpiryRemovesEntry", func(t *testing.T) {
		s := newStore(t)
		require.NoError(t, s.StoreAuthCode(ctx, "code", []byte("payload"), future()))
		// Re-store the same key with an already-passed expiry: the prior entry
		// must be gone, not left stale.
		require.NoError(t, s.StoreAuthCode(ctx, "code", []byte("payload"), time.Now().Add(-time.Second)))
		_, err := s.GetAuthCode(ctx, "code")
		require.ErrorIs(t, err, runtime.ErrNotFound)
	})

	t.Run("FlowContextUpdate", func(t *testing.T) {
		s := newStore(t)
		require.NoError(t, s.StoreFlowContext(ctx, "exec", []byte("v1"), future()))
		require.NoError(t, s.UpdateFlowContext(ctx, "exec", []byte("v2")))

		got, err := s.GetFlowContext(ctx, "exec")
		require.NoError(t, err)
		require.Equal(t, []byte("v2"), got)
	})

	t.Run("UpdateAbsentFlowContextNotFound", func(t *testing.T) {
		s := newStore(t)
		err := s.UpdateFlowContext(ctx, "missing", []byte("v"))
		require.ErrorIs(t, err, runtime.ErrNotFound)
	})

	t.Run("JTIStoreAndExists", func(t *testing.T) {
		s := newStore(t)
		exists, err := s.ExistsJTI(ctx, "jti")
		require.NoError(t, err)
		require.False(t, exists)

		require.NoError(t, s.StoreJTI(ctx, "jti", future()))
		exists, err = s.ExistsJTI(ctx, "jti")
		require.NoError(t, err)
		require.True(t, exists)
	})

	t.Run("AttributeCache", func(t *testing.T) {
		s := newStore(t)
		require.NoError(t, s.StoreAttributeCache(ctx, "id", []byte("attrs"), future()))

		got, err := s.GetAttributeCache(ctx, "id")
		require.NoError(t, err)
		require.Equal(t, []byte("attrs"), got)

		require.NoError(t, s.ExtendAttributeCacheExpiry(ctx, "id", future()))
		require.ErrorIs(t, s.ExtendAttributeCacheExpiry(ctx, "missing", future()), runtime.ErrNotFound)

		require.NoError(t, s.DeleteAttributeCache(ctx, "id"))
		_, err = s.GetAttributeCache(ctx, "id")
		require.ErrorIs(t, err, runtime.ErrNotFound)
	})
}

func TestMemoryStoreContract(t *testing.T) {
	runStoreContract(t, func(_ *testing.T) runtime.Store {
		return runtime.NewMemoryRuntimeStore()
	})
}

func TestRedisStoreContract(t *testing.T) {
	runStoreContract(t, func(t *testing.T) runtime.Store {
		return newMiniredisStore(t)
	})
}

// newMiniredisStore builds a RedisStore backed by an in-process miniredis
// server that is torn down with the test.
func newMiniredisStore(t *testing.T) *RedisStore {
	t.Helper()
	return newRedisStoreFor(t, miniredis.RunT(t), "contract")
}

// newRedisStoreFor builds a RedisStore for an existing miniredis server via
// NewRedisStore so the constructor's validation and startup ping are covered.
func newRedisStoreFor(t *testing.T, mr *miniredis.Miniredis, deploymentID string) *RedisStore {
	t.Helper()
	store, err := NewRedisStore(Config{Address: mr.Addr(), DeploymentID: deploymentID})
	require.NoError(t, err)
	t.Cleanup(func() { _ = store.Close() })
	return store
}

func TestRedisStore_RecordVanishesAfterTTL(t *testing.T) {
	mr := miniredis.RunT(t)
	s := newRedisStoreFor(t, mr, "ttl")
	ctx := context.Background()

	require.NoError(t, s.StoreAuthCode(ctx, "code", []byte("v"), time.Now().Add(60*time.Second)))
	mr.FastForward(61 * time.Second)

	_, err := s.GetAuthCode(ctx, "code")
	require.ErrorIs(t, err, runtime.ErrNotFound)
}

func TestRedisStore_UpdateFlowContextPreservesTTL(t *testing.T) {
	mr := miniredis.RunT(t)
	s := newRedisStoreFor(t, mr, "keepttl")
	ctx := context.Background()

	require.NoError(t, s.StoreFlowContext(ctx, "exec", []byte("v1"), time.Now().Add(60*time.Second)))
	mr.FastForward(30 * time.Second)

	require.NoError(t, s.UpdateFlowContext(ctx, "exec", []byte("v2")))
	got, err := s.GetFlowContext(ctx, "exec")
	require.NoError(t, err)
	require.Equal(t, []byte("v2"), got)

	// The remaining ~30s TTL must be preserved; the key vanishes once it elapses.
	mr.FastForward(31 * time.Second)
	_, err = s.GetFlowContext(ctx, "exec")
	require.ErrorIs(t, err, runtime.ErrNotFound)
}
