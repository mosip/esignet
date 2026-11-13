/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"crypto/sha256"
	"database/sql"
	"encoding/base64"
	"encoding/json"
	"slices"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/consentmgmt"
	"github.com/mosip/esignet/internal/engine/shared"
	"github.com/mosip/esignet/internal/keymanager/cryptomanager"
	"github.com/mosip/esignet/internal/keymanager/signature"
	applog "github.com/mosip/esignet/internal/log"
)

// OpenID Connect Native SSO for Mobile Apps 1.0 lets a second native app of the same vendor reuse
// the authentication a first app performed on the same device. eSignet implements the engine's
// DeviceSecretProvider: the token endpoint hands the first (super) app a device_secret next to its
// ID token, and the second (child) app later presents both on an OAuth 2.0 Token Exchange.
//
// A device secret is a signed-then-encrypted JWT: the claims are signed with eSignet's OIDC signing
// key (so the server can prove it minted them) and the JWS is then encrypted to the OIDC_SERVICE
// device-secret encryption key, so the value is opaque to the apps that store and forward it. The
// ID token carries ds_hash = BASE64URL(SHA-256(device_secret)) and the same sid as the device
// secret, which is how the exchange proves the two belong together.

const (
	// ScopeDeviceSSO is the scope a super app adds to its authorization request to ask for a
	// device secret alongside its ID token.
	ScopeDeviceSSO = "device_sso"

	// DeviceSecretTokenType is the RFC 8693 token type identifier of a device secret.
	DeviceSecretTokenType = "urn:openid:params:token-type:device-secret"

	// DeviceSecretEncryptionRefID is the reference id of the OIDC_SERVICE Component Encryption Key
	// (RSA, signed by the component master key) that device secrets are encrypted to. It is
	// provisioned at startup next to the signing keys.
	DeviceSecretEncryptionRefID = "DEVICE_SECRET_ENC"

	// defaultDeviceSecretValidity bounds a device secret's lifetime when neither the engine nor
	// the configuration sets one.
	defaultDeviceSecretValidity = 30 * 24 * time.Hour

	// deviceSecretClockSkew tolerates small clock differences when checking iat/exp.
	deviceSecretClockSkew = 60 * time.Second
)

// deviceSecretPayload is the claim set of a device secret.
type deviceSecretPayload struct {
	Iss       string          `json:"iss"`
	Sub       string          `json:"sub"`
	AuthID    string          `json:"auth_id,omitempty"`
	SID       string          `json:"sid,omitempty"`
	ClientID  string          `json:"client_id"`
	Scope     string          `json:"scope"`
	ACR       string          `json:"acr,omitempty"`
	Claims    map[string]any  `json:"claims,omitempty"`
	Cnf       deviceSecretCnf `json:"cnf"`
	Iat       int64           `json:"iat"`
	Exp       int64           `json:"exp"`
	JTI       string          `json:"jti"`
	TokenType string          `json:"token_type"`
}

type deviceSecretCnf struct {
	JKT string `json:"jkt,omitempty"`
}

type deviceSecretProvider struct {
	cfg                *config.AppConfig
	clientSvc          *clientmgmt.Service
	consentSvc         *consentmgmt.Service
	sigSvc             *signature.Service
	cryptoSvc          *cryptomanager.Service
	signReferenceID    string
	encryptReferenceID string
	logger             *applog.Logger
}

// NewDeviceSecretProvider creates the providers.DeviceSecretProvider (and
// providers.DeviceSSOConsentProvider) for Native Mobile SSO. The consent service lets a child app
// inherit the consent the user gave the super app; the crypto service encrypts device secrets.
func NewDeviceSecretProvider(
	cfg *config.AppConfig,
	clientSvc *clientmgmt.Service,
	consentSvc *consentmgmt.Service,
	sigSvc *signature.Service,
	cryptoSvc *cryptomanager.Service,
) providers.DeviceSecretProvider {
	refID := cfg.JWT.PreferredKeyID
	if refID == "" {
		refID = defaultSignReferenceID
	}
	return &deviceSecretProvider{
		cfg:                cfg,
		clientSvc:          clientSvc,
		consentSvc:         consentSvc,
		sigSvc:             sigSvc,
		cryptoSvc:          cryptoSvc,
		signReferenceID:    refID,
		encryptReferenceID: DeviceSecretEncryptionRefID,
		logger:             applog.GetLogger().Named("device_secret_provider"),
	}
}

