package e2e

import (
	"context"
	"crypto"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha256"
	"encoding/json"
	"math/big"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"
)

func boolPtr(b bool) *bool { return &b }

func TestClientConfigAdditionalConfig(t *testing.T) {
	// An unhardened client must register exactly as it did before these fields
	// existed — no additionalConfig member at all.
	if got := (ClientConfig{}).additionalConfig(); got != nil {
		t.Errorf("zero config produced additionalConfig %v, want nil", got)
	}

	got := ClientConfig{RequirePKCE: true}.additionalConfig()
	want := map[string]any{
		"require_pkce":                          true,
		"require_pushed_authorization_requests": false,
		"dpop_bound_access_tokens":              false,
	}
	if len(got) != len(want) {
		t.Fatalf("additionalConfig = %v, want %v", got, want)
	}
	for k, v := range want {
		if got[k] != v {
			t.Errorf("additionalConfig[%q] = %v, want %v", k, got[k], v)
		}
	}
}

func TestClientConfigKeyDistinguishesEveryCombination(t *testing.T) {
	seen := map[string]ClientConfig{}
	for _, pkce := range []bool{false, true} {
		for _, par := range []bool{false, true} {
			for _, dpop := range []bool{false, true} {
				c := ClientConfig{RequirePKCE: pkce, RequirePAR: par, DPoPBound: dpop}
				if prev, dup := seen[c.key()]; dup {
					t.Fatalf("key %q collides: %+v and %+v would share a client", c.key(), prev, c)
				}
				seen[c.key()] = c
			}
		}
	}
	if len(seen) != 8 {
		t.Errorf("got %d distinct keys, want 8", len(seen))
	}
	// A nil client_config must land on the same key as an explicit all-off one,
	// so the consent scenarios keep sharing one client with the plain ones.
	if (ClientConfig{}).key() != (ClientConfig{RequirePKCE: false}).key() {
		t.Error("zero and explicitly-false configs must share a key")
	}
}

func TestResolveFlowFollowsClientConfigByDefault(t *testing.T) {
	cfg := ClientConfig{RequirePAR: true, DPoPBound: true}
	plan, err := resolveFlow(cfg, nil)
	if err != nil {
		t.Fatalf("resolveFlow: %v", err)
	}
	if !plan.usePAR || !plan.useDPoP {
		t.Errorf("plan = %+v, want PAR and DPoP on to match the client config", plan)
	}
	// PKCE is always sent, whether or not the client demands it: the harness
	// has always sent S256 and an unhardened client still accepts it.
	if plan.pkce != pkceS256 {
		t.Errorf("pkce = %q, want %q", plan.pkce, pkceS256)
	}
}

func TestResolveFlowOverridesAgainstClientConfig(t *testing.T) {
	cfg := ClientConfig{RequirePAR: true, DPoPBound: true}
	plan, err := resolveFlow(cfg, &FlowSpec{UsePAR: boolPtr(false), UseDPoP: boolPtr(false)})
	if err != nil {
		t.Fatalf("resolveFlow: %v", err)
	}
	// This disagreement is the whole point of a negative case: the client
	// requires both, the RP sends neither, and the server must refuse.
	if plan.usePAR || plan.useDPoP {
		t.Errorf("plan = %+v, want both overridden off", plan)
	}
}

func TestResolveFlowPKCEModes(t *testing.T) {
	for _, tc := range []struct{ in, want string }{
		{"S256", pkceS256},
		{"s256", pkceS256},
		{"plain", pkcePlain},
		{"none", pkceNone},
		{"", pkceS256},
	} {
		plan, err := resolveFlow(ClientConfig{}, &FlowSpec{PKCE: tc.in})
		if err != nil {
			t.Fatalf("resolveFlow(%q): %v", tc.in, err)
		}
		if plan.pkce != tc.want {
			t.Errorf("pkce %q -> %q, want %q", tc.in, plan.pkce, tc.want)
		}
	}
	// A typo must not silently fall back to S256 and turn a negative case into
	// a vacuous positive.
	if _, err := resolveFlow(ClientConfig{}, &FlowSpec{PKCE: "S512"}); err == nil {
		t.Error("resolveFlow accepted an unknown pkce mode")
	}
}

func TestChooseDPoPAlg(t *testing.T) {
	for _, tc := range []struct {
		name      string
		supported []string
		want      string
	}{
		{"prefers PS256 when both offered", []string{"RS256", "PS256"}, "PS256"},
		{"falls back to RS256 when it is all that is offered", []string{"RS256"}, "RS256"},
		{"ignores algs the harness cannot produce", []string{"ES256", "EdDSA", "RS256"}, "RS256"},
		{"case insensitive", []string{"ps256"}, "PS256"},
		// Neither an empty advertisement nor an all-unsupported one is fatal:
		// sending a proof produces a real server error to read, which is more
		// useful than a harness-side guess about what would have happened.
		{"empty advertisement falls back", nil, defaultDPoPAlg},
		{"no producible alg falls back", []string{"ES512"}, defaultDPoPAlg},
	} {
		t.Run(tc.name, func(t *testing.T) {
			if got := chooseDPoPAlg(tc.supported); got != tc.want {
				t.Errorf("chooseDPoPAlg(%v) = %q, want %q", tc.supported, got, tc.want)
			}
		})
	}
}

