package io.mosip.esignet.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.exception.InvalidClientException;
import io.mosip.esignet.core.util.ClientAdditionalConfigConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClientAdditionalConfigConverterTest {

    private ClientAdditionalConfigConverter converter;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        converter = new ClientAdditionalConfigConverter();
    }

    @Test
    public void convertToDatabaseColumn_NullMap_ReturnsNull() {
        Assertions.assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    public void convertToDatabaseColumn_ValidJsonNode_ReturnsString() throws JsonProcessingException {
        String jsonString = "{\"key\":\"value\"}";
        JsonNode jsonNode = objectMapper.readTree(jsonString);

        String result = converter.convertToDatabaseColumn(jsonNode);
        Assertions.assertEquals(jsonString, result);
    }

    @Test
    public void convertToEntityAttribute_NullJson_ReturnsNull() {
        Assertions.assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    public void convertToEntityAttribute_InvalidJson_ThrowsException() {
        String invalidJson = "{\"invalid: value}";
        Assertions.assertThrows(InvalidClientException.class, () ->
                converter.convertToEntityAttribute(invalidJson)
        );
    }

}
