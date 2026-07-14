package security

import (
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
	"time"

	"github.com/mosip/esignet/internal/common"
)

func TestRequestTimeMiddleware(t *testing.T) {
	next := http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.WriteHeader(http.StatusOK)
	})

	tests := []struct {
		name       string
		body       string
		wantStatus int
	}{
		{
			name:       "within leeway",
			body:       `{"requestTime":"` + time.Now().UTC().Format(common.MOSIPTimeLayout) + `"}`,
			wantStatus: http.StatusOK,
		},
		{
			name:       "too old",
			body:       `{"requestTime":"` + time.Now().Add(-time.Hour).UTC().Format(common.MOSIPTimeLayout) + `"}`,
			wantStatus: http.StatusBadRequest,
		},
		{
			name:       "too far in future",
			body:       `{"requestTime":"` + time.Now().Add(time.Hour).UTC().Format(common.MOSIPTimeLayout) + `"}`,
			wantStatus: http.StatusBadRequest,
		},
		{
			name:       "malformed timestamp",
			body:       `{"requestTime":"not-a-time"}`,
			wantStatus: http.StatusBadRequest,
		},
		{
			name:       "missing requestTime passes through",
			body:       `{"request":{}}`,
			wantStatus: http.StatusOK,
		},
		{
			name:       "no body passes through",
			body:       "",
			wantStatus: http.StatusOK,
		},
	}

	mw := RequestTimeMiddleware(5 * time.Minute)

	for _, tt := range tests {
		t.Run(tt.name, func(t *testing.T) {
			var body *strings.Reader
			if tt.body == "" {
				body = strings.NewReader("")
			} else {
				body = strings.NewReader(tt.body)
			}
			req := httptest.NewRequest(http.MethodPost, "/client-mgmt/client", body)
			if tt.body == "" {
				req.Body = http.NoBody
			}
			rec := httptest.NewRecorder()

			mw(next).ServeHTTP(rec, req)

			if rec.Code != tt.wantStatus {
				t.Errorf("got status %d, want %d (body: %s)", rec.Code, tt.wantStatus, rec.Body.String())
			}
		})
	}
}

func TestRequestTimeMiddleware_BodyStillReadableDownstream(t *testing.T) {
	const payload = `{"requestTime":"` + "PLACEHOLDER" + `","request":{"clientId":"c1"}}`
	body := strings.Replace(payload, "PLACEHOLDER", time.Now().UTC().Format(common.MOSIPTimeLayout), 1)

	var gotBody string
	next := http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		buf := make([]byte, len(body)+16)
		n, _ := r.Body.Read(buf)
		gotBody = string(buf[:n])
		w.WriteHeader(http.StatusOK)
	})

	mw := RequestTimeMiddleware(5 * time.Minute)
	req := httptest.NewRequest(http.MethodPost, "/client-mgmt/client", strings.NewReader(body))
	rec := httptest.NewRecorder()

	mw(next).ServeHTTP(rec, req)

	if rec.Code != http.StatusOK {
		t.Fatalf("got status %d, want 200", rec.Code)
	}
	if gotBody != body {
		t.Errorf("downstream handler saw body %q, want %q", gotBody, body)
	}
}
