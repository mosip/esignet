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

// pmsStub serves just enough of PMS's /oidc-clients and eSignet's /client-mgmt/client/{id} to prove additionalConfig is patched onto eSignet directly, since PMS drops it silently on registration. eSignet's client-mgmt responses never echo additionalConfig back (see patchAdditionalConfig), so unlike lifecycle_test.go's clientMgmtStub this one has no read-back to serve.
type pmsStub struct {
	dropOnRegister bool // mirrors PMS's real behaviour: additionalConfig sent to /oidc-clients never lands
	stored         map[string]any
	patches        int
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
		if req.Method != http.MethodPatch {
			w.WriteHeader(http.StatusMethodNotAllowed)
			return
		}
		st.patches++
		var wrapper struct {
			Request struct {
				AdditionalConfig map[string]any `json:"additionalConfig"`
			} `json:"request"`
		}
		body, _ := io.ReadAll(req.Body)
		_ = json.Unmarshal(body, &wrapper)
		st.stored = wrapper.Request.AdditionalConfig
		// Real eSignet echoes only clientId+status here, additionalConfig included, so the stub matches that shape rather than the more helpful one it could serve.
		_, _ = io.WriteString(w, `{"response":{"clientId":"pms-cid-1","status":"ACTIVE"}}`)
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
	if got := st.stored["require_pkce"]; got != true {
		t.Errorf("patched require_pkce = %v, want true", got)
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
