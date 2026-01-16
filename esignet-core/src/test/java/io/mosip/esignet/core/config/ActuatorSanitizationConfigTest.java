package io.mosip.esignet.core.config;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.*;

class ActuatorSanitizationConfigTest {

    private ActuatorSanitizationConfig sanitizingFunction;

    @BeforeEach
    void setUp() {
        sanitizingFunction = new ActuatorSanitizationConfig();
        ReflectionTestUtils.setField(sanitizingFunction, "keysToBeSanitizedEndsWith", List.of("password", "secret"));
        ReflectionTestUtils.setField(sanitizingFunction, "keysToBeSanitizedContains", List.of("credentials"));
    }

    @Test
    void test_sanitizingFunction_endMatch_thenSanitized() {
        SanitizableData data = mock(SanitizableData.class);
        SanitizableData sanitizedData = mock(SanitizableData.class);

        when(data.getKey()).thenReturn("db.password");
        when(data.withSanitizedValue()).thenReturn(sanitizedData);

        SanitizableData result = sanitizingFunction.apply(data);

        assertSame(sanitizedData, result);
        verify(data).withSanitizedValue();
        verify(data, atLeastOnce()).getKey();
    }

    @Test
    void test_sanitizingFunction_containsMatch_thenSanitized() {
        SanitizableData data = mock(SanitizableData.class);
        SanitizableData sanitizedData = mock(SanitizableData.class);

        when(data.getKey()).thenReturn("API_CREDENTIALS_ID");
        when(data.withSanitizedValue()).thenReturn(sanitizedData);

        SanitizableData result = sanitizingFunction.apply(data);

        assertSame(sanitizedData, result);
        verify(data).withSanitizedValue();
        verify(data, atLeastOnce()).getKey();
    }

    @Test
    void test_sanitizingFunction_noMatch_thenOriginalData() {
        SanitizableData data = mock(SanitizableData.class);

        when(data.getKey()).thenReturn("application.name");

        SanitizableData result = sanitizingFunction.apply(data);

        assertSame(data, result);
        verify(data, never()).withSanitizedValue();
        verify(data, atLeastOnce()).getKey();
    }
}
