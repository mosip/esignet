//go:build cgo

// Package pkcs11 implements the keystore.KeyStore port against a real
// HSM or SoftHSM2 via PKCS#11, using github.com/miekg/pkcs11. This backend
// depends on cgo (miekg/pkcs11 dlopens the vendor-supplied PKCS#11 module
// via C) and is unavailable in CGO_ENABLED=0 builds — see stub.go.
package pkcs11

import (
	"context"
	"errors"
	"fmt"
	"strconv"
	"sync"
	"sync/atomic"
	"time"

	"github.com/miekg/pkcs11"

	"github.com/mosip/esignet/internal/keymanager/keystore"
	applog "github.com/mosip/esignet/internal/log"
)

func init() {
	keystore.Register("PKCS11", New)
}

const (
	sessionReloadCooldown  = 60 * time.Second
	maxRetries             = 3
	defaultSessionPoolSize = 4
)

// errObjectNotFound is wrapped by every "<kind> alias %q not found" error in
// this package, so withSession can tell a lookup that came back empty apart
// from every other failure — see needsLogin. Its text completes those
// messages ("... alias %q " + "not found"), leaving them unchanged.
var errObjectNotFound = errors.New("not found")

// module is the subset of *pkcs11.Ctx the store uses. It exists so the
// session, re-login and handle-recovery logic can be unit-tested against a
// fake token; *pkcs11.Ctx is the only production implementation.
type module interface {
	Initialize(opts ...pkcs11.InitializeOption) error
	Finalize() error
	Destroy()
	GetSlotList(tokenPresent bool) ([]uint, error)
	GetTokenInfo(slotID uint) (pkcs11.TokenInfo, error)
	OpenSession(slotID uint, flags uint) (pkcs11.SessionHandle, error)
	CloseSession(sh pkcs11.SessionHandle) error
	GetSessionInfo(sh pkcs11.SessionHandle) (pkcs11.SessionInfo, error)
	Login(sh pkcs11.SessionHandle, userType uint, pin string) error
	CreateObject(sh pkcs11.SessionHandle, temp []*pkcs11.Attribute) (pkcs11.ObjectHandle, error)
	DestroyObject(sh pkcs11.SessionHandle, oh pkcs11.ObjectHandle) error
	GetAttributeValue(sh pkcs11.SessionHandle, o pkcs11.ObjectHandle, a []*pkcs11.Attribute) ([]*pkcs11.Attribute, error)
	FindObjectsInit(sh pkcs11.SessionHandle, temp []*pkcs11.Attribute) error
	FindObjects(sh pkcs11.SessionHandle, limit int) ([]pkcs11.ObjectHandle, bool, error)
	FindObjectsFinal(sh pkcs11.SessionHandle) error
	DecryptInit(sh pkcs11.SessionHandle, m []*pkcs11.Mechanism, o pkcs11.ObjectHandle) error
	Decrypt(sh pkcs11.SessionHandle, cipher []byte) ([]byte, error)
	SignInit(sh pkcs11.SessionHandle, m []*pkcs11.Mechanism, o pkcs11.ObjectHandle) error
	Sign(sh pkcs11.SessionHandle, message []byte) ([]byte, error)
	GenerateKey(sh pkcs11.SessionHandle, m []*pkcs11.Mechanism, temp []*pkcs11.Attribute) (pkcs11.ObjectHandle, error)
	GenerateKeyPair(sh pkcs11.SessionHandle, m []*pkcs11.Mechanism, public, private []*pkcs11.Attribute) (pkcs11.ObjectHandle, pkcs11.ObjectHandle, error)
}

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

	ctx module

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

	// loginMu serializes ensureLoggedIn's re-login so that every pooled
	// session noticing a lost login at once re-authenticates the token
	// once, not poolSize times. It is taken only once a session has been
	// seen logged out; the logged-in case never touches it.
	loginMu sync.Mutex

	// loginGen counts ensureLoggedIn's re-login attempts. It is bumped
	// before C_Login, not after, so a goroutine that finds the session
	// already logged in is guaranteed to also see the bump of whichever
	// goroutine logged it in. A caller that read it before its operation
	// can thus tell a re-login started since — by any goroutine — and that
	// the operation is worth retrying.
	loginGen atomic.Uint64

	// lastLoginErr is the outcome of the latest ensureLoggedIn re-login
	// attempt (nil on success), guarded by loginMu. A caller that waited
	// out a concurrent attempt reads it to report that attempt's failure
	// instead of retrying its operation into a misleading "not found".
	lastLoginErr error

	// pinRejected latches the first login failure that means the token
	// refused s.pin (see isPINRejected). Once set, login fails fast with it
	// instead of calling C_Login again — see login.
	pinRejected atomic.Pointer[error]

	logger *applog.Logger
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
		logger:     applog.GetLogger().Named("keystore.pkcs11"),
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
	ctx := pkcs11.New(modulePath)
	if ctx == nil {
		return nil, fmt.Errorf("pkcs11: failed to load module %q", modulePath)
	}
	s.ctx = ctx
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
		if err := s.login(sh); err != nil {
			_ = s.ctx.CloseSession(sh)
			return 0, err
		}
	}
	return sh, nil
}

