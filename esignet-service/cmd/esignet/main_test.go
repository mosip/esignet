package main_test

import (
	"net/http"
	"net/http/httptest"
	"os"
	"path/filepath"
	"testing"

	"github.com/stretchr/testify/require"
	"github.com/thunder-id/thunderid/pkg/thunderidengine"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/runtime"

	"github.com/mosip/esignet/internal/catalog"
	embedhost "github.com/mosip/esignet/internal/host"
)

func TestDiscoverySmoke(t *testing.T) {
	root, err := os.Getwd()
	require.NoError(t, err)
	dataDir := filepath.Join(root, "..", "..", "data")
	signingKey := filepath.Join(root, "..", "..", "keys", "signing.key")
	if _, err := os.Stat(signingKey); err != nil {
		t.Skip("run make keys before esignet smoke test")
	}

	cat, err := catalog.Load(dataDir)
	require.NoError(t, err)

	mux := http.NewServeMux()
	_, err = thunderidengine.Initialize(mux, thunderidengine.EngineConfig{
		Issuer:  "http://127.0.0.1:8080",
		DataDir: dataDir,
		Runtime: runtime.NewMemoryRuntimeStore(),
		Crypto: thunderidengine.CryptoConfig{
			SigningKeyPath: signingKey,
		},
		FlowStore: thunderidengine.FlowProviderConfig{
			StoreMode: thunderidengine.StoreModeDeclarative,
		},
		Flow: thunderidengine.FlowConfig{
			Executors: []string{
				"BasicAuthExecutor",
				"AuthorizationExecutor",
				"AuthAssertExecutor",
				"ConsentExecutor",
			},
		},
		Actors:        embedhost.NewActorProvider(cat),
		Authn:         embedhost.NewAuthnProvider(cat),
		Authorization: embedhost.NewAuthorizationProvider(),
		Consent:       embedhost.NewConsentEnforcer(),
	})
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodGet, "/.well-known/openid-configuration", nil)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	require.Equal(t, http.StatusOK, rec.Code)
	require.Contains(t, rec.Body.String(), "authorization_endpoint")
	require.Contains(t, rec.Body.String(), "token_endpoint")
}
