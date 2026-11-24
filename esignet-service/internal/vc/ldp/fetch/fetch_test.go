/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package fetch

import (
	"context"
	"errors"
	"net/http"
	"net/http/httptest"
	"net/url"
	"strings"
	"sync"
	"sync/atomic"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"
)

type FetchTestSuite struct {
	suite.Suite
	ctx context.Context
}

func TestFetchTestSuite(t *testing.T) {
	suite.Run(t, new(FetchTestSuite))
}

func (ts *FetchTestSuite) SetupTest() {
	ts.ctx = context.Background()
}

func (ts *FetchTestSuite) TestCheckURL() {
	f := New(Options{Client: http.DefaultClient, MaxBytes: 1})
	ts.NoError(f.CheckURL("https://example.org/did.json"))
	for _, u := range []string{"http://example.org/", "ftp://example.org/", "https:///path", "https://user@example.org/", "file:///etc/passwd", "::"} {
		ts.ErrorIs(f.CheckURL(u), ErrForbiddenURL, u)
	}
	ts.NoError(New(Options{Client: http.DefaultClient, MaxBytes: 1, AllowHTTP: true}).CheckURL("http://localhost:8080/"))
}

func (ts *FetchTestSuite) TestRefuseNonPublic() {
	for _, addr := range []string{"127.0.0.1:443", "[::1]:443", "10.1.2.3:443", "192.168.0.1:443", "169.254.169.254:80", "100.64.0.1:443", "0.0.0.0:443", "[::ffff:127.0.0.1]:443",
		"0.1.2.3:443", "198.18.0.1:443", "240.0.0.1:443", "255.255.255.255:443", "[64:ff9b::a00:1]:443", "[2002:a00:1::1]:443", "[fd00::1]:443"} {
		ts.ErrorIs(refuseNonPublic("tcp", addr, nil), ErrForbiddenURL, addr)
	}
	ts.NoError(refuseNonPublic("tcp", "93.184.216.34:443", nil))
}

func (ts *FetchTestSuite) TestGet() {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/doc":
			_, _ = w.Write([]byte(`{"n": 12345678901234567890}`))
		case "/array":
			_, _ = w.Write([]byte(`[1]`))
		case "/big":
			_, _ = w.Write([]byte(strings.Repeat("x", 64)))
		case "/loop":
			http.Redirect(w, r, "/loop", http.StatusFound)
		case "/to-ftp":
			http.Redirect(w, r, "ftp://example.org/", http.StatusFound)
		default:
			http.NotFound(w, r)
		}
	}))
	defer srv.Close()
	f := New(Options{Client: DefaultClient(5*time.Second, true, true), MaxBytes: 32, AllowHTTP: true})

	doc, err := f.GetJSON(ts.ctx, srv.URL+"/doc", "")
	ts.Require().NoError(err)
	ts.Equal("12345678901234567890", doc["n"].(interface{ String() string }).String(), "numbers stay exact")

	for _, path := range []string{"/array", "/big", "/missing", "/loop", "/to-ftp"} {
		_, err := f.GetJSON(ts.ctx, srv.URL+path, "")
		ts.Error(err, path)
	}

	// The default client refuses loopback unless private networks are allowed.
	guarded := New(Options{Client: DefaultClient(5*time.Second, false, true), MaxBytes: 32, AllowHTTP: true})
	_, err = guarded.Get(ts.ctx, srv.URL+"/doc", "")
	ts.ErrorIs(err, ErrForbiddenURL)
}

// A supplied client without CheckRedirect still gets the URL policy and the
// redirect cap on every redirect.
func (ts *FetchTestSuite) TestSuppliedClientRedirects() {
	plain := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		_, _ = w.Write([]byte(`{}`))
	}))
	defer plain.Close()
	srv := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		switch r.URL.Path {
		case "/doc", "/blocked":
			_, _ = w.Write([]byte(`{}`))
		case "/to-http":
			http.Redirect(w, r, plain.URL+"/doc", http.StatusFound)
		case "/to-doc":
			http.Redirect(w, r, "/doc", http.StatusFound)
		case "/to-blocked":
			http.Redirect(w, r, "/blocked", http.StatusFound)
		case "/loop":
			http.Redirect(w, r, "/loop", http.StatusFound)
		default:
			http.NotFound(w, r)
		}
	}))
	defer srv.Close()
	client := srv.Client()
	ts.Require().Nil(client.CheckRedirect)
	f := New(Options{Client: client, MaxBytes: 32})

	_, err := f.Get(ts.ctx, srv.URL+"/to-doc", "")
	ts.NoError(err)
	for _, path := range []string{"/to-http", "/loop"} {
		_, err := f.Get(ts.ctx, srv.URL+path, "")
		ts.ErrorIs(err, ErrForbiddenURL, path)
	}
	ts.Nil(client.CheckRedirect, "the supplied client is not modified")

	errBlocked := errors.New("blocked")
	strict := f.WithRedirectCheck(func(u, origin *url.URL) error {
		if u.Path == "/blocked" && origin.Path == "/to-blocked" {
			return errBlocked
		}
		return nil
	})
	_, err = strict.Get(ts.ctx, srv.URL+"/to-blocked", "")
	ts.ErrorIs(err, errBlocked)
	_, err = strict.Get(ts.ctx, srv.URL+"/to-doc", "")
	ts.NoError(err)
	_, err = f.Get(ts.ctx, srv.URL+"/to-blocked", "")
	ts.NoError(err, "the original fetcher keeps its policy")
}