// RFC 7638 section 3.1's worked example, which pins both the member ordering
// and the absence of whitespace in the hashed form.
func TestJWKThumbprintMatchesRFC7638Example(t *testing.T) {
	jwk := map[string]any{
		"kty": "RSA",
		"n": "0vx7agoebGcQSuuPiLJXZptN9nndrQmbXEps2aiAFbWhM78LhWx4cbbfAAtVT86zwu1RK7aPFFxuhDR1L6tSoc_BJECPebWKRXjBZCiFV4n3oknjhMs" +
			"tn64tZ_2W-5JsGY4Hc5n9yBXArwl93lqt7_RN5w6Cf0h4QyQ5v-65YGjQR0_FDW2QvzqY368QQMicAtaSqzs8KJZgnYb9c7d0zgdAZHzu6qMQvRL5hajr" +
			"n1n91CbOpbISD08qNLyrdkt-bFTWhAI4vMQFh6WeZu0fM4lFd2NcRwr3XPksINHaQ-G_xBniIqbw0Ls1jF44-csFCur-kEgU8awapJzKnqDKgw",
		"e": "AQAB",
	}
	const want = "NzbLsXh8uDCcd-6MNwXF4W_7noWXFZAfHkxZsRGC9Xs"
	got, err := jwkThumbprint(jwk)
	if err != nil {
		t.Fatalf("jwkThumbprint: %v", err)
	}
	if got != want {
		t.Errorf("thumbprint = %q, want %q", got, want)
	}
}

func TestJWKThumbprintRejectsNonRSA(t *testing.T) {
	if _, err := jwkThumbprint(map[string]any{"kty": "EC", "crv": "P-256"}); err == nil {
		t.Error("accepted a non-RSA JWK")
	}
}

func TestNewDPoPSignerProofShape(t *testing.T) {
	d, err := newDPoPSigner("PS256")
	if err != nil {
		t.Fatalf("newDPoPSigner: %v", err)
	}
	// The header jwk carries only the thumbprint members, so the embedded key
	// and the advertised jkt cannot drift apart — and so no private member can
	// leak into a proof, which the server rejects outright.
	if len(d.jwk) != 3 {
		t.Errorf("proof jwk has %d members (%v), want exactly kty/n/e", len(d.jwk), d.jwk)
	}
	if jkt, err := jwkThumbprint(d.jwk); err != nil || jkt != d.jkt {
		t.Errorf("jkt %q does not match its own jwk (%q, err %v)", d.jkt, jkt, err)
	}

	proof, err := d.proof(http.MethodPost, "https://esignet.example.org/oauth2/token?ignored=1", "")
	if err != nil {
		t.Fatalf("proof: %v", err)
	}
	parts := strings.Split(proof, ".")
	if len(parts) != 3 {
		t.Fatalf("proof is not a 3-part JWS: %q", proof)
	}
	hb, err := b64urlDecode(parts[0])
	if err != nil {
		t.Fatalf("decode header: %v", err)
	}
	var hdr map[string]any
	if err := json.Unmarshal(hb, &hdr); err != nil {
		t.Fatalf("header not JSON: %v", err)
	}
	if hdr["typ"] != "dpop+jwt" {
		t.Errorf("typ = %v, want dpop+jwt", hdr["typ"])
	}
	if hdr["alg"] != "PS256" {
		t.Errorf("alg = %v, want PS256", hdr["alg"])
	}

	claims, err := decodeJWTPayload(proof)
	if err != nil {
		t.Fatalf("decode payload: %v", err)
	}
	if claims["htm"] != "POST" {
		t.Errorf("htm = %v, want POST", claims["htm"])
	}
	// The query string must not appear in htu: the server canonicalises it away
	// before comparing, so including it would never match.
	if claims["htu"] != "https://esignet.example.org/oauth2/token" {
		t.Errorf("htu = %v, want the query stripped", claims["htu"])
	}
	if _, ok := claims["ath"]; ok {
		t.Error("ath present on a proof with no access token")
	}
	if claims["jti"] == "" || claims["jti"] == nil {
		t.Error("proof has no jti")
	}
}

