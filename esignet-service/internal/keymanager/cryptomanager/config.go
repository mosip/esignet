// Package cryptomanager ports the MOSIP kernel-keymanager-service's
// CryptomanagerService business logic to Go: hybrid envelope
// encryption/decryption of arbitrary payloads (Encrypt/Decrypt) and
// RSA-OAEP-256 + A256GCM JWE encryption/decryption (JWTEncrypt/JWTDecrypt),
// using key material resolved through the existing internal/keymanager
// library rather than duplicating any of its key-lifecycle logic.
package cryptomanager

import (
	"os"
	"strconv"
	"time"
)

const (
	defaultDataKeySplitter  = "#KEY_SPLITTER#"
	defaultSessionKeyLength = 32 // AES-256
)

// Config holds settings for the cryptomanager service. Env-var driven,
// mirroring internal/keymanager.Config's LoadConfig() convention.
type Config struct {
	// DataKeySplitter is the literal delimiter between the encrypted-key
	// material and the encrypted data in the Encrypt/Decrypt wire format.
	// Env: CRYPTOMANAGER_DATA_KEY_SPLITTER — default "#KEY_SPLITTER#"
	DataKeySplitter string

	// SessionKeyLength is the ephemeral AES session key size in bytes,
	// generated fresh on every Encrypt call. 32 = AES-256.
	// Env: CRYPTOMANAGER_SESSION_KEY_LENGTH_BYTES — default 32
	SessionKeyLength int

	// EnforceJWTCertKeyLength requires the resolved/supplied certificate's
	// RSA public key to be exactly 2048 bits for JWTEncrypt.
	// Env: CRYPTOMANAGER_JWT_ENFORCE_2048 — default true
	EnforceJWTCertKeyLength bool

	// ThumbprintCacheExpiry is a forward-looking hook for caching the
	// thumbprint -> key_alias resolution (the one thing the Java reference
	// caches — see resolveDecryptionKey's doc comment), mirroring the
	// precedent already set by keymanager.Config.KeyCacheExpiry (itself
	// defined but unused today). Not read anywhere yet — resolveDecryptionKey
	// always queries GetKeyAliasByCertThumbprint directly.
	// Env: CRYPTOMANAGER_THUMBPRINT_CACHE_EXPIRE_MINS — default 0 (disabled)
	ThumbprintCacheExpiry time.Duration
}

// LoadConfig reads cryptomanager service settings from the environment.
func LoadConfig() Config {
	return Config{
		DataKeySplitter:         envOrDefault("CRYPTOMANAGER_DATA_KEY_SPLITTER", defaultDataKeySplitter),
		SessionKeyLength:        envIntOrDefault("CRYPTOMANAGER_SESSION_KEY_LENGTH_BYTES", defaultSessionKeyLength),
		EnforceJWTCertKeyLength: envBoolOrDefault("CRYPTOMANAGER_JWT_ENFORCE_2048", true),
		ThumbprintCacheExpiry:   time.Duration(envIntOrDefault("CRYPTOMANAGER_THUMBPRINT_CACHE_EXPIRE_MINS", 0)) * time.Minute,
	}
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

func envBoolOrDefault(key string, fallback bool) bool {
	raw := os.Getenv(key)
	if raw == "" {
		return fallback
	}
	b, err := strconv.ParseBool(raw)
	if err != nil {
		return fallback
	}
	return b
}
