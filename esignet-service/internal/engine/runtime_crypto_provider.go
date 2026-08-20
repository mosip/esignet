/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"crypto/rand"
	"crypto/rsa"
	"crypto/sha1"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"errors"
	"fmt"

	"github.com/go-jose/go-jose/v4"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/engine/shared"
	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
	"github.com/mosip/esignet/internal/keymanager/signature"
	applog "github.com/mosip/esignet/internal/log"
)

// defaultSymmetricEncryptReferenceID is the reference id Encrypt/Decrypt
// fall back to for the symmetric (AES) path when a KeyRef doesn't specify
// one — matches keymanager.Config's own default for
// KEYMANAGER_SYMMETRIC_KEY_ALLOWED_REF_IDS (see keymanager/config.go), and
// keymanager.RefIDCacheEncrypt, the same reference id
// cmd/esignet/main.go's provisionKeyHierarchy provisions at startup.
const defaultSymmetricEncryptReferenceID = keymanager.RefIDCacheEncrypt

// defaultSignReferenceID is the reference id Sign/Verify/GetPublicKeys fall
// back to when a KeyRef doesn't specify one — esignet's own EC sign key,
// provisioned at startup (see cmd/esignet/main.go's provisionKeyHierarchy).
const defaultSignReferenceID = keymanager.RefIDECSECP256R1Sign

type runtimeCryptoProvider struct {
	cfg             *config.AppConfig
	svc             *keymanager.Service
	sigSvc          *signature.Service
	cryptoSvc       *cryptomanager.Service
	authnProvider   shared.ConsolidatedAuthnProvider
	signReferenceID string

	// jwkCache caches getPublicKey's parsed result — see its doc comment.
	// nil is a valid zero value (caching just disabled, not a nil-pointer
	// panic — see getPublicKey), so tests constructing a runtimeCryptoProvider
	// literal directly don't need to set it.
	jwkCache *jwkCache
}

// NewRuntimeCryptoProvider returns a providers.RuntimeCryptoProvider backed
// by esignet's own keymanager/signature/cryptomanager services. It only ever
// serves esignet's own keys, scoped to config.OIDCServiceAppID —
// KeyRef.KeyID/PublicKeyFilter.KeyID is treated as the keymanager
// ReferenceID within that fixed application, defaulting to
// defaultSignReferenceID for Sign/Verify/GetPublicKeys when unset.
//
// Wired into thunderidengine.New via thunderidengine.WithRuntimeCryptoProvider
// (see cmd/esignet/main.go) — it now serves the engine's own token/ID-token/
// userinfo signing (Sign -> signature.Service.SignRaw) in addition to being
// independently usable by any esignet code that already speaks
// providers.RuntimeCryptoProvider.
func NewRuntimeCryptoProvider(cfg *config.AppConfig, svc *keymanager.Service, sigSvc *signature.Service,
	cryptoSvc *cryptomanager.Service, authnProvider shared.ConsolidatedAuthnProvider) providers.RuntimeCryptoProvider {

	referenceID := cfg.JWT.PreferredKeyID
	if referenceID == "" {
		referenceID = defaultSignReferenceID
	}

	return &runtimeCryptoProvider{
		cfg:             cfg,
		svc:             svc,
		sigSvc:          sigSvc,
		cryptoSvc:       cryptoSvc,
		authnProvider:   authnProvider,
		signReferenceID: referenceID,
		jwkCache:        newJWKCache(),
	}
}

// referenceID resolves a KeyRef/PublicKeyFilter KeyID to the keymanager
// reference id to operate on, defaulting to p.signReferenceID when unset.
func (p *runtimeCryptoProvider) referenceID(_ string) string {
	// TODO: a thumbprint supplied as the kid does not yet resolve to a reference id,
	// so keyID is ignored and the configured signing key is always used.
	return p.signReferenceID
}

