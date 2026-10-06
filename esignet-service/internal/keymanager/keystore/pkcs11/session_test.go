//go:build cgo

package pkcs11

import (
	"errors"
	"fmt"
	"sync"
	"time"

	"github.com/miekg/pkcs11"
)

func p11err(code uint) error { return pkcs11.Error(code) }

func (ts *PKCS11TestSuite) TestLogin() {
	ts.Run("success", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		ts.Require().NoError(s.login(1))
		ts.Equal(1, fm.logins)
	})
	ts.Run("already logged in is success", func() {
		fm := newFakeModule()
		fm.loginErrs = []error{p11err(pkcs11.CKR_USER_ALREADY_LOGGED_IN)}
		s := newFakeStore(fm, "1234", 1)
		ts.Require().NoError(s.login(1))
	})
	ts.Run("rejected PIN latches and stops calling C_Login", func() {
		fm := newFakeModule()
		fm.loginErrs = []error{p11err(pkcs11.CKR_PIN_INCORRECT)}
		s := newFakeStore(fm, "bad", 1)

		first := s.login(1)
		ts.Require().Error(first)
		ts.Require().ErrorIs(first, pkcs11.Error(pkcs11.CKR_PIN_INCORRECT))

		second := s.login(1)
		ts.Require().Error(second)
		ts.Contains(second.Error(), "not retrying until restart")
		ts.Equal(1, fm.logins, "latched PIN must not reach the token again")
	})
	ts.Run("other failure is not latched", func() {
		fm := newFakeModule()
		fm.loginErrs = []error{p11err(pkcs11.CKR_DEVICE_ERROR)}
		s := newFakeStore(fm, "1234", 1)
		ts.Require().Error(s.login(1))
		ts.Require().NoError(s.login(1))
		ts.Equal(2, fm.logins)
	})
}

func (ts *PKCS11TestSuite) TestOpenSession() {
	ts.Run("logs in with a PIN", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		s.slotID = new(uint)
		sh, err := s.openSession()
		ts.Require().NoError(err)
		ts.NotZero(sh)
		ts.Equal(1, fm.logins)
	})
	ts.Run("login failure closes the session", func() {
		fm := newFakeModule()
		fm.loginErrs = []error{p11err(pkcs11.CKR_PIN_LOCKED)}
		s := newFakeStore(fm, "1234", 1)
		s.slotID = new(uint)
		_, err := s.openSession()
		ts.Require().Error(err)
		ts.Len(fm.closed, 1)
	})
	ts.Run("open failure", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		s.slotID = new(uint)
		fm.openErr = errors.New("boom")
		_, err := s.openSession()
		ts.Require().ErrorContains(err, "open session")
	})
}

func (ts *PKCS11TestSuite) TestResolveSlot() {
	fm := newFakeModule()
	fm.slots = []uint{3, 7}
	fm.tokenInfo[3] = pkcs11.TokenInfo{Label: "other   "}
	fm.tokenInfo[7] = pkcs11.TokenInfo{Label: "esignet "}
	s := newFakeStore(fm, "", 1)

	s.tokenLabel = "esignet"
	slot, err := s.resolveSlot()
	ts.Require().NoError(err)
	ts.EqualValues(7, slot)

	s.tokenLabel = "missing"
	_, err = s.resolveSlot()
	ts.Require().ErrorContains(err, "no slot found")

	fm.slotsErr = errors.New("boom")
	_, err = s.resolveSlot()
	ts.Require().ErrorContains(err, "get slot list")
}

