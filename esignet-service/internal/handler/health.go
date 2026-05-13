// Package handler contains HTTP handler functions for the esignet service.
package handler

import (
	"context"
	"encoding/json"
	"log/slog"
	"net/http"
	"sync"
	"time"
)

// Pinger is the minimal interface a dependency must satisfy to participate
// in the health check.  Both *db.Postgres and *cache.Redis implement it.
type Pinger interface {
	Ping(ctx context.Context) error
}

// HealthResponse is the JSON body returned by GET /health.
type HealthResponse struct {
	Status     string                     `json:"status"`
	Timestamp  string                     `json:"timestamp"`
	Components map[string]ComponentStatus `json:"components"`
}

// ComponentStatus represents the health of a single downstream dependency.
type ComponentStatus struct {
	Status  string `json:"status"`
	Message string `json:"message,omitempty"`
}

// HealthHandler returns an [http.HandlerFunc] that pings every registered
// dependency concurrently and returns:
//
//   - 200 {"status":"ok"}         – all dependencies healthy
//   - 503 {"status":"degraded"}   – one or more dependencies unhealthy
//
// Each component's individual status is included in the "components" map so
// operators can tell at a glance which dependency is misbehaving.
func HealthHandler(log *slog.Logger, pingers map[string]Pinger) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		// Budget shared across all pings.
		ctx, cancel := context.WithTimeout(r.Context(), 5*time.Second)
		defer cancel()

		type result struct {
			name string
			err  error
		}

		ch := make(chan result, len(pingers))
		var wg sync.WaitGroup

		for name, p := range pingers {
			wg.Add(1)
			go func(name string, p Pinger) {
				defer wg.Done()
				ch <- result{name: name, err: p.Ping(ctx)}
			}(name, p)
		}

		// Close channel once all goroutines finish so the range below exits.
		go func() {
			wg.Wait()
			close(ch)
		}()

		components := make(map[string]ComponentStatus, len(pingers))
		overallOK := true

		for res := range ch {
			if res.err != nil {
				log.Warn("health check failed",
					slog.String("component", res.name),
					slog.String("error", res.err.Error()),
				)
				components[res.name] = ComponentStatus{Status: "down", Message: res.err.Error()}
				overallOK = false
			} else {
				components[res.name] = ComponentStatus{Status: "up"}
			}
		}

		status := "ok"
		httpStatus := http.StatusOK
		if !overallOK {
			status = "degraded"
			httpStatus = http.StatusServiceUnavailable
		}

		resp := HealthResponse{
			Status:     status,
			Timestamp:  time.Now().UTC().Format(time.RFC3339),
			Components: components,
		}

		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(httpStatus)
		if err := json.NewEncoder(w).Encode(resp); err != nil {
			log.Error("encode health response", slog.String("error", err.Error()))
		}
	}
}
