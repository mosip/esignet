// Package middleware provides HTTP middleware for request logging,
// panic recovery, and request ID propagation.
package middleware

import (
	"crypto/rand"
	"encoding/hex"
	"net/http"

	"github.com/mosip/esignet/pkg/log"
)

const headerRequestID = "X-Request-ID"

// RequestID reads or generates a request ID, stores it in the request
// context for downstream logging, and sets it on the response.
func RequestID(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		id := r.Header.Get(headerRequestID)
		if id == "" {
			id = generateID()
		}

		ctx := log.WithRequestID(r.Context(), id)
		w.Header().Set(headerRequestID, id)

		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

func generateID() string {
	b := make([]byte, 8)
	_, _ = rand.Read(b)

	return hex.EncodeToString(b)
}