func TestDPoPProofBindsAccessTokenWithATH(t *testing.T) {
	d, err := newDPoPSigner("RS256")
	if err != nil {
		t.Fatalf("newDPoPSigner: %v", err)
	}
	proof, err := d.proof(http.MethodGet, "https://esignet.example.org/oidc/userinfo", "the-access-token")
	if err != nil {
		t.Fatalf("proof: %v", err)
	}
	claims, err := decodeJWTPayload(proof)
	if err != nil {
		t.Fatalf("decode payload: %v", err)
	}
	// Without ath, a proof minted for the token endpoint could be replayed
	// against userinfo with a different token.
	ath, _ := claims["ath"].(string)
	if ath == "" {
		t.Fatal("no ath on a resource-endpoint proof")
	}
	other, err := d.proof(http.MethodGet, "https://esignet.example.org/oidc/userinfo", "a-different-token")
	if err != nil {
		t.Fatalf("proof: %v", err)
	}
	otherClaims, _ := decodeJWTPayload(other)
	if otherClaims["ath"] == ath {
		t.Error("ath is the same for two different access tokens")
	}
}

func TestDPoPProofJTIIsUnique(t *testing.T) {
	d, err := newDPoPSigner("RS256")
	if err != nil {
		t.Fatalf("newDPoPSigner: %v", err)
	}
	// The server records each jti to reject replays, so a repeated one would
	// make the second call of any two-call flow fail.
	seen := map[string]bool{}
	for i := 0; i < 10; i++ {
		proof, err := d.proof(http.MethodPost, "https://esignet.example.org/oauth2/token", "")
		if err != nil {
			t.Fatalf("proof: %v", err)
		}
		claims, _ := decodeJWTPayload(proof)
		jti, _ := claims["jti"].(string)
		if seen[jti] {
			t.Fatalf("jti %q reused", jti)
		}
		seen[jti] = true
	}
}

func TestDPoPNonceHandshake(t *testing.T) {
	d, err := newDPoPSigner("RS256")
	if err != nil {
		t.Fatalf("newDPoPSigner: %v", err)
	}
	if !isDPoPNonceError([]byte(`{"error":"use_dpop_nonce"}`)) {
		t.Error("use_dpop_nonce not recognised")
	}
	if isDPoPNonceError([]byte(`{"error":"invalid_dpop_proof"}`)) {
		t.Error("a different error was read as a nonce request")
	}

	h := http.Header{}
	h.Set("DPoP-Nonce", "server-nonce-1")
	if !d.dpopNonceFrom(h) {
		t.Fatal("a fresh nonce was not taken")
	}
	// Re-taking the same nonce would loop the retry forever against a server
	// that keeps rejecting for some other reason.
	if d.dpopNonceFrom(h) {
		t.Error("the same nonce was taken twice")
	}
	proof, err := d.proof(http.MethodPost, "https://esignet.example.org/oauth2/token", "")
	if err != nil {
		t.Fatalf("proof: %v", err)
	}
	claims, _ := decodeJWTPayload(proof)
	if claims["nonce"] != "server-nonce-1" {
		t.Errorf("nonce = %v, want the one the server issued", claims["nonce"])
	}
}

// PS256 is the alg the FAPI2-configured deployments advertise, so a proof
// signed with it has to verify as RSA-PSS rather than PKCS#1 v1.5.
func TestSignJWSPS256VerifiesAsPSS(t *testing.T) {
	priv, err := generateRSA()
	if err != nil {
		t.Fatalf("keygen: %v", err)
	}
	token, err := signJWS(priv, "PS256", map[string]any{"typ": "dpop+jwt"}, map[string]any{"jti": "x"})
	if err != nil {
		t.Fatalf("signJWS: %v", err)
	}
	parts := strings.Split(token, ".")
	sig, err := b64urlDecode(parts[2])
	if err != nil {
		t.Fatalf("decode sig: %v", err)
	}
	sum := sha256.Sum256([]byte(parts[0] + "." + parts[1]))
	if err := rsa.VerifyPSS(&priv.PublicKey, crypto.SHA256, sum[:], sig, nil); err != nil {
		t.Errorf("PS256 signature does not verify as PSS: %v", err)
	}
	// And must NOT verify as PKCS#1 v1.5, or the alg header would be a lie.
	if err := rsa.VerifyPKCS1v15(&priv.PublicKey, crypto.SHA256, sum[:], sig); err == nil {
		t.Error("a PS256 signature verified as PKCS#1 v1.5")
	}
}

