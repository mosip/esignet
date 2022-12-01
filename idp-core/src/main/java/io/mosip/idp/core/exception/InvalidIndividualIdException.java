/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.exception;

import io.mosip.idp.core.util.ErrorConstants;

public class InvalidIndividualIdException extends IdPException {

	public InvalidIndividualIdException() {
		super(ErrorConstants.INVALID_INDIVIDUAL_ID);
	}
}