// GenerateDeviceSecret mints a device secret for the super app named by params.ClientID. The client
// must be registered with device_sso among its allowed_authorization_scopes (Task 2 of the Native
// SSO design: the token endpoint validates the scope against the client registration).
func (p *deviceSecretProvider) GenerateDeviceSecret(
	ctx context.Context,
	params *providers.DeviceSecretParams,
) (*providers.DeviceSecretResult, *common.ServiceError) {
	if params == nil || params.Sub == "" || params.ClientID == "" {
		return nil, deviceSecretClientError("invalid_request", "Missing required parameters for device secret generation")
	}
	if params.SessionID == "" {
		return nil, deviceSecretClientError("invalid_request", "A session id (sid) is required to bind the device secret to the ID token")
	}
	// A device secret is always bound to the requesting app's DPoP key; an unbound one would be a
	// long-lived bearer credential.
	if params.CnfJKT == "" {
		return nil, deviceSecretClientError("invalid_request", "A DPoP key thumbprint (cnf.jkt) is required to bind the device secret")
	}
	if p.cryptoSvc == nil {
		return nil, deviceSecretServerError("Device secret encryption is not configured")
	}

	client, err := p.clientSvc.GetActiveClient(ctx, params.ClientID)
	if err != nil {
		p.logger.Warn(ctx, "failed to lookup client requesting a device secret",
			applog.String("clientId", params.ClientID), applog.Error(err))
		return nil, deviceSecretClientError("invalid_client", "Client not found or inactive")
	}
	if !clientAllowsDeviceSSO(client) {
		return nil, deviceSecretClientError("invalid_scope", "The device_sso scope is not enabled for this client")
	}

	now := time.Now().UTC()
	expiry := p.deviceSecretValidity(params.ExpiryDuration)
	payload := deviceSecretPayload{
		Iss:       p.issuer(),
		Sub:       params.Sub,
		AuthID:    params.AuthID,
		SID:       params.SessionID,
		ClientID:  params.ClientID,
		Scope:     params.Scope,
		ACR:       params.ACR,
		Claims:    expandKycClaims(params.Claims),
		Cnf:       deviceSecretCnf{JKT: params.CnfJKT},
		Iat:       now.Unix(),
		Exp:       now.Add(expiry).Unix(),
		JTI:       uuid.NewString(),
		TokenType: DeviceSecretTokenType,
	}

	dataJSON, err := json.Marshal(payload)
	if err != nil {
		p.logger.Error(ctx, "failed to marshal device secret payload", applog.Error(err))
		return nil, deviceSecretServerError("Failed to encode device secret payload")
	}

	signResp, err := p.sigSvc.JWSSign(ctx, signature.JWSSignRequest{
		ApplicationID: config.OIDCServiceAppID,
		ReferenceID:   p.signReferenceID,
		DataToSign:    base64.RawURLEncoding.EncodeToString(dataJSON),
		IncludeKeyID:  true,
	})
	if err != nil {
		p.logger.Error(ctx, "failed to sign device secret", applog.Error(err))
		return nil, deviceSecretServerError("Failed to sign device secret")
	}

	// Sign then encrypt: the JWS becomes the JWE payload, so the device secret is opaque to the
	// apps that hold it and only eSignet can read the claims back.
	encResp, err := p.cryptoSvc.JWTEncrypt(ctx, cryptomanager.JWTEncryptRequest{
		ApplicationID: config.OIDCServiceAppID,
		ReferenceID:   p.encryptReferenceID,
		Data:          signResp.JWTSignedData,
	})
	if err != nil {
		p.logger.Error(ctx, "failed to encrypt device secret", applog.Error(err))
		return nil, deviceSecretServerError("Failed to encrypt device secret")
	}
	deviceSecret := encResp.Data

	return &providers.DeviceSecretResult{
		DeviceSecret: deviceSecret,
		DSHash:       DeviceSecretHash(deviceSecret),
		ExpiresAt:    time.Unix(payload.Exp, 0).UTC(),
	}, nil
}

