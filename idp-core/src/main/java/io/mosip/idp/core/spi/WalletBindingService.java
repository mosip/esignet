/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.spi;

import io.mosip.idp.core.dto.WalletBindingRequest;
import io.mosip.idp.core.dto.WalletBindingResponse;
import io.mosip.idp.core.exception.IdPException;

public interface WalletBindingService {

    void sendBindingOtp() throws IdPException;

    WalletBindingResponse bindWallet(WalletBindingRequest walletBindingRequest) throws IdPException;

    void validateBinding() throws IdPException;
}