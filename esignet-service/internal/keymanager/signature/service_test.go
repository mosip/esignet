package signature_test

import (
	"context"
	"encoding/base64"
	"encoding/json"
	"errors"
	"testing"

	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/signature"
)

// newTestServices wires a keymanager.Service (backed by stateQuerier +
// fakeKeyStore) and a signature.Service on top of it, and provisions ROOT —
// every non-ROOT signing key in the hierarchy is signed by ROOT (directly,
// for EC/EdDSA sign keys and Component Master Keys alike), so ROOT must
// exist before any of them can be generated. Mirrors the setup every
// keymanager_test integration test performs.
func newTestServices(t *testing.T) (*keymanager.Service, *signature.Service) {
	t.Helper()
	return newTestServicesWithConfig(t, nil)
}

// newTestServicesWithConfig is newTestServices with an optional
// SymmetricKeyAllowedRefIDs override, for tests covering the AES-reserved
// reference id rejection.
func newTestServicesWithConfig(t *testing.T, symmetricKeyAllowedRefIDs []string) (*keymanager.Service, *signature.Service) {
	t.Helper()
	q := newStateQuerier()
	km := keymanager.NewServiceWithQuerier(q, newFakeKeyStore(), keymanager.Config{
		AsymmetricKeyLength:       2048,
		CertCommonName:            "www.mosip.io",
		CertOrganizationUnit:      "thunder-tech-team",
		CertOrganization:          "IIITB",
		CertLocation:              "Bangalore",
		CertState:                 "KA",
		CertCountry:               "IN",
		SymmetricKeyAllowedRefIDs: symmetricKeyAllowedRefIDs,
	})

	_, err := km.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: "ROOT",
		ObjectType:    keymanager.ObjectTypeCertificate,
		CommonName:    "MOSIP Root CA",
	})
	if err != nil {
		t.Fatalf("provision ROOT: %v", err)
	}

	return km, signature.NewService(km)
}

// provisionSignKey generates a signing key for (appID, refID) via
// GenerateMasterKey — the only way any resident key tier (Component Master
// Key or EC/EdDSA sign key) is originated; GetSigningCertificate only
// resolves/rotates an already-existing one (see keymanager's
// ensureCurrentKey doc comments).
func provisionSignKey(t *testing.T, km *keymanager.Service, appID, refID string) {
	t.Helper()
	_, err := km.GenerateMasterKey(context.Background(), keymanager.GenerateMasterKeyRequest{
		ApplicationID: appID,
		ReferenceID:   refID,
		ObjectType:    keymanager.ObjectTypeCertificate,
		CommonName:    "test signing key",
	})
	if err != nil {
		t.Fatalf("provision signing key %s/%s: %v", appID, refID, err)
	}
}

func b64url(s string) string { return base64.RawURLEncoding.EncodeToString([]byte(s)) }

// TestJWSSignVerify_RoundTrip_AllAlgorithms exercises every algorithm this
// port implements end to end (sign then verify) against a real in-memory
// key pair + certificate — not mocked crypto. ES256K is intentionally
// excluded: real SECP256K1 certificate generation is unsupported by both
// keystore backends in this Go port today (a pre-existing, documented
// limitation — see fakeKeyStore's generateTestKeyPair comment), so it can't
// be exercised end to end via GetSigningCertificate; its signing/
// verification logic is identical to ES256's (see sign.go's signDigest —
// both share the same case) and is covered by that path plus
// TestAlgorithmForRefID_MatchesJavaMapping/TestDerToConcat_* in sign_test.go.
func TestJWSSignVerify_RoundTrip_AllAlgorithms(t *testing.T) {
	cases := []struct {
		name  string
		appID string
		refID string
	}{
		{"PS256 via Component Master Key", "TESTAPP", keymanager.RefIDRSA2048},
		{"ES256 via EC sign key", "TESTAPP", keymanager.RefIDECSECP256R1Sign},
		{"EdDSA via ED25519 sign key", "TESTAPP", keymanager.RefIDED25519Sign},
	}

	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			km, sig := newTestServices(t)
			provisionSignKey(t, km, tc.appID, tc.refID)

			payload := b64url(`{"hello":"world"}`)
			signResp, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
				ApplicationID: tc.appID,
				ReferenceID:   tc.refID,
				DataToSign:    payload,
			})
			if err != nil {
				t.Fatalf("JWSSign: %v", err)
			}
			if signResp.JWTSignedData == "" {
				t.Fatal("expected a non-empty compact JWS")
			}

			verifyResp, err := sig.JWSVerify(context.Background(), signature.JWSVerifyRequest{
				JWTSignatureData: signResp.JWTSignedData,
				ApplicationID:    tc.appID,
				ReferenceID:      tc.refID,
			})
			if err != nil {
				t.Fatalf("JWSVerify: %v", err)
			}
			if !verifyResp.SignatureValid {
				t.Errorf("expected signature to be valid, got invalid: %s", verifyResp.Message)
			}
		})
	}
}

