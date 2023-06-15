package io.mosip.esignet.mapper.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonMappingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.exception.EsignetException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_PERMITTED_SCOPE;

@Slf4j
public class StringToMapConverter implements Converter<String,Map<String, Boolean>> {

    private static final ObjectMapper objectMapper = new ObjectMapper();

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
