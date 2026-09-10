package e2e

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/mosip/esignet/api-test/internal/result"
)

// pmsPathStub records which registration endpoint a run actually posted to, and
// what wrapper it sent. Registrations here are unhardened, so nothing reaches
// eSignet client-mgmt and no /client-mgmt route is needed.
type pmsPathStub struct {
	hits    map[string]int
	wrapper map[string]any // the last wrapper received, whichever path took it
}

func newPMSPathStub(t *testing.T, serve ...string) (*pmsPathStub, *Runner, func()) {
	t.Helper()
	st := &pmsPathStub{hits: map[string]int{}}
	mux := http.NewServeMux()
	// Only the named paths are registered, so anything else gets ServeMux's 404 —
	// exactly what PMS 1.2.2.x answers for /oidc-clients.
	for _, path := range serve {
		mux.HandleFunc(path, func(w http.ResponseWriter, req *http.Request) {
			st.hits[req.URL.Path]++
			body, _ := io.ReadAll(req.Body)
			var wrapper map[string]any
			_ = json.Unmarshal(body, &wrapper)
			st.wrapper = wrapper
			w.Header().Set("Content-Type", "application/json")
			_, _ = io.WriteString(w, `{"response":{"clientId":"cid-1","status":"ACTIVE"}}`)
		})
	}
	srv := httptest.NewServer(mux)
	r := &Runner{Base: srv.URL, PMSBaseURL: srv.URL, AuthPartnerID: "p1", PolicyID: "pol1", AdminToken: "t"}
	return st, r, srv.Close
}

func registerViaPMS(t *testing.T, r *Runner) (string, error) {
	t.Helper()
	priv, err := generateRSA()
	if err != nil {
		t.Fatalf("generateRSA: %v", err)
	}
	cl := &testClient{priv: priv, kid: "test-kid", cfg: ClientConfig{}} // unhardened: no additionalConfig patch follows
	return r.createClientViaPMS(context.Background(), &[]result.HTTPCall{}, cl, Spec{RedirectURI: "https://example.org/callback"})
}

func TestPMSClientPath_DefaultsToOIDCClients(t *testing.T) {
	for _, unset := range []string{"", "   ", "nonsense"} {
		r := &Runner{PMSClientAPI: unset}
		if got := r.pmsClientPath(); got != "/oidc-clients" {
			t.Errorf("pmsClientPath(%q) = %q, want /oidc-clients — an unset or unrecognised value must not silently change the endpoint", unset, got)
		}
	}
}

func TestPMSClientPath_SelectsTheConfiguredEndpoint(t *testing.T) {
	for api, want := range map[string]string{"oidc-clients": "/oidc-clients", "oauth-client": "/oauth/client"} {
		if got := (&Runner{PMSClientAPI: api}).pmsClientPath(); got != want {
			t.Errorf("pmsClientPath(%q) = %q, want %q", api, got, want)
		}
		// Config normalises casing before a run starts; the runner must not depend on that having happened.
		if got := (&Runner{PMSClientAPI: "  " + api + "  "}).pmsClientPath(); got != want {
			t.Errorf("pmsClientPath(padded %q) = %q, want %q", api, got, want)
		}
	}
}

// The default must keep hitting exactly the endpoint, and sending exactly the wrapper,
// that deployments serving /oidc-clients already receive.
func TestCreateClientViaPMS_DefaultPostsTheWrappedEnvelopeToOIDCClients(t *testing.T) {
	st, r, closeSrv := newPMSPathStub(t, "/oidc-clients", "/oauth/client")
	defer closeSrv()

	if _, err := registerViaPMS(t, r); err != nil {
		t.Fatalf("createClientViaPMS: %v", err)
	}
	if st.hits["/oidc-clients"] != 1 || st.hits["/oauth/client"] != 0 {
		t.Fatalf("hits = %v, want one on /oidc-clients only", st.hits)
	}
	for k, want := range map[string]any{"id": "mosip.pms.create.oidc.client.post", "version": "1.0"} {
		if st.wrapper[k] != want {
			t.Errorf("wrapper[%q] = %v, want %v", k, st.wrapper[k], want)
		}
	}
	if _, ok := st.wrapper["metadata"]; !ok {
		t.Errorf("wrapper has no metadata: %v", st.wrapper)
	}
}

// /oauth/client ignores id/version/metadata — verified live against both a 1.2.2.3 and a
// current PMS — so the harness sends one body shape and only the path changes.
func TestCreateClientViaPMS_OAuthClientGetsTheSameBodyAtTheLegacyPath(t *testing.T) {
	st, r, closeSrv := newPMSPathStub(t, "/oauth/client") // no /oidc-clients route: PMS 1.2.2.x
	defer closeSrv()
	r.PMSClientAPI = "oauth-client"

	id, err := registerViaPMS(t, r)
	if err != nil {
		t.Fatalf("createClientViaPMS: %v", err)
	}
	if id != "cid-1" {
		t.Fatalf("clientID = %q, want cid-1", id)
	}
	if st.hits["/oauth/client"] != 1 {
		t.Fatalf("hits = %v, want one on /oauth/client", st.hits)
	}
	if st.wrapper["id"] != "mosip.pms.create.oidc.client.post" || st.wrapper["requestTime"] == nil {
		t.Errorf("legacy path got a different body shape than /oidc-clients: %v", st.wrapper)
	}
}

// Without a probe, a 404 is all the operator gets — so it has to name the setting that fixes it.
func TestCreateClientViaPMS_A404NamesTheSettingThatSwitchesEndpoint(t *testing.T) {
	_, r, closeSrv := newPMSPathStub(t, "/oauth/client") // default config points at the absent /oidc-clients
	defer closeSrv()

	_, err := registerViaPMS(t, r)
	if err == nil {
		t.Fatal("want an error when the configured endpoint is absent, got nil")
	}
	for _, want := range []string{"404", "client_api", "oauth-client"} {
		if !strings.Contains(err.Error(), want) {
			t.Errorf("404 error %q does not mention %q", err, want)
		}
	}
}
