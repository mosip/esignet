package keymanager_test

import (
	"testing"
	"time"

	"github.com/stretchr/testify/assert"

	"github.com/mosip/esignet/internal/keymanager"
)

func TestLoadConfig_SymmetricKeyValidityDefaultsToFiveYears(t *testing.T) {
	t.Setenv("KEYMANAGER_SYMMETRIC_KEY_VALIDITY_DAYS", "") // force the fallback path regardless of ambient env
	cfg := keymanager.LoadConfig()
	assert.Equal(t, 1825*24*time.Hour, cfg.SymmetricKeyValidity, "default symmetric key validity must be 5 years (1825 days), not the old 2-year (730-day) default")
}

func TestLoadConfig_SymmetricKeyAllowedRefIDs_ParsedFromCommaSeparatedEnv(t *testing.T) {
	t.Setenv("KEYMANAGER_SYMMETRIC_KEY_ALLOWED_REF_IDS", " ZK_ENCRYPT, VID_ENCRYPT ,,")
	cfg := keymanager.LoadConfig()
	assert.Equal(t, []string{"ZK_ENCRYPT", "VID_ENCRYPT"}, cfg.SymmetricKeyAllowedRefIDs,
		"entries must be trimmed and empty entries from stray/trailing commas dropped")
}

func TestLoadConfig_SymmetricKeyAllowedRefIDs_DefaultsToCacheEncrypt(t *testing.T) {
	t.Setenv("KEYMANAGER_SYMMETRIC_KEY_ALLOWED_REF_IDS", "") // force the fallback path regardless of ambient env
	cfg := keymanager.LoadConfig()
	assert.Equal(t, []string{"CACHE_ENCRYPT"}, cfg.SymmetricKeyAllowedRefIDs,
		"unset allow-list must default to CACHE_ENCRYPT — esignet's own runtime crypto provider "+
			"(internal/engine/runtime_crypto_provider.go's defaultSymmetricEncryptReferenceID) generates a "+
			"CACHE_ENCRYPT symmetric key out of the box and needs it pre-allowed, not an all-empty "+
			"allow-list requiring extra operator configuration just to boot")
}

func TestLoadConfig_ForeignDomainAllowedAppIDs_DefaultsToPartnerAndIDA(t *testing.T) {
	t.Setenv("KEYMANAGER_FOREIGN_DOMAIN_ALLOWED_APP_IDS", "") // force the fallback path regardless of ambient env
	cfg := keymanager.LoadConfig()
	assert.Equal(t, []string{"PARTNER", "IDA"}, cfg.ForeignDomainAllowedAppIDs,
		"unlike SymmetricKeyAllowedRefIDs, this list has a real default rather than requiring explicit configuration")
}

func TestLoadConfig_ForeignDomainAllowedAppIDs_ParsedFromCommaSeparatedEnv(t *testing.T) {
	t.Setenv("KEYMANAGER_FOREIGN_DOMAIN_ALLOWED_APP_IDS", " PARTNER, IDA ,FOO,")
	cfg := keymanager.LoadConfig()
	assert.Equal(t, []string{"PARTNER", "IDA", "FOO"}, cfg.ForeignDomainAllowedAppIDs,
		"entries must be trimmed and empty entries from stray/trailing commas dropped, same as SymmetricKeyAllowedRefIDs")
}
