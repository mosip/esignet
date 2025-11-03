package io.mosip.esignet.api.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.exception.KBIFormException;
import io.mosip.esignet.api.util.ErrorConstants;
import io.mosip.esignet.api.util.KBIFormHelperService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class KBIFormHelperServiceTest {

    @InjectMocks
    private KBIFormHelperService kbiFormHelperService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
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

        Assert.assertNotNull(result);
        Assert.assertTrue(result.has("schema"));
        Assert.assertEquals(3, result.get("schema").size());

        JsonNode firstField = result.get("schema").get(0);
        Assert.assertEquals("individualId", firstField.get("id").asText());
        Assert.assertEquals("textbox", firstField.get("controlType").asText());
        Assert.assertFalse(firstField.get("validators").isEmpty());
    }

    @Test
    public void migrateKBIFieldDetails_withEmptyList_thenPass() throws KBIFormException {
        List<Map<String, String>> kbiFieldDetails = new ArrayList<>();
        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
        Assert.assertNull(result);
    }

    @Test
    public void migrateKBIFieldDetails_withNullRegex_thenPass() throws KBIFormException {
        Map<String, String> field = new HashMap<>();
        field.put("id", "dob");
        field.put("type", "date");
        field.put("regex", null);
        List<Map<String, String>> kbiFieldDetails = List.of(field);
        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
        Assert.assertNotNull(result);
        JsonNode fieldNode = result.get("schema").get(0);
        Assert.assertEquals("dob", fieldNode.get("id").asText());
        Assert.assertEquals("date", fieldNode.get("controlType").asText());
        Assert.assertTrue(fieldNode.get("validators").isEmpty());
    }

    @Test
    public void migrateKBIFieldDetails_withEmptyRegex_thenPass() throws KBIFormException {
        List<Map<String, String>> kbiFieldDetails = List.of(
                Map.of("id", "dob", "type", "date", "regex", "")
        );
        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
        Assert.assertNotNull(result);
        JsonNode field = result.get("schema").get(0);
        Assert.assertTrue(field.get("validators").isEmpty());
    }

    @Test
    public void migrateKBIFieldDetails_withNullInput_thenPass() throws KBIFormException {
        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(null);
        Assert.assertNull(result);
    }

    @Test
    public void migrateKBIFieldDetails_withEmptyMap_thenFail() {
        List<Map<String, String>> kbiFieldDetails = List.of(
                new HashMap<>()
        );
        try {
            kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
            Assert.fail();
        }catch (KBIFormException e){
            Assert.assertEquals(e.getErrorCode(), ErrorConstants.KBI_SCHEMA_PARSE_ERROR);
        }
    }

    @Test
    public void migrateKBIFieldDetails_withInvalidField_thenFail() {
        Map<String, String> invalidField = new HashMap<>();
        invalidField.put("type", "text");
        List<Map<String, String>> kbiFieldDetails = List.of(invalidField);
        try {
            kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
            Assert.fail();
        }catch (KBIFormException e){
            Assert.assertEquals(e.getErrorCode(),ErrorConstants.KBI_SCHEMA_PARSE_ERROR);
        }
    }

    @Test
    public void migrateKBIFieldDetails_withLanguageField_thenPass() throws KBIFormException {
        List<Map<String, String>> kbiFieldDetails = List.of(
                Map.of("id", "individualId", "type", "text", "regex", "^\\d{12}$")
        );

        JsonNode result = kbiFormHelperService.migrateKBIFieldDetails(kbiFieldDetails);
        Assert.assertNotNull(result);
        Assert.assertTrue(result.has("language"));

        JsonNode languageNode = result.get("language");
        Assert.assertTrue(languageNode.has("mandatory"));
        Assert.assertTrue(languageNode.has("optional"));
        Assert.assertTrue(languageNode.has("langCodeMap"));

        Assert.assertTrue(languageNode.get("mandatory").isArray());
        Assert.assertEquals("eng", languageNode.get("mandatory").get(0).asText());

        Assert.assertTrue(languageNode.get("optional").isArray());
        Assert.assertEquals("khm", languageNode.get("optional").get(0).asText());

        JsonNode langCodeMap = languageNode.get("langCodeMap");
        Assert.assertEquals("en", langCodeMap.get("eng").asText());
        Assert.assertEquals("hi", langCodeMap.get("hin").asText());
        Assert.assertEquals("ar", langCodeMap.get("ara").asText());
        Assert.assertEquals("kn", langCodeMap.get("kan").asText());
        Assert.assertEquals("ta", langCodeMap.get("tam").asText());
        Assert.assertEquals("km", langCodeMap.get("khm").asText());
    }

}
