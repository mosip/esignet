package host

import (
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/catalog"
	"github.com/mosip/esignet/internal/config"
)

func TestNewAuthnProviderFromConfig_catalog(t *testing.T) {
	cat := &catalog.Catalog{}
	provider, err := NewAuthnProviderFromConfig(config.Authn{Provider: config.AuthnProviderCatalog}, cat)
	require.NoError(t, err)
	require.NotNil(t, provider)
}

func TestNewAuthnProviderFromConfig_mosip(t *testing.T) {
	cat := &catalog.Catalog{}
	provider, err := NewAuthnProviderFromConfig(config.Authn{Provider: config.AuthnProviderMosip}, cat)
	require.NoError(t, err)
	require.NotNil(t, provider)
}

func TestNewAuthnProviderFromConfig_unknown(t *testing.T) {
	cat := &catalog.Catalog{}
	provider, err := NewAuthnProviderFromConfig(config.Authn{Provider: "unknown"}, cat)
	require.Error(t, err)
	require.Nil(t, provider)
	require.Contains(t, err.Error(), "unsupported authn provider")
}
