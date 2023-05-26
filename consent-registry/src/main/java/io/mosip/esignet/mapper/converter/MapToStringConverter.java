package io.mosip.esignet.mapper.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.ClaimDetail;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import java.util.Map;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;

public class MapToStringConverter implements Converter<Map<String, Boolean>,String> {

    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convert(MappingContext<Map<String, Boolean>, String> mappingContext) {
        Map<String, Boolean> map = mappingContext.getSource();
        try{
            return map!=null?objectMapper.writeValueAsString(map):null;
        }
        catch (Exception e){
            throw new RuntimeException(INVALID_CLAIM);
        }
    }
}
