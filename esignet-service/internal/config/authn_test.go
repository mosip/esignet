package config

import (
	"testing"

	"github.com/stretchr/testify/require"
)

func TestLoadAuthn_defaults(t *testing.T) {
	t.Setenv("THUNDERID_AUTHN_PROVIDER", "")

	cfg := LoadAuthn()
	require.Equal(t, AuthnProviderCatalog, cfg.Provider)
}

func TestLoadAuthn_mosip(t *testing.T) {
	t.Setenv("THUNDERID_AUTHN_PROVIDER", AuthnProviderMosip)
	t.Setenv("MOSIP_P12_PATH", "/tmp/partner.p12")

	cfg := LoadAuthn()
	require.Equal(t, AuthnProviderMosip, cfg.Provider)
	require.Equal(t, "/tmp/partner.p12", cfg.Mosip.P12Path)
}