// Encrypt encrypts content for the key named by keyRef.KeyID (a keymanager
// ReferenceID under config.OIDCServiceAppID). When algorithm is
// cryptomanagerSymmetricAlgorithm, it AES-GCM-encrypts via
// cryptomanager.Service.EncryptAES against an existing symmetric key,
// defaulting keyRef.KeyID to defaultSymmetricEncryptReferenceID when unset
// (mirrors Sign's default reference id). Otherwise it hybrid-encrypts via
// cryptomanager.Service.Encrypt for the Component Encryption Key, for which
// there is no sensible default, so keyRef.KeyID is required. No
// CryptoDetails are produced in either case: cryptomanager's AES-GCM
// nonce/tag are embedded directly in the returned envelope, not surfaced
// separately.
func (p *runtimeCryptoProvider) Encrypt(
	ctx context.Context, keyRef *providers.KeyRef, algorithm string, params map[string]interface{}, content []byte,
) ([]byte, *providers.CryptoDetails, error) {
	switch algorithm {
	case "AES-GCM":
		keyID := ""
		if keyRef != nil {
			keyID = keyRef.KeyID
		}
		resp, err := p.cryptoSvc.EncryptAES(ctx, cryptomanager.EncryptAESRequest{
			ApplicationID: config.OIDCServiceAppID,
			ReferenceID:   symmetricEncryptReferenceID(keyID),
			Data:          base64.RawURLEncoding.EncodeToString(content),
		})
		if err != nil {
			return nil, nil, err
		}
		result := map[string]string{
			"alg":    "AES-GCM",
			"cipher": resp.Data,
		}
		jsonResult, err := json.Marshal(result)
		if err != nil {
			return nil, nil, fmt.Errorf("failed to serialize encrypted data: %w", err)
		}
		return []byte(jsonResult), nil, nil

	case "RSA-OAEP":
		if keyRef == nil {
			return nil, nil, fmt.Errorf("%w: a KeyRef is required for RSA-OAEP", providers.ErrKeyNotFound)
		}
		rsaPub, err := p.getPublicKey(*keyRef)
		if err != nil {
			return nil, nil, err
		}
		pub, ok := rsaPub.Key.(*rsa.PublicKey)
		if !ok {
			return nil, nil, fmt.Errorf("%w: RSA-OAEP requires an RSA jwk", providers.ErrUnsupportedAlgorithm)
		}
		return encryptRSAOAEP(pub, params)

	case "RSA-OAEP-256":
		if keyRef == nil {
			return nil, nil, fmt.Errorf("%w: a KeyRef is required for RSA-OAEP-256", providers.ErrKeyNotFound)
		}
		rsaPub, err := p.getPublicKey(*keyRef)
		if err != nil {
			return nil, nil, err
		}
		pub, ok := rsaPub.Key.(*rsa.PublicKey)
		if !ok {
			return nil, nil, fmt.Errorf("%w: RSA-OAEP-256 requires an RSA jwk", providers.ErrUnsupportedAlgorithm)
		}
		return encryptRSAOAEP256(pub, params)
	default:
		return nil, nil, fmt.Errorf("unsupported algorithm: %s", algorithm)
	}
}

