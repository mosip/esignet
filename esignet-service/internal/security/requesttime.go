package security

import (
	"bytes"
	"encoding/json"
	"io"
	"net/http"
	"time"

	"github.com/mosip/esignet/internal/common"
)

// RequestTimeMiddleware validates that the "requestTime" field of a JSON
// request body is within leeway of the server's current time, guarding
// against replayed or clock-skewed requests. Requests with no body, no
// requestTime field, or a body that isn't valid JSON are passed through
// unchanged — presence and shape of the field are validated downstream by
// the handler's own request decoding.
func RequestTimeMiddleware(leeway time.Duration) func(http.Handler) http.Handler {
	return func(next http.Handler) http.Handler {
		return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
			if r.Body == nil || r.Body == http.NoBody {
				next.ServeHTTP(w, r)
				return
			}

			body, err := io.ReadAll(r.Body)
			if err != nil {
				common.WriteError(w, http.StatusBadRequest, "invalid_input", "malformed request body")
				return
			}
			r.Body = io.NopCloser(bytes.NewReader(body))

			var wrapper struct {
				RequestTime string `json:"requestTime"`
			}
			if err := json.Unmarshal(body, &wrapper); err != nil || wrapper.RequestTime == "" {
				next.ServeHTTP(w, r)
				return
			}

			reqTime, err := time.Parse(common.MOSIPTimeLayout, wrapper.RequestTime)
			if err != nil {
				common.WriteError(w, http.StatusBadRequest, "invalid_input", "requestTime must match format "+common.MOSIPTimeLayout)
				return
			}
			if drift := time.Since(reqTime); drift > leeway || drift < -leeway {
				common.WriteError(w, http.StatusBadRequest, "invalid_input", "requestTime is not within the allowed leeway of server time")
				return
			}

			next.ServeHTTP(w, r)
		})
	}
}
