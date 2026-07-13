/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package shared

import (
	"context"
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"
)

func (ts *NoopAuditorTestSuite) TestNoopAuditorEnabled() {
	t := ts.T()
	if !NewNoopAuditor().IsEnabled() {
		t.Error("expected enabled")
	}
}

func (ts *NoopAuditorTestSuite) TestNoopAuditorPublishIsSafe() {
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

type NoopAuditorTestSuite struct {
	suite.Suite
}

func TestNoopAuditorTestSuite(t *testing.T) {
	suite.Run(t, new(NoopAuditorTestSuite))
}
