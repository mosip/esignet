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
}
