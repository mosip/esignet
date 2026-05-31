package host

import (
	"os"
	"testing"

	"github.com/stretchr/testify/require"
)

func TestLoadRSAPrivateKeyAndCertFromP12_modernMAC(t *testing.T) {
	pfx := "../../bec4ca0b_50c5_4ed5_b6f9_53e4609e08fa.pfx"
	if _, err := os.Stat(pfx); err != nil {
		t.Skip("fixture .pfx not present:", pfx)
	}

	key, cert, err := LoadRSAPrivateKeyAndCertFromP12(pfx, "localtest")
	require.NoError(t, err)
	require.NotNil(t, key)
	require.NotNil(t, cert)
}
