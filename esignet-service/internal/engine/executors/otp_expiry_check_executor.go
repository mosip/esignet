/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package executors

import (
	"strconv"
	"time"

	"github.com/thunder-id/thunderid/pkg/thunderidengine/providers"

	"github.com/mosip/esignet/internal/engine/shared"
	applog "github.com/mosip/esignet/internal/log"
)

const (
	// ExecutorNameEsignetOTPExpiryCheck rejects OTP submissions made after the validity window.
	ExecutorNameEsignetOTPExpiryCheck = "eSignetOtpExpiryCheckExecutor"

	// propertyKeyOTPValiditySeconds is the NodeProperties key for the OTP validity window in
	// seconds, configurable per flow node.
	propertyKeyOTPValiditySeconds = "otpValiditySeconds"

	// defaultOTPValiditySeconds is the fallback validity window when the node property is absent
	// or invalid. 300 s (5 minutes) matches common identity-system OTP lifetimes.
	defaultOTPValiditySeconds = 300
)

type otpExpiryCheckExecutor struct {
	baseExecutor
}

var _ providers.Executor = (*otpExpiryCheckExecutor)(nil)

// NewOtpExpiryCheckExecutor creates an executor that rejects OTP submissions after the
// configured validity window has elapsed since the OTP was issued.
func NewOtpExpiryCheckExecutor() providers.Executor {
	return &otpExpiryCheckExecutor{}
}

func (e *otpExpiryCheckExecutor) GetName() string {
	return ExecutorNameEsignetOTPExpiryCheck
}

func (e *otpExpiryCheckExecutor) GetType() providers.ExecutorType {
	return providers.ExecutorTypeUtility
}

func (e *otpExpiryCheckExecutor) GetMeta() *providers.ExecutorMeta {
	return &providers.ExecutorMeta{
		SupportedProperties: []providers.ExecutorSupportedProperties{
			{Property: propertyKeyOTPValiditySeconds},
		},
	}
}

func (e *otpExpiryCheckExecutor) Execute(ctx *providers.NodeContext) (*providers.ExecutorResponse, error) {
	execResp := &providers.ExecutorResponse{}

	issuedAtStr := ctx.RuntimeData[otpIssuedAtKey]
	if issuedAtStr == "" {
		applog.GetLogger().Warn(ctx.Context, "OTP expiry check: otpIssuedAt missing from RuntimeData; rejecting submission")
		execResp.Status = providers.ExecFailure
		execResp.Error = shared.InvalidOTPError
		return execResp, nil
	}

	issuedAt, err := time.Parse(time.RFC3339, issuedAtStr)
	if err != nil {
		applog.GetLogger().Warn(ctx.Context, "OTP expiry check: cannot parse otpIssuedAt; rejecting submission",
			applog.String("issuedAt", issuedAtStr))
		execResp.Status = providers.ExecFailure
		execResp.Error = shared.InvalidOTPError
		return execResp, nil
	}

	validitySeconds := otpValiditySecondsFromContext(ctx)
	age := time.Since(issuedAt)
	if age > time.Duration(validitySeconds)*time.Second {
		applog.GetLogger().Warn(ctx.Context, "OTP submission rejected: OTP has expired",
			applog.String("issuedAt", issuedAtStr),
			applog.String("ageSeconds", strconv.FormatInt(int64(age.Seconds()), 10)),
			applog.String("validitySeconds", strconv.Itoa(validitySeconds)))
		execResp.Status = providers.ExecFailure
		execResp.Error = shared.InvalidOTPError
		return execResp, nil
	}

	execResp.Status = providers.ExecComplete
	return execResp, nil
}

// otpValiditySecondsFromContext reads the otpValiditySeconds NodeProperty, falling back to
// defaultOTPValiditySeconds if not set or invalid.
func otpValiditySecondsFromContext(ctx *providers.NodeContext) int {
	switch v := ctx.NodeProperties[propertyKeyOTPValiditySeconds].(type) {
	case string:
		if n, err := strconv.Atoi(v); err == nil && n > 0 {
			return n
		}
	case int:
		if v > 0 {
			return v
		}
	case float64:
		if n := int(v); n > 0 {
			return n
		}
	}
	return defaultOTPValiditySeconds
}
