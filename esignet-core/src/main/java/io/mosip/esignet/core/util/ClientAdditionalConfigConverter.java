package io.mosip.esignet.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.exception.InvalidClientException;
import lombok.extern.slf4j.Slf4j;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.Map;

@Slf4j
@Converter(autoApply = true)
public class ClientAdditionalConfigConverter implements AttributeConverter<Map<String, Object>, String> {

    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(Map<String, Object> attribute) {
        if(attribute == null) return null;
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse client additionalConfig", e);
            throw new InvalidClientException();
        }
    }

    @Override
    public Map<String, Object> convertToEntityAttribute(String dbData) {
        if(dbData == null) return null;
        try {
            return objectMapper.readValue(dbData, new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException e) {
            log.error("Failed to parse client additionalConfig", e);
            throw new InvalidClientException();
        }
    }
}