// verifyJWS must hold a PS256 signature to the salt length RFC 7518 fixes it
// at. Auto-detecting the salt would verify the maximal-salt signature below,
// which no conforming JWS producer emits — and a harness that exists to surface
// target-side deviations must not be the thing that hides one.
func TestVerifyJWSRejectsANonStandardPS256Salt(t *testing.T) {
	priv, err := generateRSA()
	if err != nil {
		t.Fatalf("keygen: %v", err)
	}

	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, _ *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		_ = json.NewEncoder(w).Encode(map[string]any{"keys": []jwksKey{{
			Kid: "k1",
			Kty: "RSA",
			N:   b64(priv.PublicKey.N.Bytes()),
			E:   b64(big.NewInt(int64(priv.PublicKey.E)).Bytes()),
		}}})
	}))
	defer srv.Close()

	// Same signing input either way; only the salt length differs.
	signWithSalt := func(saltLen int) string {
		hdr := b64([]byte(`{"alg":"PS256","kid":"k1"}`))
		pl := b64([]byte(`{"sub":"u1"}`))
		sum := sha256.Sum256([]byte(hdr + "." + pl))
		sig, serr := rsa.SignPSS(rand.Reader, priv, crypto.SHA256, sum[:],
			&rsa.PSSOptions{SaltLength: saltLen, Hash: crypto.SHA256})
		if serr != nil {
			t.Fatalf("SignPSS(salt=%d): %v", saltLen, serr)
		}
		return hdr + "." + pl + "." + b64(sig)
	}

	// The conforming signature is the control: if this does not verify, the
	// rejection below proves nothing.
	if err := verifyJWS(context.Background(), signWithSalt(rsa.PSSSaltLengthEqualsHash), srv.URL, true); err != nil {
		t.Fatalf("a hash-length-salt PS256 JWS failed to verify: %v", err)
	}
	if err := verifyJWS(context.Background(), signWithSalt(rsa.PSSSaltLengthAuto), srv.URL, true); err == nil {
		t.Error("a PS256 JWS with a maximal salt was accepted; RFC 7518 fixes the salt at the hash length")
	}
}

func TestSignJWSRejectsUnknownAlg(t *testing.T) {
	priv, err := generateRSA()
	if err != nil {
		t.Fatalf("keygen: %v", err)
	}
	if _, err := signJWS(priv, "HS256", nil, map[string]any{}); err == nil {
		t.Error("signJWS accepted an alg it cannot produce")
	}
}

func TestSignJWSAlgHeaderAlwaysMatchesTheScheme(t *testing.T) {
	priv, err := generateRSA()
	if err != nil {
		t.Fatalf("keygen: %v", err)
	}
	// A caller-supplied alg must not survive: header and signature scheme
	// disagreeing is exactly what a verifier rejects.
	token, err := signJWS(priv, "PS256", map[string]any{"alg": "RS256"}, map[string]any{})
	if err != nil {
		t.Fatalf("signJWS: %v", err)
	}
	hb, err := b64urlDecode(strings.Split(token, ".")[0])
	if err != nil {
		t.Fatalf("decode header: %v", err)
	}
	var hdr map[string]any
	if err := json.Unmarshal(hb, &hdr); err != nil {
		t.Fatalf("header not JSON: %v", err)
	}
	if hdr["alg"] != "PS256" {
		t.Errorf("alg = %v, want PS256 to have overridden the caller's value", hdr["alg"])
	}
}

func TestFlowPlanLabel(t *testing.T) {
	plan, err := resolveFlow(ClientConfig{RequirePAR: true, DPoPBound: true}, nil)
	if err != nil {
		t.Fatalf("resolveFlow: %v", err)
	}
	label := plan.label()
	for _, want := range []string{"pkce=S256", "par", "dpop"} {
		if !strings.Contains(label, want) {
			t.Errorf("label %q does not mention %q", label, want)
		}
	}
}

func TestClientConfigLabel(t *testing.T) {
	for _, tc := range []struct {
		cfg  ClientConfig
		want string
	}{
		{ClientConfig{}, "none"},
		{ClientConfig{RequirePKCE: true}, "pkce"},
		{ClientConfig{RequirePKCE: true, RequirePAR: true, DPoPBound: true}, "pkce+par+dpop"},
	} {
		if got := tc.cfg.label(); got != tc.want {
			t.Errorf("label(%+v) = %q, want %q", tc.cfg, got, tc.want)
		}
	}
}

// Guards the assumption publicJWK and the DPoP jwk both rely on: a big.Int
// round-trip of the RSA exponent.
func TestDPoPJWKExponentRoundTrips(t *testing.T) {
	d, err := newDPoPSigner("RS256")
	if err != nil {
		t.Fatalf("newDPoPSigner: %v", err)
	}
	e, ok := d.jwk["e"].(string)
	if !ok {
		t.Fatalf("jwk e is %T, want string", d.jwk["e"])
	}
	eb, err := b64urlDecode(e)
	if err != nil {
		t.Fatalf("decode e: %v", err)
	}
	if got := int(new(big.Int).SetBytes(eb).Int64()); got != d.priv.E {
		t.Errorf("e round-tripped to %d, want %d", got, d.priv.E)
	}
}
