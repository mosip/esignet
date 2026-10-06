//go:build cgo

package pkcs11

import (
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/asn1"
	"errors"
	"math/big"

	"github.com/miekg/pkcs11"
)

func (ts *PKCS11TestSuite) TestIsHandleInvalid() {
	ts.True(isHandleInvalid(p11err(pkcs11.CKR_OBJECT_HANDLE_INVALID)))
	ts.True(isHandleInvalid(p11err(pkcs11.CKR_KEY_HANDLE_INVALID)))
	ts.True(isHandleInvalid(errors.Join(errors.New("ctx"), p11err(pkcs11.CKR_KEY_HANDLE_INVALID))))
	ts.False(isHandleInvalid(p11err(pkcs11.CKR_SESSION_HANDLE_INVALID)))
	ts.False(isHandleInvalid(errors.New("boom")))
	ts.False(isHandleInvalid(nil))
}

func (ts *PKCS11TestSuite) newKey(fm *fakeModule, keyType uint, pub crypto.PublicKey) *privateKey {
	fm.objects[10] = fakeObject{class: pkcs11.CKO_PRIVATE_KEY, label: "k", private: true}
	return &privateKey{store: newFakeStore(fm, "1234", 1), alias: "k", keyType: keyType, pub: pub, handle: 10}
}

func (ts *PKCS11TestSuite) TestWithKeyHandle() {
	ts.Run("valid handle is used as is", func() {
		fm := newFakeModule()
		k := ts.newKey(fm, pkcs11.CKK_EC, nil)
		var got pkcs11.ObjectHandle
		ts.Require().NoError(k.withKeyHandle(func(_ pkcs11.SessionHandle, h pkcs11.ObjectHandle) error {
			got = h
			return nil
		}))
		ts.EqualValues(10, got)
	})
	ts.Run("other errors pass through untouched", func() {
		k := ts.newKey(newFakeModule(), pkcs11.CKK_EC, nil)
		calls := 0
		err := k.withKeyHandle(func(pkcs11.SessionHandle, pkcs11.ObjectHandle) error {
			calls++
			return errors.New("boom")
		})
		ts.Require().EqualError(err, "boom")
		ts.Equal(1, calls)
	})
	ts.Run("invalidated handle is re-resolved by alias and remembered", func() {
		fm := newFakeModule()
		k := ts.newKey(fm, pkcs11.CKK_EC, nil)
		// The token re-issued the key under a new handle after a re-login.
		delete(fm.objects, 10)
		fm.objects[20] = fakeObject{class: pkcs11.CKO_PRIVATE_KEY, label: "k", private: true}

		var seen []pkcs11.ObjectHandle
		err := k.withKeyHandle(func(_ pkcs11.SessionHandle, h pkcs11.ObjectHandle) error {
			seen = append(seen, h)
			if h == 10 {
				return p11err(pkcs11.CKR_OBJECT_HANDLE_INVALID)
			}
			return nil
		})
		ts.Require().NoError(err)
		ts.Equal([]pkcs11.ObjectHandle{10, 20}, seen)

		seen = nil
		ts.Require().NoError(k.withKeyHandle(func(_ pkcs11.SessionHandle, h pkcs11.ObjectHandle) error {
			seen = append(seen, h)
			return nil
		}))
		ts.Equal([]pkcs11.ObjectHandle{20}, seen, "the new handle is cached")
	})
	ts.Run("key gone after logout re-logs in and finds the new handle", func() {
		fm := newFakeModule()
		k := ts.newKey(fm, pkcs11.CKK_EC, nil)
		delete(fm.objects, 10)
		fm.objects[30] = fakeObject{class: pkcs11.CKO_PRIVATE_KEY, label: "k", private: true}
		fm.logout()

		var last pkcs11.ObjectHandle
		err := k.withKeyHandle(func(_ pkcs11.SessionHandle, h pkcs11.ObjectHandle) error {
			last = h
			if h == 10 {
				return p11err(pkcs11.CKR_OBJECT_HANDLE_INVALID)
			}
			return nil
		})
		ts.Require().NoError(err)
		ts.EqualValues(30, last)
		ts.Equal(1, fm.logins)
	})
	ts.Run("key no longer on the token", func() {
		fm := newFakeModule()
		k := ts.newKey(fm, pkcs11.CKK_EC, nil)
		delete(fm.objects, 10)
		err := k.withKeyHandle(func(pkcs11.SessionHandle, pkcs11.ObjectHandle) error {
			return p11err(pkcs11.CKR_OBJECT_HANDLE_INVALID)
		})
		ts.Require().ErrorIs(err, errObjectNotFound)
	})
	ts.Run("lookup failure is wrapped", func() {
		fm := newFakeModule()
		k := ts.newKey(fm, pkcs11.CKK_EC, nil)
		fm.findInitErr = errors.New("find boom")
		err := k.withKeyHandle(func(pkcs11.SessionHandle, pkcs11.ObjectHandle) error {
			return p11err(pkcs11.CKR_OBJECT_HANDLE_INVALID)
		})
		ts.Require().ErrorContains(err, "re-resolve private key")
	})
}

