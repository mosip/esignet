/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.authwrapper.dto;

import io.mosip.idp.core.dto.Error;
import lombok.Data;

import java.util.List;

@Data
public class IdaSendOtpResponse {

    private String id;
    private String version;
    private String transactionID;
    private String responseTime;
    private List<Error> errors;
    private IdaOtpResponse response;
}

