//go:build cgo

package pkcs11

import (
	"sync"

	"github.com/miekg/pkcs11"

	applog "github.com/mosip/esignet/internal/log"
)

// fakeModule is an in-memory stand-in for a PKCS#11 token, enough to drive
// Store's session, re-login and key-handle recovery paths without SoftHSM.
// Each field named *Err is returned by the matching call when non-nil; the
// *Fn hooks, when set, take over a call entirely.
type fakeModule struct {
	mu sync.Mutex

	state     uint // pkcs11.CKS_*; session state reported for every session
	loginErrs []error
	logins    int
	// loginHook, when set, runs at the start of every Login, outside mu —
	// e.g. to hold a login open while concurrent callers pile up.
	loginHook func()
	nextSess  pkcs11.SessionHandle

	sessionInfoErr error
	openErr        error
	initErr        error
	finalizeErr    error
	closeErr       error
	closed         []pkcs11.SessionHandle
	destroyed      int
	finalized      bool

	slots     []uint
	tokenInfo map[uint]pkcs11.TokenInfo
	slotsErr  error

	// objects are the objects visible to a logged-in session, keyed by
	// handle; private ones disappear from searches while logged out.
	objects map[pkcs11.ObjectHandle]fakeObject

	findInitErr  error
	findErr      error
	attrFn       func(h pkcs11.ObjectHandle, a []*pkcs11.Attribute) ([]*pkcs11.Attribute, error)
	createErr    error
	created      [][]*pkcs11.Attribute
	destroyErr   error
	destroyedObj []pkcs11.ObjectHandle
	genKeyErr    error
	genKeyTmpl   []*pkcs11.Attribute

	// signFn/decryptFn see the handle the last SignInit/DecryptInit used.
	signInitFn    func(h pkcs11.ObjectHandle) error
	decryptInitFn func(h pkcs11.ObjectHandle) error
	signOut       []byte
	signErr       error
	decryptOut    []byte
	decryptErr    error
	lastMech      uint
	lastSigned    []byte
	lastHandle    pkcs11.ObjectHandle

	pending []pkcs11.ObjectHandle
}

type fakeObject struct {
	class   uint
	label   string
	private bool
}

func newFakeModule() *fakeModule {
	return &fakeModule{
		state:     pkcs11.CKS_RW_USER_FUNCTIONS,
		objects:   map[pkcs11.ObjectHandle]fakeObject{},
		tokenInfo: map[uint]pkcs11.TokenInfo{},
	}
}

func (f *fakeModule) logout() { f.mu.Lock(); f.state = pkcs11.CKS_RW_PUBLIC_SESSION; f.mu.Unlock() }

func (f *fakeModule) Initialize(...pkcs11.InitializeOption) error { return f.initErr }
func (f *fakeModule) Finalize() error                             { f.finalized = true; return f.finalizeErr }
func (f *fakeModule) Destroy()                                    { f.destroyed++ }

func (f *fakeModule) GetSlotList(bool) ([]uint, error) { return f.slots, f.slotsErr }
func (f *fakeModule) GetTokenInfo(slot uint) (pkcs11.TokenInfo, error) {
	return f.tokenInfo[slot], nil
}

func (f *fakeModule) OpenSession(uint, uint) (pkcs11.SessionHandle, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.openErr != nil {
		return 0, f.openErr
	}
	f.nextSess++
	return f.nextSess, nil
}

func (f *fakeModule) CloseSession(sh pkcs11.SessionHandle) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.closed = append(f.closed, sh)
	return f.closeErr
}

func (f *fakeModule) GetSessionInfo(pkcs11.SessionHandle) (pkcs11.SessionInfo, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if f.sessionInfoErr != nil {
		return pkcs11.SessionInfo{}, f.sessionInfoErr
	}
	return pkcs11.SessionInfo{State: f.state}, nil
}

