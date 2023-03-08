/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;

import io.mosip.esignet.core.dto.BindingOtpResponse;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.dto.BindingOtpRequest;
import io.mosip.esignet.core.dto.WalletBindingRequest;
import io.mosip.esignet.core.dto.WalletBindingResponse;

import java.util.Map;

public interface KeyBindingService {

    BindingOtpResponse sendBindingOtp(BindingOtpRequest otpRequest, Map<String, String> requestHeaders) throws EsignetException;

    WalletBindingResponse bindWallet(WalletBindingRequest walletBindingRequest, Map<String, String> requestHeaders) throws EsignetException;
}
