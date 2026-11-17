package mds

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/mosip/esignet/api-test/internal/esignet"
)

// Same derivation as esignet-service's shared.DeriveAuthTransactionID and oidc-ui's deriveAuthTransactionId.
func TestDeriveTransactionID(t *testing.T) {
	cases := map[string]string{
		"abc-def_12":                           "21fedcba21",
		"1234567890abcdef":                     "fedcba0987",
		"0d4c3c2a-6f1e-4b9a-8c7d-5e6f7a8b9c0d": "d0c9b8a7f6",
	}
	for in, want := range cases {
		got, err := DeriveTransactionID(in, AuthTransactionIDLength)
		if err != nil || got != want {
			t.Errorf("DeriveTransactionID(%q) = %q, %v; want %q", in, got, err, want)
		}
	}
	if _, err := DeriveTransactionID("-_-", 10); err == nil {
		t.Error("an execution id of only separators must be an error")
	}
}

type fakeMDS struct {
	profiles []string
	captures []map[string]any
	errCode  string
}

func (f *fakeMDS) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	raw, _ := io.ReadAll(r.Body)
	var req map[string]any
	_ = json.Unmarshal(raw, &req)
	switch {
	case r.Method == "MOSIPDISC" && r.URL.Path == "/device":
		_, _ = io.WriteString(w, `[{"deviceId":"4","deviceStatus":"Ready","specVersion":["0.9.5"],"deviceSubId":["0"]}]`)
	case r.Method == http.MethodPost && r.URL.Path == "/admin/profile":
		f.profiles = append(f.profiles, req["profileId"].(string))
		_, _ = io.WriteString(w, `{"errorCode":"0"}`)
	case r.Method == "CAPTURE" && r.URL.Path == "/capture":
		f.captures = append(f.captures, req)
		code := "0"
		if f.errCode != "" {
			code = f.errCode
		}
		_, _ = io.WriteString(w, `{"biometrics":[{"specVersion":"0.9.5","data":"eyJhbGciOiJSUzI1NiJ9.eyJiaW9UeXBlIjoiRmluZ2VyIn0.c2ln","hash":"h","sessionKey":"k","thumbprint":"t","error":{"errorCode":"`+code+`","errorInfo":"x"}}]}`)
	default:
		http.Error(w, "unexpected "+r.Method+" "+r.URL.Path, http.StatusBadRequest)
	}
}

func decodeRecords(t *testing.T, v string) []map[string]any {
	t.Helper()
	raw, err := base64.StdEncoding.DecodeString(v)
	if err != nil {
		t.Fatalf("capture is not standard base64: %v", err)
	}
	var recs []map[string]any
	if err := json.Unmarshal(raw, &recs); err != nil {
		t.Fatalf("capture is not a JSON array: %v", err)
	}
	return recs
}

func TestCaptureMatchesTheUIEncoding(t *testing.T) {
	f := &fakeMDS{}
	srv := httptest.NewServer(f)
	defer srv.Close()
	c := New(srv.URL, "Staging", "https://esignet.example.org")

	v, calls, err := c.Capture(context.Background(), "abc-def_12", esignet.CaptureSpec{})
	if err != nil {
		t.Fatalf("capture: %v", err)
	}
	recs := decodeRecords(t, v)
	if len(recs) != 1 || recs[0]["hash"] != "h" {
		t.Fatalf("records not passed through: %v", recs)
	}
	if _, has := recs[0]["error"]; has {
		t.Error("the error member must be removed, as the UI does")
	}
	req := f.captures[0]
	if req["transactionId"] != "21fedcba21" || req["env"] != "Staging" || req["domainUri"] != "https://esignet.example.org" || req["purpose"] != "Auth" {
		t.Errorf("capture request not bound to the execution and target: %v", req)
	}
	bio := req["bio"].([]any)[0].(map[string]any)
	if bio["type"] != "Finger" || bio["deviceId"] != "4" {
		t.Errorf("capture must default to the discovered Finger device: %v", bio)
	}
	if len(f.profiles) != 0 {
		t.Errorf("the default profile must not be switched: %v", f.profiles)
	}
	if len(calls) != 2 {
		t.Errorf("want discover + capture in the trace, got %d calls", len(calls))
	}

	// Discovery is cached per modality.
	if _, calls, _ := c.Capture(context.Background(), "e", esignet.CaptureSpec{}); len(calls) != 1 {
		t.Errorf("discovery should run once per modality, got %d calls", len(calls))
	}
}

func TestCaptureFromAnotherProfileRestoresDefault(t *testing.T) {
	f := &fakeMDS{}
	srv := httptest.NewServer(f)
	defer srv.Close()
	c := New(srv.URL, "Staging", "https://x")
	if _, _, err := c.Capture(context.Background(), "e", esignet.CaptureSpec{Profile: "Automatic"}); err != nil {
		t.Fatalf("capture: %v", err)
	}
	if strings.Join(f.profiles, ",") != "Automatic,Default" {
		t.Fatalf("profile must be switched for the capture and back after: %v", f.profiles)
	}
}

func TestCaptureReportsAnMDSError(t *testing.T) {
	srv := httptest.NewServer(&fakeMDS{errCode: "801"})
	defer srv.Close()
	_, _, err := New(srv.URL, "Staging", "https://x").Capture(context.Background(), "e", esignet.CaptureSpec{})
	if err == nil || !strings.Contains(err.Error(), "801") {
		t.Fatalf("want the MDS error code surfaced, got %v", err)
	}
}

func TestTamperChangesOnlyThePayload(t *testing.T) {
	srv := httptest.NewServer(&fakeMDS{})
	defer srv.Close()
	c := New(srv.URL, "Staging", "https://x")
	plain, _, _ := c.Capture(context.Background(), "e", esignet.CaptureSpec{})
	altered, _, err := c.Capture(context.Background(), "e", esignet.CaptureSpec{Tamper: true})
	if err != nil {
		t.Fatalf("tampered capture: %v", err)
	}
	a := strings.Split(decodeRecords(t, plain)[0]["data"].(string), ".")
	b := strings.Split(decodeRecords(t, altered)[0]["data"].(string), ".")
	if a[0] != b[0] || a[2] != b[2] || a[1] == b[1] || len(a[1]) != len(b[1]) {
		t.Fatalf("tamper must alter only the payload segment:\n%v\n%v", a, b)
	}
}