// ValidateDeviceSecret decrypts and verifies a device secret presented by clientID on a token
// exchange and returns its claims. Besides the signature and expiry, it checks that the presenting
// client is registered for device_sso and belongs to the same vendor family as the client the
// secret was minted for (same relying party, or listed in the super app's allowed_child_clients).
func (p *deviceSecretProvider) ValidateDeviceSecret(
	ctx context.Context,
	clientID string,
	deviceSecret string,
) (*providers.DeviceSecretClaims, *common.ServiceError) {
	if clientID == "" || deviceSecret == "" {
		return nil, deviceSecretClientError("invalid_request", "Client ID and device secret are required")
	}
	if p.cryptoSvc == nil {
		return nil, deviceSecretServerError("Device secret encryption is not configured")
	}

	payload, svcErr := p.openDeviceSecret(ctx, deviceSecret)
	if svcErr != nil {
		return nil, svcErr
	}

	now := time.Now().UTC()
	if now.After(time.Unix(payload.Exp, 0).Add(deviceSecretClockSkew)) {
		return nil, deviceSecretClientError("invalid_grant", "Device secret has expired")
	}
	if payload.Iat > 0 && time.Unix(payload.Iat, 0).After(now.Add(deviceSecretClockSkew)) {
		return nil, deviceSecretClientError("invalid_grant", "Device secret is not yet valid")
	}
	if iss := p.issuer(); iss != "" && payload.Iss != iss {
		return nil, deviceSecretClientError("invalid_grant", "Device secret was not issued by this server")
	}

	// The presenting client must itself be enabled for Native SSO.
	childClient, err := p.clientSvc.GetActiveClient(ctx, clientID)
	if err != nil {
		p.logger.Warn(ctx, "failed to lookup client presenting a device secret",
			applog.String("clientId", clientID), applog.Error(err))
		return nil, deviceSecretClientError("invalid_client", "Client not found or inactive")
	}
	if !clientAllowsDeviceSSO(childClient) {
		return nil, deviceSecretClientError("unauthorized_client", "The device_sso scope is not enabled for this client")
	}

	// Vendor family isolation: only the super app itself or a sibling app may redeem the secret.
	if clientID != payload.ClientID {
		superClient, err := p.clientSvc.GetActiveClient(ctx, payload.ClientID)
		if err != nil {
			p.logger.Warn(ctx, "failed to lookup the client the device secret was minted for",
				applog.String("superClientId", payload.ClientID), applog.Error(err))
			return nil, deviceSecretClientError("invalid_client", "Original client from device secret not found or inactive")
		}
		if !sameVendorFamily(superClient, childClient) {
			p.logger.Warn(ctx, "vendor relationship mismatch between child app and super app",
				applog.String("childClientId", clientID),
				applog.String("superClientId", payload.ClientID),
				applog.String("childRpId", childClient.RpID),
				applog.String("superRpId", superClient.RpID),
			)
			return nil, deviceSecretClientError("invalid_grant", "Client is not authorized to exchange this device secret (vendor mismatch)")
		}
	}

	return &providers.DeviceSecretClaims{
		Sub:       payload.Sub,
		AuthID:    payload.AuthID,
		ClientID:  payload.ClientID,
		Scope:     payload.Scope,
		ACR:       payload.ACR,
		Claims:    payload.Claims,
		CnfJKT:    payload.Cnf.JKT,
		Issuer:    payload.Iss,
		ExpiresAt: time.Unix(payload.Exp, 0).UTC(),
		JTI:       payload.JTI,
		SessionID: payload.SID,
	}, nil
}

