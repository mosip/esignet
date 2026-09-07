package httpx

import (
	"net/http"
	"net/http/httptest"
	"testing"
	"time"
)

// Every caller of NewClient sends AdminToken (or another bearer credential)
// via the Authorization header. A server that redirects to a plain-http
// target must not have that request followed onto it -- CheckRedirect is the
// defense-in-depth backstop for that, independent of whatever scheme the
// caller's own base URL used.
func TestNewClientRefusesRedirectToNonHTTPS(t *testing.T) {
	target := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))
	defer target.Close()

	redirector := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, target.URL, http.StatusFound) // target.URL is http://, from httptest
	}))
	defer redirector.Close()

	client := NewClient(true, 5*time.Second)
	resp, err := client.Get(redirector.URL)
	if err == nil {
		resp.Body.Close()
		t.Fatal("client.Get followed a redirect to a non-https URL, want an error")
	}
}

// A redirect to an https target must still be followed -- CheckRedirect blocks
// the scheme downgrade specifically, not redirects in general.
func TestNewClientAllowsRedirectToHTTPS(t *testing.T) {
	target := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusTeapot) // distinct status so a followed redirect is unambiguous
	}))
	defer target.Close()

	redirector := httptest.NewTLSServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		http.Redirect(w, r, target.URL, http.StatusFound)
	}))
	defer redirector.Close()

	client := NewClient(false, 5*time.Second) // TLSVerify:false so the test TLS certs are accepted
	resp, err := client.Get(redirector.URL)
	if err != nil {
		t.Fatalf("client.Get: %v", err)
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusTeapot {
		t.Errorf("status = %d, want %d (the redirect to the https target should have been followed)", resp.StatusCode, http.StatusTeapot)
	}
}