// login logs the token in as CKU_USER via sh, treating
// CKR_USER_ALREADY_LOGGED_IN as success.
//
// A PIN the token has rejected is never tried again: s.pin only changes on
// restart, so a retry can't succeed, and every failed C_Login counts toward
// the token's lockout threshold. Without the latch, a rotated or wrong PIN
// would turn ensureLoggedIn's per-request re-login into one failed attempt
// per request and lock the user PIN (CKR_PIN_LOCKED) within a few calls,
// which only an SO-PIN unlock undoes.
func (s *Store) login(sh pkcs11.SessionHandle) error {
	if rejected := s.pinRejected.Load(); rejected != nil {
		return *rejected
	}
	if err := s.ctx.Login(sh, pkcs11.CKU_USER, s.pin); err != nil {
		var perr pkcs11.Error
		if errors.As(err, &perr) && uint(perr) == pkcs11.CKR_USER_ALREADY_LOGGED_IN {
			return nil
		}
		err = fmt.Errorf("pkcs11: login: %w", err)
		if isPINRejected(err) {
			latched := fmt.Errorf("%w (not retrying until restart to avoid locking the token's PIN)", err)
			if s.pinRejected.CompareAndSwap(nil, &latched) {
				s.logger.Error(context.Background(), "pkcs11 token rejected the configured PIN; further logins disabled until restart",
					applog.Error(err))
			}
		}
		return err
	}
	return nil
}

// isPINRejected reports whether a C_Login failure means the token refused
// the PIN itself, so retrying the same PIN can only add failed attempts.
func isPINRejected(err error) bool {
	var perr pkcs11.Error
	if !errors.As(err, &perr) {
		return false
	}
	switch uint(perr) {
	case pkcs11.CKR_PIN_INCORRECT, pkcs11.CKR_PIN_INVALID, pkcs11.CKR_PIN_LEN_RANGE,
		pkcs11.CKR_PIN_EXPIRED, pkcs11.CKR_PIN_LOCKED:
		return true
	default:
		return false
	}
}

// ensureLoggedIn re-authenticates the token if sh reports a public
// (logged-out) session state. It returns true if a re-login has happened
// since the caller read loginGen as seenGen — by this call or a concurrent
// one — meaning an operation that failed under the old login state is
// worth retrying. Login state is
// token-wide and not owned by this process: another client of the same
// token — notably another esignet pod sharing one SoftHSM through
// pkcs11-proxy — can log it out from under every pooled session, or a
// token/proxy restart can drop it. Nothing about the sessions themselves
// signals that: private objects (private keys, the AES keys — see
// GenerateAndStoreSymmetricKey) simply stop matching C_FindObjects, so
// without this check a live key reads as "not found" until restart.
//
// The session-state check runs without loginMu, so lookups of a genuinely
// missing alias on a logged-in token — the common case that lands here —
// cost one C_GetSessionInfo and never queue behind each other. The one
// case it can't see is another *client* logging the token back in between
// the failed operation and this check; that operation's "not found"
// surfaces, and the next call succeeds.
func (s *Store) ensureLoggedIn(sh pkcs11.SessionHandle, seenGen uint64) (bool, error) {
	if s.pin == "" {
		return false, nil
	}
	loggedOut, state, err := s.sessionLoggedOut(sh)
	if err != nil {
		return false, err
	}
	if !loggedOut {
		// Logged in — possibly by a concurrent re-login that completed
		// after the check above; its loginGen bump precedes its C_Login.
		return s.loginGen.Load() != seenGen, nil
	}

	// Under loginMu every earlier re-login attempt has finished, so the
	// session state and lastLoginErr read below are its final outcome.
	s.loginMu.Lock()
	defer s.loginMu.Unlock()
	if loggedOut, _, err = s.sessionLoggedOut(sh); err != nil {
		return false, err
	}
	if !loggedOut {
		// Logged back in while this goroutine waited — by a concurrent
		// re-login or by another client of the token.
		return true, nil
	}
	if s.loginGen.Load() != seenGen && s.lastLoginErr != nil {
		// A re-login started since the caller read seenGen has already
		// failed, and the token is still logged out: retrying the
		// operation could only fail again as "not found", hiding the real
		// cause (and keeping a transient one away from withSession's
		// session reload).
		return false, s.lastLoginErr
	}
	s.loginGen.Add(1)
	s.lastLoginErr = s.login(sh)
	if s.lastLoginErr != nil {
		return false, s.lastLoginErr
	}
	s.logger.Warn(context.Background(), "pkcs11 token was logged out externally; re-authenticated",
		applog.Int("previousSessionState", int(state)))
	return true, nil
}

