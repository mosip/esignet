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
	"github.com/mosip/esignet/internal/config"
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

	engineCfg := config.Engine{
		Issuer:         "http://127.0.0.1:8080",
		DataDir:        dataDir,
		SigningKeyPath: signingKey,
		GateClient: config.EngineGateClient{
			Scheme:    "http",
			Hostname:  "127.0.0.1",
			Port:      8080,
			LoginPath: "/signin",
			ErrorPath: "/error",
		},
	}

	mux := http.NewServeMux()
	thunderCfg := engineCfg.ThunderEngineConfig()
	thunderCfg.Runtime = runtime.NewMemoryRuntimeStore()
	thunderCfg.Actors = embedhost.NewActorProvider(cat)
	thunderCfg.Authn = embedhost.NewAuthnProvider(cat)
	thunderCfg.Authorization = embedhost.NewAuthorizationProvider()
	thunderCfg.Consent = embedhost.NewConsentEnforcer()

	_, err = thunderidengine.Initialize(mux, thunderCfg)
	require.NoError(t, err)

	req := httptest.NewRequest(http.MethodGet, "/.well-known/openid-configuration", nil)
	rec := httptest.NewRecorder()
	mux.ServeHTTP(rec, req)

	require.Equal(t, http.StatusOK, rec.Code)
	require.Contains(t, rec.Body.String(), "authorization_endpoint")
	require.Contains(t, rec.Body.String(), "token_endpoint")
}