// ResolveDerivedConsent decides whether the consent the user gave the super app also covers a child
// app's token exchange (bonus task: re-consent when the child asks for more than was consented).
// The child app's required claims (its registered claims released by the requested scopes) must all
// have been accepted by the user for the super app, and any requested authorization scope must have
// been permitted. When they are, a consent record is written for the child app so the audit trail
// shows the derived consent and a later interactive login of the child app skips the prompt. A
// consent the child app already holds is only ever widened, and it is not rewritten (nor a history
// row added) when it already covers the derived consent, so repeated exchanges leave one audit row.
func (p *deviceSecretProvider) ResolveDerivedConsent(
	ctx context.Context,
	req *providers.DerivedConsentRequest,
) (*providers.DerivedConsentResult, *common.ServiceError) {
	if req == nil || req.Sub == "" || req.ClientID == "" || req.OriginClientID == "" {
		return nil, deviceSecretClientError("invalid_request", "Missing required parameters for consent resolution")
	}
	if p.consentSvc == nil {
		return nil, deviceSecretServerError("Consent service is not configured")
	}

	childClient, err := p.clientSvc.GetActiveClient(ctx, req.ClientID)
	if err != nil {
		p.logger.Warn(ctx, "failed to lookup child client for consent resolution",
			applog.String("clientId", req.ClientID), applog.Error(err))
		return nil, deviceSecretClientError("invalid_client", "Client not found or inactive")
	}

	var standardScopes, authorizationScopes []string
	for _, scope := range req.RequestedScopes {
		switch {
		case scope == ScopeDeviceSSO:
			// Never exchangeable; the engine rejects it before consent is resolved.
		case p.cfg.IsAuthorizationScope(scope):
			authorizationScopes = append(authorizationScopes, scope)
		default:
			standardScopes = append(standardScopes, scope)
		}
	}
	requiredClaims := claimsForScopes(p.cfg.ScopeClaims, standardScopes, childClient.Claims)

	origin, err := p.consentSvc.FetchRecord(ctx, req.OriginClientID, req.Sub)
	if err != nil {
		p.logger.Error(ctx, "failed to read the originating consent record", applog.Error(err))
		return nil, deviceSecretServerError("Failed to read consent record")
	}
	if origin == nil || origin.IsExpired(time.Now().UTC()) {
		p.logger.Debug(ctx, "no active consent for the originating client; interaction required",
			applog.String("originClientId", req.OriginClientID))
		return &providers.DerivedConsentResult{
			Allowed:       false,
			MissingScopes: authorizationScopes,
			MissingClaims: requiredClaims,
		}, nil
	}

	missingClaims := difference(requiredClaims, origin.AcceptedClaims)
	missingScopes := difference(authorizationScopes, origin.PermittedScopes)
	if len(missingClaims) > 0 || len(missingScopes) > 0 {
		p.logger.Debug(ctx, "originating consent does not cover the exchange; interaction required",
			applog.String("clientId", req.ClientID),
			applog.String("missingClaims", strings.Join(missingClaims, " ")),
			applog.String("missingScopes", strings.Join(missingScopes, " ")))
		return &providers.DerivedConsentResult{
			Allowed:       false,
			MissingScopes: missingScopes,
			MissingClaims: missingClaims,
		}, nil
	}

	// A consent the child app already holds that covers the derived one is left as it is: the
	// exchange neither narrows it nor adds an audit row for a consent that did not change.
	now := time.Now().UTC()
	existing, err := p.consentSvc.FetchRecord(ctx, req.ClientID, req.Sub)
	if err != nil {
		p.logger.Error(ctx, "failed to read the child app's consent record", applog.Error(err))
		return nil, deviceSecretServerError("Failed to read consent record")
	}
	if existing != nil && existing.IsExpired(now) {
		existing = nil
	}
	if existing != nil && len(difference(requiredClaims, existing.AcceptedClaims)) == 0 &&
		len(difference(authorizationScopes, existing.PermittedScopes)) == 0 {
		p.logger.Debug(ctx, "the child app's consent already covers the exchange",
			applog.String("clientId", req.ClientID))
		return &providers.DerivedConsentResult{Allowed: true}, nil
	}

	// Record the derived consent for the child app: the claims request mirrors what an interactive
	// authorization with the same scopes would hash to, so a later interactive login skips consent.
	record := &consentmgmt.ConsentRecord{
		ID:                  uuid.NewString(),
		ClientID:            req.ClientID,
		UserID:              req.Sub,
		Claims:              mergeScopeClaims(map[string]any{}, requiredClaims),
		AuthorizationScopes: authorizationScopes,
		AcceptedClaims:      requiredClaims,
		PermittedScopes:     authorizationScopes,
		CreatedAt:           now,
		ExpiresAt:           sql.NullTime{Time: origin.ExpiresAt.Time, Valid: origin.ExpiresAt.Valid},
	}
	// An existing consent that the derived one extends is widened, never narrowed: what the user
	// already granted the child app is kept and the derived claims and scopes are added to it.
	if existing != nil {
		record.ID = existing.ID
		record.Claims = mergeScopeClaims(existing.Claims, requiredClaims)
		record.AuthorizationScopes = union(existing.AuthorizationScopes, authorizationScopes)
		record.AcceptedClaims = union(existing.AcceptedClaims, requiredClaims)
		record.PermittedScopes = union(existing.PermittedScopes, authorizationScopes)
	}
	if err := p.consentSvc.SaveRecord(ctx, record); err != nil {
		p.logger.Error(ctx, "failed to record derived consent", applog.Error(err))
		return nil, deviceSecretServerError("Failed to record consent")
	}
	p.logger.Info(ctx, "consent derived from the originating application",
		applog.String("clientId", req.ClientID), applog.String("originClientId", req.OriginClientID))

	return &providers.DerivedConsentResult{Allowed: true}, nil
}

