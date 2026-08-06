// Package pkcs12 implements the keystore.KeyStore port as a single software
// keystore file, for development/test environments (never production — see
// the warning logged in New, matching the equivalent warning in the Java
// PKCS12KeyStoreImpl).
//
// Implementation note (deviation from the original plan, disclosed here
// rather than silently): go-pkcs12's public API (Encode/Decode) only
// supports exactly one private key + certificate (+ optional CA chain) per
// PFX blob — it has no exported way to place multiple unrelated key+cert
// pairs into a single standards-compliant PKCS#12 SafeContents, and it has
// no concept of symmetric/secret keys at all. A true one-PFX-file store
// holding N independent aliases, readable by external tools like `openssl
// pkcs12`, is not achievable with this library's public surface without
// hand-rolling PKCS#12's ASN.1 structures from scratch.
//
// The pragmatic implementation used here keeps the "single file on disk"
// property (one path, one file, per plan §3/§10) by storing a small JSON
// container of independently-valid, per-alias artifacts in that one file:
// asymmetric aliases are stored as full go-pkcs12-encoded PFX blobs (real,
// standards-compliant PKCS#12, individually decodable), and symmetric
// aliases as AES-GCM-encrypted raw key bytes (since PKCS#12 itself has no
// secret-key bag). The trade-off is that the file as a whole is not
// directly readable by external PKCS#12 tooling — only by this package.
package pkcs12

import (
	"context"
	"crypto"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"crypto/x509"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"os"
	"path/filepath"
	"sync"

	"golang.org/x/crypto/scrypt"
	gopkcs12 "software.sslmate.com/src/go-pkcs12"

	"github.com/mosip/esignet/internal/keymanager/keystore"
	applog "github.com/mosip/esignet/internal/log"
)

func init() {
	keystore.Register("PKCS12", New)
}

const entryVersion = 1

type entryType string

const (
	entryAsymmetric entryType = "asymmetric"
	entrySymmetric  entryType = "symmetric"
)

type fileEntry struct {
	Type entryType `json:"type"`
	Data string    `json:"data"` // base64
}

type fileFormat struct {
	Version int                  `json:"version"`
	Salt    string               `json:"salt,omitempty"` // base64 scrypt salt, see Store.aesKey
	Entries map[string]fileEntry `json:"entries"`
}

// Store implements keystore.KeyStore against a single JSON-container file
// (see package doc for the multi-entry caveat).
type Store struct {
	path     string
	password string

	mu      sync.Mutex
	entries map[string]fileEntry
	salt    []byte // scrypt salt for aesKey, persisted in fileFormat.Salt
}

// New constructs a PKCS#12-backed keystore.KeyStore from config params:
//
//	config-path — path to the keystore file (created on first write if absent)
//	keystore-pass — password protecting every entry in the file
func New(params map[string]string) (keystore.KeyStore, error) {
	path := params["config-path"]
	if path == "" {
		return nil, fmt.Errorf("pkcs12: config-path is required")
	}
	password := params["keystore-pass"]
	if password == "" {
		return nil, fmt.Errorf("pkcs12: keystore-pass is required")
	}
	applog.GetLogger().Named("keystore.pkcs12").Warn(context.Background(),
		"pkcs12 keystore backend is not recommended for production use", applog.String("path", path))

	s := &Store{path: path, password: password, entries: map[string]fileEntry{}}
	if err := s.load(); err != nil {
		return nil, err
	}
	if len(s.salt) == 0 {
		salt := make([]byte, 16)
		if _, err := rand.Read(salt); err != nil {
			return nil, fmt.Errorf("pkcs12: generate salt: %w", err)
		}
		s.salt = salt
		s.mu.Lock()
		err := s.saveLocked()
		s.mu.Unlock()
		if err != nil {
			return nil, err
		}
	}
	return s, nil
}

// ProviderName implements keystore.KeyStore.
func (s *Store) ProviderName() string { return "PKCS12" }

func (s *Store) load() error {
	data, err := os.ReadFile(s.path)
	if os.IsNotExist(err) {
		return nil // first run — file created lazily on first write
	}
	if err != nil {
		return fmt.Errorf("pkcs12: read %s: %w", s.path, err)
	}
	var ff fileFormat
	if err := json.Unmarshal(data, &ff); err != nil {
		return fmt.Errorf("pkcs12: parse %s: %w", s.path, err)
	}
	if ff.Entries == nil {
		ff.Entries = map[string]fileEntry{}
	}
	s.entries = ff.Entries
	if ff.Salt != "" {
		salt, err := base64.StdEncoding.DecodeString(ff.Salt)
		if err != nil {
			return fmt.Errorf("pkcs12: decode salt: %w", err)
		}
		s.salt = salt
	}
	return nil
}

