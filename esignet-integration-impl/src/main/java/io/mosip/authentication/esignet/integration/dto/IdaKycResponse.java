/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.authentication.esignet.integration.dto;

import lombok.Data;

@Data
public class IdaKycResponse {

	/** The Variable to hold value of kyc Status */
	private boolean kycStatus;
	
	/** The Variable to hold value of kyc Status */
	private boolean authStatus;

	/** The Variable to hold value of auth Token */
	private String authToken;
	
	private String thumbprint;
	
	private String sessionKey;

	/** The Variable to hold value of identity */
	private String identity;
}
