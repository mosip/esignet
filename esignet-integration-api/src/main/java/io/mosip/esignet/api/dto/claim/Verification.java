package io.mosip.esignet.api.dto.claim;

/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import io.mosip.esignet.api.util.ErrorConstants;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class Verification implements Serializable {

    @NotBlank(message=ErrorConstants.INVALID_TRUST_FRAMEWORK)
    private FilterCriteria trustFramework;
    private FilterTime time;
    private FilterCriteria assuranceLevel;
    private List<Evidence> evidence;

}
