package handler

import (
	"context"
	"net/http"

	"github.com/mosip/esignet/pkg/respond"
)

// Pinger tests connectivity to a backing service.
type Pinger interface {
	Ping(ctx context.Context) error
}

type healthResponse struct {
	Status string `json:"status"`
}

// Health returns a handler that reports service health by pinging
// the given backing service.
func Health(p Pinger) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		if err := p.Ping(r.Context()); err != nil {
			respond.Error(w, http.StatusServiceUnavailable, "database unavailable")
			return
		}

		respond.JSON(w, http.StatusOK, healthResponse{Status: "ok"})
	}
}
