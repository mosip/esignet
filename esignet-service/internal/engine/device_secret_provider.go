/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"context"
	"crypto/sha256"
	"encoding/base64"
	"encoding/json"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/common"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/keymanager/signature"
	applog "github.com/mosip/esignet/internal/log"
)

type deviceSecretPayload struct {
	Iss       string         `json:"iss"`
	Sub       string         `json:"sub"`
	AuthID    string         `json:"auth_id,omitempty"`
	ClientID  string         `json:"client_id"`
	Scope     string         `json:"scope"`
	ACR       string         `json:"acr,omitempty"`
	Claims    map[string]any `json:"claims,omitempty"`
	Cnf       deviceSecretCnf `json:"cnf"`
	Iat       int64          `json:"iat"`
	Exp       int64          `json:"exp"`
	JTI       string         `json:"jti"`
	TokenType string         `json:"token_type"`
}

type deviceSecretCnf struct {
	JKT string `json:"jkt,omitempty"`
}

type deviceSecretProvider struct {
	cfg             *config.AppConfig
	clientSvc       *clientmgmt.Service
	sigSvc          *signature.Service
	signReferenceID string
	logger          *applog.Logger
}

// NewDeviceSecretProvider creates a new providers.DeviceSecretProvider for Native Mobile SSO.
func NewDeviceSecretProvider(
	cfg *config.AppConfig,
	clientSvc *clientmgmt.Service,
	sigSvc *signature.Service,
) providers.DeviceSecretProvider {
	refID := cfg.JWT.PreferredKeyID
	if refID == "" {
		refID = defaultSignReferenceID
	}
	return &deviceSecretProvider{
		cfg:             cfg,
		clientSvc:       clientSvc,
		sigSvc:          sigSvc,
		signReferenceID: refID,
		logger:          applog.GetLogger().Named("device_secret_provider"),
	}
}

func (p *deviceSecretProvider) GenerateDeviceSecret(
	ctx context.Context,
	params *providers.DeviceSecretParams,
) (*providers.DeviceSecretResult, *common.ServiceError) {
	if params == nil || params.Sub == "" || params.ClientID == "" {
		return nil, deviceSecretError("invalid_request", "Missing required parameters for device secret generation")
	}

	expiry := params.ExpiryDuration
	if expiry == 0 {
		expiry = 30 * 24 * time.Hour
	}

	now := time.Now().UTC()
	iss := p.cfg.Issuer
	if iss == "" {
		iss = p.cfg.Server.Identifier
	}

	payload := deviceSecretPayload{
		Iss:       iss,
		Sub:       params.Sub,
		AuthID:    params.AuthID,
		ClientID:  params.ClientID,
		Scope:     params.Scope,
		ACR:       params.ACR,
		Claims:    params.Claims,
		Cnf:       deviceSecretCnf{JKT: params.CnfJKT},
		Iat:       now.Unix(),
		Exp:       now.Add(expiry).Unix(),
		JTI:       uuid.NewString(),
		TokenType: "urn:openid:params:token-type:device-secret",
	}

	dataJSON, err := json.Marshal(payload)
	if err != nil {
		p.logger.Error(ctx, "failed to marshal device secret payload", applog.Error(err))
		return nil, deviceSecretError("server_error", "Failed to encode device secret payload")
	}

	signResp, err := p.sigSvc.JWSSign(ctx, signature.JWSSignRequest{
		ApplicationID: config.OIDCServiceAppID,
		ReferenceID:   p.signReferenceID,
		DataToSign:    base64.RawURLEncoding.EncodeToString(dataJSON),
		IncludeKeyID:  true,
	})
	if err != nil {
		p.logger.Error(ctx, "failed to sign device secret", applog.Error(err))
		return nil, deviceSecretError("server_error", "Failed to sign device secret")
	}

	deviceSecretJWT := signResp.JWTSignedData

	hasher := sha256.New()
	hasher.Write([]byte(deviceSecretJWT))
	dsHash := base64.RawURLEncoding.EncodeToString(hasher.Sum(nil))

	return &providers.DeviceSecretResult{
		DeviceSecret: deviceSecretJWT,
		DSHash:       dsHash,
		ExpiresAt:    now.Add(expiry),
	}, nil
}

