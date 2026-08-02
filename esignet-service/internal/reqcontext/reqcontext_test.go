/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package reqcontext

import (
	"context"
	"testing"

	"github.com/stretchr/testify/assert"
)

func TestTraceIDFromContext_DefaultsToDashWhenAbsent(t *testing.T) {
	assert.Equal(t, "-", TraceIDFromContext(context.Background()))
}

func TestTraceIDFromContext_ReturnsValueStoredByWithTraceID(t *testing.T) {
	ctx := WithTraceID(context.Background(), "abc-123")
	assert.Equal(t, "abc-123", TraceIDFromContext(ctx))
}

func TestTraceIDFromContext_DefaultsToDashWhenEmpty(t *testing.T) {
	ctx := WithTraceID(context.Background(), "")
	assert.Equal(t, "-", TraceIDFromContext(ctx))
}

func TestTraceIDFromContext_DefaultsToDashWhenNil(t *testing.T) {
	var ctx context.Context
	assert.Equal(t, "-", TraceIDFromContext(ctx))
}
