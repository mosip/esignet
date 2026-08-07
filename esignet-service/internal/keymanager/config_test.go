package keymanager_test

import (
	"testing"
	"time"

	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/keymanager"
)

type KeymanagerTestSuite struct {
	suite.Suite
}

func TestKeymanagerTestSuite(t *testing.T) {
	suite.Run(t, new(KeymanagerTestSuite))
}

func (ts *KeymanagerTestSuite) TestLoadConfig_SymmetricKeyValidityDefaultsToFiveYears() {
	t := ts.T()
	t.Setenv("KEYMANAGER_SYMMETRIC_KEY_VALIDITY_DAYS", "") // force the fallback path regardless of ambient env
	cfg := keymanager.LoadConfig()
	ts.Assert().Equal(1825*24*time.Hour, cfg.SymmetricKeyValidity, "default symmetric key validity must be 5 years (1825 days), not the old 2-year (730-day) default")
}

func (ts *KeymanagerTestSuite) TestLoadConfig_SymmetricKeyAllowedRefIDs_ParsedFromCommaSeparatedEnv() {
	t := ts.T()
	t.Setenv("KEYMANAGER_SYMMETRIC_KEY_ALLOWED_REF_IDS", " ZK_ENCRYPT, VID_ENCRYPT ,,")
	cfg := keymanager.LoadConfig()
	ts.Assert().Equal([]string{"ZK_ENCRYPT", "VID_ENCRYPT"}, cfg.SymmetricKeyAllowedRefIDs,
		"entries must be trimmed and empty entries from stray/trailing commas dropped")
}

func (ts *KeymanagerTestSuite) TestLoadConfig_SymmetricKeyAllowedRefIDs_DefaultsToCacheEncrypt() {
	t := ts.T()
	t.Setenv("KEYMANAGER_SYMMETRIC_KEY_ALLOWED_REF_IDS", "") // force the fallback path regardless of ambient env
	cfg := keymanager.LoadConfig()
	ts.Assert().Equal([]string{"CACHE_ENCRYPT"}, cfg.SymmetricKeyAllowedRefIDs,
		"unset allow-list must default to CACHE_ENCRYPT — esignet's own runtime crypto provider "+
			"(internal/engine/runtime_crypto_provider.go's defaultSymmetricEncryptReferenceID) generates a "+
			"CACHE_ENCRYPT symmetric key out of the box and needs it pre-allowed, not an all-empty "+
			"allow-list requiring extra operator configuration just to boot")
}

func (ts *KeymanagerTestSuite) TestLoadConfig_ForeignDomainAllowedAppIDs_DefaultsToPartnerAndIDA() {
	t := ts.T()
	t.Setenv("KEYMANAGER_FOREIGN_DOMAIN_ALLOWED_APP_IDS", "") // force the fallback path regardless of ambient env
	cfg := keymanager.LoadConfig()
	ts.Assert().Equal([]string{"PARTNER", "IDA"}, cfg.ForeignDomainAllowedAppIDs,
		"unlike SymmetricKeyAllowedRefIDs, this list has a real default rather than requiring explicit configuration")
}

func (ts *KeymanagerTestSuite) TestLoadConfig_ForeignDomainAllowedAppIDs_ParsedFromCommaSeparatedEnv() {
	t := ts.T()
	t.Setenv("KEYMANAGER_FOREIGN_DOMAIN_ALLOWED_APP_IDS", " PARTNER, IDA ,FOO,")
	cfg := keymanager.LoadConfig()
	ts.Assert().Equal([]string{"PARTNER", "IDA", "FOO"}, cfg.ForeignDomainAllowedAppIDs,
		"entries must be trimmed and empty entries from stray/trailing commas dropped, same as SymmetricKeyAllowedRefIDs")
}

func (ts *KeymanagerTestSuite) TestLoadConfig_CertificateAllowedRefIDs_DefaultsToEmpty() {
	t := ts.T()
	t.Setenv("KEYMANAGER_CERTIFICATE_ALLOWED_REF_IDS", "") // force the fallback path regardless of ambient env
	cfg := keymanager.LoadConfig()
	ts.Assert().Empty(cfg.CertificateAllowedRefIDs,
		"unlike ForeignDomainAllowedAppIDs, an unset allow-list must permit no Component Encryption Key "+
			"reference id on the getCertificate HTTP read path — it must be configured explicitly")
}

func (ts *KeymanagerTestSuite) TestLoadConfig_CertificateAllowedRefIDs_ParsedFromCommaSeparatedEnv() {
	t := ts.T()
	t.Setenv("KEYMANAGER_CERTIFICATE_ALLOWED_REF_IDS", " ZK_ENCRYPT, VID_ENCRYPT ,,")
	cfg := keymanager.LoadConfig()
	ts.Assert().Equal([]string{"ZK_ENCRYPT", "VID_ENCRYPT"}, cfg.CertificateAllowedRefIDs,
		"entries must be trimmed and empty entries from stray/trailing commas dropped, same as SymmetricKeyAllowedRefIDs")
}
