/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

// Package fetch is the outbound HTTP client shared by the ldp resolvers
// (did:web, HTTPS key and controller documents, status lists, allow-listed
// JSON-LD contexts): HTTPS only, bounded response bodies, limited redirects,
// concurrent requests for a URL collapsed into one, failures remembered
// briefly and, for the default client, a dialer that refuses non-public
// addresses.
package fetch

import (
	"bytes"
	"context"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"net"
	"net/http"
	"net/netip"
	"net/url"
	"strings"
	"sync"
	"syscall"
	"time"

	"golang.org/x/sync/singleflight"
)

const (
	maxRedirects     = 3
	defaultTimeout   = 10 * time.Second
	defaultCacheSize = 256
)

// ErrForbiddenURL reports a URL the fetcher refuses to request.
var ErrForbiddenURL = errors.New("url not allowed")

// nonPublic lists special-purpose ranges netip has no predicate for.
var nonPublic = []netip.Prefix{
	netip.MustParsePrefix("0.0.0.0/8"),      // "this network"
	netip.MustParsePrefix("100.64.0.0/10"),  // shared address space (CGNAT)
	netip.MustParsePrefix("192.0.0.0/24"),   // IETF protocol assignments
	netip.MustParsePrefix("198.18.0.0/15"),  // benchmarking
	netip.MustParsePrefix("240.0.0.0/4"),    // reserved, including broadcast
	netip.MustParsePrefix("64:ff9b::/96"),   // NAT64, embeds IPv4
	netip.MustParsePrefix("64:ff9b:1::/48"), // local-use NAT64
	netip.MustParsePrefix("2002::/16"),      // 6to4, embeds IPv4
	netip.MustParsePrefix("100::/64"),       // discard-only
}

// Options configures a Fetcher. Zero values take defaults.
type Options struct {
	// Client performs the requests. Nil gets DefaultClient(Timeout, false,
	// AllowHTTP). The Fetcher uses a copy whose redirects must also pass the
	// URL policy and redirect cap; the client's own CheckRedirect, if any,
	// still applies after them.
	Client *http.Client
	// Timeout bounds each request, independently of the callers waiting for
	// it (default 10s).
	Timeout time.Duration
	// MaxBytes caps response bodies.
	MaxBytes int64
	// AllowHTTP permits http:// URLs (local development only).
	AllowHTTP bool
	// FailureTTL is how long a failed fetch is remembered and returned
	// without a new request. Zero disables it.
	FailureTTL time.Duration
	// CacheSize caps the remembered failures (default 256).
	CacheSize int
}

// Fetcher performs bounded GET requests. It is safe for concurrent use.
type Fetcher struct {
	client    *http.Client
	timeout   time.Duration
	maxBytes  int64
	allowHTTP bool
	inflight  singleflight.Group
	failures  *Cache[error]
}

// New returns a Fetcher.
func New(o Options) *Fetcher {
	if o.Timeout <= 0 {
		o.Timeout = defaultTimeout
	}
	if o.CacheSize <= 0 {
		o.CacheSize = defaultCacheSize
	}
	if o.Client == nil {
		o.Client = DefaultClient(o.Timeout, false, o.AllowHTTP)
	}
	return &Fetcher{
		client:    guardRedirects(o.Client, o.AllowHTTP, nil),
		timeout:   o.Timeout,
		maxBytes:  o.MaxBytes,
		allowHTTP: o.AllowHTTP,
		failures:  NewCache[error](o.FailureTTL, o.CacheSize),
	}
}

// WithRedirectCheck returns a Fetcher that also requires every redirect
// target to pass check, which is given the target and the URL first
// requested. It shares f's connections but not its in-flight requests or
// remembered failures, which depend on the policy.
func (f *Fetcher) WithRedirectCheck(check func(target, origin *url.URL) error) *Fetcher {
	return &Fetcher{
		client:    guardRedirects(f.client, f.allowHTTP, check),
		timeout:   f.timeout,
		maxBytes:  f.maxBytes,
		allowHTTP: f.allowHTTP,
		failures:  NewCache[error](f.failures.ttl, f.failures.max),
	}
}

// guardRedirects returns a copy of c whose redirects must pass the redirect
// cap, the URL policy and check (if set) before c's own CheckRedirect. Without
// it, a client with no CheckRedirect follows an HTTPS URL to plain HTTP.
func guardRedirects(c *http.Client, allowHTTP bool, check func(target, origin *url.URL) error) *http.Client {
	guarded := *c
	next := c.CheckRedirect
	guarded.CheckRedirect = func(req *http.Request, via []*http.Request) error {
		if len(via) >= maxRedirects {
			return fmt.Errorf("%w: more than %d redirects", ErrForbiddenURL, maxRedirects)
		}
		if err := checkURL(req.URL, allowHTTP); err != nil {
			return err
		}
		if check != nil {
			if err := check(req.URL, via[0].URL); err != nil {
				return err
			}
		}
		if next != nil {
			return next(req, via)
		}
		return nil
	}
	return &guarded
}

