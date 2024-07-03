/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto.claim;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class VerificationDetail implements Serializable {

    private String trust_framework;
    private String time;
    private String assurance_level;
    private AssuranceProcess assurance_process;
    private String verification_process;
    private List<Evidence> evidence;

}

@Data
class AssuranceProcess {
    private String policy;
    private String procedure;
    private List<AssuranceDetail> assurance_details;
}

@Data
class AssuranceDetail {
    private String assurance_type;
    private String assurance_classification;
    private List<EvidenceRef> evidence_ref;
}

@Data
class EvidenceRef {
    private String txn;
    private String evidence_metadata;
    private String evidence_classification;
}

@Data
class Evidence {
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
class VerificationMethod {
    private String type;
}

@Data
class EvidenceCheckDetail {

    private String check_method;
    private String organisation;
    private String txn;
    private String time;

}

@Data
class DocumentDetail {
    private String type;
    private String document_number;
    private String date_of_issuance;
    private String date_of_expiry;
    private EvidenceIssuer issuer;
}

@Data
class ElectronicRecord {
    private String type;
    private String personal_number;
    private String created_at;
    private String date_of_expiry;
    private EvidenceIssuer source;
}

@Data
class EvidenceIssuer {
    private String name;
    private String country;
    private String country_code;
    private String jurisdiction;
}
