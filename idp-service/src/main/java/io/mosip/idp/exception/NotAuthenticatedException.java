/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.exception;

import io.mosip.idp.util.ErrorConstants;

public class NotAuthenticatedException extends IdPException {

    public NotAuthenticatedException() {
        super(ErrorConstants.INVALID_AUTH_TOKEN);
    }
}
