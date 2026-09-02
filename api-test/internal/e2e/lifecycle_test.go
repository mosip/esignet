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

// clientMgmtStub serves just enough of /client-mgmt/client/{id} to record the
// status writes a lifecycle makes and answer the read-backs consistently.
type clientMgmtStub struct {
	status  string
	patches []string
	reads   int
}

func newClientMgmtStub(t *testing.T) (*clientMgmtStub, *Runner, func()) {
	t.Helper()
	st := &clientMgmtStub{status: statusActive}
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch req.Method {
		case http.MethodPatch:
			body, _ := io.ReadAll(req.Body)
			var wrapper struct {
				Request struct {
					Status string `json:"status"`
				} `json:"request"`
			}
			_ = json.Unmarshal(body, &wrapper)
			st.status = wrapper.Request.Status
			st.patches = append(st.patches, wrapper.Request.Status)
			_, _ = io.WriteString(w, `{"response":{"clientId":"cid-1","status":"`+st.status+`"}}`)
		case http.MethodGet:
			st.reads++
			_, _ = io.WriteString(w, `{"response":{"clientId":"cid-1","status":"`+st.status+`"}}`)
		default:
			w.WriteHeader(http.StatusMethodNotAllowed)
		}
	}))
	return st, &Runner{Base: srv.URL, AdminToken: "t"}, srv.Close
}

func TestLifecycleValidate(t *testing.T) {
	for _, tc := range []struct {
		name    string
		spec    *ClientLifecycle
		wantErr bool
	}{
		{"nil is fine", nil, false},
		{"before_authorize", &ClientLifecycle{Deactivate: stageBeforeAuthorize}, false},
		{"after_authorize", &ClientLifecycle{Deactivate: stageAfterAuthorize}, false},
		{"after_token", &ClientLifecycle{Deactivate: stageAfterToken}, false},
		{"case and space insensitive", &ClientLifecycle{Deactivate: "  Before_Authorize "}, false},
		{"empty stage", &ClientLifecycle{}, true},
		{"unknown stage", &ClientLifecycle{Deactivate: "after_userinfo"}, true},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := tc.spec.validate(); (got != "") != tc.wantErr {
				t.Errorf("validate() = %q, wantErr %v", got, tc.wantErr)
			}
		})
	}
}

// A mistyped stage must be caught as a spec error rather than silently doing
// nothing — a lifecycle that never fires would let an inactive-client negative
// pass while the client stayed active the whole time.
func TestScenarioConfigRejectsUnknownLifecycleStage(t *testing.T) {
	sc := Scenario{AuthFactor: "otp", ClientLifecycle: &ClientLifecycle{Deactivate: "whenever"}}
	msg := scenarioConfigError(sc)
	if !strings.Contains(msg, "whenever") {
		t.Errorf("scenarioConfigError = %q, want it to name the bad stage", msg)
	}
}

func TestApplyAtOnlyFiresAtItsOwnStage(t *testing.T) {
	st, r, done := newClientMgmtStub(t)
	defer done()

	var calls []result.HTTPCall
	cl := &testClient{clientID: "cid-1"}
	spec := &ClientLifecycle{Deactivate: stageAfterAuthorize}

	for _, stage := range []string{stageBeforeAuthorize, stageAfterToken} {
		if a, err := r.applyAt(context.Background(), &calls, cl, spec, stage); err != nil || a != nil {
			t.Fatalf("applyAt(%s) = %v, %v; want no-op", stage, a, err)
		}
	}
	if len(st.patches) != 0 {
		t.Fatalf("patches = %v, want none before the named stage", st.patches)
	}

	asserts, err := r.applyAt(context.Background(), &calls, cl, spec, stageAfterAuthorize)
	if err != nil {
		t.Fatalf("applyAt: %v", err)
	}
	if len(st.patches) != 1 || st.patches[0] != statusInactive {
		t.Errorf("patches = %v, want one %s", st.patches, statusInactive)
	}
	if len(asserts) != 1 || !asserts[0].Passed {
		t.Errorf("assertions = %+v, want one passing status assertion", asserts)
	}
}

// The read-back is the point of the assertion: a patch response echoing
// INACTIVE while the stored record stays ACTIVE must fail the scenario, not
// pass it. Otherwise every negative below would be testing an active client.
func TestSetClientStatusFailsWhenTheStoredStatusDisagrees(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, req *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		if req.Method == http.MethodPatch {
			_, _ = io.WriteString(w, `{"response":{"clientId":"cid-1","status":"INACTIVE"}}`)
			return
		}
		_, _ = io.WriteString(w, `{"response":{"clientId":"cid-1","status":"ACTIVE"}}`)
	}))
	defer srv.Close()

	r := &Runner{Base: srv.URL, AdminToken: "t"}
	var calls []result.HTTPCall
	asserts, err := r.setClientStatus(context.Background(), &calls, "cid-1", statusInactive)
	if err != nil {
		t.Fatalf("setClientStatus: %v", err)
	}
	if len(asserts) != 1 || asserts[0].Passed {
		t.Errorf("assertions = %+v, want the status assertion to FAIL on the read-back", asserts)
	}
}

func TestReactivateRestoresTheClient(t *testing.T) {
	st, r, done := newClientMgmtStub(t)
	defer done()

	var calls []result.HTTPCall
	cl := &testClient{clientID: "cid-1"}
	spec := &ClientLifecycle{Deactivate: stageBeforeAuthorize, Reactivate: true}

	asserts, err := r.applyAt(context.Background(), &calls, cl, spec, stageBeforeAuthorize)
	if err != nil {
		t.Fatalf("applyAt: %v", err)
	}
	if want := []string{statusInactive, statusActive}; len(st.patches) != 2 ||
		st.patches[0] != want[0] || st.patches[1] != want[1] {
		t.Errorf("patches = %v, want %v in that order", st.patches, want)
	}
	if st.status != statusActive {
		t.Errorf("final status = %s, want %s — the control must leave the client usable", st.status, statusActive)
	}
	if len(asserts) != 2 {
		t.Fatalf("assertions = %+v, want one per status write", asserts)
	}
	for _, a := range asserts {
		if !a.Passed {
			t.Errorf("assertion %+v failed", a)
		}
	}
}

// A refused status write means the scenario's premise never held. Reporting the
// flow's outcome afterwards would report a client whose status is unknown.
func TestApplyAtErrorsWhenESignetRefusesTheWrite(t *testing.T) {
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_, _ = io.WriteString(w, `{"errors":[{"errorCode":"invalid_client_id"}]}`)
	}))
	defer srv.Close()

	r := &Runner{Base: srv.URL, AdminToken: "t"}
	var calls []result.HTTPCall
	_, err := r.applyAt(context.Background(), &calls, &testClient{clientID: "gone"},
		&ClientLifecycle{Deactivate: stageBeforeAuthorize}, stageBeforeAuthorize)
	if err == nil || !strings.Contains(err.Error(), "invalid_client_id") {
		t.Errorf("applyAt error = %v, want it to name the rejection", err)
	}
}

func TestLifecycleLabel(t *testing.T) {
	for _, tc := range []struct {
		spec *ClientLifecycle
		want string
	}{
		{nil, ""},
		{&ClientLifecycle{Deactivate: stageBeforeAuthorize}, "deactivated before_authorize"},
		{&ClientLifecycle{Deactivate: stageAfterToken, Reactivate: true}, "deactivated+reactivated after_token"},
	} {
		if got := tc.spec.label(); got != tc.want {
			t.Errorf("label() = %q, want %q", got, tc.want)
		}
	}
}
