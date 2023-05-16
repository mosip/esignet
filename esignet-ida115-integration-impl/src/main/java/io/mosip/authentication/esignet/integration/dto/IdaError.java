/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.authentication.esignet.integration.dto;

import lombok.Data;

@Data
public class IdaError {

    private String actionMessage;
    private String errorCode;
    private String errorMessage;
}
