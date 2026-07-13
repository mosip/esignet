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

func (ts *IdsystemFactoryTestSuite) TestNewIDSystemProviders() {
	t := ts.T()
	t.Run("mock provider", func(t *testing.T) {
		authn, observability, err := NewIDSystemProviders(&config.AppConfig{Provider: "mock"}, nil)
		if err != nil {
			t.Fatalf("NewIDSystemProviders: %v", err)
		}
		if authn == nil || observability == nil {
			t.Error("expected non-nil authn and observability providers")
		}
	})

	t.Run("sunbird provider missing config errors", func(t *testing.T) {
		t.Setenv("MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_REGISTRY_SEARCH_URL", "")
		if _, _, err := NewIDSystemProviders(&config.AppConfig{Provider: "sunbird"}, nil); err == nil {
			t.Error("expected error when sunbird search URL is unconfigured")
		}
	})

	t.Run("unsupported provider", func(t *testing.T) {
		_, _, err := NewIDSystemProviders(&config.AppConfig{Provider: "bogus"}, nil)
		if err == nil {
			t.Fatal("expected error for unsupported provider")
		}
	})
}

type IdsystemFactoryTestSuite struct {
	suite.Suite
}

func TestIdsystemFactoryTestSuite(t *testing.T) {
	suite.Run(t, new(IdsystemFactoryTestSuite))
}