func TestJWSSignVerify_DetachedPayload_RoundTrip(t *testing.T) {
	km, sig := newTestServices(t)
	provisionSignKey(t, km, "TESTAPP", keymanager.RefIDECSECP256R1Sign)

	payload := b64url(`{"detached":true}`)
	includePayload := false
	signResp, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
		DataToSign: payload, IncludePayload: &includePayload,
	})
	if err != nil {
		t.Fatalf("JWSSign: %v", err)
	}

	verifyResp, err := sig.JWSVerify(context.Background(), signature.JWSVerifyRequest{
		JWTSignatureData: signResp.JWTSignedData,
		ActualData:       payload,
		ApplicationID:    "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
	})
	if err != nil {
		t.Fatalf("JWSVerify: %v", err)
	}
	if !verifyResp.SignatureValid {
		t.Errorf("expected detached-payload verification to succeed, got: %s", verifyResp.Message)
	}

	// Without ActualData, verification of a detached JWS must fail (empty
	// payload segment doesn't match what was actually signed).
	verifyResp2, err := sig.JWSVerify(context.Background(), signature.JWSVerifyRequest{
		JWTSignatureData: signResp.JWTSignedData,
		ApplicationID:    "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
	})
	if err != nil {
		t.Fatalf("JWSVerify (no ActualData): %v", err)
	}
	if verifyResp2.SignatureValid {
		t.Error("expected verification without ActualData to fail for a detached JWS")
	}
}

func TestJWSSignVerify_B64False_RFC7797_RoundTrip(t *testing.T) {
	km, sig := newTestServices(t)
	provisionSignKey(t, km, "TESTAPP", keymanager.RefIDED25519Sign)

	payload := b64url(`{"unencoded":true}`)
	b64False := false
	signResp, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDED25519Sign,
		DataToSign: payload, B64: &b64False,
	})
	if err != nil {
		t.Fatalf("JWSSign: %v", err)
	}

	verifyResp, err := sig.JWSVerify(context.Background(), signature.JWSVerifyRequest{
		JWTSignatureData: signResp.JWTSignedData,
		ApplicationID:    "TESTAPP", ReferenceID: keymanager.RefIDED25519Sign,
	})
	if err != nil {
		t.Fatalf("JWSVerify: %v", err)
	}
	if !verifyResp.SignatureValid {
		t.Errorf("expected b64=false round trip to verify, got: %s", verifyResp.Message)
	}
}

func TestJWSVerify_TamperedSignature_Invalid(t *testing.T) {
	km, sig := newTestServices(t)
	provisionSignKey(t, km, "TESTAPP", keymanager.RefIDECSECP256R1Sign)

	signResp, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
		DataToSign: b64url(`{"a":1}`),
	})
	if err != nil {
		t.Fatalf("JWSSign: %v", err)
	}

	tampered := signResp.JWTSignedData[:len(signResp.JWTSignedData)-4] + "AAAA"
	verifyResp, err := sig.JWSVerify(context.Background(), signature.JWSVerifyRequest{
		JWTSignatureData: tampered,
		ApplicationID:    "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
	})
	if err != nil {
		t.Fatalf("JWSVerify: %v", err)
	}
	if verifyResp.SignatureValid {
		t.Error("expected a tampered signature to fail verification")
	}
}

func TestJWSVerify_EmbeddedCertificate_NoKeymanagerLookupNeeded(t *testing.T) {
	km, sig := newTestServices(t)
	provisionSignKey(t, km, "TESTAPP", keymanager.RefIDECSECP256R1Sign)

	includeCert := true
	signResp, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
		DataToSign: b64url(`{"a":1}`), IncludeCertificate: &includeCert,
	})
	if err != nil {
		t.Fatalf("JWSSign: %v", err)
	}

	// No ApplicationID/ReferenceID/CertificatePEM given — verification must
	// still succeed by extracting the leaf certificate embedded in the
	// header's x5c (explicitly requested above; IncludeCertificate now
	// defaults to false — certificate embedding is opt-in).
	verifyResp, err := sig.JWSVerify(context.Background(), signature.JWSVerifyRequest{
		JWTSignatureData: signResp.JWTSignedData,
	})
	if err != nil {
		t.Fatalf("JWSVerify: %v", err)
	}
	if !verifyResp.SignatureValid {
		t.Errorf("expected header-embedded certificate to be sufficient, got: %s", verifyResp.Message)
	}
}

func TestJWSSign_BlankDataToSign_Rejected(t *testing.T) {
	_, sig := newTestServices(t)
	_, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
	})
	if err == nil {
		t.Fatal("expected an error for blank DataToSign")
	}
}

