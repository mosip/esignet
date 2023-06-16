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
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;

@Slf4j
public class StringToClaimsConverter implements Converter<String, Claims>
{
    private final ObjectMapper objectMapper;

    public StringToClaimsConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Claims convert(MappingContext<String, Claims> context) {
        String claims = context.getSource();
        try {
            return StringUtils.isNotBlank(claims) ? objectMapper.readValue(claims, Claims.class) : null;
        } catch (JsonProcessingException e) {
            throw new EsignetException(INVALID_CLAIM);
        }
    }
}
