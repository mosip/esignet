package keymanager

import (
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

const (
	defaultKeyCacheExpireMins = 1440
	// defaultSymmetricKeyValidityDays is 5 years (365*5) — was 2 years (730)
	// until you asked for it to be 5.
	defaultSymmetricKeyValidityDays = 1825
	defaultAsymmetricKeyLength      = 2048
	defaultDBSchema                 = "keymgr"
	defaultDBSSLMode                = "disable"

	defaultCertCommonName       = "www.mosip.io"
	defaultCertOrganizationUnit = "thunder-tech-team"
	defaultCertOrganization     = "IIITB"
	defaultCertLocation         = "Bangalore"
	defaultCertState            = "KA"
	defaultCertCountry          = "IN"

	// defaultForeignDomainAllowedAppIDs is the out-of-the-box allow-list for
	// UploadOtherDomainCertificate's ApplicationID — PARTNER and IDA are the
	// two foreign domains known at the time this check was added; unlike
	// SymmetricKeyAllowedRefIDs, this has a non-empty default rather than
	// requiring explicit configuration before anything works.
	defaultForeignDomainAllowedAppIDs = "PARTNER,IDA"
)

// Config holds settings for the keymanager service — DB connection,
// keystore backend selection/parameters, algorithm sizing, and cache TTLs.
// Env-var driven, mirroring internal/clientmgmt's LoadConfig() convention.
type Config struct {
	// --- Database ---

	// DatabaseURL, if set, is used as the full Postgres DSN as-is and
	// overrides every other DB* field below.
	// Env: KEYMANAGER_DATABASE_URL
	DatabaseURL string

	// DBHost/DBPort/DBName/DBUser/DBPassword/DBSSLMode build the DSN when
	// DatabaseURL is unset.
	// Env: KEYMANAGER_DB_HOST      — default "localhost"
	// Env: KEYMANAGER_DB_PORT      — default "5432"
	// Env: KEYMANAGER_DB_NAME      — default "mosip_keymgr"
	// Env: KEYMANAGER_DB_USER      — default "postgres"
	// Env: KEYMANAGER_DB_PASSWORD  — no default; required unless DatabaseURL is set
	// Env: KEYMANAGER_DB_SSLMODE   — default "disable"
	DBHost     string
	DBPort     string
	DBName     string
	DBUser     string
	DBPassword string
	DBSSLMode  string

	// DBSchema is the Postgres schema holding key_alias/key_policy_def/key_store.
	// The Java service uses "keymgr"; point this at a separate schema (e.g.
	// "keymgr_go") to test this library without touching the Java service's data.
	// Env: KEYMANAGER_DB_SCHEMA — default "keymgr"
	DBSchema string

	// --- Keystore ---

	// KeystoreType selects the keystore.KeyStore backend: "PKCS11" or "PKCS12".
	// Env: KEYMANAGER_KEYSTORE_TYPE — default "PKCS11"
	KeystoreType string

	// KeystoreParams is passed directly to keystore.New. Populated from the
	// PKCS#11/PKCS#12-specific env vars below. Neither backend has a
	// built-in default path/password — both must be set explicitly.
	KeystoreParams map[string]string

	// --- Certificate subject defaults ---

	// CertCommonName/CertOrganizationUnit/CertOrganization/CertLocation/
	// CertState/CertCountry are the default certificate subject (DN) fields
	// used by GenerateMasterKey when its request doesn't supply one (ROOT
	// and Component Master Key certificates only — GetCertificate/
	// GenerateCSR always derive DN structurally, never from these, see
	// service.go's ensureCurrentKey). CertCommonName additionally gets the
	// application id appended in braces, e.g. "www.mosip.io (ROOT)" for
	// ROOT, "www.mosip.io (ESIGNET_RSA)" for a Component Master Key or EC
	// sign key, "www.mosip.io (ENC)" for a Component Encryption Key — see
	// certificateSubject in service.go.
	// Env: KEYMANAGER_CERT_CN — default "www.mosip.io"
	// Env: KEYMANAGER_CERT_OU — default "thunder-tech-team"
	// Env: KEYMANAGER_CERT_O  — default "IIITB"
	// Env: KEYMANAGER_CERT_L  — default "Bangalore"
	// Env: KEYMANAGER_CERT_ST — default "KA"
	// Env: KEYMANAGER_CERT_C  — default "IN"
	CertCommonName       string
	CertOrganizationUnit string
	CertOrganization     string
	CertLocation         string
	CertState            string
	CertCountry          string

	// --- Algorithm / cache tuning ---

	// AsymmetricKeyLength is the RSA modulus size in bits.
	// Env: KEYMANAGER_ASYMMETRIC_KEY_LENGTH — default 2048
	AsymmetricKeyLength int

	// KeyCacheExpiry controls how long a "current key" lookup is cached
	// before being re-checked against the DB.
	// Env: KEYMANAGER_KEY_CACHE_EXPIRE_MINS — default 1440 (24h)
	KeyCacheExpiry time.Duration

	// SymmetricKeyValidity is the fixed validity window for symmetric
	// (AES) keys — Java's SYMMETRIC_KEY_VALIDITY constant, though the
	// default here is 5 years, not Java's 2.
	// Env: KEYMANAGER_SYMMETRIC_KEY_VALIDITY_DAYS — default 1825 (5 years)
	SymmetricKeyValidity time.Duration

	// SymmetricKeyAllowedRefIDs lists the reference ids permitted for
	// GenerateSymmetricKey (see validateSymmetricKeyRefID in validate.go) —
	// any ReferenceID not in this list is rejected with
	// ErrSymmetricKeyRefIDNotAllowed. There is no natural registry for
	// symmetric key reference ids (unlike ApplicationID, checked against
	// key_policy_def), so this is config-driven; an empty/unset list
	// allows nothing, requiring explicit configuration.
	// Env: KEYMANAGER_SYMMETRIC_KEY_ALLOWED_REF_IDS — comma-separated, e.g. "ZK_ENCRYPT,VID_ENCRYPT"
	SymmetricKeyAllowedRefIDs []string

	// ForeignDomainAllowedAppIDs lists the ApplicationIDs permitted for
	// UploadOtherDomainCertificate (see validateForeignDomainAppID in
	// validate.go) — a foreign-domain, cert-only upload's ApplicationID must
	// both appear in this list AND have no row of its own in key_policy_def
	// (it must never be confusable with one of MOSIP's own key-hierarchy
	// applications). Unlike SymmetricKeyAllowedRefIDs, this has a real
	// default (PARTNER, IDA) rather than requiring explicit configuration.
	// Env: KEYMANAGER_FOREIGN_DOMAIN_ALLOWED_APP_IDS — comma-separated, default "PARTNER,IDA"
	ForeignDomainAllowedAppIDs []string
}

// LoadConfig reads keymanager service settings from the environment.
func LoadConfig() Config {
	return Config{
		DatabaseURL: os.Getenv("KEYMANAGER_DATABASE_URL"),
		DBHost:      envOrDefault("KEYMANAGER_DB_HOST", "localhost"),
		DBPort:      envOrDefault("KEYMANAGER_DB_PORT", "5432"),
		DBName:      envOrDefault("KEYMANAGER_DB_NAME", "mosip_keymgr"),
		DBUser:      envOrDefault("KEYMANAGER_DB_USER", "postgres"),
		DBPassword:  os.Getenv("KEYMANAGER_DB_PASSWORD"),
		DBSSLMode:   envOrDefault("KEYMANAGER_DB_SSLMODE", defaultDBSSLMode),
		DBSchema:    envOrDefault("KEYMANAGER_DB_SCHEMA", defaultDBSchema),

		KeystoreType: envOrDefault("KEYMANAGER_KEYSTORE_TYPE", "PKCS11"),
		KeystoreParams: map[string]string{
			// PKCS#11
			"module-path":                os.Getenv("KEYMANAGER_PKCS11_MODULE_PATH"),
			"token-label":                os.Getenv("KEYMANAGER_PKCS11_TOKEN_LABEL"),
			"slot-id":                    os.Getenv("KEYMANAGER_PKCS11_SLOT_ID"),
			"pin":                        os.Getenv("KEYMANAGER_PKCS11_PIN"),
			"enable-key-reference-cache": envOrDefault("KEYMANAGER_PKCS11_ENABLE_KEY_REFERENCE_CACHE", "true"),
			// PKCS#12
			"config-path":   os.Getenv("KEYMANAGER_PKCS12_FILE_PATH"),
			"keystore-pass": os.Getenv("KEYMANAGER_PKCS12_PASSWORD"),
		},

		CertCommonName:       envOrDefault("KEYMANAGER_CERT_CN", defaultCertCommonName),
		CertOrganizationUnit: envOrDefault("KEYMANAGER_CERT_OU", defaultCertOrganizationUnit),
		CertOrganization:     envOrDefault("KEYMANAGER_CERT_O", defaultCertOrganization),
		CertLocation:         envOrDefault("KEYMANAGER_CERT_L", defaultCertLocation),
		CertState:            envOrDefault("KEYMANAGER_CERT_ST", defaultCertState),
		CertCountry:          envOrDefault("KEYMANAGER_CERT_C", defaultCertCountry),

		AsymmetricKeyLength:       envIntOrDefault("KEYMANAGER_ASYMMETRIC_KEY_LENGTH", defaultAsymmetricKeyLength),
		KeyCacheExpiry:            time.Duration(envIntOrDefault("KEYMANAGER_KEY_CACHE_EXPIRE_MINS", defaultKeyCacheExpireMins)) * time.Minute,
		SymmetricKeyValidity:      time.Duration(envIntOrDefault("KEYMANAGER_SYMMETRIC_KEY_VALIDITY_DAYS", defaultSymmetricKeyValidityDays)) * 24 * time.Hour,
		SymmetricKeyAllowedRefIDs: splitAndTrim(os.Getenv("KEYMANAGER_SYMMETRIC_KEY_ALLOWED_REF_IDS")),

		ForeignDomainAllowedAppIDs: splitAndTrim(envOrDefault("KEYMANAGER_FOREIGN_DOMAIN_ALLOWED_APP_IDS", defaultForeignDomainAllowedAppIDs)),
	}
}

// DSN builds the Postgres connection string, honoring DatabaseURL as a
// full override when set.
func (c Config) DSN() string {
	if c.DatabaseURL != "" {
		return c.DatabaseURL
	}
	return fmt.Sprintf(
		"host=%s port=%s dbname=%s user=%s password=%s sslmode=%s",
		c.DBHost, c.DBPort, c.DBName, c.DBUser, c.DBPassword, c.DBSSLMode,
	)
}

func envOrDefault(key, fallback string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return fallback
}

func envIntOrDefault(key string, fallback int) int {
	raw := os.Getenv(key)
	if raw == "" {
		return fallback
	}
	n, err := strconv.Atoi(raw)
	if err != nil {
		return fallback
	}
	return n
}

// splitAndTrim splits a comma-separated env var value into a trimmed,
// non-empty slice — "" (unset) yields nil, and "A, B,,C" yields
// ["A", "B", "C"] (empty entries from stray/trailing commas are dropped).
func splitAndTrim(s string) []string {
	if s == "" {
		return nil
	}
	parts := strings.Split(s, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