func (ts *PKCS11TestSuite) TestEnsureLoggedIn() {
	ts.Run("no PIN means nothing to re-login with", func() {
		s := newFakeStore(newFakeModule(), "", 1)
		relogged, err := s.ensureLoggedIn(1, 0)
		ts.Require().NoError(err)
		ts.False(relogged)
	})
	ts.Run("still logged in, nobody re-logged", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		relogged, err := s.ensureLoggedIn(1, s.loginGen.Load())
		ts.Require().NoError(err)
		ts.False(relogged)
		ts.Zero(fm.logins)
	})
	ts.Run("another goroutine already re-logged since the caller looked", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		s.loginGen.Add(1)
		relogged, err := s.ensureLoggedIn(1, 0)
		ts.Require().NoError(err)
		ts.True(relogged)
		ts.Zero(fm.logins)
	})
	ts.Run("logged out re-authenticates", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		fm.logout()
		relogged, err := s.ensureLoggedIn(1, s.loginGen.Load())
		ts.Require().NoError(err)
		ts.True(relogged)
		ts.Equal(1, fm.logins)
		ts.EqualValues(1, s.loginGen.Load())
	})
	ts.Run("session info failure", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		fm.sessionInfoErr = p11err(pkcs11.CKR_SESSION_HANDLE_INVALID)
		_, err := s.ensureLoggedIn(1, 0)
		ts.Require().ErrorContains(err, "get session info")
	})
	ts.Run("login failure", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		fm.logout()
		fm.loginErrs = []error{p11err(pkcs11.CKR_DEVICE_ERROR)}
		relogged, err := s.ensureLoggedIn(1, s.loginGen.Load())
		ts.Require().Error(err)
		ts.False(relogged)
	})
	ts.Run("concurrent re-login that failed is reported, not retried", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		fm.logout()
		seen := s.loginGen.Load()
		s.loginGen.Add(1)
		s.lastLoginErr = fmt.Errorf("pkcs11: login: %w", p11err(pkcs11.CKR_DEVICE_ERROR))
		relogged, err := s.ensureLoggedIn(1, seen)
		ts.Require().ErrorIs(err, pkcs11.Error(pkcs11.CKR_DEVICE_ERROR))
		ts.False(relogged)
		ts.Zero(fm.logins, "must not log in again behind a concurrent attempt that just failed")
	})
	ts.Run("concurrent re-login failed but token is logged in now", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		seen := s.loginGen.Load()
		s.loginGen.Add(1)
		s.lastLoginErr = fmt.Errorf("pkcs11: login: %w", p11err(pkcs11.CKR_DEVICE_ERROR))
		relogged, err := s.ensureLoggedIn(1, seen)
		ts.Require().NoError(err, "someone logged the token in; the operation is worth retrying")
		ts.True(relogged)
	})
	ts.Run("re-login failure from before the caller looked is retried", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		fm.logout()
		s.loginGen.Add(1)
		s.lastLoginErr = fmt.Errorf("pkcs11: login: %w", p11err(pkcs11.CKR_DEVICE_ERROR))
		relogged, err := s.ensureLoggedIn(1, s.loginGen.Load())
		ts.Require().NoError(err)
		ts.True(relogged)
		ts.Equal(1, fm.logins)
		ts.NoError(s.lastLoginErr, "a successful re-login clears the recorded failure")
	})
	ts.Run("concurrent callers re-login once", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 4)
		fm.logout()
		gen := s.loginGen.Load()
		var wg sync.WaitGroup
		for i := 0; i < 8; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				_, _ = s.ensureLoggedIn(1, gen)
			}()
		}
		wg.Wait()
		ts.Equal(1, fm.logins)
	})
}

