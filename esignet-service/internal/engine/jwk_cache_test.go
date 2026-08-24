package engine

import (
	"crypto/sha256"
	"testing"

	jose "github.com/go-jose/go-jose/v4"
	"github.com/stretchr/testify/require"
)

func TestJWKCache_GetMissOnEmptyCache(t *testing.T) {
	c := newJWKCache()
	_, ok := c.get(sha256.Sum256([]byte("anything")))
	require.False(t, ok)
}

func TestJWKCache_SetThenGet_ReturnsSameKey(t *testing.T) {
	c := newJWKCache()
	digest := sha256.Sum256([]byte("jwk-bytes"))
	key := &jose.JSONWebKey{}

	c.set(digest, key)
	got, ok := c.get(digest)
	require.True(t, ok)
	require.Same(t, key, got)
}

func TestJWKCache_DifferentDigests_DoNotCollide(t *testing.T) {
	c := newJWKCache()
	d1 := sha256.Sum256([]byte("jwk-1"))
	d2 := sha256.Sum256([]byte("jwk-2"))
	k1 := &jose.JSONWebKey{}

	c.set(d1, k1)
	_, ok := c.get(d2)
	require.False(t, ok, "a different digest must not hit an unrelated entry")
}

func TestJWKCache_Set_ResetsWhenOverCapacity(t *testing.T) {
	c := newJWKCache()
	// Fill to the cap so the next set triggers the defensive reset.
	for i := 0; i < maxJWKCacheEntries; i++ {
		digest := sha256.Sum256([]byte{byte(i), byte(i >> 8)})
		c.set(digest, &jose.JSONWebKey{})
	}
	require.Len(t, c.entries, maxJWKCacheEntries)

	overflowDigest := sha256.Sum256([]byte("overflow"))
	overflowKey := &jose.JSONWebKey{}
	c.set(overflowDigest, overflowKey)

	require.Len(t, c.entries, 1, "hitting the cap must reset rather than grow unbounded")
	got, ok := c.get(overflowDigest)
	require.True(t, ok)
	require.Same(t, overflowKey, got)
}
