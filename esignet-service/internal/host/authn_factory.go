package host

import (
	"fmt"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/host"

	"github.com/mosip/esignet/internal/catalog"
	"github.com/mosip/esignet/internal/config"
)

// NewAuthnProviderFromConfig creates a host.AuthnProvider based on configuration.
func NewAuthnProviderFromConfig(cfg config.Authn, cat *catalog.Catalog) (host.AuthnProvider, error) {
	switch cfg.Provider {
	case config.AuthnProviderCatalog:
		return NewAuthnProvider(cat), nil
	case config.AuthnProviderMosip:
		return NewMosipAuthnProvider(cfg.Mosip), nil
	case config.AuthnProviderSunbird:
		return NewSunbirdAuthnProvider(cfg.Sunbird)
	default:
		return nil, fmt.Errorf("unsupported authn provider %q", cfg.Provider)
	}
}
