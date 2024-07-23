/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto.claim;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class VerificationFilter implements Serializable {

    private FilterCriteria trust_framework;
    private FilterDateTime time;
    private FilterCriteria assurance_level;
    private List<EvidenceFilter> evidence;
    private FilterCriteria verification_process;

}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class EvidenceFilter implements Serializable  {

    private FilterCriteria type;
    private FilterCriteria method;
    private FilterDateTime time;
    private VerificationMethodFilter verification_method;
    private List<EvidenceCheckDetailFilter> check_details;
    private DocumentDetailFilter document_details;
    private String attestation;
    private ElectronicRecordFilter record;

    private FilterCriteria signature_type;
    private FilterCriteria issuer;
    private String serial_number;
    private FilterDateTime created_at;

}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class VerificationMethodFilter implements Serializable {
    private FilterCriteria type;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class EvidenceCheckDetailFilter implements Serializable {
    private String check_method;
    private String organisation;
    private String txn;
    private FilterDateTime time;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class DocumentDetailFilter implements Serializable {
    private FilterCriteria type;
    private String document_number;
    private FilterDateTime date_of_issuance;
    private FilterDateTime date_of_expiry;
    private EvidenceIssuer issuer;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class ElectronicRecordFilter implements Serializable {
    private FilterCriteria type;
    private String personal_number;
    private FilterDateTime created_at;
    private FilterDateTime date_of_expiry;
    private EvidenceIssuerFilter source;
}

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
class EvidenceIssuerFilter implements Serializable {
    private FilterCriteria name;
    private FilterCriteria country;
    private FilterCriteria country_code;
    private FilterCriteria jurisdiction;
}
