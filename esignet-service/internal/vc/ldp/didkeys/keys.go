/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package didkeys

import (
	"bytes"
	"crypto"
	"crypto/ecdsa"
	"crypto/ed25519"
	"crypto/elliptic"
	"crypto/rsa"
	"crypto/sha256"
	"crypto/x509"
	"encoding/base64"
	"encoding/binary"
	"encoding/hex"
	"encoding/pem"
	"fmt"
	"math/big"
	"slices"
	"strings"

	"github.com/btcsuite/btcd/btcec/v2"
	"github.com/mr-tron/base58"
)

// Multicodec codes for public keys (https://github.com/multiformats/multicodec).
const (
	codecEd25519   = 0xed
	codecP256      = 0x1200
	codecSecp256k1 = 0xe7
	codecRSA       = 0x1205
)

const (
	p256CoordSize      = 32
	compressedKeySize  = 33
	uncompressedPrefix = 0x04
)

// Verification method types (W3C security vocabulary).
const (
	vmEd25519Key2018   = "Ed25519VerificationKey2018"
	vmEd25519Key2020   = "Ed25519VerificationKey2020"
	vmSecp256r1Key2019 = "EcdsaSecp256r1VerificationKey2019"
	vmSecp256k1Key2019 = "EcdsaSecp256k1VerificationKey2019"
	vmRSAKey2018       = "RsaVerificationKey2018"
	vmJSONWebKey2020   = "JsonWebKey2020"
	vmJSONWebKey       = "JsonWebKey"
	vmMultikey         = "Multikey"
)

// keyFromVM reads the public key of a verification method (a DID document
// entry or an HTTPS key document) from the first key field present, and
// checks the key against the method's declared type.
func keyFromVM(vm map[string]any) (crypto.PublicKey, error) {
	vmType, _ := vm["type"].(string)
	var pub crypto.PublicKey
	var err error
	switch {
	case vm["publicKeyJwk"] != nil:
		jwk, ok := vm["publicKeyJwk"].(map[string]any)
		if !ok {
			return nil, fmt.Errorf("%w: publicKeyJwk is not an object", ErrResolution)
		}
		pub, err = parseJWK(jwk)
	case vm["publicKeyHex"] != nil:
		pub, err = keyFromHex(stringField(vm, "publicKeyHex"), vmType)
	case vm["publicKeyPem"] != nil:
		pub, err = keyFromPEM(stringField(vm, "publicKeyPem"))
	case vm["publicKeyMultibase"] != nil:
		pub, err = keyFromMultibase(stringField(vm, "publicKeyMultibase"))
	case vm["publicKeyBase58"] != nil:
		pub, err = keyFromBase58(stringField(vm, "publicKeyBase58"), vmType)
	default:
		return nil, fmt.Errorf("%w: verification method has no supported public key field", ErrUnsupportedKey)
	}
	if err != nil {
		return nil, err
	}
	if err := checkVMType(vmType, pub); err != nil {
		return nil, err
	}
	return pub, nil
}

func stringField(m map[string]any, key string) string {
	s, _ := m[key].(string)
	return s
}

// checkVMType rejects keys that contradict their declared method type, and
// method types this package doesn't know.
func checkVMType(vmType string, pub crypto.PublicKey) error {
	ok := false
	switch vmType {
	case vmEd25519Key2018, vmEd25519Key2020:
		_, ok = pub.(ed25519.PublicKey)
	case vmSecp256r1Key2019:
		k, isEC := pub.(*ecdsa.PublicKey)
		ok = isEC && k.Curve == elliptic.P256()
	case vmSecp256k1Key2019:
		_, ok = pub.(*btcec.PublicKey)
	case vmRSAKey2018:
		_, ok = pub.(*rsa.PublicKey)
	case vmJSONWebKey2020, vmJSONWebKey, vmMultikey:
		ok = true
	default:
		return fmt.Errorf("%w: verification method type %q", ErrUnsupportedKey, vmType)
	}
	if !ok {
		return fmt.Errorf("%w: %T key in a %s verification method", ErrResolution, pub, vmType)
	}
	return nil
}