func (ts *PKCS11TestSuite) TestSign_RSAPKCS1() {
	fm := newFakeModule()
	k := ts.newKey(fm, pkcs11.CKK_RSA, nil)
	fm.signOut = []byte("sig")
	digest := sha256.Sum256([]byte("m"))

	sig, err := k.Sign(rand.Reader, digest[:], crypto.SHA256)
	ts.Require().NoError(err)
	ts.Equal([]byte("sig"), sig)
	ts.EqualValues(pkcs11.CKM_RSA_PKCS, fm.lastMech)
	ts.Equal(append(append([]byte{}, digestInfoPrefixes[crypto.SHA256]...), digest[:]...), fm.lastSigned)

	_, err = k.Sign(rand.Reader, digest[:], crypto.SHA1)
	ts.Require().ErrorContains(err, "unsupported hash")

	fm.signErr = errors.New("sign boom")
	_, err = k.Sign(rand.Reader, digest[:], crypto.SHA256)
	ts.Require().ErrorContains(err, "sign boom")

	fm.signErr = nil
	fm.signInitFn = func(pkcs11.ObjectHandle) error { return errors.New("init boom") }
	_, err = k.Sign(rand.Reader, digest[:], crypto.SHA256)
	ts.Require().ErrorContains(err, "init boom")
}

func (ts *PKCS11TestSuite) TestSign_RSAPSS() {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	ts.Require().NoError(err)
	fm := newFakeModule()
	k := ts.newKey(fm, pkcs11.CKK_RSA, &priv.PublicKey)
	digest := sha256.Sum256([]byte("m"))

	// A short raw result must be left-padded to the modulus size.
	fm.signOut = []byte{1, 2, 3}
	sig, err := k.Sign(rand.Reader, digest[:], &rsa.PSSOptions{Hash: crypto.SHA256, SaltLength: rsa.PSSSaltLengthEqualsHash})
	ts.Require().NoError(err)
	ts.Len(sig, 256)
	ts.Equal([]byte{1, 2, 3}, sig[253:])
	ts.EqualValues(pkcs11.CKM_RSA_X_509, fm.lastMech)

	_, err = k.Sign(rand.Reader, digest[:], &rsa.PSSOptions{Hash: crypto.SHA256, SaltLength: rsa.PSSSaltLengthAuto})
	ts.Require().NoError(err)

	_, err = k.Sign(rand.Reader, digest[:], &rsa.PSSOptions{})
	ts.Require().ErrorContains(err, "requires PSSOptions.Hash")

	fm.signErr = errors.New("sign boom")
	_, err = k.Sign(rand.Reader, digest[:], &rsa.PSSOptions{Hash: crypto.SHA256})
	ts.Require().ErrorContains(err, "sign boom")

	noPub := ts.newKey(fm, pkcs11.CKK_RSA, nil)
	_, err = noPub.Sign(rand.Reader, digest[:], &rsa.PSSOptions{Hash: crypto.SHA256})
	ts.Require().ErrorContains(err, "no RSA public key")
}

func (ts *PKCS11TestSuite) TestSign_ECDSA() {
	fm := newFakeModule()
	k := ts.newKey(fm, pkcs11.CKK_EC, nil)

	raw := append(bytesOf(32, 0x01), bytesOf(32, 0x02)...)
	fm.signOut = raw
	sig, err := k.Sign(rand.Reader, make([]byte, 32), crypto.SHA256)
	ts.Require().NoError(err)
	var rs struct{ R, S *big.Int }
	_, err = asn1.Unmarshal(sig, &rs)
	ts.Require().NoError(err)
	ts.Equal(new(big.Int).SetBytes(raw[:32]), rs.R)
	ts.Equal(new(big.Int).SetBytes(raw[32:]), rs.S)
	ts.EqualValues(pkcs11.CKM_ECDSA, fm.lastMech)

	fm.signOut = []byte{1, 2, 3}
	_, err = k.Sign(rand.Reader, make([]byte, 32), crypto.SHA256)
	ts.Require().ErrorContains(err, "even-length")
	fm.signOut = nil
	_, err = k.Sign(rand.Reader, make([]byte, 32), crypto.SHA256)
	ts.Require().ErrorContains(err, "even-length")

	fm.signErr = errors.New("sign boom")
	_, err = k.Sign(rand.Reader, make([]byte, 32), crypto.SHA256)
	ts.Require().ErrorContains(err, "sign boom")
}

