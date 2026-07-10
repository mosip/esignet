package mosip

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"sync/atomic"
	"testing"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

// newTestAuditor starts a stub audit manager and returns a configured
// auditor plus a channel receiving each posted audit record.
func newTestAuditor(t *testing.T) (providers.ObservabilityProvider, <-chan AuditRequest) {
	t.Helper()
	received := make(chan AuditRequest, 4)
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		b, _ := io.ReadAll(r.Body)
		var wrapper AuditRequestWrapper[AuditRequest]
		_ = json.Unmarshal(b, &wrapper)
		received <- wrapper.Request
		w.WriteHeader(http.StatusOK)
	}))
	t.Cleanup(srv.Close)

	tokenSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("authorization", "tok-test")
		w.WriteHeader(http.StatusOK)
	}))
	t.Cleanup(tokenSrv.Close)

	t.Setenv("MOSIP_ESIGNET_AUDIT_MANAGER_URL", srv.URL)
	t.Setenv("MOSIP_ESIGNET_AUTH_TOKEN_URL", tokenSrv.URL)
	t.Setenv("MOSIP_ESIGNET_IDA_CLIENT_SECRET", "secret")

	p, err := NewAuditor(&http.Client{})
	if err != nil {
		t.Fatalf("NewAuditor: %v", err)
	}
	return p, received
}

func TestPublishMapsFailedFlow(t *testing.T) {
	p, received := newTestAuditor(t)

	p.PublishEvent(context.Background(), &providers.Event{
		TraceID:   "trace-1",
		Type:      "FLOW_FAILED",
		Timestamp: time.Now(),
		Component: "FlowEngine",
		Status:    providers.StatusFailure,
		Data: map[string]interface{}{
			"user_id":      "user-42",
			"execution_id": "exec-99",
			"client_id":    "client-abc",
			"error":        "boom",
		},
	})

	select {
	case got := <-received:
		if got.EventID != "FLOW_FAILED" {
			t.Errorf("eventId = %q, want FLOW_FAILED", got.EventID)
		}
		if got.EventType != "ERROR" {
			t.Errorf("eventType = %q, want ERROR", got.EventType)
		}
		if got.ApplicationID != "eSignet" {
			t.Errorf("applicationId = %q, want eSignet", got.ApplicationID)
		}
		if got.SessionUserName != "user-42" {
			t.Errorf("sessionUserName = %q, want user-42", got.SessionUserName)
		}
		if got.ID != "exec-99" {
			t.Errorf("id = %q, want exec-99", got.ID)
		}
		if got.ModuleID != "FlowEngine" {
			t.Errorf("moduleId = %q, want FlowEngine", got.ModuleID)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("timed out waiting for audit POST")
	}
}

func TestPublishNoUserFallback(t *testing.T) {
	p, received := newTestAuditor(t)

	p.PublishEvent(context.Background(), &providers.Event{
		Type:      "FLOW_STARTED",
		Timestamp: time.Now(),
		Component: "FlowEngine",
		Status:    providers.StatusInProgress,
	})

	select {
	case got := <-received:
		if got.SessionUserName != "no-user" {
			t.Errorf("sessionUserName = %q, want no-user", got.SessionUserName)
		}
		if got.EventType != "IN_PROGRESS" {
			t.Errorf("eventType = %q, want IN_PROGRESS", got.EventType)
		}
	case <-time.After(3 * time.Second):
		t.Fatal("timed out waiting for audit POST")
	}
}

func TestClientPostSendsWrappedBodyAndCookie(t *testing.T) {
	tokenSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("authorization", "tok-xyz")
		w.WriteHeader(http.StatusOK)
	}))
	defer tokenSrv.Close()

	var gotCookie string
	var gotBody AuditRequestWrapper[AuditRequest]
	auditSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		gotCookie = r.Header.Get("Cookie")
		b, _ := io.ReadAll(r.Body)
		_ = json.Unmarshal(b, &gotBody)
		w.WriteHeader(http.StatusOK)
		_, _ = w.Write([]byte(`{}`))
	}))
	defer auditSrv.Close()

	c := NewClient(AuditConfig{
		AuditManagerURL: auditSrv.URL,
		AuthTokenURL:    tokenSrv.URL,
		SecretKey:       "secret",
		ClientID:        "cid",
		AppID:           "ida",
	}, &http.Client{})

	err := c.Post(context.Background(), AuditRequest{EventID: "FLOW_FAILED", EventType: "ERROR"})
	if err != nil {
		t.Fatalf("Post: %v", err)
	}

	if gotCookie != "Authorization=tok-xyz" {
		t.Fatalf("cookie = %q, want Authorization=tok-xyz", gotCookie)
	}
	if gotBody.ID != "ida" {
		t.Fatalf("wrapper id = %q, want ida", gotBody.ID)
	}
	if gotBody.RequestTime == "" {
		t.Fatal("wrapper requesttime is empty")
	}
	if gotBody.Request.EventID != "FLOW_FAILED" {
		t.Fatalf("request eventId = %q, want FLOW_FAILED", gotBody.Request.EventID)
	}
}

func TestClientPostPurgesAndRetriesOn401(t *testing.T) {
	var tokenCalls int32
	tokenSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		atomic.AddInt32(&tokenCalls, 1)
		w.Header().Set("authorization", "tok")
		w.WriteHeader(http.StatusOK)
	}))
	defer tokenSrv.Close()

	var auditCalls int32
	auditSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		if atomic.AddInt32(&auditCalls, 1) == 1 {
			w.WriteHeader(http.StatusUnauthorized)
			return
		}
		w.WriteHeader(http.StatusOK)
	}))
	defer auditSrv.Close()

	c := NewClient(AuditConfig{
		AuditManagerURL: auditSrv.URL,
		AuthTokenURL:    tokenSrv.URL,
		SecretKey:       "secret",
	}, &http.Client{})

	if err := c.Post(context.Background(), AuditRequest{}); err != nil {
		t.Fatalf("Post: %v", err)
	}

	if got := atomic.LoadInt32(&auditCalls); got != 2 {
		t.Fatalf("audit calls = %d, want 2 (retry after 401)", got)
	}
	if got := atomic.LoadInt32(&tokenCalls); got != 2 {
		t.Fatalf("token calls = %d, want 2 (purge + refetch)", got)
	}
}

func TestClientPostFailsWithoutAuthTokenURL(t *testing.T) {
	auditSrv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer auditSrv.Close()

	c := NewClient(AuditConfig{AuditManagerURL: auditSrv.URL}, &http.Client{})
	if err := c.Post(context.Background(), AuditRequest{}); err == nil {
		t.Fatal("Post: want error when auth token URL is not configured")
	}
}