func (ts *PKCS11TestSuite) TestRunWithRelogin() {
	notFound := fmt.Errorf("pkcs11: key alias %q %w", "a", errObjectNotFound)

	ts.Run("success runs once", func() {
		s := newFakeStore(newFakeModule(), "1234", 1)
		calls := 0
		ts.Require().NoError(s.runWithRelogin(1, func(pkcs11.SessionHandle) error { calls++; return nil }))
		ts.Equal(1, calls)
	})
	ts.Run("unrelated error is not retried", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		fm.logout()
		calls := 0
		err := s.runWithRelogin(1, func(pkcs11.SessionHandle) error { calls++; return errors.New("boom") })
		ts.Require().EqualError(err, "boom")
		ts.Equal(1, calls)
		ts.Zero(fm.logins)
	})
	ts.Run("genuinely missing alias on a logged-in token is returned as is", func() {
		s := newFakeStore(newFakeModule(), "1234", 1)
		calls := 0
		err := s.runWithRelogin(1, func(pkcs11.SessionHandle) error { calls++; return notFound })
		ts.Require().ErrorIs(err, errObjectNotFound)
		ts.Equal(1, calls)
	})
	ts.Run("not found while logged out re-logs in and retries", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		fm.logout()
		calls := 0
		err := s.runWithRelogin(1, func(pkcs11.SessionHandle) error {
			calls++
			if calls == 1 {
				return notFound
			}
			return nil
		})
		ts.Require().NoError(err)
		ts.Equal(2, calls)
		ts.Equal(1, fm.logins)
	})
	ts.Run("callers waiting out a failed re-login report its error, not not-found", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 4)
		fm.logout()
		fm.loginErrs = []error{p11err(pkcs11.CKR_DEVICE_ERROR)}
		const n = 8
		// Every caller's first lookup fails before any of them reaches
		// ensureLoggedIn (the barrier), so all read loginGen before the
		// first re-login attempt; that attempt is held open so the rest
		// queue on loginMu behind it.
		var firstLookups sync.WaitGroup
		firstLookups.Add(n)
		var once sync.Once
		fm.loginHook = func() { once.Do(func() { time.Sleep(50 * time.Millisecond) }) }
		loggedOut := func() bool {
			fm.mu.Lock()
			defer fm.mu.Unlock()
			return fm.state == pkcs11.CKS_RW_PUBLIC_SESSION
		}

		errs := make(chan error, n)
		var wg sync.WaitGroup
		for i := 0; i < n; i++ {
			wg.Add(1)
			go func() {
				defer wg.Done()
				first := true
				errs <- s.runWithRelogin(1, func(pkcs11.SessionHandle) error {
					out := loggedOut()
					if first {
						first = false
						firstLookups.Done()
						firstLookups.Wait()
					}
					if out {
						return notFound
					}
					return nil
				})
			}()
		}
		wg.Wait()
		close(errs)
		for err := range errs {
			ts.Require().ErrorIs(err, pkcs11.Error(pkcs11.CKR_DEVICE_ERROR), "the login failure must not be replaced by a retried not-found: %v", err)
			ts.True(isTransient(err), "a transient login failure must reach withSession's reload path")
		}
		ts.Equal(1, fm.logins, "callers behind the failed attempt must not log in again")
	})
	ts.Run("re-login check failure is joined onto the original error", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		fm.sessionInfoErr = p11err(pkcs11.CKR_SESSION_HANDLE_INVALID)
		err := s.runWithRelogin(1, func(pkcs11.SessionHandle) error { return notFound })
		ts.Require().ErrorIs(err, errObjectNotFound)
		ts.Require().ErrorIs(err, pkcs11.Error(pkcs11.CKR_SESSION_HANDLE_INVALID))
		ts.True(isTransient(err), "a dead session must still reach withSession's reload path")
	})
}

func (ts *PKCS11TestSuite) TestWithSession() {
	ts.Run("returns the session to the pool", func() {
		s := newFakeStore(newFakeModule(), "1234", 2)
		ts.Require().NoError(s.withSession(func(pkcs11.SessionHandle) error { return nil }))
		ts.Len(s.sessions, 2)
	})
	ts.Run("non-transient error is returned without a reload", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		err := s.withSession(func(pkcs11.SessionHandle) error { return errors.New("boom") })
		ts.Require().EqualError(err, "boom")
		ts.Empty(fm.closed)
	})
	ts.Run("transient error reloads the session and retries", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		s.slotID = new(uint)
		var seen []pkcs11.SessionHandle
		err := s.withSession(func(sh pkcs11.SessionHandle) error {
			seen = append(seen, sh)
			if len(seen) == 1 {
				return p11err(pkcs11.CKR_SESSION_HANDLE_INVALID)
			}
			return nil
		})
		ts.Require().NoError(err)
		ts.Require().Len(seen, 2)
		ts.NotEqual(seen[0], seen[1])
		ts.Equal([]pkcs11.SessionHandle{seen[0]}, fm.closed)
		ts.Len(s.sessions, 1)
	})
	ts.Run("second transient error hits the reload cooldown", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		s.slotID = new(uint)
		err := s.withSession(func(pkcs11.SessionHandle) error { return p11err(pkcs11.CKR_DEVICE_ERROR) })
		ts.Require().ErrorContains(err, "on cooldown")
		ts.Require().ErrorIs(err, pkcs11.Error(pkcs11.CKR_DEVICE_ERROR))
	})
	ts.Run("reload failure is reported", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 1)
		s.slotID = new(uint)
		fm.openErr = errors.New("no token")
		err := s.withSession(func(pkcs11.SessionHandle) error { return p11err(pkcs11.CKR_DEVICE_ERROR) })
		ts.Require().ErrorContains(err, "no token")
	})
}