// openDeviceSecret decrypts the device secret, verifies the inner JWS and parses its claims.
func (p *deviceSecretProvider) openDeviceSecret(ctx context.Context, deviceSecret string) (*deviceSecretPayload, *common.ServiceError) {
	decResp, err := p.cryptoSvc.JWTDecrypt(ctx, cryptomanager.JWTDecryptRequest{
		ApplicationID: config.OIDCServiceAppID,
		ReferenceID:   p.encryptReferenceID,
		EncData:       deviceSecret,
	})
	if err != nil {
		p.logger.Warn(ctx, "device secret decryption failed", applog.Error(err))
		return nil, deviceSecretClientError("invalid_grant", "Device secret could not be decrypted")
	}
	jwsBytes, err := base64.RawURLEncoding.DecodeString(decResp.Data)
	if err != nil {
		return nil, deviceSecretClientError("invalid_grant", "Malformed device secret")
	}
	jws := string(jwsBytes)

	vResp, err := p.sigSvc.JWSVerify(ctx, signature.JWSVerifyRequest{
		JWTSignatureData: jws,
		ApplicationID:    config.OIDCServiceAppID,
		ReferenceID:      p.signReferenceID,
	})
	if err != nil || !vResp.SignatureValid {
		p.logger.Warn(ctx, "device secret signature verification failed", applog.Error(err))
		return nil, deviceSecretClientError("invalid_grant", "Invalid device secret signature")
	}

	parts := strings.Split(jws, ".")
	if len(parts) != 3 {
		return nil, deviceSecretClientError("invalid_grant", "Malformed device secret compact JWS")
	}
	payloadBytes, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return nil, deviceSecretClientError("invalid_grant", "Invalid base64 encoding in device secret payload")
	}
	var payload deviceSecretPayload
	if err := json.Unmarshal(payloadBytes, &payload); err != nil {
		return nil, deviceSecretClientError("invalid_grant", "Failed to parse device secret payload JSON")
	}
	if payload.TokenType != DeviceSecretTokenType {
		return nil, deviceSecretClientError("invalid_grant", "Presented token is not a device secret")
	}
	if payload.Sub == "" || payload.ClientID == "" {
		return nil, deviceSecretClientError("invalid_grant", "Device secret is missing required claims")
	}
	if payload.Cnf.JKT == "" {
		return nil, deviceSecretClientError("invalid_grant", "Device secret is not bound to a DPoP key (cnf.jkt)")
	}
	return &payload, nil
}

// deviceSecretValidity picks the device secret lifetime: the configured bound when set, otherwise
// what the engine asked for, otherwise the default. A configured bound never extends what the
// engine requested.
func (p *deviceSecretProvider) deviceSecretValidity(requested time.Duration) time.Duration {
	validity := requested
	if validity == 0 {
		validity = defaultDeviceSecretValidity
	}
	if configured := p.cfg.NativeSSO.DeviceSecretValidity(); configured > 0 && configured < validity {
		validity = configured
	}
	return validity
}

func (p *deviceSecretProvider) issuer() string {
	if p.cfg.Issuer != "" {
		return p.cfg.Issuer
	}
	return p.cfg.Server.Identifier
}

