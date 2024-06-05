/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto.claim;

import io.mosip.esignet.api.util.ErrorConstants;
import io.mosip.esignet.api.util.EvidenceType;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.util.List;

@Data
public class Evidence {

        private EvidenceType evidenceType;
        private FilterCriteria method;
        private FilterTime time;
        private VerificationMethod verificationMethod;
        private List<EvidenceCheckDetail> checkDetails;
        private DocumentDetails documentDetails;

}
