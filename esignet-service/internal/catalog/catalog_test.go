package catalog_test

import (
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/mosip/esignet/internal/catalog"
)

func TestLoad(t *testing.T) {
	dataDir := filepath.Join("..", "..", "data")
	cat, err := catalog.Load(dataDir)
	require.NoError(t, err)

	user, ok := cat.FindUserByUsername("decl-user-1")
	require.True(t, ok)
	require.Equal(t, "decl-user-1", user.ID)
	require.Equal(t, "TempPassword123!", user.Password)

	app, ok := cat.ApplicationByID("decl-app-1")
	require.True(t, ok)
	require.Equal(t, "decl-public-client-1", app.ClientID)
	require.Contains(t, app.RedirectURIs, "https://localhost:3000")
	require.Equal(t, "decl-flow-1", app.AuthFlowID)

	byClient, ok := cat.ApplicationByClientID("decl-public-client-1")
	require.True(t, ok)
	require.Equal(t, app.ID, byClient.ID)

	otpApp, ok := cat.ApplicationByClientID("I6eXdnnLGGj2A2BcTL-jug_0ujpnQXlBpKAbBCkGWEM")
	require.True(t, ok)
	require.Equal(t, "private_key_jwt", otpApp.TokenEndpointAuthMethod)
	require.False(t, otpApp.PublicClient)
	require.NotNil(t, otpApp.Certificate)
	require.Equal(t, "JWKS", otpApp.Certificate.Type)
	require.Contains(t, otpApp.Certificate.Value, `"kty":"RSA"`)
}
