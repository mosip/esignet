/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.authentication.esignet.integration.dto;

import java.util.List;

import lombok.Data;

@Data
public class IdaSendOtpRequest {

    private String id;
    private String version;
    private String individualId;
    private String individualIdType;
    private String transactionID;
    private String requestTime;
    private List<String> otpChannel;

}
