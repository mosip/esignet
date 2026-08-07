package signature_test

import (
	"github.com/mosip/esignet/internal/keymanager/kmtest"
)

// stateQuerier and fakeKeyStore are shared, hand-written in-memory fakes for
// keymanager's db.Querier and keystore.KeyStore ports, used by both this
// package's tests and internal/engine/mosip's — see kmtest's package doc.
type stateQuerier = kmtest.StateQuerier
type fakeKeyStore = kmtest.FakeKeyStore

func newStateQuerier() *stateQuerier { return kmtest.NewStateQuerier() }
func newFakeKeyStore() *fakeKeyStore { return kmtest.NewFakeKeyStore() }
