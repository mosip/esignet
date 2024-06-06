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
public class Evidence implements Serializable  {

        private FilterCriteria type;
        private FilterCriteria method;
        private FilterDateTime time;
        private VerificationMethod verification_method;
        private List<EvidenceCheckDetail> check_details;
        private DocumentDetail document_details;
        private String attestation;
        private ElectronicRecord record;

        private FilterCriteria signature_type;
        private FilterCriteria issuer;
        private String serial_number;
        private FilterDateTime created_at;

}
