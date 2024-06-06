/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class DocumentDetail implements Serializable {

    private FilterCriteria type;
    private String document_number;
    private FilterDateTime date_of_issuance;
    private FilterDateTime date_of_expiry;
    private EvidenceIssuer issuer;


}