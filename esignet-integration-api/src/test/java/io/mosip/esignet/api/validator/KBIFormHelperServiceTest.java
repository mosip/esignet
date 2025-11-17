package io.mosip.esignet.api.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.exception.KBIFormException;
import io.mosip.esignet.api.util.ErrorConstants;
import io.mosip.esignet.api.util.KBIFormHelperService;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ExtendWith(MockitoExtension.class)
public class KBIFormHelperServiceTest {

    @InjectMocks
    private KBIFormHelperService kbiFormHelperService;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(kbiFormHelperService, "objectMapper", new ObjectMapper());
    }

    @Test
    public void migrateKBIFieldDetails_withValidInput_thenPass() throws KBIFormException {
        List<Map<String, String>> kbiFieldDetails = List.of(
                Map.of("id", "individualId", "type", "text", "regex", "^\\d{12}$"),
                Map.of("id", "fullName", "type", "text", "regex", "^[A-Za-z\\s]{1,}[\\.]{0,1}[A-Za-z\\s]{0,}$"),
                Map.of("id", "dob", "type", "date")
        );

        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);

        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.has("schema"));
        Assertions.assertEquals(3, result.get("schema").size());

        JsonNode firstField = result.get("schema").get(0);
        Assertions.assertEquals("individualId", firstField.get("id").asText());
        Assertions.assertEquals("textbox", firstField.get("controlType").asText());
        Assertions.assertFalse(firstField.get("validators").isEmpty());
    }

    @Test
    public void migrateKBIFieldDetails_withEmptyList_thenPass() throws KBIFormException {
        List<Map<String, String>> kbiFieldDetails = new ArrayList<>();
        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
        Assertions.assertNull(result);
    }

    @Test
    public void migrateKBIFieldDetails_withNullRegex_thenPass() throws KBIFormException {
        Map<String, String> field = new HashMap<>();
        field.put("id", "dob");
        field.put("type", "date");
        field.put("regex", null);
        List<Map<String, String>> kbiFieldDetails = List.of(field);
        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
        Assertions.assertNotNull(result);
        JsonNode fieldNode = result.get("schema").get(0);
        Assertions.assertEquals("dob", fieldNode.get("id").asText());
        Assertions.assertEquals("date", fieldNode.get("controlType").asText());
        Assertions.assertTrue(fieldNode.get("validators").isEmpty());
    }

    @Test
    public void migrateKBIFieldDetails_withEmptyRegex_thenPass() throws KBIFormException {
        List<Map<String, String>> kbiFieldDetails = List.of(
                Map.of("id", "dob", "type", "date", "regex", "")
        );
        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
        Assertions.assertNotNull(result);
        JsonNode field = result.get("schema").get(0);
        Assertions.assertTrue(field.get("validators").isEmpty());
    }

    @Test
    public void migrateKBIFieldDetails_withNullInput_thenPass() throws KBIFormException {
        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(null);
        Assertions.assertNull(result);
    }

    @Test
    public void migrateKBIFieldDetails_withEmptyMap_thenFail() {
        List<Map<String, String>> kbiFieldDetails = List.of(
                new HashMap<>()
        );
        try {
            kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
            Assertions.fail();
        } catch (KBIFormException e) {
            Assertions.assertEquals(e.getErrorCode(), ErrorConstants.KBI_SCHEMA_PARSE_ERROR);
        }
    }

    @Test
    public void migrateKBIFieldDetails_withInvalidField_thenFail() {
        Map<String, String> invalidField = new HashMap<>();
        invalidField.put("type", "text");
        List<Map<String, String>> kbiFieldDetails = List.of(invalidField);
        try {
            kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
            Assertions.fail();
        } catch (KBIFormException e) {
            Assertions.assertEquals(e.getErrorCode(), ErrorConstants.KBI_SCHEMA_PARSE_ERROR);
        }
    }

    @Test
    public void migrateKBIFieldDetails_withLanguageField_thenPass() throws KBIFormException {
        List<Map<String, String>> kbiFieldDetails = List.of(
                Map.of("id", "individualId", "type", "text", "regex", "^\\d{12}$")
        );

        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
        Assertions.assertNotNull(result);
        Assertions.assertTrue(result.has("language"));

        JsonNode languageNode = result.get("language");
        Assertions.assertTrue(languageNode.has("mandatory"));
        Assertions.assertTrue(languageNode.has("langCodeMap"));

        Assertions.assertTrue(languageNode.get("mandatory").isArray());
        Assertions.assertEquals("eng", languageNode.get("mandatory").get(0).asText());

        JsonNode langCodeMap = languageNode.get("langCodeMap");
        Assertions.assertEquals("en", langCodeMap.get("eng").asText());
    }

    @Test
    public void migrateKBIFieldDetails_withDateTypeAndDefaultFormat_thenPass() throws KBIFormException {
        List<Map<String, String>> kbiFieldDetails = List.of(
                Map.of("id", "dob", "type", "date")
        );

        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
        Assertions.assertNotNull(result);

        JsonNode field = result.get("schema").get(0);
        Assertions.assertEquals("date", field.get("type").asText());
        Assertions.assertEquals("yyyy-MM-dd", field.get("format").asText());
    }

    @Test
    public void migrateKBIFieldDetails_withDateTypeAndCustomFormat_thenPass() throws KBIFormException {
        List<Map<String, String>> kbiFieldDetails = List.of(
                Map.of("id", "dob", "type", "date", "format", "MM/dd/yyyy")
        );

        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
        Assertions.assertNotNull(result);

        JsonNode field = result.get("schema").get(0);
        Assertions.assertEquals("date", field.get("type").asText());
        Assertions.assertEquals("MM/dd/yyyy", field.get("format").asText());
    }

}
