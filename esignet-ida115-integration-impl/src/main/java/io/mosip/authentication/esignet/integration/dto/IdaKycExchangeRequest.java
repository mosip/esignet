/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.authentication.esignet.integration.dto;

import java.util.List;

import lombok.Data;

@Data
public class IdaKycExchangeRequest {

    private String id;
    private String version;
    private String requestTime;
    private String transactionID;
    private String kycToken;
    private List<String> consentObtained;
    private List<String> locales;
    private String respType;
    private String individualId;
}