// saveLocked persists s.entries to disk, atomically: the JSON container is
// written to a temp file in the same directory and renamed into place, so a
// crash/partial write during saveLocked never corrupts s.path — a torn write
// there would otherwise be unparsable, and every alias in the file
// unrecoverable. Caller must hold s.mu.
func (s *Store) saveLocked() error {
	if err := os.MkdirAll(filepath.Dir(s.path), 0o700); err != nil {
		return fmt.Errorf("pkcs12: create keystore directory: %w", err)
	}
	ff := fileFormat{Version: entryVersion, Salt: base64.StdEncoding.EncodeToString(s.salt), Entries: s.entries}
	data, err := json.MarshalIndent(ff, "", "  ")
	if err != nil {
		return fmt.Errorf("pkcs12: marshal keystore: %w", err)
	}
	tmp, err := os.CreateTemp(filepath.Dir(s.path), ".keystore-*.tmp")
	if err != nil {
		return fmt.Errorf("pkcs12: create temp file: %w", err)
	}
	defer func() { _ = os.Remove(tmp.Name()) }()
	if err := tmp.Chmod(0o600); err != nil {
		_ = tmp.Close()
		return fmt.Errorf("pkcs12: chmod temp file: %w", err)
	}
	if _, err := tmp.Write(data); err != nil {
		_ = tmp.Close()
		return fmt.Errorf("pkcs12: write temp file: %w", err)
	}
	if err := tmp.Sync(); err != nil {
		_ = tmp.Close()
		return fmt.Errorf("pkcs12: sync temp file: %w", err)
	}
	if err := tmp.Close(); err != nil {
		return fmt.Errorf("pkcs12: close temp file: %w", err)
	}
	if err := os.Rename(tmp.Name(), s.path); err != nil {
		return fmt.Errorf("pkcs12: replace %s: %w", s.path, err)
	}
	return nil
}

// aesKey derives a 32-byte AES-256 key from the keystore password using
// scrypt with the per-file random salt persisted in fileFormat.Salt — unlike
// a bare hash, this is deliberately slow/memory-hard so the AES-GCM key
// protecting every stored symmetric key isn't brute-forceable offline at
// hashing speed, and a per-file salt means identical passwords across
// deployments don't yield identical keys.
func (s *Store) aesKey() ([32]byte, error) {
	var out [32]byte
	dk, err := scrypt.Key([]byte(s.password), s.salt, 1<<15, 8, 1, 32)
	if err != nil {
		return out, fmt.Errorf("pkcs12: derive key: %w", err)
	}
	copy(out[:], dk)
	return out, nil
}

func (s *Store) encryptSymmetric(raw []byte) (string, error) {
	key, err := s.aesKey()
	if err != nil {
		return "", err
	}
	block, err := aes.NewCipher(key[:])
	if err != nil {
		return "", err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return "", err
	}
	nonce := make([]byte, gcm.NonceSize())
	if _, err := rand.Read(nonce); err != nil {
		return "", err
	}
	ciphertext := gcm.Seal(nonce, nonce, raw, nil)
	return base64.StdEncoding.EncodeToString(ciphertext), nil
}

func (s *Store) decryptSymmetric(encoded string) ([]byte, error) {
	ciphertext, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		return nil, err
	}
	key, err := s.aesKey()
	if err != nil {
		return nil, err
	}
	block, err := aes.NewCipher(key[:])
	if err != nil {
		return nil, err
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, err
	}
	if len(ciphertext) < gcm.NonceSize() {
		return nil, fmt.Errorf("pkcs12: ciphertext too short")
	}
	nonce, ct := ciphertext[:gcm.NonceSize()], ciphertext[gcm.NonceSize():]
	return gcm.Open(nil, nonce, ct, nil)
}

func (s *Store) encodePFX(priv crypto.PrivateKey, cert *x509.Certificate) (string, error) {
	pfx, err := gopkcs12.Modern.WithRand(rand.Reader).Encode(priv, cert, nil, s.password)
	if err != nil {
		return "", fmt.Errorf("pkcs12: encode PFX: %w", err)
	}
	return base64.StdEncoding.EncodeToString(pfx), nil
}

func (s *Store) decodePFX(encoded string) (crypto.PrivateKey, *x509.Certificate, error) {
	pfx, err := base64.StdEncoding.DecodeString(encoded)
	if err != nil {
		return nil, nil, err
	}
	priv, cert, err := gopkcs12.Decode(pfx, s.password)
	if err != nil {
		return nil, nil, fmt.Errorf("pkcs12: decode PFX: %w", err)
	}
	return priv, cert, nil
}
