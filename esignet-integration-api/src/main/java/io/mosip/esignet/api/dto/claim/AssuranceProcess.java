/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AssuranceProcess {
    private String policy;
    private String procedure;
    private List<AssuranceDetail> assurance_details;
}


@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class AssuranceDetail {
    private String assurance_type;
    private String assurance_classification;
    private List<EvidenceRef> evidence_ref;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class EvidenceRef {
    private String txn;
    private String evidence_metadata;
    private String evidence_classification;
}