func TestJWSSign_InvalidJSON_RejectedByDefault(t *testing.T) {
	km, sig := newTestServices(t)
	provisionSignKey(t, km, "TESTAPP", keymanager.RefIDECSECP256R1Sign)

	_, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
		DataToSign: b64url("not json"),
	})
	if err == nil {
		t.Fatal("expected ValidateJSON's default-true behavior to reject non-JSON payloads")
	}
}

func TestJWSSign_InvalidJSON_AllowedWhenValidateJSONFalse(t *testing.T) {
	km, sig := newTestServices(t)
	provisionSignKey(t, km, "TESTAPP", keymanager.RefIDECSECP256R1Sign)

	validateJSON := false
	resp, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
		DataToSign: b64url("not json"), ValidateJSON: &validateJSON,
	})
	if err != nil {
		t.Fatalf("expected non-JSON payload to be allowed when ValidateJSON=false: %v", err)
	}
	if resp.JWTSignedData == "" {
		t.Fatal("expected a signed JWS")
	}
}

// TestJWSSign_InputValidationOrder confirms ApplicationID, ReferenceID, and
// DataToSign are validated first, and in that order — ApplicationID before
// ReferenceID before DataToSign — before any other business-logic check
// (e.g. key-tier restriction) runs.
func TestJWSSign_InputValidationOrder(t *testing.T) {
	_, sig := newTestServices(t)

	_, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{})
	if !errors.Is(err, signature.ErrBlankApplicationID) {
		t.Fatalf("expected ErrBlankApplicationID when everything is blank, got %v", err)
	}

	_, err = sig.JWSSign(context.Background(), signature.JWSSignRequest{ApplicationID: "TESTAPP"})
	if !errors.Is(err, signature.ErrBlankReferenceID) {
		t.Fatalf("expected ErrBlankReferenceID when only ApplicationID is set, got %v", err)
	}

	_, err = sig.JWSSign(context.Background(), signature.JWSSignRequest{ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign})
	if !errors.Is(err, signature.ErrBlankSignatureData) {
		t.Fatalf("expected ErrBlankSignatureData when DataToSign is still blank, got %v", err)
	}
}

// TestJWSSign_ROOT_BlankReferenceID_Allowed confirms ROOT's always-blank
// reference id is exempt from ErrBlankReferenceID, mirroring
// keymanager.Service's own ensureCurrentKey convention, and that ROOT (a
// keystore-resident tier) is an allowed signing key per the resolved key-tier
// scope.
func TestJWSSign_ROOT_BlankReferenceID_Allowed(t *testing.T) {
	_, sig := newTestServices(t)
	resp, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: keymanager.AppIDRoot,
		DataToSign:    b64url(`{"a":1}`),
	})
	if err != nil {
		t.Fatalf("expected ROOT with a blank reference id to be allowed, got: %v", err)
	}
	if resp.JWTSignedData == "" {
		t.Fatal("expected a signed JWS")
	}
}

// TestJWSSign_DefaultHeader_ContainsOnlyAlg confirms certificate embedding
// is opt-in: with every other flag left at its default, the JWS header must
// contain nothing but "alg".
func TestJWSSign_DefaultHeader_ContainsOnlyAlg(t *testing.T) {
	km, sig := newTestServices(t)
	provisionSignKey(t, km, "TESTAPP", keymanager.RefIDECSECP256R1Sign)

	resp, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
		DataToSign: b64url(`{"a":1}`),
	})
	if err != nil {
		t.Fatalf("JWSSign: %v", err)
	}

	headerB64 := resp.JWTSignedData[:indexOf(resp.JWTSignedData, '.')]
	raw, err := base64.RawURLEncoding.DecodeString(headerB64)
	if err != nil {
		t.Fatalf("decode header: %v", err)
	}
	var header map[string]json.RawMessage
	if err := json.Unmarshal(raw, &header); err != nil {
		t.Fatalf("unmarshal header: %v", err)
	}
	if len(header) != 1 {
		t.Fatalf("expected the header to contain only \"alg\" by default, got %v", header)
	}
	if _, ok := header["alg"]; !ok {
		t.Errorf("expected \"alg\" to be present, got %v", header)
	}
}

// TestJWSSign_ComponentEncryptionKey_Rejected confirms a reference id that
// resolves to a DB-resident Component Encryption Key (not keystore-resident,
// not reserved for symmetric use either) is rejected before any key
// material is touched.
func TestJWSSign_ComponentEncryptionKey_Rejected(t *testing.T) {
	_, sig := newTestServices(t)
	_, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: "SOME_ENCRYPTION_KEY",
		DataToSign: b64url(`{"a":1}`),
	})
	if !errors.Is(err, signature.ErrEncryptionKeyNotAllowedForSigning) {
		t.Fatalf("expected ErrEncryptionKeyNotAllowedForSigning, got %v", err)
	}
}

