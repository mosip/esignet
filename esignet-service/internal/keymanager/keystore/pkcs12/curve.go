package pkcs12

import (
	"crypto/elliptic"

	"github.com/btcsuite/btcd/btcec/v2"
)

// secp256k1 is not in Go's standard library (crypto/elliptic only ships the
// NIST curves, whose generic CurveParams arithmetic hardcodes a=-3 and is
// mathematically wrong for secp256k1, which has a=0). btcec's KoblitzCurve
// implements the elliptic.Curve interface with correct secp256k1 arithmetic.
func secp256k1() elliptic.Curve { return btcec.S256() }
