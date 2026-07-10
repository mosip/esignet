// Package shared provides common types and errors for authentication providers.
//
// noopAuditor is a logging-based ThunderID observability provider — an
// alternative to the mosip auditor that logs flow event details via the
// application logger with no external dependencies.
package shared

import (
	"context"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	applog "github.com/mosip/esignet/internal/log"
)

// noopAuditor logs flow event details instead of forwarding them to an
// external audit store.
type noopAuditor struct {
	log *applog.Logger
}

// NewNoopAuditor builds a logging observability provider. It has no external
// dependencies.
func NewNoopAuditor() providers.ObservabilityProvider {
	return &noopAuditor{log: applog.GetLogger()}
}

// IsEnabled reports whether event logging is active.
func (p *noopAuditor) IsEnabled() bool {
	return true
}

// PublishEvent logs the event details at info level.
func (p *noopAuditor) PublishEvent(_ context.Context, evt *providers.Event) {
	if evt == nil {
		return
	}
	p.log.Info("observability event",
		applog.String("event_id", evt.EventID),
		applog.String("event_type", evt.Type),
		applog.String("status", evt.Status),
		applog.String("component", evt.Component),
		applog.String("trace_id", evt.TraceID),
		applog.Any("timestamp", evt.Timestamp),
		applog.Any("data", evt.Data),
	)
}
