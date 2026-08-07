//go:build !cgo

package pkcs11

import (
	"errors"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

func (ts *PKCS11TestSuite) TestNew_ReturnsCgoRequiredError() {
	ks, err := New(map[string]string{"module-path": "/usr/lib/softhsm/libsofthsm2.so"})
	ts.Require().Nil(ks, "New() returned a non-nil KeyStore in a CGO_ENABLED=0 build: %v", ks)
	ts.Require().True(errors.Is(err, ErrCgoRequired), "New() error = %v, want errors.Is(err, ErrCgoRequired)", err)
}

func (ts *PKCS11TestSuite) TestRegisteredWithKeystoreRegistry() {
	// Confirms this build still registers "PKCS11" (so selecting it fails
	// with this package's clear error, not keystore.New's generic
	// "unsupported keystore type" — the two errors mean different things:
	// unsupported means the name is unknown, this stub means cgo is off.
	_, err := keystore.New("PKCS11", nil)
	ts.Require().True(errors.Is(err, ErrCgoRequired), "keystore.New(\"PKCS11\", ...) error = %v, want errors.Is(err, ErrCgoRequired)", err)
}
