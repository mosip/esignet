package io.mosip.esignet.controllers;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.web.error.ErrorAttributeOptions;
import org.springframework.boot.web.servlet.error.ErrorAttributes;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class RestErrorControllerTest {

    @Test
    void handleError_shouldReturnErrorAttributesAndStatus() {
        // Arrange
        ErrorAttributes errorAttributes = Mockito.mock(ErrorAttributes.class);
        RestErrorController controller = new RestErrorController(errorAttributes);
        MockHttpServletRequest request = new MockHttpServletRequest();

        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("status", 404);
        errorMap.put("error", "Not Found");
        errorMap.put("message", "Resource not found");
        Mockito.when(errorAttributes.getErrorAttributes(Mockito.any(), Mockito.any(ErrorAttributeOptions.class)))
                .thenReturn(errorMap);

        // Act
        ResponseEntity<Map<String, Object>> response = controller.handleError(request);

        // Assert
        assertEquals(HttpStatus.NOT_FOUND, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("", response.getBody().get("path"));
    }

    @Test
    void handleError_shouldDefaultTo500IfStatusMissing() {
        ErrorAttributes errorAttributes = Mockito.mock(ErrorAttributes.class);
        RestErrorController controller = new RestErrorController(errorAttributes);
        MockHttpServletRequest request = new MockHttpServletRequest();

        Map<String, Object> errorMap = new HashMap<>();
        errorMap.put("error", "Internal Server Error");
        Mockito.when(errorAttributes.getErrorAttributes(Mockito.any(), Mockito.any(ErrorAttributeOptions.class)))
                .thenReturn(errorMap);

        ResponseEntity<Map<String, Object>> response = controller.handleError(request);

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, response.getStatusCode());
        assertNotNull(response.getBody());
        assertEquals("Internal Server Error", response.getBody().get("error"));
        assertEquals("", response.getBody().get("path"));
    }
}

