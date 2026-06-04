package jwk

import (
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"strings"
	"testing"
)

// sha256OfHex is the test oracle: lowercase hex SHA-256 of the input.
// ComputeHash must produce the same value for the same key material.
func sha256OfHex(s string) string {
	sum := sha256.Sum256([]byte(s))
	return hex.EncodeToString(sum[:])
}

func TestParse_RoundTripRSA(t *testing.T) {
	const (
		nVal = "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMstn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajrn1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw"
	)
	raw := json.RawMessage(`{"kty":"RSA","n":"` + nVal + `","e":"AQAB"}`)

	k, err := Parse(raw)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if k == nil {
		t.Fatal("Parse returned nil key")
	}
}

func TestParse_RoundTripEC(t *testing.T) {
	// P-256 sample key (RFC 7518 Appendix A.4 style).
	raw := json.RawMessage(`{"kty":"EC","crv":"P-256","x":"MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4","y":"4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM"}`)

	k, err := Parse(raw)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	if k == nil {
		t.Fatal("Parse returned nil key")
	}
}

func TestParse_Errors(t *testing.T) {
	t.Run("empty", func(t *testing.T) {
		_, err := Parse(json.RawMessage(``))
		if err == nil {
			t.Fatal("expected error on empty input")
		}
	})
	t.Run("malformed", func(t *testing.T) {
		_, err := Parse(json.RawMessage(`{not-json`))
		if err == nil {
			t.Fatal("expected error on malformed JSON")
		}
	})
	t.Run("missing kty", func(t *testing.T) {
		_, err := Parse(json.RawMessage(`{"n":"abc","e":"AQAB"}`))
		if err == nil {
			t.Fatal("expected error on missing kty")
		}
	})
}

func TestComputeHash_RSA(t *testing.T) {
	// SHA-256("abc") = ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad
	const nVal = "abc"
	raw := json.RawMessage(`{"kty":"RSA","n":"` + nVal + `","e":"AQAB"}`)

	k, err := Parse(raw)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}

	got, err := ComputeHash(k)
	if err != nil {
		t.Fatalf("ComputeHash: %v", err)
	}
	want := sha256OfHex(nVal)
	if got != want {
		t.Errorf("ComputeHash RSA: got %s, want %s", got, want)
	}
}

func TestComputeHash_EC(t *testing.T) {
	// A valid P-256 key; the digest is SHA-256(x||y).
	const (
		xVal = "MKBCTNIcKUSDii11ySs3526iDZ8AiTo7Tu6KPAqv7D4"
		yVal = "4Etl6SRW2YiLUrN5vfvVHuhp7x8PxltmWWlbbM4IFyM"
	)
	raw := json.RawMessage(`{"kty":"EC","crv":"P-256","x":"` + xVal + `","y":"` + yVal + `"}`)

	k, err := Parse(raw)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}

	got, err := ComputeHash(k)
	if err != nil {
		t.Fatalf("ComputeHash: %v", err)
	}
	want := sha256OfHex(xVal + yVal)
	if got != want {
		t.Errorf("ComputeHash EC: got %s, want %s", got, want)
	}
}

func TestComputeHash_LowercaseHex(t *testing.T) {
	// public_key_hash must be lowercase hex (matches the column's stored format).
	raw := json.RawMessage(`{"kty":"RSA","n":"ABCDEF","e":"AQAB"}`)
	k, err := Parse(raw)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	got, err := ComputeHash(k)
	if err != nil {
		t.Fatalf("ComputeHash: %v", err)
	}
	if got != strings.ToLower(got) {
		t.Errorf("ComputeHash must be lowercase hex, got %q", got)
	}
	if len(got) != 64 {
		t.Errorf("ComputeHash must be 64 hex chars (SHA-256), got len=%d", len(got))
	}
}

func TestComputeHash_UnsupportedKty(t *testing.T) {
	raw := json.RawMessage(`{"kty":"oct","k":"GawgguFyGrWKav7AX4VKUg"}`)
	k, err := Parse(raw)
	if err != nil {
		t.Fatalf("Parse: %v", err)
	}
	_, err = ComputeHash(k)
	if err == nil {
		t.Fatal("expected error for unsupported kty=oct")
	}
	if !errors.Is(err, ErrUnsupportedKeyType) {
		t.Errorf("expected ErrUnsupportedKeyType, got %v", err)
	}
}

func TestComputeHash_NilKey(t *testing.T) {
	_, err := ComputeHash(nil)
	if err == nil {
		t.Fatal("expected error on nil key")
	}
}
