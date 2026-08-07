//go:build !cgo

// Package pkcs11 (this file) is the CGO_ENABLED=0 build of the PKCS#11
// backend. The real implementation (store.go, keys.go, signer.go) depends
// on cgo — miekg/pkcs11 dlopens the vendor-supplied PKCS#11 module via C —
// so it cannot exist in a pure-Go binary. This stub keeps the "PKCS11" name
// registered so an operator who selects it without cgo gets a clear error
// at startup instead of "unsupported keystore type" or a compile failure.
package pkcs11

import (
	"errors"

	"github.com/mosip/esignet/internal/keymanager/keystore"
)

func init() {
	keystore.Register("PKCS11", New)
}

// ErrCgoRequired is returned by New in a CGO_ENABLED=0 build. Exported so
// callers (and tests) can distinguish "PKCS11 selected without cgo" from any
// other error via errors.Is, instead of matching on error text.
var ErrCgoRequired = errors.New("PKCS11 keystore requires a build with cgo enabled (CGO_ENABLED=1); use KEYMANAGER_KEYSTORE_TYPE=PKCS12 or rebuild with CGO_ENABLED=1")

func New(map[string]string) (keystore.KeyStore, error) {
	return nil, ErrCgoRequired
}
