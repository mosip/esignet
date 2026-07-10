package shared

import (
	"context"
	"testing"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

func TestNoopAuditorEnabled(t *testing.T) {
	if !NewNoopAuditor().IsEnabled() {
		t.Error("expected enabled")
	}
}

func TestNoopAuditorPublishIsSafe(_ *testing.T) {
	p := NewNoopAuditor()
	// Should not panic on a populated event or a nil event.
	p.PublishEvent(context.Background(), &providers.Event{
		Type:      "FLOW_COMPLETED",
		Status:    providers.StatusSuccess,
		Component: "FlowEngine",
		Timestamp: time.Now(),
		Data:      map[string]interface{}{"user_id": "u1"},
	})
	p.PublishEvent(context.Background(), nil)
}