// SameOrigin is a redirect check that keeps every redirect on the scheme,
// host and port of the URL first requested.
func SameOrigin(target, origin *url.URL) error {
	if target.Scheme != origin.Scheme || !strings.EqualFold(target.Hostname(), origin.Hostname()) ||
		effectivePort(target) != effectivePort(origin) {
		return fmt.Errorf("%w: cross-origin redirect to %s", ErrForbiddenURL, target.Redacted())
	}
	return nil
}

func effectivePort(u *url.URL) string {
	if p := u.Port(); p != "" {
		return p
	}
	if u.Scheme == "http" {
		return "80"
	}
	return "443"
}

// DefaultClient returns an HTTP client with an overall timeout and a redirect
// cap. Unless allowPrivate is set, its dialer refuses loopback, private,
// link-local and other non-public addresses (SSRF guard).
//
// It never uses a proxy: the guard checks the address it dials, which with a
// proxy would be the proxy's, not the target's. A deployment that needs a
// proxy supplies its own client and its own egress controls.
func DefaultClient(timeout time.Duration, allowPrivate, allowHTTP bool) *http.Client {
	dialer := &net.Dialer{Timeout: timeout}
	if !allowPrivate {
		dialer.Control = refuseNonPublic
	}
	transport := &http.Transport{
		Proxy:               nil,
		DialContext:         dialer.DialContext,
		TLSHandshakeTimeout: timeout,
		MaxIdleConns:        16,
		IdleConnTimeout:     90 * time.Second,
	}
	return &http.Client{
		Timeout:   timeout,
		Transport: transport,
		CheckRedirect: func(req *http.Request, via []*http.Request) error {
			if len(via) >= maxRedirects {
				return fmt.Errorf("%w: more than %d redirects", ErrForbiddenURL, maxRedirects)
			}
			return checkURL(req.URL, allowHTTP)
		},
	}
}

func refuseNonPublic(_, address string, _ syscall.RawConn) error {
	host, _, err := net.SplitHostPort(address)
	if err != nil {
		return err
	}
	addr, err := netip.ParseAddr(host)
	if err != nil {
		return fmt.Errorf("%w: %s", ErrForbiddenURL, address)
	}
	addr = addr.Unmap()
	if !isPublic(addr) {
		return fmt.Errorf("%w: non-public address %s", ErrForbiddenURL, addr)
	}
	return nil
}

func isPublic(addr netip.Addr) bool {
	if addr.IsLoopback() || addr.IsPrivate() || addr.IsLinkLocalUnicast() ||
		addr.IsLinkLocalMulticast() || addr.IsInterfaceLocalMulticast() ||
		addr.IsMulticast() || addr.IsUnspecified() {
		return false
	}
	for _, p := range nonPublic {
		if p.Contains(addr) {
			return false
		}
	}
	return true
}

// CheckURL reports whether the fetcher would request rawURL.
func (f *Fetcher) CheckURL(rawURL string) error {
	u, err := url.Parse(rawURL)
	if err != nil {
		return fmt.Errorf("%w: %w", ErrForbiddenURL, err)
	}
	return checkURL(u, f.allowHTTP)
}

func checkURL(u *url.URL, allowHTTP bool) error {
	switch {
	case u.Scheme != "https" && (!allowHTTP || u.Scheme != "http"):
		return fmt.Errorf("%w: scheme %q", ErrForbiddenURL, u.Scheme)
	case u.Hostname() == "":
		return fmt.Errorf("%w: missing host", ErrForbiddenURL)
	case u.User != nil:
		return fmt.Errorf("%w: userinfo not allowed", ErrForbiddenURL)
	}
	return nil
}

