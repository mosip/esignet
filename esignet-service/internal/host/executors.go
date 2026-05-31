package host

import (
	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"

	"github.com/mosip/esignet/internal/config"
)

// CustomExecutorDeps holds host dependencies needed to construct custom executors.
type CustomExecutorDeps struct {
	Authn    host.AuthnProvider
	AuthnCfg config.Authn
}

// RegisterCustomExecutors registers all host-provided flow executors.
// Add new executors here; keep each executor's logic in its own file.
func RegisterCustomExecutors(reg thunderidengine.ExecutorRegistry, factory thunderidengine.FlowFactory,
	deps CustomExecutorDeps) error {
	if err := registerMosipOtpExecutor(reg, factory, deps); err != nil {
		return err
	}
	return nil
}
