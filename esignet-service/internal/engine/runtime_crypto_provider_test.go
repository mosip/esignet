/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package engine

import (
	"testing"

	"github.com/stretchr/testify/suite"

	"github.com/mosip/esignet/internal/config"
)

type RuntimeCryptoProviderTestSuite struct {
	suite.Suite
}

func TestRuntimeCryptoProviderTestSuite(t *testing.T) {
	suite.Run(t, new(RuntimeCryptoProviderTestSuite))
}

func (ts *RuntimeCryptoProviderTestSuite) TestNewRuntimeCryptoProvider_DefaultsSignReferenceIDWhenUnset() {
	cfg := &config.AppConfig{}

	p := NewRuntimeCryptoProvider(cfg, nil, nil, nil).(*runtimeCryptoProvider)

	ts.Require().Equal(defaultSignReferenceID, p.signReferenceID)
}

func (ts *RuntimeCryptoProviderTestSuite) TestNewRuntimeCryptoProvider_UsesConfiguredPreferredKeyID() {
	cfg := &config.AppConfig{}
	cfg.JWT.PreferredKeyID = "RSA_2048"

	p := NewRuntimeCryptoProvider(cfg, nil, nil, nil).(*runtimeCryptoProvider)

	ts.Require().Equal("RSA_2048", p.signReferenceID)
}

func (ts *RuntimeCryptoProviderTestSuite) TestReferenceID_IgnoresKeyIDAndUsesSignReferenceID() {
	p := &runtimeCryptoProvider{signReferenceID: "configured-ref-id"}

	ts.Require().Equal("configured-ref-id", p.referenceID("some-other-key-id"))
	ts.Require().Equal("configured-ref-id", p.referenceID(""))
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedSigningAlgorithms_FiltersToConfiguredSet() {
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{
		SupportedSigningAlgorithms: []string{"PS256", "EdDSA"},
	}}

	ts.Require().Equal([]string{"PS256", "EdDSA"}, p.GetSupportedSigningAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedSigningAlgorithms_PreservesSignaturePackageOrder() {
	// signature.SupportedAlgorithms() returns PS256, RS256, ES256, ES256K, EdDSA;
	// the configured set here lists them in the opposite order, so the
	// filtered result should still come back in the signature package's order.
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{
		SupportedSigningAlgorithms: []string{"EdDSA", "ES256K", "PS256"},
	}}

	ts.Require().Equal([]string{"PS256", "ES256K", "EdDSA"}, p.GetSupportedSigningAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedSigningAlgorithms_UnknownConfiguredAlgorithmIgnored() {
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{
		SupportedSigningAlgorithms: []string{"BOGUS"},
	}}

	ts.Require().Empty(p.GetSupportedSigningAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedSigningAlgorithms_EmptyConfigReturnsEmpty() {
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{}}

	ts.Require().Empty(p.GetSupportedSigningAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedEncryptionAlgorithms_ReturnsConfiguredList() {
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{
		SupportedEncAlgorithms: []string{"AES-GCM", "RSA-OAEP-256"},
	}}

	ts.Require().Equal([]string{"AES-GCM", "RSA-OAEP-256"}, p.GetSupportedEncryptionAlgorithms())
}

func (ts *RuntimeCryptoProviderTestSuite) TestGetSupportedEncryptionAlgorithms_EmptyConfigReturnsEmpty() {
	p := &runtimeCryptoProvider{cfg: &config.AppConfig{}}

	ts.Require().Empty(p.GetSupportedEncryptionAlgorithms())
}