// Decrypt reverses Encrypt. When algorithm is cryptomanagerSymmetricAlgorithm
// it routes to cryptomanager.Service.DecryptAES, applying the same
// keyRef.KeyID default as Encrypt; otherwise it routes to
// cryptomanager.Service.Decrypt. content is the base64url wire-format
// envelope the matching Encrypt call returned.
func (p *runtimeCryptoProvider) Decrypt(ctx context.Context, keyRef *providers.KeyRef, algorithm string, _ map[string]interface{},
	content []byte) ([]byte, error) {
	if algorithm == "AES-GCM" {
		keyID := ""
		if keyRef != nil {
			keyID = keyRef.KeyID
		}
		jsonContent := make(map[string]string)
		if err := json.Unmarshal(content, &jsonContent); err != nil {
			return nil, fmt.Errorf("invalid data format: %w", err)
		}
		ciphertext, ok := jsonContent["cipher"]
		if !ok {
			return nil, fmt.Errorf("invalid data format: missing 'cipher' field")
		}
		resp, err := p.cryptoSvc.DecryptAES(ctx, cryptomanager.DecryptAESRequest{
			ApplicationID: config.OIDCServiceAppID,
			ReferenceID:   symmetricEncryptReferenceID(keyID),
			Data:          ciphertext,
		})
		if err != nil {
			return nil, err
		}
		plaintext, err := base64.RawURLEncoding.DecodeString(resp.Data)
		if err != nil {
			return nil, fmt.Errorf("decode decrypted plaintext: %w", err)
		}
		return plaintext, nil
	}

	// No other decryption is required in esignet-service, so we can return an error as unsupported algorithms.
	return nil, fmt.Errorf("%w: %q", providers.ErrUnsupportedAlgorithm, algorithm)
}

// Sign signs content — an already-assembled JWS/JWT signing input
// (base64url(header) + "." + base64url(payload)), per
// providers.RuntimeCryptoProvider's contract — with the signing key named by
// keyRef.KeyID, via signature.Service.SignRaw.
func (p *runtimeCryptoProvider) Sign(ctx context.Context, keyRef providers.KeyRef, alg string, content []byte) ([]byte, error) {
	sig, err := p.sigSvc.SignRaw(ctx, config.OIDCServiceAppID, p.referenceID(keyRef.KeyID), alg, content)
	if err != nil {
		if errors.Is(err, signature.ErrUnsupportedAlgorithm) {
			return nil, fmt.Errorf("%w: %w", providers.ErrUnsupportedAlgorithm, err)
		}
		return nil, err
	}
	return sig, nil
}

// Verify verifies signature over content either against a caller-supplied
// keyRef.PublicKeyJWK, via signature.Service.VerifyWithJWK, or otherwise
// against the signing key named by (config.OIDCServiceAppID, keyRef.KeyID),
// via signature.Service.VerifyRaw.
func (p *runtimeCryptoProvider) Verify(ctx context.Context, keyRef providers.KeyRef, alg string, content, sigBytes []byte) error {
	if len(keyRef.PublicKeyJWK) > 0 {
		jwk, err := json.Marshal(keyRef.PublicKeyJWK)
		if err != nil {
			return fmt.Errorf("marshal public key jwk: %w", err)
		}
		return p.sigSvc.VerifyWithJWK(ctx, string(jwk), alg, content, sigBytes)
	}
	err := p.sigSvc.VerifyRaw(ctx, config.OIDCServiceAppID, p.referenceID(keyRef.KeyID), alg, content, sigBytes)
	if err == nil {
		return nil
	}
	if errors.Is(err, signature.ErrVerifyCertificateNotFound) {
		return fmt.Errorf("%w: %w", providers.ErrKeyNotFound, err)
	}
	if errors.Is(err, signature.ErrUnsupportedAlgorithm) {
		return fmt.Errorf("%w: %w", providers.ErrUnsupportedAlgorithm, err)
	}
	return err
}

// GetPublicKeys resolves the current certificate for filter.KeyID (a
// keymanager ReferenceID under config.OIDCServiceAppID, defaulting to
// defaultSignReferenceID) and returns its public key/certificate info.
// filter.Algorithm is not used to narrow results — this provider serves one
// certificate per reference id, whose algorithm is determined by the key
// itself, not selectable by the caller.
func (p *runtimeCryptoProvider) GetPublicKeys(ctx context.Context, filter providers.PublicKeyFilter) ([]providers.PublicKeyInfo, error) {
	refID := p.referenceID(filter.KeyID)
	resp, err := p.svc.GetCertificate(ctx, config.OIDCServiceAppID, refID)
	if err != nil {
		return nil, fmt.Errorf("%w: %w", providers.ErrKeyNotFound, err)
	}
	cert, err := keymanager.ParseCertPEM(resp.Certificate)
	if err != nil {
		return nil, fmt.Errorf("parse resolved certificate: %w", err)
	}
	keys := []providers.PublicKeyInfo{{
		KeyID:          refID,
		Algorithm:      signature.AlgorithmForRefID(refID),
		PublicKey:      cert.PublicKey,
		Thumbprint:     keymanager.ThumbprintForCert(cert),
		CertificateDER: cert.Raw,
	}}
	return append(keys, p.idSystemPublicKeys(ctx)...), nil
}

