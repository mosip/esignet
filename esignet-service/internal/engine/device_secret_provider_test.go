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
	"strings"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
	"github.com/stretchr/testify/suite"
	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/clientmgmt"
	"github.com/mosip/esignet/internal/clientmgmt/db"
	"github.com/mosip/esignet/internal/config"
	"github.com/mosip/esignet/internal/keymanager"
	"github.com/mosip/esignet/internal/keymanager/kmtest"
	"github.com/mosip/esignet/internal/keymanager/signature"
)

type DeviceSecretProviderTestSuite struct {
	suite.Suite
	km        *keymanager.Service
	sigSvc    *signature.Service
	appCfg    *config.AppConfig
	superRow  db.ClientDetail
	childRow  db.ClientDetail
	otherRow  db.ClientDetail
	clientSvc *clientmgmt.Service
}

func TestDeviceSecretProviderTestSuite(t *testing.T) {
	suite.Run(t, new(DeviceSecretProviderTestSuite))
}

type multiClientStubQuerier struct {
	db.Querier
	clients map[string]db.ClientDetail
}

func (q *multiClientStubQuerier) GetActiveClient(_ context.Context, id string) (db.ClientDetail, error) {
	c, ok := q.clients[id]
	if !ok || c.Status != "ACTIVE" {
		return db.ClientDetail{}, sql.ErrNoRows
	}
	return c, nil
}