// Get fetches rawURL and returns its body, failing on a non-200 status or a
// body larger than the configured limit. Concurrent calls for the same URL
// share one request; the returned body must not be modified.
func (f *Fetcher) Get(ctx context.Context, rawURL, accept string) ([]byte, error) {
	if err := f.CheckURL(rawURL); err != nil {
		return nil, err
	}
	key := accept + " " + rawURL
	if err, ok := f.failures.Get(key); ok {
		return nil, err
	}
	// The shared request must outlive any one caller, so it runs on its own
	// timeout rather than on ctx.
	ch := f.inflight.DoChan(key, func() (any, error) {
		reqCtx, cancel := context.WithTimeout(context.WithoutCancel(ctx), f.timeout)
		defer cancel()
		body, err := f.get(reqCtx, rawURL, accept)
		if err != nil {
			f.failures.Put(key, err)
		}
		return body, err
	})
	select {
	case <-ctx.Done():
		return nil, ctx.Err()
	case r := <-ch:
		if r.Err != nil {
			return nil, r.Err
		}
		body, _ := r.Val.([]byte)
		return body, nil
	}
}

func (f *Fetcher) get(ctx context.Context, rawURL, accept string) ([]byte, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, rawURL, http.NoBody)
	if err != nil {
		return nil, err
	}
	if accept != "" {
		req.Header.Set("Accept", accept)
	}
	resp, err := f.client.Do(req)
	if err != nil {
		return nil, err
	}
	defer func() { _ = resp.Body.Close() }()
	if resp.StatusCode != http.StatusOK {
		return nil, fmt.Errorf("GET %s: unexpected status %d", rawURL, resp.StatusCode)
	}
	body, err := io.ReadAll(io.LimitReader(resp.Body, f.maxBytes+1))
	if err != nil {
		return nil, err
	}
	if int64(len(body)) > f.maxBytes {
		return nil, fmt.Errorf("GET %s: response exceeds %d bytes", rawURL, f.maxBytes)
	}
	return body, nil
}

// GetJSON fetches rawURL and decodes a JSON object, keeping numbers as
// json.Number.
func (f *Fetcher) GetJSON(ctx context.Context, rawURL, accept string) (map[string]any, error) {
	body, err := f.Get(ctx, rawURL, accept)
	if err != nil {
		return nil, err
	}
	dec := json.NewDecoder(bytes.NewReader(body))
	dec.UseNumber()
	var doc map[string]any
	if err := dec.Decode(&doc); err != nil {
		return nil, fmt.Errorf("GET %s: invalid JSON: %w", rawURL, err)
	}
	if doc == nil {
		return nil, fmt.Errorf("GET %s: response is not a JSON object", rawURL)
	}
	return doc, nil
}

// Cache is a size-bounded TTL cache safe for concurrent use. A zero TTL or
// size disables it: Get always misses and Put is a no-op.
type Cache[V any] struct {
	mu      sync.Mutex
	ttl     time.Duration
	max     int
	now     func() time.Time
	entries map[string]cacheEntry[V]
}

type cacheEntry[V any] struct {
	value   V
	expires time.Time
}

// NewCache returns a cache holding at most max entries for ttl each.
func NewCache[V any](ttl time.Duration, maxEntries int) *Cache[V] {
	return &Cache[V]{ttl: ttl, max: maxEntries, now: time.Now, entries: map[string]cacheEntry[V]{}}
}

// Get returns the live entry for key.
func (c *Cache[V]) Get(key string) (V, bool) {
	c.mu.Lock()
	defer c.mu.Unlock()
	e, ok := c.entries[key]
	if !ok || !c.now().Before(e.expires) {
		var zero V
		return zero, false
	}
	return e.value, true
}

// Put stores value for the cache TTL.
func (c *Cache[V]) Put(key string, value V) {
	c.PutUntil(key, value, time.Time{})
}

// PutUntil stores value until the earlier of the cache TTL and notAfter
// (ignored when zero).
func (c *Cache[V]) PutUntil(key string, value V, notAfter time.Time) {
	if c.ttl <= 0 || c.max <= 0 {
		return
	}
	c.mu.Lock()
	defer c.mu.Unlock()
	now := c.now()
	expires := now.Add(c.ttl)
	if !notAfter.IsZero() && notAfter.Before(expires) {
		expires = notAfter
	}
	if !now.Before(expires) {
		return
	}
	if _, exists := c.entries[key]; !exists && len(c.entries) >= c.max {
		c.evict(now)
	}
	c.entries[key] = cacheEntry[V]{value: value, expires: expires}
}

// evict drops expired entries, or the soonest-expiring one if none expired.
func (c *Cache[V]) evict(now time.Time) {
	var victim string
	var soonest time.Time
	for k, e := range c.entries {
		if !now.Before(e.expires) {
			delete(c.entries, k)
			continue
		}
		if victim == "" || e.expires.Before(soonest) {
			victim, soonest = k, e.expires
		}
	}
	if len(c.entries) >= c.max && victim != "" {
		delete(c.entries, victim)
	}
}
