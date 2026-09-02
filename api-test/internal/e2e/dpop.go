package e2e

import (
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/json"
	"fmt"
	"math/big"
	"net/http"
	"net/url"
	"strings"
	"time"
)

// DPoP (RFC 9449) sender-constrained access tokens; the DPoP key is deliberately NOT the client's registration key, so a wrong-key proof is a meaningful negative.

// defaultDPoPAlg is used when discovery advertises no dpop_signing_alg_values_supported; it is in the shipped deployment.yaml allow-list.
const defaultDPoPAlg = "RS256"

// dpopAlgs are the algs this harness can produce, in preference order; both sign with the same RSA key, so picking between them costs nothing.
var dpopAlgs = []string{"PS256", "RS256"}

// chooseDPoPAlg picks the first alg the harness can produce that the deployment also advertises, falling back to defaultDPoPAlg rather than failing.
func chooseDPoPAlg(supported []string) string {
	for _, want := range dpopAlgs {
		for _, got := range supported {
			if strings.EqualFold(want, got) {
				return want
			}
		}
	}
	return defaultDPoPAlg
}

// dpopSigner mints the DPoP proofs for one scenario's flow, shared across PAR, token and userinfo so the shared jkt is the binding the server enforces.
type dpopSigner struct {
	priv *rsa.PrivateKey
	alg  string
	jwk  map[string]any // exactly the RFC 7638 members, so it also serves as the proof header's jwk
	jkt  string         // base64url(SHA-256(canonical jwk)) — the cnf.jkt the token carries

	// nonce is the most recent DPoP-Nonce the server issued; the next proof replays it.
	nonce string
}

// newDPoPSigner generates a fresh proof key and precomputes its thumbprint.
func newDPoPSigner(alg string) (*dpopSigner, error) {
	priv, err := generateRSA()
	if err != nil {
		return nil, fmt.Errorf("dpop keygen: %w", err)
	}
	// Only kty/n/e: the thumbprint is defined over exactly these members.
	jwk := map[string]any{
		"kty": "RSA",
		"n":   b64(priv.PublicKey.N.Bytes()),
		"e":   b64(big.NewInt(int64(priv.PublicKey.E)).Bytes()),
	}
	jkt, err := jwkThumbprint(jwk)
	if err != nil {
		return nil, err
	}
	return &dpopSigner{priv: priv, alg: alg, jwk: jwk, jkt: jkt}, nil
}

// jwkThumbprint computes the RFC 7638 SHA-256 thumbprint of an RSA JWK: required members only, lexicographically ordered, no whitespace.
func jwkThumbprint(jwk map[string]any) (string, error) {
	kty, _ := jwk["kty"].(string)
	n, _ := jwk["n"].(string)
	e, _ := jwk["e"].(string)
	if kty != "RSA" || n == "" || e == "" {
		return "", fmt.Errorf("thumbprint: not an RSA JWK with n and e")
	}
	// Assembled by hand rather than marshalled from a map, since a generic encoder guarantees neither member order nor absent whitespace.
	eb, err := json.Marshal(e)
	if err != nil {
		return "", err
	}
	kb, err := json.Marshal(kty)
	if err != nil {
		return "", err
	}
	nb, err := json.Marshal(n)
	if err != nil {
		return "", err
	}
	sum := sha256.Sum256([]byte(`{"e":` + string(eb) + `,"kty":` + string(kb) + `,"n":` + string(nb) + `}`))
	return b64(sum[:]), nil
}

// proof returns a DPoP proof JWT for one request; accessToken is passed only at resource endpoints, adding the ath claim that ties the proof to that token.
func (d *dpopSigner) proof(method, uri, accessToken string) (string, error) {
	jti := make([]byte, 16)
	if _, err := rand.Read(jti); err != nil {
		return "", fmt.Errorf("dpop jti: %w", err)
	}
	claims := map[string]any{
		"jti": b64(jti),
		"htm": strings.ToUpper(method),
		"htu": htu(uri),
		"iat": time.Now().Unix(),
	}
	if accessToken != "" {
		sum := sha256.Sum256([]byte(accessToken))
		claims["ath"] = b64(sum[:])
	}
	if d.nonce != "" {
		claims["nonce"] = d.nonce
	}
	return signJWS(d.priv, d.alg, map[string]any{"typ": "dpop+jwt", "jwk": d.jwk}, claims)
}

// htu strips the query and fragment, which RFC 9449 excludes from the htu claim.
func htu(raw string) string {
	u, err := url.Parse(raw)
	if err != nil {
		return raw
	}
	u.RawQuery = ""
	u.Fragment = ""
	u.RawFragment = ""
	return u.String()
}

// dpopNonceFrom records a server-issued DPoP nonce and reports whether it is new.
func (d *dpopSigner) dpopNonceFrom(h http.Header) bool {
	n := strings.TrimSpace(h.Get("DPoP-Nonce"))
	if n == "" || n == d.nonce {
		return false
	}
	d.nonce = n
	return true
}

// isDPoPNonceError reports whether a response body is the use_dpop_nonce error asking the client to retry with the DPoP-Nonce header's value.
func isDPoPNonceError(body []byte) bool {
	var e struct {
		Error string `json:"error"`
	}
	if json.Unmarshal(body, &e) != nil {
		return false
	}
	return e.Error == "use_dpop_nonce"
}
