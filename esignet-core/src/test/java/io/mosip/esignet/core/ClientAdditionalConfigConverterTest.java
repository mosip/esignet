package io.mosip.esignet.core;

import io.mosip.esignet.core.exception.InvalidClientException;
import io.mosip.esignet.core.util.ClientAdditionalConfigConverter;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClientAdditionalConfigConverterTest {

    private ClientAdditionalConfigConverter converter;

    @BeforeEach
    void setUp() {
        converter = new ClientAdditionalConfigConverter();
    }

    @Test
    public void convertToDatabaseColumn_NullMap_ReturnsNull() {
        Assertions.assertNull(converter.convertToDatabaseColumn(null));
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