// sessionLoggedOut reports whether sh is in a public (not logged-in)
// session state, along with that state.
func (s *Store) sessionLoggedOut(sh pkcs11.SessionHandle) (bool, uint, error) {
	info, err := s.ctx.GetSessionInfo(sh)
	if err != nil {
		return false, 0, fmt.Errorf("pkcs11: get session info: %w", err)
	}
	return info.State == pkcs11.CKS_RO_PUBLIC_SESSION || info.State == pkcs11.CKS_RW_PUBLIC_SESSION, info.State, nil
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
// PKCS#11 errors, and once more after a re-login if fn failed because the
// token had been logged out (see runWithRelogin). The session — the
// original or, after a reload, its replacement — is always returned to the
// pool before withSession returns.
func (s *Store) withSession(fn func(sh pkcs11.SessionHandle) error) error {
	sh := s.acquire()
	defer func() { s.release(sh) }()

	var lastErr error
	for attempt := 0; attempt < maxRetries; attempt++ {
		err := s.runWithRelogin(sh, fn)
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

// runWithRelogin runs fn once and, if it failed in a way a lost login
// explains (needsLogin), re-authenticates via ensureLoggedIn and runs fn
// exactly once more. If the session turns out to be logged in after all,
// with no re-login since fn started, fn's error is genuine (e.g. the alias
// really doesn't exist) and is returned as is. A failed re-login — this
// goroutine's or a concurrent one it waited out — is joined onto fn's
// error, so a dead session (CKR_SESSION_HANDLE_INVALID from GetSessionInfo)
// or a transient login failure still reaches withSession's isTransient
// reload path.
func (s *Store) runWithRelogin(sh pkcs11.SessionHandle, fn func(sh pkcs11.SessionHandle) error) error {
	gen := s.loginGen.Load()
	err := fn(sh)
	if err == nil || !needsLogin(err) {
		return err
	}
	relogged, lerr := s.ensureLoggedIn(sh, gen)
	if lerr != nil {
		return fmt.Errorf("%w (re-login attempt: %w)", err, lerr)
	}
	if !relogged {
		return err
	}
	return fn(sh)
}

// needsLogin reports whether err is what an operation returns when the
// token has been logged out: an empty lookup (private objects are invisible
// to a public session) or an explicit CKR_USER_NOT_LOGGED_IN (e.g. from
// generating a private object). A private-key handle held across the logout
// fails differently (CKR_OBJECT_HANDLE_INVALID on SoftHSM2);
// privateKey.withKeyHandle turns that into a fresh lookup, which lands here
// as not-found.
func needsLogin(err error) bool {
	if errors.Is(err, errObjectNotFound) {
		return true
	}
	var perr pkcs11.Error
	return errors.As(err, &perr) && uint(perr) == pkcs11.CKR_USER_NOT_LOGGED_IN
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

// Close implements keystore.KeyStore. It closes every pooled session, then
// finalizes and destroys the module context, releasing the token sessions
// so a graceful restart doesn't leave them open until the HSM reclaims
// them. It deliberately never calls C_Logout: login state is token-wide,
// not per-process, so on a token shared with other clients (e.g. every
// esignet pod behind one pkcs11-proxy/SoftHSM, including the incoming pod
// of a rolling update) a logout here would deauthenticate all of them.
// This process's own login ends with its last session anyway. Safe to call
// once during service shutdown; not safe to call concurrently with
// in-flight withSession callers — it drains exactly poolSize sessions from
// the pool, which only holds all of them when nothing is currently checked
// out.
func (s *Store) Close() error {
	var errs []error
	for i := 0; i < s.poolSize; i++ {
		sh := <-s.sessions
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
