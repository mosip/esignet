// Package pkcs11 implements the keystore.KeyStore port against a real
// HSM or SoftHSM2 via PKCS#11, using github.com/miekg/pkcs11.
package pkcs11

import (
	"fmt"
	"strconv"
	"sync"
	"time"

	"github.com/miekg/pkcs11"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

func init() {
	keystore.Register("PKCS11", New)
}

const (
	sessionReloadCooldown = 60 * time.Second
	maxRetries            = 3
)

// Store implements keystore.KeyStore against a PKCS#11 module.
type Store struct {
	modulePath  string
	tokenLabel  string
	slotID      *uint
	pin         string
	enableCache bool

	mu          sync.Mutex
	ctx         *pkcs11.Ctx
	session     pkcs11.SessionHandle
	sessionOpen bool
	lastReload  time.Time

	cacheMu sync.RWMutex
	cache   map[string]pkcs11.ObjectHandle
}

// New constructs a PKCS#11-backed keystore.KeyStore from config params:
//
//	module-path — path to the PKCS#11 shared library (.so)
//	token-label — token label to select a slot (used if slot-id is unset)
//	slot-id     — numeric slot id (takes precedence over token-label)
//	pin         — user PIN for login
//	enable-key-reference-cache — "true"/"false", default "true"
func New(params map[string]string) (keystore.KeyStore, error) {
	modulePath := params["module-path"]
	if modulePath == "" {
		return nil, fmt.Errorf("pkcs11: module-path is required")
	}
	s := &Store{
		modulePath:  modulePath,
		tokenLabel:  params["token-label"],
		pin:         params["pin"],
		enableCache: params["enable-key-reference-cache"] != "false",
		cache:       make(map[string]pkcs11.ObjectHandle),
	}
	if v := params["slot-id"]; v != "" {
		id, err := strconv.ParseUint(v, 10, 32)
		if err != nil {
			return nil, fmt.Errorf("pkcs11: invalid slot-id %q: %w", v, err)
		}
		u := uint(id)
		s.slotID = &u
	}
	s.ctx = pkcs11.New(modulePath)
	if s.ctx == nil {
		return nil, fmt.Errorf("pkcs11: failed to load module %q", modulePath)
	}
	if err := s.ctx.Initialize(); err != nil {
		return nil, fmt.Errorf("pkcs11: initialize: %w", err)
	}
	if err := s.openSessionLocked(); err != nil {
		return nil, err
	}
	return s, nil
}

func (s *Store) ProviderName() string { return "PKCS11" }

// resolveSlot finds the slot by explicit slot-id, or by matching token-label
// against the token info of every present slot.
func (s *Store) resolveSlot() (uint, error) {
	if s.slotID != nil {
		return *s.slotID, nil
	}
	slots, err := s.ctx.GetSlotList(true)
	if err != nil {
		return 0, fmt.Errorf("pkcs11: get slot list: %w", err)
	}
	for _, slot := range slots {
		info, err := s.ctx.GetTokenInfo(slot)
		if err != nil {
			continue
		}
		if s.tokenLabel == "" || trimPadded(info.Label) == s.tokenLabel {
			return slot, nil
		}
	}
	return 0, fmt.Errorf("pkcs11: no slot found for token-label %q", s.tokenLabel)
}

func trimPadded(s string) string {
	for len(s) > 0 && s[len(s)-1] == ' ' {
		s = s[:len(s)-1]
	}
	return s
}

// openSessionLocked opens a new session and logs in. Caller must hold s.mu.
func (s *Store) openSessionLocked() error {
	slot, err := s.resolveSlot()
	if err != nil {
		return err
	}
	sh, err := s.ctx.OpenSession(slot, pkcs11.CKF_SERIAL_SESSION|pkcs11.CKF_RW_SESSION)
	if err != nil {
		return fmt.Errorf("pkcs11: open session: %w", err)
	}
	if s.pin != "" {
		if err := s.ctx.Login(sh, pkcs11.CKU_USER, s.pin); err != nil {
			_ = s.ctx.CloseSession(sh)
			return fmt.Errorf("pkcs11: login: %w", err)
		}
	}
	s.session = sh
	s.sessionOpen = true
	s.lastReload = time.Now()
	return nil
}

// reloadSessionLocked closes the current session (best-effort) and reopens
// it, subject to the 60s cooldown so a storm of transient errors doesn't
// hammer the token with reconnects. Caller must hold s.mu.
func (s *Store) reloadSessionLocked() error {
	if time.Since(s.lastReload) < sessionReloadCooldown {
		return fmt.Errorf("pkcs11: session reload on cooldown (last reload %s ago)", time.Since(s.lastReload))
	}
	if s.sessionOpen {
		_ = s.ctx.Logout(s.session)
		_ = s.ctx.CloseSession(s.session)
		s.sessionOpen = false
	}
	s.cacheMu.Lock()
	s.cache = make(map[string]pkcs11.ObjectHandle)
	s.cacheMu.Unlock()
	return s.openSessionLocked()
}

// withSession runs fn against the current session, retrying up to
// maxRetries times with a session reload (rate-limited by the cooldown) on
// transient PKCS#11 errors.
func (s *Store) withSession(fn func(sh pkcs11.SessionHandle) error) error {
	s.mu.Lock()
	defer s.mu.Unlock()

	var lastErr error
	for attempt := 0; attempt < maxRetries; attempt++ {
		if !s.sessionOpen {
			if err := s.openSessionLocked(); err != nil {
				lastErr = err
				continue
			}
		}
		err := fn(s.session)
		if err == nil {
			return nil
		}
		lastErr = err
		if !isTransient(err) {
			return err
		}
		if rerr := s.reloadSessionLocked(); rerr != nil {
			// Reload itself failed (likely cooldown) — surface the original error.
			return fmt.Errorf("%w (reload attempt: %v)", lastErr, rerr)
		}
	}
	return fmt.Errorf("pkcs11: exhausted %d retries: %w", maxRetries, lastErr)
}

func isTransient(err error) bool {
	perr, ok := err.(pkcs11.Error)
	if !ok {
		return false
	}
	switch uint(perr) {
	case pkcs11.CKR_SESSION_HANDLE_INVALID, pkcs11.CKR_SESSION_CLOSED,
		pkcs11.CKR_DEVICE_ERROR, pkcs11.CKR_DEVICE_REMOVED, pkcs11.CKR_TOKEN_NOT_PRESENT:
		return true
	default:
		return false
	}
}

func (s *Store) cacheGet(alias string) (pkcs11.ObjectHandle, bool) {
	if !s.enableCache {
		return 0, false
	}
	s.cacheMu.RLock()
	defer s.cacheMu.RUnlock()
	h, ok := s.cache[alias]
	return h, ok
}

func (s *Store) cachePut(alias string, h pkcs11.ObjectHandle) {
	if !s.enableCache {
		return
	}
	s.cacheMu.Lock()
	defer s.cacheMu.Unlock()
	s.cache[alias] = h
}

func (s *Store) cacheInvalidate(alias string) {
	s.cacheMu.Lock()
	defer s.cacheMu.Unlock()
	delete(s.cache, alias)
}
