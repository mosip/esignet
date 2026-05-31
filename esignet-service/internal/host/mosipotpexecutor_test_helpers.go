package host

import (
	"testing"

	"github.com/thunder-id/thunderid/pkg/thunderidengine"
)

func newTestExecutorRegistry(t *testing.T) thunderidengine.ExecutorRegistry {
	t.Helper()
	return thunderidengine.NewEmptyExecutorRegistry()
}

func testFlowFactory(t *testing.T) thunderidengine.FlowFactory {
	t.Helper()
	return thunderidengine.NewDefaultFlowFactory()
}
