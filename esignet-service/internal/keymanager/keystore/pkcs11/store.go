//go:build cgo

// Package pkcs11 implements the keystore.KeyStore port against a real
// HSM or SoftHSM2 via PKCS#11, using github.com/miekg/pkcs11. This backend
// depends on cgo (miekg/pkcs11 dlopens the vendor-supplied PKCS#11 module
// via C) and is unavailable in CGO_ENABLED=0 builds — see stub.go.
package pkcs11

import (
	"errors"
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
	sessionReloadCooldown  = 60 * time.Second
	maxRetries             = 3
	defaultSessionPoolSize = 4
)

// Store implements keystore.KeyStore against a PKCS#11 module. Every
// withSession call is served by one session checked out of a fixed-size
// pool (poolSize), rather than a single session serialized behind a mutex —
// signing/decryption throughput under concurrent load would otherwise be
// capped at one HSM operation at a time regardless of how many requests
// arrive concurrently. Initialize is called with the library default
// CKF_OS_LOCKING_OK, which requires a PKCS#11-compliant module to be safe
// under concurrent calls across sessions — pooling relies on that.
type Store struct {
	modulePath string
	tokenLabel string
	slotID     *uint
	pin        string
	poolSize   int

	ctx *pkcs11.Ctx

	// sessions holds exactly poolSize open, logged-in session handles when
	// none are checked out — acquire()/release() are the only way sessions
	// leave/return to it.
	sessions chan pkcs11.SessionHandle

	// reloadMu/lastReload rate-limit session reloads store-wide (not
	// per-session): if the token is genuinely down, every pooled session
	// will hit transient errors around the same time, and without a shared
	// cooldown that becomes poolSize independent reconnect storms instead
	// of one.
	reloadMu   sync.Mutex
	lastReload time.Time
}

// New constructs a PKCS#11-backed keystore.KeyStore from config params:
//
//	module-path      — path to the PKCS#11 shared library (.so)
//	token-label      — token label to select a slot (used if slot-id is unset)
//	slot-id          — numeric slot id (takes precedence over token-label)
//	pin              — user PIN for login
//	session-pool-size — number of concurrently open PKCS#11 sessions (default 4)
func New(params map[string]string) (keystore.KeyStore, error) {
	modulePath := params["module-path"]
	if modulePath == "" {
		return nil, fmt.Errorf("pkcs11: module-path is required")
	}
	s := &Store{
		modulePath: modulePath,
		tokenLabel: params["token-label"],
		pin:        params["pin"],
		poolSize:   defaultSessionPoolSize,
	}
	if v := params["slot-id"]; v != "" {
		id, err := strconv.ParseUint(v, 10, 32)
		if err != nil {
			return nil, fmt.Errorf("pkcs11: invalid slot-id %q: %w", v, err)
		}
		u := uint(id)
		s.slotID = &u
	}
	if s.slotID == nil && s.tokenLabel == "" {
		return nil, fmt.Errorf("pkcs11: one of slot-id or token-label is required; refusing to select an arbitrary token")
	}
	if v := params["session-pool-size"]; v != "" {
		n, err := strconv.Atoi(v)
		if err != nil || n < 1 {
			return nil, fmt.Errorf("pkcs11: invalid session-pool-size %q: must be a positive integer", v)
		}
		s.poolSize = n
	}
	s.ctx = pkcs11.New(modulePath)
	if s.ctx == nil {
		return nil, fmt.Errorf("pkcs11: failed to load module %q", modulePath)
	}
	if err := s.ctx.Initialize(); err != nil {
		s.ctx.Destroy()
		return nil, fmt.Errorf("pkcs11: initialize: %w", err)
	}

	s.sessions = make(chan pkcs11.SessionHandle, s.poolSize)
	for i := 0; i < s.poolSize; i++ {
		sh, err := s.openSession()
		if err != nil {
			s.closeSessionsOpenedSoFar()
			_ = s.ctx.Finalize()
			s.ctx.Destroy()
			return nil, err
		}
		s.sessions <- sh
	}
	return s, nil
}

// closeSessionsOpenedSoFar drains and closes whatever sessions New managed
// to open before a later one failed — cleanup for New's own error path.
func (s *Store) closeSessionsOpenedSoFar() {
	for {
		select {
		case sh := <-s.sessions:
			_ = s.ctx.CloseSession(sh)
		default:
			return
		}
	}
}

// ProviderName implements keystore.KeyStore.
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
		if trimPadded(info.Label) == s.tokenLabel {
			return slot, nil
		}
	}
	return 0, fmt.Errorf("pkcs11: no slot found for token-label %q among %d present slots", s.tokenLabel, len(slots))
}