// idSystemPublicKeys fetches the signing certificates of the configured ID
// system backend (mosip, mock, or sunbird — whichever NewIDSystemProviders
// selected) and converts them into providers.PublicKeyInfo entries. Fetch
// failures are logged and skipped rather than propagated, since they'd
// otherwise take down JWKS/discovery for esignet's own signing key over a
// dependency that is auxiliary to it.
func (p *runtimeCryptoProvider) idSystemPublicKeys(ctx context.Context) []providers.PublicKeyInfo {
	if p.authnProvider == nil {
		return nil
	}
	certs, svcErr := p.authnProvider.GetSigningCertificates(ctx)
	if svcErr != nil {
		applog.GetLogger().Warn(ctx, "failed to fetch ID system signing certificates", applog.String("error", svcErr.Code))
		return nil
	}
	keys := make([]providers.PublicKeyInfo, 0, len(certs))
	for _, certData := range certs {
		cert, err := keymanager.ParseCertPEM(certData.Certificate)
		if err != nil {
			applog.GetLogger().Warn(ctx, "failed to parse ID system signing certificate",
				applog.String("keyId", certData.KeyID), applog.Error(err))
			continue
		}
		keys = append(keys, providers.PublicKeyInfo{
			KeyID:          certData.KeyID,
			Algorithm:      signature.AlgorithmForPublicKey(cert.PublicKey),
			PublicKey:      cert.PublicKey,
			Thumbprint:     keymanager.ThumbprintForCert(cert),
			CertificateDER: cert.Raw,
		})
	}
	return keys
}

func (p *runtimeCryptoProvider) GetSupportedSigningAlgorithms() []string {
	algorithms := signature.SupportedAlgorithms()

	configured := make(map[string]bool, len(p.cfg.SupportedSigningAlgorithms))
	for _, alg := range p.cfg.SupportedSigningAlgorithms {
		configured[alg] = true
	}

	supported := make([]string, 0, len(algorithms))
	for _, alg := range algorithms {
		if configured[alg] {
			supported = append(supported, alg)
		}
	}
	return supported
}

// GetSupportedEncryptionAlgorithms returns the list of algorithms supported by Encrypt and Decrypt.
func (p *runtimeCryptoProvider) GetSupportedEncryptionAlgorithms() []string {
	return p.cfg.SupportedEncAlgorithms
}

// getPublicKey parses/validates keyRef.PublicKeyJWK into a public
// jose.JSONWebKey. Encrypt calls this on every RSA-OAEP/RSA-OAEP-256
// request (e.g. userinfo JWE for a client registered with an encryption
// key), so the parsed result is cached by the JWK's own content digest
// (p.jwkCache) — re-marshaling, re-parsing, and re-validating the same
// client's registered key on every single call is pure avoidable overhead.
func (p *runtimeCryptoProvider) getPublicKey(keyRef providers.KeyRef) (*jose.JSONWebKey, error) {
	if len(keyRef.PublicKeyJWK) == 0 {
		return nil, fmt.Errorf("%w: a PublicKeyJWK is required", providers.ErrKeyNotFound)
	}

	jwkBytes, err := json.Marshal(keyRef.PublicKeyJWK)
	if err != nil {
		return nil, fmt.Errorf("marshal public key jwk: %w", err)
	}

	digest := sha256.Sum256(jwkBytes)
	if p.jwkCache != nil {
		if cached, ok := p.jwkCache.get(digest); ok {
			return cached, nil
		}
	}

	var key jose.JSONWebKey
	if err := key.UnmarshalJSON(jwkBytes); err != nil {
		return nil, fmt.Errorf("%w: parse jwk: %w", providers.ErrKeyNotFound, err)
	}
	if !key.Valid() {
		return nil, fmt.Errorf("%w: jwk is not valid", providers.ErrKeyNotFound)
	}
	pub := key.Public()
	if !pub.IsPublic() {
		return nil, fmt.Errorf("%w: jwk does not resolve to a public key", providers.ErrKeyNotFound)
	}
	if p.jwkCache != nil {
		p.jwkCache.set(digest, &pub)
	}
	return &pub, nil
}

