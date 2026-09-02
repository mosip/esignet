package e2e

import (
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"testing"

	"github.com/mosip/esignet/api-test/internal/result"
)

// pmsStub serves just enough of PMS's /oidc-clients and eSignet's /client-mgmt/client/{id} to prove additionalConfig is patched onto eSignet directly, since PMS drops it silently on registration.
type pmsStub struct {
	dropOnRegister bool // mirrors PMS's real behaviour: additionalConfig sent to /oidc-clients never lands
	stored         map[string]any
	patches        int
	reads          int
}

func newPMSStub(t *testing.T, dropOnRegister bool) (*pmsStub, *Runner, func()) {
	t.Helper()
	st := &pmsStub{dropOnRegister: dropOnRegister}
	mux := http.NewServeMux()
	mux.HandleFunc("/oidc-clients", func(w http.ResponseWriter, req *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if !st.dropOnRegister {
			var wrapper struct {
				Request struct {
					AdditionalConfig map[string]any `json:"additionalConfig"`
				} `json:"request"`
			}
			body, _ := io.ReadAll(req.Body)
			_ = json.Unmarshal(body, &wrapper)
			st.stored = wrapper.Request.AdditionalConfig
		}
		_, _ = io.WriteString(w, `{"response":{"clientId":"pms-cid-1"}}`)
	})
	mux.HandleFunc("/client-mgmt/client/pms-cid-1", func(w http.ResponseWriter, req *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch req.Method {
		case http.MethodPatch:
			st.patches++
			var wrapper struct {
				Request struct {
					AdditionalConfig map[string]any `json:"additionalConfig"`
				} `json:"request"`
			}
			body, _ := io.ReadAll(req.Body)
			_ = json.Unmarshal(body, &wrapper)
			st.stored = wrapper.Request.AdditionalConfig
			_, _ = io.WriteString(w, `{"response":{"clientId":"pms-cid-1"}}`)
		case http.MethodGet:
			st.reads++
			b, _ := json.Marshal(map[string]any{"response": map[string]any{"clientId": "pms-cid-1", "additionalConfig": st.stored}})
			_, _ = w.Write(b)
		default:
			w.WriteHeader(http.StatusMethodNotAllowed)
		}
	})
	srv := httptest.NewServer(mux)
	r := &Runner{Base: srv.URL, PMSBaseURL: srv.URL, AuthPartnerID: "p1", PolicyID: "pol1", AdminToken: "t"}
	return st, r, srv.Close
}

func newPKCEClient(t *testing.T) *testClient {
	t.Helper()
	priv, err := generateRSA()
	if err != nil {
		t.Fatalf("generateRSA: %v", err)
	}
	return &testClient{priv: priv, kid: "test-kid", cfg: ClientConfig{RequirePKCE: true}}
}

func TestCreateClientViaPMS_PatchesAdditionalConfigAfterPMSDropsIt(t *testing.T) {
	st, r, closeSrv := newPMSStub(t, true) // PMS drops additionalConfig, same as the real deployment
	defer closeSrv()

	cl := newPKCEClient(t)
	id, err := r.createClientViaPMS(context.Background(), &[]result.HTTPCall{}, cl, Spec{RedirectURI: "https://example.org/callback"})
	if err != nil {
		t.Fatalf("createClientViaPMS: %v", err)
	}
	if id != "pms-cid-1" {
		t.Fatalf("clientID = %q, want pms-cid-1", id)
	}
	if st.patches != 1 {
		t.Fatalf("patches = %d, want 1 — additionalConfig must be patched onto eSignet directly since PMS dropped it", st.patches)
	}
	if st.reads != 1 {
		t.Fatalf("reads = %d, want 1 — the patch must be read back, not trusted from its echo", st.reads)
	}
	if got := st.stored["require_pkce"]; got != true {
		t.Errorf("stored require_pkce = %v, want true", got)
	}
}

func TestCreateClientViaPMS_UnhardenedClientSkipsThePatchEntirely(t *testing.T) {
	st, r, closeSrv := newPMSStub(t, true)
	defer closeSrv()

	priv, err := generateRSA()
	if err != nil {
		t.Fatalf("generateRSA: %v", err)
	}
	cl := &testClient{priv: priv, kid: "test-kid", cfg: ClientConfig{}} // zero value: unhardened
	if _, err := r.createClientViaPMS(context.Background(), &[]result.HTTPCall{}, cl, Spec{RedirectURI: "https://example.org/callback"}); err != nil {
		t.Fatalf("createClientViaPMS: %v", err)
	}
	if st.patches != 0 {
		t.Errorf("patches = %d, want 0 — nothing to harden means no patch call at all", st.patches)
	}
}

func TestCreateClientViaPMS_FailsLoudlyWhenTheReadbackStillDisagrees(t *testing.T) {
	// A PATCH eSignet accepts but silently fails to persist must fail registration, not succeed unhardened.
	mux := http.NewServeMux()
	mux.HandleFunc("/oidc-clients", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"response":{"clientId":"pms-cid-2"}}`)
	})
	mux.HandleFunc("/client-mgmt/client/pms-cid-2", func(w http.ResponseWriter, req *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch req.Method {
		case http.MethodPatch:
			_, _ = io.WriteString(w, `{"response":{"clientId":"pms-cid-2"}}`)
		case http.MethodGet:
			// Always reports unhardened, as if the patch never took.
			_, _ = io.WriteString(w, `{"response":{"clientId":"pms-cid-2","additionalConfig":{"require_pkce":false,"require_pushed_authorization_requests":false,"dpop_bound_access_tokens":false}}}`)
		}
	})
	srv := httptest.NewServer(mux)
	defer srv.Close()
	r := &Runner{Base: srv.URL, PMSBaseURL: srv.URL, AuthPartnerID: "p1", PolicyID: "pol1", AdminToken: "t"}

	cl := newPKCEClient(t)
	_, err := r.createClientViaPMS(context.Background(), &[]result.HTTPCall{}, cl, Spec{RedirectURI: "https://example.org/callback"})
	if err == nil {
		t.Fatal("createClientViaPMS: want an error when the readback disagrees with what was patched, got nil")
	}
}

func TestCreateClientViaPMS_FailsWhenTheEsignetPatchIsRejected(t *testing.T) {
	mux := http.NewServeMux()
	mux.HandleFunc("/oidc-clients", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"response":{"clientId":"pms-cid-3"}}`)
	})
	mux.HandleFunc("/client-mgmt/client/pms-cid-3", func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusForbidden)
		_, _ = io.WriteString(w, `{"errors":[{"errorCode":"KER-ATH-403","errorMessage":"forbidden"}]}`)
	})
	srv := httptest.NewServer(mux)
	defer srv.Close()
	r := &Runner{Base: srv.URL, PMSBaseURL: srv.URL, AuthPartnerID: "p1", PolicyID: "pol1", AdminToken: "t"}

	cl := newPKCEClient(t)
	_, err := r.createClientViaPMS(context.Background(), &[]result.HTTPCall{}, cl, Spec{RedirectURI: "https://example.org/callback"})
	if err == nil {
		t.Fatal("createClientViaPMS: want an error when eSignet rejects the additionalConfig patch, got nil")
	}
}
