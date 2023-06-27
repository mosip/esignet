/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.mapper.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.core.exception.EsignetException;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;

@Slf4j
public class ClaimsToStringConverter implements Converter<Claims, String> {
    private final ObjectMapper objectMapper;

    public ClaimsToStringConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convert(MappingContext<Claims, String> context) {
        Claims claims = context.getSource();
        try {
            return claims != null ? objectMapper.writeValueAsString(claims) : "";
        } catch (JsonProcessingException e) {
            throw new EsignetException(INVALID_CLAIM);
        }
    }
}