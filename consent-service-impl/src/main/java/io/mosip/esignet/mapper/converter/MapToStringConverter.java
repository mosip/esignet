/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.mapper.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.exception.EsignetException;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import java.util.Map;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_PERMITTED_SCOPE;

public class MapToStringConverter implements Converter<Map<String, Boolean>,String> {

    private final ObjectMapper objectMapper;

    public MapToStringConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convert(MappingContext<Map<String, Boolean>, String> mappingContext) {
        Map<String, Boolean> map = mappingContext.getSource();
        try{
            return map!=null?objectMapper.writeValueAsString(map):"";
        }catch (JsonProcessingException e) {
            throw new EsignetException(INVALID_PERMITTED_SCOPE);
        }
    }
}