func (ts *PKCS11TestSuite) TestClose() {
	ts.Run("closes every session without logging out", func() {
		fm := newFakeModule()
		s := newFakeStore(fm, "1234", 3)
		ts.Require().NoError(s.Close())
		ts.Len(fm.closed, 3)
		ts.True(fm.finalized)
		ts.Equal(1, fm.destroyed)
	})
	ts.Run("collects errors", func() {
		fm := newFakeModule()
		fm.closeErr = errors.New("close boom")
		fm.finalizeErr = errors.New("finalize boom")
		s := newFakeStore(fm, "1234", 2)
		err := s.Close()
		ts.Require().ErrorContains(err, "close boom")
		ts.Require().ErrorContains(err, "finalize boom")
		ts.Equal(1, fm.destroyed, "module is destroyed even when close fails")
	})
}

func (ts *PKCS11TestSuite) TestGenerateSymmetricKeyIsPrivate() {
	fm := newFakeModule()
	s := newFakeStore(fm, "1234", 1)
	ts.Require().NoError(s.GenerateAndStoreSymmetricKey("aes"))

	var found bool
	for _, a := range fm.genKeyTmpl {
		if a.Type == pkcs11.CKA_PRIVATE {
			found = true
			ts.Equal([]byte{1}, a.Value, "CKA_PRIVATE must be true")
		}
	}
	ts.True(found, "template must set CKA_PRIVATE explicitly")

	fm.genKeyErr = errors.New("boom")
	ts.Require().ErrorContains(s.GenerateAndStoreSymmetricKey("aes"), "generate symmetric key")
}

func (ts *PKCS11TestSuite) TestGetSymmetricKey() {
	fm := newFakeModule()
	fm.objects[5] = fakeObject{class: pkcs11.CKO_SECRET_KEY, label: "aes", private: true}
	fm.attrFn = func(_ pkcs11.ObjectHandle, _ []*pkcs11.Attribute) ([]*pkcs11.Attribute, error) {
		return []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_VALUE, []byte("secret"))}, nil
	}
	s := newFakeStore(fm, "1234", 1)

	key, err := s.GetSymmetricKey("aes")
	ts.Require().NoError(err)
	ts.Equal([]byte("secret"), key)

	_, err = s.GetSymmetricKey("missing")
	ts.Require().ErrorIs(err, errObjectNotFound)
	ts.Zero(fm.logins, "a missing alias on a logged-in token must not trigger a login")

	// Logged out: private objects vanish, the store re-logs in and finds it.
	fm.logout()
	key, err = s.GetSymmetricKey("aes")
	ts.Require().NoError(err)
	ts.Equal([]byte("secret"), key)
	ts.Equal(1, fm.logins)

	fm.attrFn = func(pkcs11.ObjectHandle, []*pkcs11.Attribute) ([]*pkcs11.Attribute, error) {
		return nil, errors.New("boom")
	}
	_, err = s.GetSymmetricKey("aes")
	ts.Require().ErrorContains(err, "read symmetric key value")

	fm.findInitErr = errors.New("find boom")
	_, err = s.GetSymmetricKey("aes")
	ts.Require().ErrorContains(err, "find boom")
}

