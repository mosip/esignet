/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package mosip

import (
	"context"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"

	"github.com/stretchr/testify/suite"
)

func (ts *UtilsTestSuite) TestTokenProviderFetchAndCache() {
	t := ts.T()
	var calls int32
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		atomic.AddInt32(&calls, 1)
		w.Header().Set("authorization", "tok-123")
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	tp := newTokenProvider(AuditConfig{AuthTokenURL: srv.URL, SecretKey: "s"}, srv.Client())

	token, err := tp.GetAuthToken(context.Background())
	if err != nil {
		t.Fatalf("GetAuthToken: %v", err)
	}
	if token != "tok-123" {
		t.Fatalf("token = %q, want tok-123", token)
	}

	// Second call served from cache — no additional HTTP request.
	if _, err = tp.GetAuthToken(context.Background()); err != nil {
		t.Fatalf("GetAuthToken (cached): %v", err)
	}
	if got := atomic.LoadInt32(&calls); got != 1 {
		t.Fatalf("auth server calls = %d, want 1 (cached)", got)
	}

	// Purge forces a refetch.
	tp.Purge()
	if _, err = tp.GetAuthToken(context.Background()); err != nil {
		t.Fatalf("GetAuthToken (after purge): %v", err)
	}
	if got := atomic.LoadInt32(&calls); got != 2 {
		t.Fatalf("auth server calls = %d, want 2 (refetch after purge)", got)
	}
}

func (ts *UtilsTestSuite) TestTokenProviderEmptyHeader() {
	t := ts.T()
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer srv.Close()

	tp := newTokenProvider(AuditConfig{AuthTokenURL: srv.URL, SecretKey: "s"}, srv.Client())
	if _, err := tp.GetAuthToken(context.Background()); err == nil {
		t.Fatal("expected error for empty authorization header")
	}
}

type UtilsTestSuite struct {
	suite.Suite
}

func TestUtilsTestSuite(t *testing.T) {
	suite.Run(t, new(UtilsTestSuite))
}
