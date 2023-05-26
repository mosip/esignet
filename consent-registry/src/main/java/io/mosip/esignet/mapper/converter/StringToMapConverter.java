package io.mosip.esignet.mapper.converter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.Converter;
import org.modelmapper.spi.MappingContext;

import java.util.Map;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;

@Slf4j
public class StringToMapConverter implements Converter<String,Map<String, Boolean>> {

    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Map<String,Boolean> convert(MappingContext<String,Map<String, Boolean>> mappingContext) {
        String authorizeScopes= mappingContext.getSource();
        try{
            log.info("StringToMapConverter.convert() authorizeScopes: {}", authorizeScopes);
            return authorizeScopes!=null?objectMapper.readValue(authorizeScopes,Map.class):null;

        }
        catch (Exception e){
            throw new RuntimeException(INVALID_CLAIM);
        }
    }
}
