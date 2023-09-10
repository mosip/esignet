/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.spi;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.BindingAuthResult;
import io.mosip.esignet.api.exception.KycAuthException;

import java.util.List;

public interface KeyBindingValidator {

    BindingAuthResult validateBindingAuth(String transactionId, String individualId,
                                          List<AuthChallenge> challengeList) throws KycAuthException;
}
