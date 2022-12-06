/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.BindingOtpRequest;
import io.mosip.idp.core.dto.OtpResponse;
import io.mosip.idp.core.dto.ValidateBindingRequest;
import io.mosip.idp.core.dto.ValidateBindingResponse;
import io.mosip.idp.core.dto.WalletBindingRequest;
import io.mosip.idp.core.dto.WalletBindingResponse;
import io.mosip.idp.core.exception.IdPException;

public interface WalletBindingService {

	OtpResponse sendBindingOtp(BindingOtpRequest otpRequest) throws IdPException;

    WalletBindingResponse bindWallet(WalletBindingRequest walletBindingRequest) throws IdPException;

    ValidateBindingResponse validateBinding(ValidateBindingRequest validateBindingRequest) throws IdPException;
}