// parseJWK converts a public JWK (OKP/Ed25519, EC/P-256, EC/secp256k1, RSA)
// to a key. JWKs carrying private members are rejected.
func parseJWK(jwk map[string]any) (crypto.PublicKey, error) {
	for _, private := range []string{"d", "p", "q", "dp", "dq", "qi"} {
		if _, ok := jwk[private]; ok {
			return nil, fmt.Errorf("%w: JWK contains private member %q", ErrResolution, private)
		}
	}
	kty, _ := jwk["kty"].(string)
	crv, _ := jwk["crv"].(string)
	switch {
	case kty == "OKP" && crv == "Ed25519":
		x, err := jwkBytes(jwk, "x")
		if err != nil {
			return nil, err
		}
		if len(x) != ed25519.PublicKeySize {
			return nil, fmt.Errorf("%w: Ed25519 JWK x must be %d bytes", ErrResolution, ed25519.PublicKeySize)
		}
		return ed25519.PublicKey(x), nil
	case kty == "EC" && (crv == "P-256" || crv == "secp256k1"):
		x, err := jwkBytes(jwk, "x")
		if err != nil {
			return nil, err
		}
		y, err := jwkBytes(jwk, "y")
		if err != nil {
			return nil, err
		}
		if len(x) != p256CoordSize || len(y) != p256CoordSize {
			return nil, fmt.Errorf("%w: %s JWK coordinates must be %d bytes", ErrResolution, crv, p256CoordSize)
		}
		point := slices.Concat([]byte{uncompressedPrefix}, x, y)
		if crv == "secp256k1" {
			return parseSecp256k1(point)
		}
		return parseP256(point)
	case kty == "RSA":
		n, err := jwkBytes(jwk, "n")
		if err != nil {
			return nil, err
		}
		e, err := jwkBytes(jwk, "e")
		if err != nil {
			return nil, err
		}
		exp := new(big.Int).SetBytes(e)
		if !exp.IsInt64() || exp.Int64() < 3 || exp.Int64() > 1<<31-1 || exp.Bit(0) == 0 {
			return nil, fmt.Errorf("%w: invalid RSA exponent", ErrResolution)
		}
		return &rsa.PublicKey{N: new(big.Int).SetBytes(n), E: int(exp.Int64())}, nil
	default:
		return nil, fmt.Errorf("%w: JWK kty %q crv %q", ErrUnsupportedKey, kty, crv)
	}
}

func jwkBytes(jwk map[string]any, member string) ([]byte, error) {
	s, _ := jwk[member].(string)
	if s == "" {
		return nil, fmt.Errorf("%w: JWK is missing %q", ErrResolution, member)
	}
	b, err := base64.RawURLEncoding.DecodeString(strings.TrimRight(s, "="))
	if err != nil {
		return nil, fmt.Errorf("%w: JWK %q: %w", ErrResolution, member, err)
	}
	return b, nil
}

// decodeMulticodecKey decodes a varint multicodec header followed by the key
// bytes, as used by did:key and publicKeyMultibase.
func decodeMulticodecKey(b []byte) (crypto.PublicKey, error) {
	code, n := binary.Uvarint(b)
	if n <= 0 {
		return nil, fmt.Errorf("%w: invalid multicodec header", ErrResolution)
	}
	raw := b[n:]
	switch code {
	case codecEd25519:
		if len(raw) != ed25519.PublicKeySize {
			return nil, fmt.Errorf("%w: Ed25519 key must be %d bytes", ErrResolution, ed25519.PublicKeySize)
		}
		return ed25519.PublicKey(bytes.Clone(raw)), nil
	case codecP256:
		return parseP256(raw)
	case codecSecp256k1:
		return parseSecp256k1(raw)
	case codecRSA:
		if k, err := x509.ParsePKCS1PublicKey(raw); err == nil {
			return k, nil
		}
		return parseRSAPKIX(raw)
	default:
		return nil, fmt.Errorf("%w: multicodec 0x%x", ErrUnsupportedKey, code)
	}
}

// parseP256 accepts a compressed (33 B) or uncompressed (65 B) SEC1 point.
func parseP256(point []byte) (*ecdsa.PublicKey, error) {
	if len(point) == compressedKeySize {
		x, y := elliptic.UnmarshalCompressed(elliptic.P256(), point)
		if x == nil {
			return nil, fmt.Errorf("%w: invalid compressed P-256 point", ErrResolution)
		}
		point = make([]byte, 1+2*p256CoordSize)
		point[0] = uncompressedPrefix
		x.FillBytes(point[1 : 1+p256CoordSize])
		y.FillBytes(point[1+p256CoordSize:])
	}
	k, err := ecdsa.ParseUncompressedPublicKey(elliptic.P256(), point)
	if err != nil {
		return nil, fmt.Errorf("%w: invalid P-256 point: %w", ErrResolution, err)
	}
	return k, nil
}

func parseSecp256k1(point []byte) (*btcec.PublicKey, error) {
	k, err := btcec.ParsePubKey(point)
	if err != nil {
		return nil, fmt.Errorf("%w: invalid secp256k1 point: %w", ErrResolution, err)
	}
	return k, nil
}