func encryptRSAOAEP256(rsaPub *rsa.PublicKey, params map[string]interface{}) ([]byte, *providers.CryptoDetails, error) {
	cea, ok := params["contentEncryptionAlgorithm"].(string)
	if !ok || cea == "" {
		return nil, nil, errors.New("ContentEncryptionAlgorithm required for RSA-OAEP-256 CEK generation")
	}
	cekLen, err := contentEncKeyLen(cea)
	if err != nil {
		return nil, nil, err
	}
	cek := make([]byte, cekLen)
	if _, err := rand.Read(cek); err != nil {
		return nil, nil, fmt.Errorf("CEK generation failed: %w", err)
	}
	encryptedCEK, err := rsa.EncryptOAEP(sha256.New(), rand.Reader, rsaPub, cek, nil)
	if err != nil {
		for i := range cek {
			cek[i] = 0
		}
		return nil, nil, err
	}
	return encryptedCEK, &providers.CryptoDetails{CEK: cek}, nil
}

func encryptRSAOAEP(rsaPub *rsa.PublicKey, params map[string]interface{}) ([]byte, *providers.CryptoDetails, error) {
	cea, ok := params["contentEncryptionAlgorithm"].(string)
	if !ok || cea == "" {
		return nil, nil, errors.New("ContentEncryptionAlgorithm required for RSA-OAEP CEK generation")
	}
	cekLen, err := contentEncKeyLen(cea)
	if err != nil {
		return nil, nil, err
	}
	cek := make([]byte, cekLen)
	if _, err := rand.Read(cek); err != nil {
		return nil, nil, fmt.Errorf("CEK generation failed: %w", err)
	}
	encryptedCEK, err := rsa.EncryptOAEP(sha1.New(), rand.Reader, rsaPub, cek, nil) //nolint:gosec
	if err != nil {
		for i := range cek {
			cek[i] = 0
		}
		return nil, nil, err
	}
	return encryptedCEK, &providers.CryptoDetails{CEK: cek}, nil
}

// ecdhContentEncKeyLen returns the CEK length in bytes for the given content encryption algorithm.
func contentEncKeyLen(alg string) (int, error) {
	switch alg {
	case "A128GCM":
		return 16, nil
	case "A192GCM":
		return 24, nil
	case "A256GCM":
		return 32, nil
	case "A128CBC-HS256":
		return 32, nil
	case "A192CBC-HS384":
		return 48, nil
	case "A256CBC-HS512":
		return 64, nil
	default:
		return 0, fmt.Errorf("unsupported content encryption algorithm: %s", alg)
	}
}

// symmetricEncryptReferenceID resolves a KeyRef's KeyID to an AES reference
// id, defaulting to defaultSymmetricEncryptReferenceID when unset — mirrors
// signReferenceID's pattern for the symmetric Encrypt/Decrypt path.
func symmetricEncryptReferenceID(keyID string) string {
	if keyID == "" {
		return defaultSymmetricEncryptReferenceID
	}
	return keyID
}
