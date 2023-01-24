/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.spi;


import io.mosip.esignet.core.dto.ValidateBindingResponse;
import io.mosip.esignet.core.dto.ValidateBindingRequest;
import io.mosip.esignet.core.exception.IdPException;

public interface KeyBindingValidator {
    ValidateBindingResponse validateBinding(ValidateBindingRequest validateBindingRequest) throws IdPException;
}
