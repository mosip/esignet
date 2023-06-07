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
    private static final ObjectMapper objectMapper = new ObjectMapper();
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