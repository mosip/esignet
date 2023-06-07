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
    private static final ObjectMapper objectMapper = new ObjectMapper();
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