func (p *deviceSecretProvider) ValidateDeviceSecret(
	ctx context.Context,
	clientID string,
	deviceSecret string,
) (*providers.DeviceSecretClaims, *common.ServiceError) {
	if clientID == "" || deviceSecret == "" {
		return nil, deviceSecretError("invalid_request", "Client ID and device secret are required")
	}

	vResp, err := p.sigSvc.JWSVerify(ctx, signature.JWSVerifyRequest{
		JWTSignatureData: deviceSecret,
		ApplicationID:    config.OIDCServiceAppID,
		ReferenceID:      p.signReferenceID,
	})
	if err != nil || !vResp.SignatureValid {
		p.logger.Warn(ctx, "device secret signature verification failed", applog.Error(err))
		return nil, deviceSecretError("invalid_grant", "Invalid device secret signature")
	}

	parts := strings.Split(deviceSecret, ".")
	if len(parts) != 3 {
		return nil, deviceSecretError("invalid_grant", "Malformed device secret compact JWS")
	}

	payloadBytes, err := base64.RawURLEncoding.DecodeString(parts[1])
	if err != nil {
		return nil, deviceSecretError("invalid_grant", "Invalid base64 encoding in device secret payload")
	}

	var payload deviceSecretPayload
	if err := json.Unmarshal(payloadBytes, &payload); err != nil {
		return nil, deviceSecretError("invalid_grant", "Failed to parse device secret payload JSON")
	}

	now := time.Now().UTC().Unix()
	if now > payload.Exp {
		return nil, deviceSecretError("invalid_grant", "Device secret has expired")
	}

	// Validate client and vendor family relationship
	if clientID != payload.ClientID {
		childClient, err := p.clientSvc.GetActiveClient(ctx, clientID)
		if err != nil {
			p.logger.Warn(ctx, "failed to lookup child client", applog.String("clientId", clientID), applog.Error(err))
			return nil, deviceSecretError("invalid_client", "Child client not found or inactive")
		}

		superClient, err := p.clientSvc.GetActiveClient(ctx, payload.ClientID)
		if err != nil {
			p.logger.Warn(ctx, "failed to lookup original client", applog.String("superClientId", payload.ClientID), applog.Error(err))
			return nil, deviceSecretError("invalid_client", "Original client from device secret not found or inactive")
		}

		isAllowed := false
		if childClient.RpID != "" && childClient.RpID == superClient.RpID {
			isAllowed = true
		} else if allowedClients, ok := superClient.AdditionalConfig["allowed_child_clients"].([]any); ok {
			for _, ac := range allowedClients {
				if s, ok := ac.(string); ok && s == clientID {
					isAllowed = true
					break
				}
			}
		} else if allowedClientsStr, ok := superClient.AdditionalConfig["allowed_child_clients"].([]string); ok {
			for _, s := range allowedClientsStr {
				if s == clientID {
					isAllowed = true
					break
				}
			}
		}

		if !isAllowed {
			p.logger.Warn(ctx, "vendor relationship mismatch between child app and super app",
				applog.String("childClientId", clientID),
				applog.String("superClientId", payload.ClientID),
				applog.String("childRpId", childClient.RpID),
				applog.String("superRpId", superClient.RpID),
			)
			return nil, deviceSecretError("invalid_grant", "Client is not authorized to exchange this device secret (vendor mismatch)")
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
		ExpiresAt: time.Unix(payload.Exp, 0),
		JTI:       payload.JTI,
	}, nil
}

func deviceSecretError(code, desc string) *common.ServiceError {
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
