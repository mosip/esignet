//go:build !cgo

package pkcs11

import (
	"testing"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

func TestNew_ReturnsCgoRequiredError(t *testing.T) {
	ks, err := New(map[string]string{"module-path": "/usr/lib/softhsm/libsofthsm2.so"})
	if ks != nil {
		t.Fatalf("New() returned a non-nil KeyStore in a CGO_ENABLED=0 build: %v", ks)
	}
	if err == nil {
		t.Fatal("New() error = nil, want a cgo-required error")
	}
}

func TestRegisteredWithKeystoreRegistry(t *testing.T) {
	// Confirms this build still registers "PKCS11" (so selecting it fails
	// with this package's clear error, not keystore.New's generic
	// "unsupported keystore type" — the two errors mean different things:
	// unsupported means the name is unknown, this stub means cgo is off.
	_, err := keystore.New("PKCS11", nil)
	if err == nil {
		t.Fatal("keystore.New(\"PKCS11\", ...) error = nil, want a cgo-required error")
	}
}