// TestJWSSign_SymmetricKeyRefID_Rejected confirms a reference id configured
// as an AES/symmetric key (Config.SymmetricKeyAllowedRefIDs) is rejected
// with the specific AES message rather than the generic encryption-key one.
func TestJWSSign_SymmetricKeyRefID_Rejected(t *testing.T) {
	_, sig := newTestServicesWithConfig(t, []string{"ZK_ENCRYPT"})
	_, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: "ZK_ENCRYPT",
		DataToSign: b64url(`{"a":1}`),
	})
	if !errors.Is(err, signature.ErrAESNotAllowedForSigning) {
		t.Fatalf("expected ErrAESNotAllowedForSigning, got %v", err)
	}
	if err.Error() != "Not allowed to use AES for JWS Signing." {
		t.Errorf("unexpected error message: %q", err.Error())
	}
}

// TestJWSSign_ComponentMasterKey_Allowed and the RefIDECSECP256R1Sign/
// RefIDED25519Sign coverage in TestJWSSignVerify_RoundTrip_AllAlgorithms
// together confirm every keystore-resident tier remains usable for signing
// (only Component Encryption Keys and AES reference ids are rejected).
func TestJWSSign_ComponentMasterKey_Allowed(t *testing.T) {
	km, sig := newTestServices(t)
	provisionSignKey(t, km, "TESTAPP", keymanager.RefIDRSA2048)
	_, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDRSA2048,
		DataToSign: b64url(`{"a":1}`),
	})
	if err != nil {
		t.Fatalf("expected a Component Master Key (RSA_2048) to be an allowed signing key: %v", err)
	}
}

func TestJWSVerify_BlankData_Rejected(t *testing.T) {
	_, sig := newTestServices(t)
	if _, err := sig.JWSVerify(context.Background(), signature.JWSVerifyRequest{}); err == nil {
		t.Fatal("expected an error for blank JWTSignatureData")
	}
}

func TestJWSVerify_MalformedJWS_Rejected(t *testing.T) {
	_, sig := newTestServices(t)
	if _, err := sig.JWSVerify(context.Background(), signature.JWSVerifyRequest{JWTSignatureData: "not-a-jws"}); err == nil {
		t.Fatal("expected an error for a non-3-segment JWS")
	}
}

func TestJWSVerify_NoCertificateResolvable(t *testing.T) {
	_, sig := newTestServices(t)
	verifyResp, err := sig.JWSVerify(context.Background(), signature.JWSVerifyRequest{
		JWTSignatureData: "eyJhbGciOiJQUzI1NiJ9.eyJhIjoxfQ.c2ln",
	})
	if err != nil {
		t.Fatalf("JWSVerify: %v", err)
	}
	if verifyResp.SignatureValid {
		t.Error("expected verification to fail when no certificate can be resolved")
	}
}

// TestJWSSign_KeyIDAndIssuerPrepend covers IncludeKeyID plus the
// PAYLOAD_ISSUER kid-prepend feature (deriving the kid prefix from the
// payload's "iss" claim).
func TestJWSSign_KeyIDAndIssuerPrepend(t *testing.T) {
	km, sig := newTestServices(t)
	provisionSignKey(t, km, "TESTAPP", keymanager.RefIDECSECP256R1Sign)

	payload := b64url(`{"iss":"https://issuer.example"}`)
	resp, err := sig.JWSSign(context.Background(), signature.JWSSignRequest{
		ApplicationID: "TESTAPP", ReferenceID: keymanager.RefIDECSECP256R1Sign,
		DataToSign: payload, IncludeKeyID: true, KeyIDPrepend: "PAYLOAD_ISSUER",
	})
	if err != nil {
		t.Fatalf("JWSSign: %v", err)
	}

	headerB64 := resp.JWTSignedData[:indexOf(resp.JWTSignedData, '.')]
	rawHeader, err := base64.RawURLEncoding.DecodeString(headerB64)
	if err != nil {
		t.Fatalf("decode header: %v", err)
	}
	var h struct {
		Kid string `json:"kid"`
	}
	if err := json.Unmarshal(rawHeader, &h); err != nil {
		t.Fatalf("unmarshal header: %v", err)
	}
	if h.Kid == "" {
		t.Fatal("expected a kid header to be present")
	}
	wantPrefix := "https://issuer.example"
	if len(h.Kid) < len(wantPrefix) || h.Kid[:len(wantPrefix)] != wantPrefix {
		t.Errorf("expected kid to be prefixed with the payload's iss claim, got %q", h.Kid)
	}
}

func indexOf(s string, b byte) int {
	for i := 0; i < len(s); i++ {
		if s[i] == b {
			return i
		}
	}
	return -1
}