func (f *fakeModule) Login(pkcs11.SessionHandle, uint, string) error {
	if f.loginHook != nil {
		f.loginHook()
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	f.logins++
	if len(f.loginErrs) > 0 {
		err := f.loginErrs[0]
		f.loginErrs = f.loginErrs[1:]
		if err != nil {
			return err
		}
	}
	f.state = pkcs11.CKS_RW_USER_FUNCTIONS
	return nil
}

func (f *fakeModule) CreateObject(_ pkcs11.SessionHandle, t []*pkcs11.Attribute) (pkcs11.ObjectHandle, error) {
	f.created = append(f.created, t)
	return 1, f.createErr
}

func (f *fakeModule) DestroyObject(_ pkcs11.SessionHandle, h pkcs11.ObjectHandle) error {
	f.destroyedObj = append(f.destroyedObj, h)
	return f.destroyErr
}

func (f *fakeModule) GetAttributeValue(_ pkcs11.SessionHandle, h pkcs11.ObjectHandle, a []*pkcs11.Attribute) ([]*pkcs11.Attribute, error) {
	if f.attrFn != nil {
		return f.attrFn(h, a)
	}
	return a, nil
}

func (f *fakeModule) FindObjectsInit(_ pkcs11.SessionHandle, t []*pkcs11.Attribute) error {
	if f.findInitErr != nil {
		return f.findInitErr
	}
	f.mu.Lock()
	defer f.mu.Unlock()
	var class *uint
	var label *string
	for _, a := range t {
		switch a.Type {
		case pkcs11.CKA_CLASS:
			c := decodeCKULong(a.Value)
			class = &c
		case pkcs11.CKA_LABEL:
			l := string(a.Value)
			label = &l
		}
	}
	f.pending = nil
	for h, o := range f.objects {
		if o.private && f.state == pkcs11.CKS_RW_PUBLIC_SESSION {
			continue
		}
		if (class == nil || *class == o.class) && (label == nil || *label == o.label) {
			f.pending = append(f.pending, h)
		}
	}
	return nil
}

func (f *fakeModule) FindObjects(_ pkcs11.SessionHandle, limit int) ([]pkcs11.ObjectHandle, bool, error) {
	if f.findErr != nil {
		return nil, false, f.findErr
	}
	n := len(f.pending)
	if n > limit {
		n = limit
	}
	out := f.pending[:n]
	f.pending = f.pending[n:]
	return out, false, nil
}

func (f *fakeModule) FindObjectsFinal(pkcs11.SessionHandle) error { return nil }

func (f *fakeModule) DecryptInit(_ pkcs11.SessionHandle, m []*pkcs11.Mechanism, h pkcs11.ObjectHandle) error {
	f.lastMech, f.lastHandle = m[0].Mechanism, h
	if f.decryptInitFn != nil {
		return f.decryptInitFn(h)
	}
	return nil
}

func (f *fakeModule) Decrypt(pkcs11.SessionHandle, []byte) ([]byte, error) {
	return f.decryptOut, f.decryptErr
}

func (f *fakeModule) SignInit(_ pkcs11.SessionHandle, m []*pkcs11.Mechanism, h pkcs11.ObjectHandle) error {
	f.lastMech, f.lastHandle = m[0].Mechanism, h
	if f.signInitFn != nil {
		return f.signInitFn(h)
	}
	return nil
}

func (f *fakeModule) Sign(_ pkcs11.SessionHandle, msg []byte) ([]byte, error) {
	f.lastSigned = msg
	return f.signOut, f.signErr
}

func (f *fakeModule) GenerateKey(_ pkcs11.SessionHandle, _ []*pkcs11.Mechanism, t []*pkcs11.Attribute) (pkcs11.ObjectHandle, error) {
	f.genKeyTmpl = t
	return 1, f.genKeyErr
}

func (f *fakeModule) GenerateKeyPair(pkcs11.SessionHandle, []*pkcs11.Mechanism, []*pkcs11.Attribute, []*pkcs11.Attribute) (pkcs11.ObjectHandle, pkcs11.ObjectHandle, error) {
	return 0, 0, nil
}

var _ module = (*fakeModule)(nil)

// newFakeStore builds a Store with a pool of poolSize sessions over fm, the
// way New does after loading a real module.
func newFakeStore(fm *fakeModule, pin string, poolSize int) *Store {
	s := &Store{
		pin:      pin,
		poolSize: poolSize,
		ctx:      fm,
		sessions: make(chan pkcs11.SessionHandle, poolSize),
		logger:   applog.GetLogger().Named("keystore.pkcs11.test"),
	}
	for i := 0; i < poolSize; i++ {
		sh, _ := fm.OpenSession(0, 0)
		s.sessions <- sh
	}
	return s
}
