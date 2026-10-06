package signature_test

import (
	"crypto/ecdsa"
	"crypto/elliptic"
	"crypto/rand"
	"crypto/sha256"
	"encoding/asn1"
	"math/big"

	"github.com/mosip/esignet/internal/keymanager/signature"
)

// TestDerToConcat_RoundTrip generates a real ECDSA signature, DER-encodes
// it, converts it via the exported test hook (see export_test.go), and
// confirms the resulting r||s decodes back to the original r,s — the same
// property Java's EcdsaUsingShaAlgorithm.convertDerToConcatenated must hold.
func (ts *SignatureTestSuite) TestDerToConcat_RoundTrip() {
	t := ts.T()
	priv, err := ecdsa.GenerateKey(elliptic.P256(), rand.Reader)
	if err != nil {
		t.Fatalf("generate key: %v", err)
	}
	digest := sha256.Sum256([]byte("derToConcat round trip"))
	r, s, err := ecdsa.Sign(rand.Reader, priv, digest[:])
	if err != nil {
		t.Fatalf("ecdsa.Sign: %v", err)
	}
	der, err := asn1.Marshal(struct{ R, S *big.Int }{r, s})
	if err != nil {
		t.Fatalf("asn1.Marshal: %v", err)
	}

	concat, err := signature.DerToConcat(der, 64)
	if err != nil {
		t.Fatalf("DerToConcat: %v", err)
	}
	if len(concat) != 64 {
		t.Fatalf("expected 64-byte concatenated signature, got %d", len(concat))
	}

	gotR := new(big.Int).SetBytes(concat[:32])
	gotS := new(big.Int).SetBytes(concat[32:])
	if gotR.Cmp(r) != 0 {
		t.Errorf("R mismatch: got %x, want %x", gotR, r)
	}
	if gotS.Cmp(s) != 0 {
		t.Errorf("S mismatch: got %x, want %x", gotS, s)
	}

	// The concatenated form must itself verify against the original digest.
	if !ecdsa.Verify(&priv.PublicKey, digest[:], gotR, gotS) {
		t.Error("reconstructed R,S failed to verify against the original digest")
	}
}

// TestDerToConcat_LeftZeroPadded confirms small R/S values are left-padded
// to the full half-length, not left short — JOSE requires fixed-length
// components.
func (ts *SignatureTestSuite) TestDerToConcat_LeftZeroPadded() {
	t := ts.T()
	// R and S both small enough to encode in far fewer than 32 bytes.
	der, err := asn1.Marshal(struct{ R, S *big.Int }{big.NewInt(1), big.NewInt(2)})
	if err != nil {
		t.Fatalf("asn1.Marshal: %v", err)
	}
	concat, err := signature.DerToConcat(der, 64)
	if err != nil {
		t.Fatalf("DerToConcat: %v", err)
	}
	if len(concat) != 64 {
		t.Fatalf("expected 64 bytes, got %d", len(concat))
	}
	if concat[31] != 1 || concat[63] != 2 {
		t.Errorf("expected R/S in the low byte of each half, got % x", concat)
	}
	for _, b := range concat[:31] {
		if b != 0 {
			t.Errorf("expected R half left-zero-padded, got % x", concat[:32])
			break
		}
	}
}

func (ts *SignatureTestSuite) TestDerToConcat_MalformedInput() {
	t := ts.T()
	if _, err := signature.DerToConcat([]byte("not DER"), 64); err == nil {
		t.Fatal("expected an error for malformed DER input")
	}
}

// TestAlgorithmForRefID_MatchesJavaMapping confirms the refID -> JWS
// algorithm mapping matches Java's SignatureUtil.getSignAlgorithm exactly.
func (ts *SignatureTestSuite) TestAlgorithmForRefID_MatchesJavaMapping() {
	t := ts.T()
	cases := []struct {
		refID, want string
	}{
		{"EC_SECP256R1_SIGN", "ES256"},
		{"EC_SECP256K1_SIGN", "ES256K"},
		{"ED25519_SIGN", "EdDSA"},
		{"RSA_2048", "PS256"},
		{"", "PS256"},
		{"SOME_OTHER_REF_ID", "PS256"},
	}
	for _, c := range cases {
		if got := signature.AlgorithmForRefID(c.refID); got != c.want {
			t.Errorf("algorithmForRefID(%q) = %q, want %q", c.refID, got, c.want)
		}
	}
}