func (ts *PKCS11TestSuite) TestSign_EdDSAAndUnsupported() {
	fm := newFakeModule()
	k := ts.newKey(fm, ckkECEdwards, nil)
	fm.signOut = []byte("ed-sig")
	sig, err := k.Sign(rand.Reader, []byte("message"), crypto.Hash(0))
	ts.Require().NoError(err)
	ts.Equal([]byte("ed-sig"), sig)
	ts.EqualValues(ckmEDDSA, fm.lastMech)
	ts.Equal([]byte("message"), fm.lastSigned)

	fm.signErr = errors.New("sign boom")
	_, err = k.Sign(rand.Reader, []byte("message"), crypto.Hash(0))
	ts.Require().ErrorContains(err, "sign boom")

	bad := ts.newKey(fm, pkcs11.CKK_AES, nil)
	_, err = bad.Sign(rand.Reader, nil, crypto.SHA256)
	ts.Require().ErrorContains(err, "unsupported key type")
}

func (ts *PKCS11TestSuite) TestSign_RecoversFromInvalidatedHandle() {
	fm := newFakeModule()
	k := ts.newKey(fm, ckkECEdwards, nil)
	delete(fm.objects, 10)
	fm.objects[40] = fakeObject{class: pkcs11.CKO_PRIVATE_KEY, label: "k", private: true}
	fm.signInitFn = func(h pkcs11.ObjectHandle) error {
		if h == 10 {
			return p11err(pkcs11.CKR_KEY_HANDLE_INVALID)
		}
		return nil
	}
	fm.signOut = []byte("ok")
	sig, err := k.Sign(rand.Reader, []byte("m"), crypto.Hash(0))
	ts.Require().NoError(err)
	ts.Equal([]byte("ok"), sig)
	ts.EqualValues(40, fm.lastHandle)
}

func (ts *PKCS11TestSuite) TestDecrypt() {
	priv, err := rsa.GenerateKey(rand.Reader, 2048)
	ts.Require().NoError(err)
	fm := newFakeModule()
	k := ts.newKey(fm, pkcs11.CKK_RSA, &priv.PublicKey)
	oaepOpts := &rsa.OAEPOptions{Hash: crypto.SHA256}

	plaintext := []byte("a 32 byte data-encryption key..!")
	ct, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, &priv.PublicKey, plaintext, nil)
	ts.Require().NoError(err)
	fm.decryptOut = rawRSADecrypt(ts.T(), priv, ct)

	got, err := k.Decrypt(rand.Reader, ct, oaepOpts)
	ts.Require().NoError(err)
	ts.Equal(plaintext, got)
	ts.EqualValues(pkcs11.CKM_RSA_X_509, fm.lastMech)

	// A raw result that lost its leading zero bytes is left-padded.
	fm.decryptOut = fm.decryptOut[1:]
	got, err = k.Decrypt(rand.Reader, ct, oaepOpts)
	ts.Require().NoError(err)
	ts.Equal(plaintext, got)

	_, err = k.Decrypt(rand.Reader, ct, &rsa.OAEPOptions{Hash: crypto.SHA1})
	ts.Require().ErrorContains(err, "SHA-256")
	_, err = k.Decrypt(rand.Reader, ct, nil)
	ts.Require().ErrorContains(err, "SHA-256")

	fm.decryptErr = errors.New("decrypt boom")
	_, err = k.Decrypt(rand.Reader, ct, oaepOpts)
	ts.Require().ErrorContains(err, "decrypt boom")

	ec := ts.newKey(fm, pkcs11.CKK_EC, nil)
	_, err = ec.Decrypt(rand.Reader, ct, oaepOpts)
	ts.Require().ErrorContains(err, "only supported for RSA")

	noPub := ts.newKey(fm, pkcs11.CKK_RSA, nil)
	_, err = noPub.Decrypt(rand.Reader, ct, oaepOpts)
	ts.Require().ErrorContains(err, "no RSA public key")
}

func bytesOf(n int, v byte) []byte {
	b := make([]byte, n)
	for i := range b {
		b[i] = v
	}
	return b
}
