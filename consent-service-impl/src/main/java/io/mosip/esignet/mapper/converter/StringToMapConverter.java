/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.mapper.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.exception.EsignetException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import java.util.Collections;
import java.util.Map;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_PERMITTED_SCOPE;

@Slf4j
public class StringToMapConverter implements Converter<String,Map<String, Boolean>> {

    private final ObjectMapper objectMapper;

    public StringToMapConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String,Boolean> convert(MappingContext<String,Map<String, Boolean>> mappingContext) {
        String authorizeScopes= mappingContext.getSource();
        try{
            return StringUtils.isNotBlank(authorizeScopes) ? objectMapper.readValue(authorizeScopes,Map.class): Collections.emptyMap();
        } catch (JsonProcessingException e) {
            throw new EsignetException(INVALID_PERMITTED_SCOPE);
        }
    }
}
