package io.mosip.esignet.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.exception.InvalidClientException;
import lombok.extern.slf4j.Slf4j;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Slf4j
@Converter(autoApply = true)
public class ClientAdditionalConfigConverter implements AttributeConverter<JsonNode, String> {

    ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(JsonNode attribute) {
        if(attribute == null || attribute.isNull()) return null;
        try {
            return objectMapper.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse client additionalConfig", e);
            throw new InvalidClientException();
        }
    }

    @Override
    public JsonNode convertToEntityAttribute(String dbData) {
        if(dbData == null) return null;
        try {
            return objectMapper.readTree(dbData);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse client additionalConfig", e);
            throw new InvalidClientException();
        }
    }
}
