//go:build cgo

package pkcs11

import (
	"errors"
	"fmt"

	"github.com/miekg/pkcs11"
)

// TestNeedsLogin pins which failures withSession treats as a possibly lost
// login (and so re-checks the session state and retries once) versus
// failures it must pass through untouched — see needsLogin/runWithRelogin.
func (ts *PKCS11TestSuite) TestNeedsLogin() {
	cases := []struct {
		name string
		err  error
		want bool
	}{
		{"alias not found", fmt.Errorf("pkcs11: symmetric key alias %q %w", "a", errObjectNotFound), true},
		{"alias not found, wrapped again", fmt.Errorf("get symmetric key: %w", fmt.Errorf("pkcs11: private key alias %q %w", "a", errObjectNotFound)), true},
		{"CKR_USER_NOT_LOGGED_IN", pkcs11.Error(pkcs11.CKR_USER_NOT_LOGGED_IN), true},
		{"CKR_USER_NOT_LOGGED_IN, wrapped", fmt.Errorf("generate symmetric key: %w", pkcs11.Error(pkcs11.CKR_USER_NOT_LOGGED_IN)), true},
		{"CKR_OBJECT_HANDLE_INVALID (withKeyHandle re-resolves instead)", pkcs11.Error(pkcs11.CKR_OBJECT_HANDLE_INVALID), false},
		{"transient session error", pkcs11.Error(pkcs11.CKR_SESSION_HANDLE_INVALID), false},
		{"other pkcs11 error", pkcs11.Error(pkcs11.CKR_ARGUMENTS_BAD), false},
		{"non-pkcs11 error", errors.New("parse certificate: boom"), false},
	}
	for _, c := range cases {
		ts.Equal(c.want, needsLogin(c.err), c.name)
	}
}

// TestObjectNotFoundMessageUnchanged guards the error text operators and
// log searches already rely on: wrapping errObjectNotFound must still read
// exactly `... alias "<alias>" not found`.
func (ts *PKCS11TestSuite) TestObjectNotFoundMessageUnchanged() {
	err := fmt.Errorf("pkcs11: symmetric key alias %q %w", "abc", errObjectNotFound)
	ts.Equal(`pkcs11: symmetric key alias "abc" not found`, err.Error())
}

// TestIsPINRejected pins which C_Login failures latch the store's login off
// (retrying the same PIN would only count toward the token's lockout) versus
// failures that leave later logins free to try again — see Store.login.
func (ts *PKCS11TestSuite) TestIsPINRejected() {
	cases := []struct {
		name string
		err  error
		want bool
	}{
		{"CKR_PIN_INCORRECT, wrapped", fmt.Errorf("pkcs11: login: %w", pkcs11.Error(pkcs11.CKR_PIN_INCORRECT)), true},
		{"CKR_PIN_LOCKED", pkcs11.Error(pkcs11.CKR_PIN_LOCKED), true},
		{"CKR_PIN_EXPIRED", pkcs11.Error(pkcs11.CKR_PIN_EXPIRED), true},
		{"CKR_PIN_INVALID", pkcs11.Error(pkcs11.CKR_PIN_INVALID), true},
		{"CKR_PIN_LEN_RANGE", pkcs11.Error(pkcs11.CKR_PIN_LEN_RANGE), true},
		{"transient session error", pkcs11.Error(pkcs11.CKR_SESSION_HANDLE_INVALID), false},
		{"device error", pkcs11.Error(pkcs11.CKR_DEVICE_ERROR), false},
		{"non-pkcs11 error", errors.New("proxy: connection refused"), false},
	}
	for _, c := range cases {
		ts.Equal(c.want, isPINRejected(c.err), c.name)
	}
}