func parseRSAPKIX(der []byte) (*rsa.PublicKey, error) {
	k, err := x509.ParsePKIXPublicKey(der)
	if err != nil {
		return nil, fmt.Errorf("%w: invalid RSA key: %w", ErrResolution, err)
	}
	rk, ok := k.(*rsa.PublicKey)
	if !ok {
		return nil, fmt.Errorf("%w: expected an RSA key, got %T", ErrResolution, k)
	}
	return rk, nil
}

// keyFromMultibase reads publicKeyMultibase: base58btc over a
// multicodec-prefixed key.
func keyFromMultibase(s string) (crypto.PublicKey, error) {
	if !strings.HasPrefix(s, "z") {
		return nil, fmt.Errorf("%w: publicKeyMultibase must use base58btc", ErrUnsupportedKey)
	}
	raw, err := base58.Decode(s[1:])
	if err != nil {
		return nil, fmt.Errorf("%w: publicKeyMultibase: %w", ErrResolution, err)
	}
	return decodeMulticodecKey(raw)
}

// keyFromBase58 reads the legacy publicKeyBase58 (raw key bytes).
func keyFromBase58(s, vmType string) (crypto.PublicKey, error) {
	raw, err := base58.Decode(s)
	if err != nil {
		return nil, fmt.Errorf("%w: publicKeyBase58: %w", ErrResolution, err)
	}
	return rawKey(raw, vmType)
}

// keyFromHex reads publicKeyHex: raw Ed25519 or SEC1 EC points, interpreted
// by the method type.
func keyFromHex(s, vmType string) (crypto.PublicKey, error) {
	raw, err := hex.DecodeString(s)
	if err != nil {
		return nil, fmt.Errorf("%w: publicKeyHex: %w", ErrResolution, err)
	}
	return rawKey(raw, vmType)
}

func rawKey(raw []byte, vmType string) (crypto.PublicKey, error) {
	switch vmType {
	case vmEd25519Key2018, vmEd25519Key2020:
		if len(raw) != ed25519.PublicKeySize {
			return nil, fmt.Errorf("%w: Ed25519 key must be %d bytes", ErrResolution, ed25519.PublicKeySize)
		}
		return ed25519.PublicKey(raw), nil
	case vmSecp256r1Key2019:
		return parseP256(raw)
	case vmSecp256k1Key2019:
		return parseSecp256k1(raw)
	default:
		return nil, fmt.Errorf("%w: raw key in a %q verification method", ErrUnsupportedKey, vmType)
	}
}

// keyFromPEM reads an SPKI PEM public key.
func keyFromPEM(s string) (crypto.PublicKey, error) {
	block, _ := pem.Decode([]byte(s))
	if block == nil {
		return nil, fmt.Errorf("%w: publicKeyPem is not PEM", ErrResolution)
	}
	k, err := x509.ParsePKIXPublicKey(block.Bytes)
	if err != nil {
		return nil, fmt.Errorf("%w: publicKeyPem: %w", ErrResolution, err)
	}
	return k, nil
}

// Thumbprint returns the RFC 7638 JWK thumbprint of pub: the base64url
// SHA-256 hash of its required JWK members in lexicographic order.
func Thumbprint(pub crypto.PublicKey) (string, error) {
	var members string
	switch k := pub.(type) {
	case ed25519.PublicKey:
		members = fmt.Sprintf(`{"crv":"Ed25519","kty":"OKP","x":"%s"}`, b64(k))
	case *ecdsa.PublicKey:
		point, err := k.Bytes()
		if err != nil {
			return "", fmt.Errorf("%w: %w", ErrUnsupportedKey, err)
		}
		members = ecMembers(k.Curve.Params().Name, point)
	case *btcec.PublicKey:
		members = ecMembers("secp256k1", k.SerializeUncompressed())
	case *rsa.PublicKey:
		members = fmt.Sprintf(`{"e":"%s","kty":"RSA","n":"%s"}`, b64(big.NewInt(int64(k.E)).Bytes()), b64(k.N.Bytes()))
	default:
		return "", fmt.Errorf("%w: %T", ErrUnsupportedKey, pub)
	}
	sum := sha256.Sum256([]byte(members))
	return b64(sum[:]), nil
}

// ecMembers splits an uncompressed SEC1 point into the JWK x and y members.
func ecMembers(crv string, point []byte) string {
	coords := point[1:]
	n := len(coords) / 2
	return fmt.Sprintf(`{"crv":"%s","kty":"EC","x":"%s","y":"%s"}`, crv, b64(coords[:n]), b64(coords[n:]))
}

func b64(b []byte) string {
	return base64.RawURLEncoding.EncodeToString(b)
}
