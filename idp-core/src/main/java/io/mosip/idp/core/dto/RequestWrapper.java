/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.dto;

import io.mosip.idp.core.validator.RequestTime;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.NotNull;

import static io.mosip.idp.core.util.ErrorConstants.INVALID_REQUEST;

@Data
public class RequestWrapper<T> {

    private String id;
    private String version;

    @RequestTime
    private String requestTime;

    @NotNull(message = INVALID_REQUEST)
    @Valid
    private T request;
}