func (ts *PKCS11TestSuite) TestDeleteKey() {
	ts.Run("destroys every object with the label", func() {
		fm := newFakeModule()
		fm.objects[1] = fakeObject{class: pkcs11.CKO_PRIVATE_KEY, label: "k", private: true}
		fm.objects[2] = fakeObject{class: pkcs11.CKO_CERTIFICATE, label: "k"}
		fm.objects[3] = fakeObject{class: pkcs11.CKO_CERTIFICATE, label: "other"}
		s := newFakeStore(fm, "1234", 1)
		ts.Require().NoError(s.DeleteKey("k"))
		ts.ElementsMatch([]pkcs11.ObjectHandle{1, 2}, fm.destroyedObj)
	})
	ts.Run("re-logs in first so the private key is not orphaned", func() {
		fm := newFakeModule()
		fm.objects[1] = fakeObject{class: pkcs11.CKO_PRIVATE_KEY, label: "k", private: true}
		fm.objects[2] = fakeObject{class: pkcs11.CKO_CERTIFICATE, label: "k"}
		s := newFakeStore(fm, "1234", 1)
		fm.logout()
		ts.Require().NoError(s.DeleteKey("k"))
		ts.ElementsMatch([]pkcs11.ObjectHandle{1, 2}, fm.destroyedObj)
		ts.Equal(1, fm.logins)
	})
	ts.Run("re-login failure aborts before destroying anything", func() {
		fm := newFakeModule()
		fm.objects[2] = fakeObject{class: pkcs11.CKO_CERTIFICATE, label: "k"}
		s := newFakeStore(fm, "1234", 1)
		fm.logout()
		fm.loginErrs = []error{p11err(pkcs11.CKR_PIN_INCORRECT)}
		ts.Require().Error(s.DeleteKey("k"))
		ts.Empty(fm.destroyedObj)
	})
	ts.Run("destroy failure", func() {
		fm := newFakeModule()
		fm.objects[1] = fakeObject{class: pkcs11.CKO_CERTIFICATE, label: "k"}
		fm.destroyErr = errors.New("boom")
		s := newFakeStore(fm, "1234", 1)
		ts.Require().ErrorContains(s.DeleteKey("k"), "destroy object")
	})
}

func (ts *PKCS11TestSuite) TestGetPrivateKey() {
	fm := newFakeModule()
	fm.objects[1] = fakeObject{class: pkcs11.CKO_PRIVATE_KEY, label: "ed", private: true}
	fm.attrFn = func(_ pkcs11.ObjectHandle, _ []*pkcs11.Attribute) ([]*pkcs11.Attribute, error) {
		return []*pkcs11.Attribute{pkcs11.NewAttribute(pkcs11.CKA_KEY_TYPE, ckkECEdwards)}, nil
	}
	s := newFakeStore(fm, "1234", 1)

	k, err := s.GetPrivateKey("ed")
	ts.Require().NoError(err)
	pk, ok := k.(*privateKey)
	ts.Require().True(ok)
	ts.EqualValues(ckkECEdwards, pk.keyType)
	ts.EqualValues(1, pk.handle)
	ts.Nil(pk.Public(), "no public key object on the token")

	_, err = s.GetPrivateKey("missing")
	ts.Require().ErrorIs(err, errObjectNotFound)
	ts.ErrorContains(err, `private key alias "missing" not found`)

	// Logged out: the private key is invisible until the store re-logs in.
	fm.logout()
	_, err = s.GetPrivateKey("ed")
	ts.Require().NoError(err)
	ts.Equal(1, fm.logins)
}

func (ts *PKCS11TestSuite) TestGetCertificate_NotFound() {
	fm := newFakeModule()
	s := newFakeStore(fm, "1234", 1)
	_, err := s.GetCertificate("missing")
	ts.Require().ErrorIs(err, errObjectNotFound)
	ts.ErrorContains(err, `certificate alias "missing" not found`)
}
