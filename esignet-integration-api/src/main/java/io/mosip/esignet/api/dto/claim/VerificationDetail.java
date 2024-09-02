/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.dto.claim;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.List;

@Slf4j
@Data
public class VerificationDetail implements Serializable {

    private static final long serialVersionUID = 1L;

    private String trust_framework;
    private String time;
    private String assurance_level;
    private AssuranceProcess assurance_process;
    private String verification_process;
    private List<Evidence> evidence;
}
