package keymanager

import (
	"errors"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

func TestCacheKey_DistinguishesAppIDAndRefIDCombinations(t *testing.T) {
	// Without a delimiter, ("A", "BC") and ("AB", "C") would collide.
	require.NotEqual(t, cacheKey("A", "BC"), cacheKey("AB", "C"))
	require.Equal(t, cacheKey("A", "B"), cacheKey("A", "B"))
}

func TestTTLCache_GetMissOnEmptyCache(t *testing.T) {
	c := newTTLCache[string]()
	_, ok := c.get("k")
	require.False(t, ok)
}

func TestTTLCache_SetThenGet_ReturnsValueBeforeExpiry(t *testing.T) {
	c := newTTLCache[string]()
	c.set("k", "v", time.Now().UTC().Add(time.Hour))
	v, ok := c.get("k")
	require.True(t, ok)
	require.Equal(t, "v", v)
}

func TestTTLCache_Get_ExpiredEntryIsAMiss(t *testing.T) {
	c := newTTLCache[string]()
	c.set("k", "v", time.Now().UTC().Add(-time.Second)) // already expired
	_, ok := c.get("k")
	require.False(t, ok)
}

func TestTTLCache_Invalidate_RemovesEntry(t *testing.T) {
	c := newTTLCache[string]()
	c.set("k", "v", time.Now().UTC().Add(time.Hour))
	c.invalidate("k")
	_, ok := c.get("k")
	require.False(t, ok)
}

func TestTTLCache_Invalidate_UnknownKeyIsANoop(_ *testing.T) {
	c := newTTLCache[string]()
	c.invalidate("does-not-exist") // must not panic
}

func TestTTLCache_GetOrLoad_CacheHitSkipsLoad(t *testing.T) {
	c := newTTLCache[string]()
	c.set("k", "cached", time.Now().UTC().Add(time.Hour))

	loadCalled := false
	v, err := c.getOrLoad("k", func() (string, time.Time, error) {
		loadCalled = true
		return "fresh", time.Now().UTC().Add(time.Hour), nil
	})
	require.NoError(t, err)
	require.Equal(t, "cached", v)
	require.False(t, loadCalled, "getOrLoad must not call load on a cache hit")
}

func TestTTLCache_GetOrLoad_MissCallsLoadAndPopulatesCache(t *testing.T) {
	c := newTTLCache[string]()
	loadCalls := 0
	load := func() (string, time.Time, error) {
		loadCalls++
		return "fresh", time.Now().UTC().Add(time.Hour), nil
	}

	v, err := c.getOrLoad("k", load)
	require.NoError(t, err)
	require.Equal(t, "fresh", v)
	require.Equal(t, 1, loadCalls)

	// Second call must be served from the now-populated cache.
	v2, err := c.getOrLoad("k", load)
	require.NoError(t, err)
	require.Equal(t, "fresh", v2)
	require.Equal(t, 1, loadCalls, "second getOrLoad call must be a cache hit, not another load")
}

func TestTTLCache_GetOrLoad_LoadErrorIsNotCached(t *testing.T) {
	c := newTTLCache[string]()
	wantErr := errors.New("boom")
	loadCalls := 0
	load := func() (string, time.Time, error) {
		loadCalls++
		if loadCalls == 1 {
			return "", time.Time{}, wantErr
		}
		return "fresh", time.Now().UTC().Add(time.Hour), nil
	}

	_, err := c.getOrLoad("k", load)
	require.ErrorIs(t, err, wantErr)

	v, err := c.getOrLoad("k", load)
	require.NoError(t, err)
	require.Equal(t, "fresh", v)
	require.Equal(t, 2, loadCalls, "a failed load must not be cached — the next call must retry")
}

func TestTTLCache_GetOrLoad_ConcurrentMissesCollapseIntoOneLoad(t *testing.T) {
	c := newTTLCache[string]()
	var loadCalls int32
	release := make(chan struct{})
	load := func() (string, time.Time, error) {
		atomic.AddInt32(&loadCalls, 1)
		<-release // hold every concurrent caller here until released together
		return "fresh", time.Now().UTC().Add(time.Hour), nil
	}

	const n = 20
	var wg sync.WaitGroup
	results := make([]string, n)
	wg.Add(n)
	for i := 0; i < n; i++ {
		go func(i int) {
			defer wg.Done()
			v, err := c.getOrLoad("k", load)
			require.NoError(t, err)
			results[i] = v
		}(i)
	}

	// Give every goroutine a chance to reach the load closure before
	// releasing it, so this actually exercises the concurrent-miss path
	// rather than racing the first goroutine to completion.
	time.Sleep(50 * time.Millisecond)
	close(release)
	wg.Wait()

	require.EqualValues(t, 1, atomic.LoadInt32(&loadCalls), "concurrent misses for the same key must collapse into a single load")
	for _, v := range results {
		require.Equal(t, "fresh", v)
	}
}

func TestTTLCache_GetOrLoad_InvalidateDuringLoadIsNotOverwritten(t *testing.T) {
	c := newTTLCache[string]()
	loadStarted := make(chan struct{})
	release := make(chan struct{})
	load := func() (string, time.Time, error) {
		close(loadStarted)
		<-release // simulate a slow DB/keystore call the invalidate races against
		return "stale-pre-revocation-value", time.Now().UTC().Add(time.Hour), nil
	}

	var wg sync.WaitGroup
	wg.Add(1)
	var loadedValue string
	go func() {
		defer wg.Done()
		v, err := c.getOrLoad("k", load)
		require.NoError(t, err)
		loadedValue = v
	}()

	<-loadStarted     // load is now in flight, holding data read before the invalidate below
	c.invalidate("k") // e.g. RevokeKey committing and invalidating concurrently
	close(release)    // let the in-flight load finish and attempt to cache its (now-stale) result
	wg.Wait()

	require.Equal(t, "stale-pre-revocation-value", loadedValue, "the in-flight caller still gets its own load's result")

	_, ok := c.get("k")
	require.False(t, ok, "a load that started before invalidate() must not repopulate the cache after it — doing so would resurrect the invalidated (e.g. revoked) value")
}
