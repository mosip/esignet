package client

import (
	"encoding/json"
	"io"
	"net/http"
	"time"

	applog "github.com/mosip/esignet/internal/log"
)

// writeEnvelope writes the envelope as JSON with HTTP 200. Errors travel
// in the body, never in the status code.
func writeEnvelope[E any](w http.ResponseWriter, log *applog.Logger, env E) {
	w.Header().Set("Content-Type", "application/json")
	w.WriteHeader(http.StatusOK)
	// Status + Content-Type are already flushed; an encode failure here
	// can't be recovered into a clean response. Log and move on.
	if err := json.NewEncoder(w).Encode(env); err != nil {
		log.Error("encode client-mgmt response", applog.Error(err))
	}
}

// utcTimestamp matches the format used elsewhere in the service: ISO 8601
// with millisecond precision and a literal Z suffix.
func utcTimestamp() string { return time.Now().UTC().Format("2006-01-02T15:04:05.000Z") }

// newCreateErrorEnvelope pairs each wire error code with its message.
func newCreateErrorEnvelope(codes ...string) createResponseEnvelope {
	entries := make([]errorEntry, 0, len(codes))
	for _, c := range codes {
		entries = append(entries, errorEntry{ErrorCode: c, ErrorMessage: messageFor(c)})
	}
	return createResponseEnvelope{ResponseTime: utcTimestamp(), Errors: entries}
}

// newCreateSuccessEnvelope builds the success envelope with an empty
// (never nil) errors array.
func newCreateSuccessEnvelope(resp *createResponse) createResponseEnvelope {
	return createResponseEnvelope{
		ResponseTime: utcTimestamp(),
		Response:     resp,
		Errors:       []errorEntry{},
	}
}

// MgmtCreate handles POST /v1/esignet/client-mgmt/client.
//
// Pipeline: cap body → decode untyped → schema-validate → decode typed →
// hand to service.
func MgmtCreate(
	svc *Service,
	val *Validator,
	log *applog.Logger,
) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		r.Body = http.MaxBytesReader(w, r.Body, maxRequestBodyBytes)

		rawBytes, err := io.ReadAll(r.Body)
		if err != nil {
			writeEnvelope(w, log, newCreateErrorEnvelope(errInvalidInput))
			return
		}

		var rawDoc any
		if err := json.Unmarshal(rawBytes, &rawDoc); err != nil {
			writeEnvelope(w, log, newCreateErrorEnvelope(errInvalidInput))
			return
		}

		if codes := val.ValidateCreate(rawDoc); len(codes) > 0 {
			writeEnvelope(w, log, newCreateErrorEnvelope(codes...))
			return
		}

		var env createRequestEnvelope
		if err := json.Unmarshal(rawBytes, &env); err != nil {
			// Unreachable in practice — schema validation passed.
			writeEnvelope(w, log, newCreateErrorEnvelope(errInvalidInput))
			return
		}

		resp, errs := svc.create(r.Context(), &env.Request)
		if len(errs) > 0 {
			writeEnvelope(w, log, newCreateErrorEnvelope(errs...))
			return
		}
		writeEnvelope(w, log, newCreateSuccessEnvelope(resp))
	}
}
