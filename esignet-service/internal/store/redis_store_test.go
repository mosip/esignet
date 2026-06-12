package store_test

import (
	"context"
	"testing"
	"time"

	"github.com/alicebob/miniredis/v2"
	"github.com/redis/go-redis/v9"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"

	"github.com/mosip/esignet/internal/store"
)

// newTestStore spins up a miniredis server and returns a RedisStore backed by it.
// The server is automatically stopped when the test ends.
func newTestStore(t *testing.T) *store.RedisStore {
	t.Helper()
	srv := miniredis.RunT(t)
	client := redis.NewClient(&redis.Options{Addr: srv.Addr()})
	t.Cleanup(func() { _ = client.Close() })
	return store.NewRedisStore(client, "test:")
}

func ctx() context.Context { return context.Background() }

// ── FlowContext ───────────────────────────────────────────────────────────────

func TestFlowContext_StoreGetDelete(t *testing.T) {
	s := newTestStore(t)
	data := []byte(`{"step":"login"}`)
	expiry := time.Now().Add(time.Minute)

	require.NoError(t, s.StoreFlowContext(ctx(), "exec-1", data, expiry))

	got, err := s.GetFlowContext(ctx(), "exec-1")
	require.NoError(t, err)
	assert.Equal(t, data, got)

	require.NoError(t, s.DeleteFlowContext(ctx(), "exec-1"))

	_, err = s.GetFlowContext(ctx(), "exec-1")
	assert.ErrorIs(t, err, runtime.ErrNotFound)
}

func TestFlowContext_UpdatePreservesTTL(t *testing.T) {
	s := newTestStore(t)
	expiry := time.Now().Add(2 * time.Minute)

	require.NoError(t, s.StoreFlowContext(ctx(), "exec-2", []byte("v1"), expiry))
	require.NoError(t, s.UpdateFlowContext(ctx(), "exec-2", []byte("v2")))

	got, err := s.GetFlowContext(ctx(), "exec-2")
	require.NoError(t, err)
	assert.Equal(t, []byte("v2"), got)
}

func TestFlowContext_GetNotFound(t *testing.T) {
	s := newTestStore(t)
	_, err := s.GetFlowContext(ctx(), "missing")
	assert.ErrorIs(t, err, runtime.ErrNotFound)
}

// ── AuthCode ──────────────────────────────────────────────────────────────────

func TestAuthCode_StoreGetDelete(t *testing.T) {
	s := newTestStore(t)
	data := []byte(`{"sub":"user-1"}`)

	require.NoError(t, s.StoreAuthCode(ctx(), "code-abc", data, time.Now().Add(time.Minute)))

	got, err := s.GetAuthCode(ctx(), "code-abc")
	require.NoError(t, err)
	assert.Equal(t, data, got)

	require.NoError(t, s.DeleteAuthCode(ctx(), "code-abc"))

	_, err = s.GetAuthCode(ctx(), "code-abc")
	assert.ErrorIs(t, err, runtime.ErrNotFound)
}

func TestAuthCode_GetNotFound(t *testing.T) {
	s := newTestStore(t)
	_, err := s.GetAuthCode(ctx(), "no-such-code")
	assert.ErrorIs(t, err, runtime.ErrNotFound)
}

// ── AuthRequest ───────────────────────────────────────────────────────────────

func TestAuthRequest_StoreGetDelete(t *testing.T) {
	s := newTestStore(t)
	data := []byte(`{"client_id":"app-1"}`)

	require.NoError(t, s.StoreAuthRequest(ctx(), "req-1", data, time.Now().Add(time.Minute)))

	got, err := s.GetAuthRequest(ctx(), "req-1")
	require.NoError(t, err)
	assert.Equal(t, data, got)

	require.NoError(t, s.DeleteAuthRequest(ctx(), "req-1"))

	_, err = s.GetAuthRequest(ctx(), "req-1")
	assert.ErrorIs(t, err, runtime.ErrNotFound)
}

// ── PAR ───────────────────────────────────────────────────────────────────────

func TestPAR_StoreGetDelete(t *testing.T) {
	s := newTestStore(t)
	data := []byte(`{"scope":"openid"}`)
	uri := "urn:ietf:params:oauth:request_uri:abc123"

	require.NoError(t, s.StorePAR(ctx(), uri, data, time.Now().Add(time.Minute)))

	got, err := s.GetPAR(ctx(), uri)
	require.NoError(t, err)
	assert.Equal(t, data, got)

	require.NoError(t, s.DeletePAR(ctx(), uri))

	_, err = s.GetPAR(ctx(), uri)
	assert.ErrorIs(t, err, runtime.ErrNotFound)
}

// ── JTI ───────────────────────────────────────────────────────────────────────

func TestJTI_StoreAndExists(t *testing.T) {
	s := newTestStore(t)

	exists, err := s.ExistsJTI(ctx(), "jti-new")
	require.NoError(t, err)
	assert.False(t, exists, "unseen JTI should not exist")

	require.NoError(t, s.StoreJTI(ctx(), "jti-new", time.Now().Add(time.Minute)))

	exists, err = s.ExistsJTI(ctx(), "jti-new")
	require.NoError(t, err)
	assert.True(t, exists, "stored JTI should exist")
}

func TestJTI_ExpiredReturnsNotExists(t *testing.T) {
	srv := miniredis.RunT(t)
	client := redis.NewClient(&redis.Options{Addr: srv.Addr()})
	t.Cleanup(func() { _ = client.Close() })
	s := store.NewRedisStore(client, "test:")

	require.NoError(t, s.StoreJTI(ctx(), "jti-short", time.Now().Add(time.Second)))

	// Fast-forward miniredis clock past TTL
	srv.FastForward(2 * time.Second)

	exists, err := s.ExistsJTI(ctx(), "jti-short")
	require.NoError(t, err)
	assert.False(t, exists, "expired JTI should not exist")
}

// ── AttributeCache ────────────────────────────────────────────────────────────

func TestAttributeCache_StoreGetDelete(t *testing.T) {
	s := newTestStore(t)
	data := []byte(`{"name":"Alice"}`)

	require.NoError(t, s.StoreAttributeCache(ctx(), "user-1", data, time.Now().Add(time.Minute)))

	got, err := s.GetAttributeCache(ctx(), "user-1")
	require.NoError(t, err)
	assert.Equal(t, data, got)

	require.NoError(t, s.DeleteAttributeCache(ctx(), "user-1"))

	_, err = s.GetAttributeCache(ctx(), "user-1")
	assert.ErrorIs(t, err, runtime.ErrNotFound)
}

func TestAttributeCache_ExtendExpiry(t *testing.T) {
	s := newTestStore(t)

	require.NoError(t, s.StoreAttributeCache(ctx(), "user-2", []byte("data"), time.Now().Add(time.Minute)))

	newExpiry := time.Now().Add(10 * time.Minute)
	require.NoError(t, s.ExtendAttributeCacheExpiry(ctx(), "user-2", newExpiry))

	// Value should still be accessible after extension
	got, err := s.GetAttributeCache(ctx(), "user-2")
	require.NoError(t, err)
	assert.Equal(t, []byte("data"), got)
}

func TestAttributeCache_ExtendExpiry_NotFound(t *testing.T) {
	s := newTestStore(t)
	err := s.ExtendAttributeCacheExpiry(ctx(), "no-such-user", time.Now().Add(time.Minute))
	assert.ErrorIs(t, err, runtime.ErrNotFound)
}
