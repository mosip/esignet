package pkcs11

import (
	"crypto/elliptic"

	"github.com/btcsuite/btcd/btcec/v2"
)

// secp256k1 is not in Go's standard library (crypto/elliptic only ships the
// NIST curves, whose generic CurveParams arithmetic hardcodes a=-3 and is
// mathematically wrong for secp256k1, which has a=0). btcec's KoblitzCurve
// implements the elliptic.Curve interface with correct secp256k1 arithmetic.
// Needed here only to reconstruct an ecdsa.PublicKey for X.509 certificate
// embedding after CKA_EC_POINT is read back from the HSM — all private-key
// operations happen inside the token via CKM_ECDSA.
func secp256k1() elliptic.Curve { return btcec.S256() }