func (ts *DeviceSecretProviderTestSuite) SetupSuite() {
	t := ts.T()
	ctx := context.Background()

	ts.km = keymanager.NewServiceWithQuerier(kmtest.NewStateQuerier(), kmtest.NewFakeKeyStore(), keymanager.Config{
		AsymmetricKeyLength:  2048,
		CertCommonName:       "www.mosip.io",
		CertOrganizationUnit: "thunder-tech-team",
		CertOrganization:     "IIITB",
		CertLocation:         "Bangalore",
		CertState:            "KA",
		CertCountry:          "IN",
	})

	_, err := ts.km.GenerateMasterKey(ctx, keymanager.GenerateMasterKeyRequest{
		ApplicationID: keymanager.AppIDRoot,
		ObjectType:    keymanager.ObjectTypeCertificate,
		CommonName:    "MOSIP Root CA",
	})
	require.NoError(t, err)

	_, err = ts.km.GenerateMasterKey(ctx, keymanager.GenerateMasterKeyRequest{
		ApplicationID: config.OIDCServiceAppID,
		ReferenceID:   defaultSignReferenceID,
		ObjectType:    keymanager.ObjectTypeCertificate,
		CommonName:    "test esignet sign key",
	})
	require.NoError(t, err)

	ts.sigSvc = signature.NewService(ts.km)
	ts.appCfg = &config.AppConfig{
		Issuer: "https://esignet.example.com",
	}

	ts.superRow = db.ClientDetail{
		ID:           "super-app-id",
		Name:         `{"@none":"Super App"}`,
		RpID:         "vendor-family-1",
		RedirectUris: `["https://super.example.com"]`,
		Claims:       `["openid"]`,
		AcrValues:    `[]`,
		GrantTypes:   `["authorization_code"]`,
		AuthMethods:  `["client_secret_basic"]`,
		Status:       "ACTIVE",
	}

	ts.childRow = db.ClientDetail{
		ID:           "child-app-id",
		Name:         `{"@none":"Child App"}`,
		RpID:         "vendor-family-1", // Same RpID
		RedirectUris: `["https://child.example.com"]`,
		Claims:       `["openid"]`,
		AcrValues:    `[]`,
		GrantTypes:   `["token_exchange"]`,
		AuthMethods:  `["client_secret_basic"]`,
		Status:       "ACTIVE",
	}

	ts.otherRow = db.ClientDetail{
		ID:           "other-vendor-app-id",
		Name:         `{"@none":"Other App"}`,
		RpID:         "vendor-family-2", // Different RpID
		RedirectUris: `["https://other.example.com"]`,
		Claims:       `["openid"]`,
		AcrValues:    `[]`,
		GrantTypes:   `["token_exchange"]`,
		AuthMethods:  `["client_secret_basic"]`,
		Status:       "ACTIVE",
	}

	clients := map[string]db.ClientDetail{
		ts.superRow.ID: ts.superRow,
		ts.childRow.ID: ts.childRow,
		ts.otherRow.ID: ts.otherRow,
	}

	ts.clientSvc = clientmgmt.NewServiceWithQuerier(&multiClientStubQuerier{clients: clients}, nil, 0, nil)
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_Success() {
	p := NewDeviceSecretProvider(ts.appCfg, ts.clientSvc, ts.sigSvc)

	params := &providers.DeviceSecretParams{
		Sub:            "user-12345",
		AuthID:         "code-67890",
		ClientID:       "super-app-id",
		Scope:          "openid profile device_sso",
		ACR:            "mosip:idp:acr:generated-code",
		Claims:         map[string]any{"name": "Alice"},
		CnfJKT:         "dpop-key-a-jkt-thumbprint",
		ExpiryDuration: 30 * 24 * time.Hour,
	}

	res, svcErr := p.GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(svcErr)
	ts.Require().NotNil(res)
	ts.Require().NotEmpty(res.DeviceSecret)
	ts.Require().NotEmpty(res.DSHash)

	// Check that DeviceSecret is a 3-part compact JWS
	parts := strings.Split(res.DeviceSecret, ".")
	ts.Require().Len(parts, 3)

	// Check ds_hash matches sha256 of DeviceSecret
	hasher := sha256.New()
	hasher.Write([]byte(res.DeviceSecret))
	expectedDSHash := base64.RawURLEncoding.EncodeToString(hasher.Sum(nil))
	ts.Require().Equal(expectedDSHash, res.DSHash)
}

func (ts *DeviceSecretProviderTestSuite) TestGenerateDeviceSecret_MissingSub_Fails() {
	p := NewDeviceSecretProvider(ts.appCfg, ts.clientSvc, ts.sigSvc)

	params := &providers.DeviceSecretParams{
		ClientID: "super-app-id",
	}

	res, svcErr := p.GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(res)
	ts.Require().NotNil(svcErr)
	ts.Require().Equal("invalid_request", svcErr.Code)
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_SameClient_Success() {
	p := NewDeviceSecretProvider(ts.appCfg, ts.clientSvc, ts.sigSvc)

	params := &providers.DeviceSecretParams{
		Sub:            "user-12345",
		AuthID:         "code-67890",
		ClientID:       "super-app-id",
		Scope:          "openid profile",
		CnfJKT:         "dpop-thumbprint",
		ExpiryDuration: time.Hour,
	}

	genRes, svcErr := p.GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(svcErr)

	// Validate by super-app itself
	claims, valErr := p.ValidateDeviceSecret(context.Background(), "super-app-id", genRes.DeviceSecret)
	ts.Require().Nil(valErr)
	ts.Require().NotNil(claims)
	ts.Require().Equal("user-12345", claims.Sub)
	ts.Require().Equal("super-app-id", claims.ClientID)
	ts.Require().Equal("dpop-thumbprint", claims.CnfJKT)
	ts.Require().Equal("openid profile", claims.Scope)
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_ChildClientSameVendor_Success() {
	p := NewDeviceSecretProvider(ts.appCfg, ts.clientSvc, ts.sigSvc)

	params := &providers.DeviceSecretParams{
		Sub:            "user-12345",
		AuthID:         "code-67890",
		ClientID:       "super-app-id",
		Scope:          "openid profile",
		CnfJKT:         "dpop-thumbprint",
		ExpiryDuration: time.Hour,
	}

	genRes, svcErr := p.GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(svcErr)

	// Validate by child-app-id (which shares RpID "vendor-family-1" with super-app-id)
	claims, valErr := p.ValidateDeviceSecret(context.Background(), "child-app-id", genRes.DeviceSecret)
	ts.Require().Nil(valErr)
	ts.Require().NotNil(claims)
	ts.Require().Equal("user-12345", claims.Sub)
	ts.Require().Equal("super-app-id", claims.ClientID)
	ts.Require().Equal("dpop-thumbprint", claims.CnfJKT)
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_DifferentVendor_Rejected() {
	p := NewDeviceSecretProvider(ts.appCfg, ts.clientSvc, ts.sigSvc)

	params := &providers.DeviceSecretParams{
		Sub:            "user-12345",
		AuthID:         "code-67890",
		ClientID:       "super-app-id",
		Scope:          "openid profile",
		CnfJKT:         "dpop-thumbprint",
		ExpiryDuration: time.Hour,
	}

	genRes, svcErr := p.GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(svcErr)

	// Validate by other-vendor-app-id (has RpID "vendor-family-2") -> must be rejected
	claims, valErr := p.ValidateDeviceSecret(context.Background(), "other-vendor-app-id", genRes.DeviceSecret)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_grant", valErr.Code)
	ts.Require().Contains(valErr.ErrorDescription.DefaultValue, "vendor mismatch")
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_TamperedToken_Rejected() {
	p := NewDeviceSecretProvider(ts.appCfg, ts.clientSvc, ts.sigSvc)

	params := &providers.DeviceSecretParams{
		Sub:            "user-12345",
		ClientID:       "super-app-id",
		ExpiryDuration: time.Hour,
	}

	genRes, svcErr := p.GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(svcErr)

	// Tamper signature by changing last character
	tampered := genRes.DeviceSecret[:len(genRes.DeviceSecret)-2] + "xx"

	claims, valErr := p.ValidateDeviceSecret(context.Background(), "super-app-id", tampered)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_grant", valErr.Code)
	ts.Require().Contains(valErr.ErrorDescription.DefaultValue, "Invalid device secret signature")
}

func (ts *DeviceSecretProviderTestSuite) TestValidateDeviceSecret_ExpiredToken_Rejected() {
	p := NewDeviceSecretProvider(ts.appCfg, ts.clientSvc, ts.sigSvc)

	// Pass negative duration so exp is in the past
	params := &providers.DeviceSecretParams{
		Sub:            "user-12345",
		ClientID:       "super-app-id",
		ExpiryDuration: -time.Hour,
	}

	genRes, svcErr := p.GenerateDeviceSecret(context.Background(), params)
	ts.Require().Nil(svcErr)

	claims, valErr := p.ValidateDeviceSecret(context.Background(), "super-app-id", genRes.DeviceSecret)
	ts.Require().Nil(claims)
	ts.Require().NotNil(valErr)
	ts.Require().Equal("invalid_grant", valErr.Code)
	ts.Require().Contains(valErr.ErrorDescription.DefaultValue, "Device secret has expired")
}
