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
public class Evidence {

    private String type;
    private String method;
    private String time;
    private VerificationMethod verification_method;
    private List<EvidenceCheckDetail> check_details;
    private DocumentDetail document_details;
    private String attestation;
    private ElectronicRecord record;

    private String signature_type;
    private String issuer;
    private String serial_number;
    private String created_at;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class VerificationMethod {
    private String type;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class EvidenceCheckDetail {

    private String check_method;
    private String organisation;
    private String check_id;
    private String time;

}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class DocumentDetail {
    private String type;
    private String document_number;
    private String date_of_issuance;
    private String date_of_expiry;
    private EvidenceIssuer issuer;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class ElectronicRecord {
    private String type;
    private String personal_number;
    private String created_at;
    private String date_of_expiry;
    private EvidenceIssuer source;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class EvidenceIssuer {
    private String name;
    private String country;
    private String country_code;
    private String jurisdiction;
}