// reservedJWTClaims are registered claims of the identity system's KYC token that describe the
// token itself, not the user, and so are not carried into the device secret.
var reservedJWTClaims = map[string]bool{"iss": true, "aud": true, "exp": true, "iat": true, "nbf": true, "jti": true, "sub": true}

// expandKycClaims turns the attribute set the engine resolved for the user into the claim snapshot
// the device secret carries. eSignet's authenticators hand the identity system's KYC token through
// as one opaque attribute (providers.RawJWTAttributeKey). When that token is a plain signed JWS
// (mock identity system) its payload is unpacked into individual claims so a sibling app's userinfo
// can be served from the device secret; an encrypted KYC token (MOSIP IDA, encrypted to the
// relying party's key) stays opaque, and sibling apps of that relying party decrypt it themselves.
func expandKycClaims(attrs map[string]any) map[string]any {
	if len(attrs) == 0 {
		return attrs
	}
	raw, ok := attrs[providers.RawJWTAttributeKey].(string)
	if !ok {
		return attrs
	}
	parts := strings.Split(raw, ".")
	if len(parts) != 3 {
		return attrs
	}
	payloadBytes, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return attrs
	}
	var payload map[string]any
	if err := json.Unmarshal(payloadBytes, &payload); err != nil {
		return attrs
	}
	out := make(map[string]any, len(attrs)+len(payload))
	for k, v := range attrs {
		if k != providers.RawJWTAttributeKey {
			out[k] = v
		}
	}
	for k, v := range payload {
		if !reservedJWTClaims[k] {
			out[k] = v
		}
	}
	return out
}

// DeviceSecretHash computes ds_hash = BASE64URL(SHA-256(device_secret)).
func DeviceSecretHash(deviceSecret string) string {
	sum := sha256.Sum256([]byte(deviceSecret))
	return base64.RawURLEncoding.EncodeToString(sum[:])
}

// clientAllowsDeviceSSO reports whether the client registration lists device_sso among its
// allowed_authorization_scopes.
func clientAllowsDeviceSSO(client clientmgmt.ClientResponse) bool {
	return slices.Contains(shared.AllowedAuthorizationScopes(client.AdditionalConfig), ScopeDeviceSSO)
}

// sameVendorFamily reports whether child may redeem a device secret minted for super: both belong
// to the same relying party, or super explicitly lists child in allowed_child_clients.
func sameVendorFamily(super, child clientmgmt.ClientResponse) bool {
	if child.RpID != "" && child.RpID == super.RpID {
		return true
	}
	switch allowed := super.AdditionalConfig["allowed_child_clients"].(type) {
	case []any:
		for _, item := range allowed {
			if s, ok := item.(string); ok && s == child.ClientID {
				return true
			}
		}
	case []string:
		return slices.Contains(allowed, child.ClientID)
	}
	return false
}

// difference returns the members of want that are not in have, preserving order.
func difference(want, have []string) []string {
	haveSet := make(map[string]bool, len(have))
	for _, h := range have {
		haveSet[h] = true
	}
	var out []string
	for _, w := range want {
		if !haveSet[w] {
			out = append(out, w)
		}
	}
	return out
}

// union returns the members of a followed by the members of b not already in a.
func union(a, b []string) []string {
	out := slices.Clone(a)
	for _, v := range b {
		if !slices.Contains(out, v) {
			out = append(out, v)
		}
	}
	return out
}

func deviceSecretClientError(code, desc string) *common.ServiceError {
	return &common.ServiceError{
		Code: code,
		Type: common.ClientErrorType,
		Error: common.I18nMessage{
			Key:          code,
			DefaultValue: desc,
		},
		ErrorDescription: common.I18nMessage{
			Key:          code + "_desc",
			DefaultValue: desc,
		},
	}
}

func deviceSecretServerError(desc string) *common.ServiceError {
	return &common.ServiceError{
		Code: "server_error",
		Type: common.ServerErrorType,
		Error: common.I18nMessage{
			Key:          "server_error",
			DefaultValue: desc,
		},
		ErrorDescription: common.I18nMessage{
			Key:          "server_error_desc",
			DefaultValue: desc,
		},
	}
}

var _ providers.DeviceSSOConsentProvider = (*deviceSecretProvider)(nil)