func trimPadded(s string) string {
	for len(s) > 0 && s[len(s)-1] == ' ' {
		s = s[:len(s)-1]
	}
	return s
}

// openSession opens and logs in a brand new session. Login is token-wide,
// not session-wide (PKCS#11 §5.6.4): once any session on this token has
// logged in, a second Login on another session returns
// CKR_USER_ALREADY_LOGGED_IN, which is expected here (every pooled session
// after the first hits it) and is not an error.
func (s *Store) openSession() (pkcs11.SessionHandle, error) {
	slot, err := s.resolveSlot()
	if err != nil {
		return 0, err
	}
	sh, err := s.ctx.OpenSession(slot, pkcs11.CKF_SERIAL_SESSION|pkcs11.CKF_RW_SESSION)
	if err != nil {
		return 0, fmt.Errorf("pkcs11: open session: %w", err)
	}
	if s.pin != "" {
		if err := s.ctx.Login(sh, pkcs11.CKU_USER, s.pin); err != nil {
			var perr pkcs11.Error
			if !errors.As(err, &perr) || uint(perr) != pkcs11.CKR_USER_ALREADY_LOGGED_IN {
				_ = s.ctx.CloseSession(sh)
				return 0, fmt.Errorf("pkcs11: login: %w", err)
			}
		}
	}
	return sh, nil
}

// acquire checks out one session from the pool, blocking if every session
// is currently in use.
func (s *Store) acquire() pkcs11.SessionHandle {
	return <-s.sessions
}

// release returns sh to the pool.
func (s *Store) release(sh pkcs11.SessionHandle) {
	s.sessions <- sh
}

// reloadSession replaces a session that hit a transient error with a fresh
// one, subject to the store-wide cooldown so a storm of transient errors
// across the pool doesn't hammer the token with reconnects. The bad session
// is closed but never logged out — logout is token-wide and would
// deauthenticate every other session still checked out of the pool.
func (s *Store) reloadSession(bad pkcs11.SessionHandle) (pkcs11.SessionHandle, error) {
	s.reloadMu.Lock()
	if wait := time.Since(s.lastReload); wait < sessionReloadCooldown {
		s.reloadMu.Unlock()
		return 0, fmt.Errorf("pkcs11: session reload on cooldown (last reload %s ago)", wait)
	}
	s.lastReload = time.Now()
	s.reloadMu.Unlock()

	_ = s.ctx.CloseSession(bad)
	return s.openSession()
}

// withSession runs fn against a pooled session, retrying up to maxRetries
// times with a session reload (rate-limited by the cooldown) on transient
// PKCS#11 errors. The session — the original or, after a reload, its
// replacement — is always returned to the pool before withSession returns.
func (s *Store) withSession(fn func(sh pkcs11.SessionHandle) error) error {
	sh := s.acquire()
	defer func() { s.release(sh) }()

	var lastErr error
	for attempt := 0; attempt < maxRetries; attempt++ {
		err := fn(sh)
		if err == nil {
			return nil
		}
		lastErr = err
		if !isTransient(err) {
			return err
		}
		newSh, rerr := s.reloadSession(sh)
		if rerr != nil {
			// Reload itself failed (likely cooldown) — surface the original error.
			return fmt.Errorf("%w (reload attempt: %w)", lastErr, rerr)
		}
		sh = newSh
	}
	return fmt.Errorf("pkcs11: exhausted %d retries: %w", maxRetries, lastErr)
}

func isTransient(err error) bool {
	var perr pkcs11.Error
	if !errors.As(err, &perr) {
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

// Close implements keystore.KeyStore. It logs out (once — logout is
// token-wide) and closes every pooled session, then finalizes and destroys
// the module context, releasing the token sessions so a graceful restart
// doesn't leave them open until the HSM reclaims them. Safe to call once
// during service shutdown; not safe to call concurrently with in-flight
// withSession callers — it drains exactly poolSize sessions from the pool,
// which only holds all of them when nothing is currently checked out.
func (s *Store) Close() error {
	var errs []error
	loggedOut := false
	for i := 0; i < s.poolSize; i++ {
		sh := <-s.sessions
		if !loggedOut {
			if err := s.ctx.Logout(sh); err != nil {
				errs = append(errs, fmt.Errorf("pkcs11: logout: %w", err))
			}
			loggedOut = true
		}
		if err := s.ctx.CloseSession(sh); err != nil {
			errs = append(errs, fmt.Errorf("pkcs11: close session: %w", err))
		}
	}
	if err := s.ctx.Finalize(); err != nil {
		errs = append(errs, fmt.Errorf("pkcs11: finalize: %w", err))
	}
	s.ctx.Destroy()

	return errors.Join(errs...)
}