func (ts *FetchTestSuite) TestSameOrigin() {
	origin, err := url.Parse("https://Issuer.example.org/.well-known/did.json")
	ts.Require().NoError(err)
	for _, target := range []string{"https://issuer.example.org/did.json", "https://issuer.example.org:443/did.json"} {
		u, err := url.Parse(target)
		ts.Require().NoError(err)
		ts.NoError(SameOrigin(u, origin), target)
	}
	for _, target := range []string{"https://www.issuer.example.org/did.json", "https://issuer.example.org:8443/did.json", "http://issuer.example.org/did.json"} {
		u, err := url.Parse(target)
		ts.Require().NoError(err)
		ts.ErrorIs(SameOrigin(u, origin), ErrForbiddenURL, target)
	}
}

func (ts *FetchTestSuite) TestDefaultClientIgnoresProxy() {
	transport, ok := DefaultClient(time.Second, false, false).Transport.(*http.Transport)
	ts.Require().True(ok)
	ts.Nil(transport.Proxy, "the address guard would check the proxy, not the target")
}

// Concurrent requests for one URL share a single fetch.
func (ts *FetchTestSuite) TestConcurrentGetsShareOneRequest() {
	var hits atomic.Int32
	release := make(chan struct{})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		hits.Add(1)
		<-release
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()
	f := New(Options{Client: srv.Client(), MaxBytes: 32, AllowHTTP: true})

	var wg sync.WaitGroup
	for range 5 {
		wg.Go(func() {
			_, err := f.GetJSON(ts.ctx, srv.URL, "")
			ts.NoError(err)
		})
	}
	ts.Eventually(func() bool { return hits.Load() == 1 }, time.Second, time.Millisecond)
	time.Sleep(20 * time.Millisecond) // let the other callers join the request
	close(release)
	wg.Wait()
	ts.Equal(int32(1), hits.Load())
}

// A caller that gives up gets its context error; the shared fetch is unaffected.
func (ts *FetchTestSuite) TestGetHonoursCallerContext() {
	release := make(chan struct{})
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		<-release
		_, _ = w.Write([]byte(`{}`))
	}))
	defer srv.Close()
	defer close(release)
	f := New(Options{Client: srv.Client(), MaxBytes: 32, AllowHTTP: true})
	ctx, cancel := context.WithTimeout(ts.ctx, 20*time.Millisecond)
	defer cancel()
	_, err := f.Get(ctx, srv.URL, "")
	ts.ErrorIs(err, context.DeadlineExceeded)
}

func (ts *FetchTestSuite) TestFailuresAreRemembered() {
	var hits atomic.Int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		hits.Add(1)
		http.NotFound(w, r)
	}))
	defer srv.Close()
	f := New(Options{Client: srv.Client(), MaxBytes: 32, AllowHTTP: true, FailureTTL: time.Minute})
	for range 3 {
		_, err := f.Get(ts.ctx, srv.URL, "")
		ts.Error(err)
	}
	ts.Equal(int32(1), hits.Load())

	f = New(Options{Client: srv.Client(), MaxBytes: 32, AllowHTTP: true})
	for range 2 {
		_, err := f.Get(ts.ctx, srv.URL, "")
		ts.Error(err)
	}
	ts.Equal(int32(3), hits.Load(), "zero FailureTTL retries every time")
}

func (ts *FetchTestSuite) TestCache() {
	now := time.Date(2026, 1, 1, 0, 0, 0, 0, time.UTC)
	c := NewCache[int](time.Minute, 2)
	c.now = func() time.Time { return now }

	c.Put("a", 1)
	v, ok := c.Get("a")
	ts.True(ok)
	ts.Equal(1, v)

	c.PutUntil("b", 2, now.Add(10*time.Second))
	c.Put("c", 3) // full: evicts the soonest-expiring entry
	_, ok = c.Get("b")
	ts.False(ok)
	_, ok = c.Get("a")
	ts.True(ok)

	now = now.Add(2 * time.Minute)
	_, ok = c.Get("a")
	ts.False(ok, "expired")

	c.PutUntil("d", 4, now.Add(-time.Second))
	_, ok = c.Get("d")
	ts.False(ok, "already past notAfter")

	disabled := NewCache[int](0, 10)
	disabled.Put("a", 1)
	_, ok = disabled.Get("a")
	ts.False(ok)
}
