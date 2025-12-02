package io.mosip.esignet.api.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.exception.KBIFormException;
import io.mosip.esignet.api.util.ErrorConstants;
import io.mosip.esignet.api.util.KBIFormHelperService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.Mock;
import org.springframework.core.env.Environment;
import org.springframework.core.io.ResourceLoader;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.validation.ConstraintValidatorContext;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
public class ValidatorTest {

    @InjectMocks
    private AuthChallengeFactorFormatValidator authChallengeFactorFormatValidator;

    @InjectMocks
    private PurposeValidator purposeValidator;

    @Mock
    private Environment environment;

    @Mock
    private ConstraintValidatorContext constraintValidatorContext;

    @Mock
    private ResourceLoader resourceLoader;

    @BeforeEach
    public void setUp() {
        ReflectionTestUtils.setField(purposeValidator, "minLength", 3);
        ReflectionTestUtils.setField(purposeValidator, "maxLength", 300);
    }

    @Test
    public void testIsValid_WithValidPurpose_thenPass() {
        String purpose = "Purpose";
        boolean isValid = purposeValidator.isValid(purpose, constraintValidatorContext);
        assertTrue(isValid);
    }

    @Test
    public void testIsValid_WithPurposeWithInvalidLength_thenFail() {
        String purpose = "In";
        boolean isValid = purposeValidator.isValid(purpose, constraintValidatorContext);
        assertFalse(isValid);
    }

    @Test
    public void testIsValid_WithNullPurpose_theFail() {
        String purpose = null;
        boolean isValid = purposeValidator.isValid(purpose, constraintValidatorContext);
        assertTrue(isValid);
    }

    @Test
    public void testIsValid_WithEmptyPurpose_thenFail() {
        String purpose = "";
        boolean isValid = purposeValidator.isValid(purpose, constraintValidatorContext);
        assertFalse(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_validAuthFactorType_thenPass() {
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("111111");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.OTP.format", String.class)).thenReturn("alpha-numeric");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.OTP.min-length", Integer.TYPE, 50)).thenReturn(6);
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.OTP.max-length", Integer.TYPE, 50)).thenReturn(6);
        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        assertTrue(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_invalidAuthFactorType_thenFail() {
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType(null);
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("111111");
        Mockito.when(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(mock(ConstraintValidatorContext.ConstraintViolationBuilder.class));
        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        Mockito.verify(constraintValidatorContext).buildConstraintViolationWithTemplate(ErrorConstants.INVALID_AUTH_FACTOR_TYPE);
        assertFalse(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_lowerCaseAuthFactorType_thenFail() {
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("otp");
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("111111");
        Mockito.when(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(mock(ConstraintValidatorContext.ConstraintViolationBuilder.class));
        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        Mockito.verify(constraintValidatorContext).buildConstraintViolationWithTemplate(ErrorConstants.INVALID_AUTH_FACTOR_TYPE);
        assertFalse(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_invalidChallengeLength_theFail() {
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat("alpha-numeric");
        authChallenge.setChallenge("1111111");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.OTP.format", String.class)).thenReturn("alpha-numeric");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.OTP.min-length", Integer.TYPE, 50)).thenReturn(6);
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.OTP.max-length", Integer.TYPE, 50)).thenReturn(6);
        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        assertFalse(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_invalidFactoFormat_thenFail() {
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("OTP");
        authChallenge.setFormat("jwt");
        authChallenge.setChallenge("1111");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.OTP.format", String.class)).thenReturn("alpha-numeric");
        Mockito.when(constraintValidatorContext.buildConstraintViolationWithTemplate(anyString()))
                .thenReturn(mock(ConstraintValidatorContext.ConstraintViolationBuilder.class));
        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        assertFalse(isValid);

    }

    @Test
    public void authChallengeFactorFormatValidator_withKBIAuthFactor_thenPass() throws KBIFormException {
        ObjectMapper objectMapper = new ObjectMapper();
        ResourceLoader resourceLoader = Mockito.mock(ResourceLoader.class);

        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "idField", "individualId");

        List<Map<String, String>> fieldDetailList = List.of(
                Map.of("id", "individualId", "type", "text", "format", "string", "regex", "^\\d+$"),
                Map.of("id", "fullName", "type", "text", "format", "", "regex", "^[\\p{L} .'-]+$"),
                Map.of("id", "dateOfBirth", "type", "date", "format", "yyyy-MM-dd", "regex", "^(\\d{4})-(\\d{2})-(\\d{2})$")
        );

        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "fieldDetailList", fieldDetailList);


        KBIFormHelperService helperService = new KBIFormHelperService();
        ReflectionTestUtils.setField(helperService, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(helperService, "resourceLoader", resourceLoader);

        JsonNode schema = helperService.migrateKBIFieldDetails(fieldDetailList);

        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "kbiFormHelperService", helperService);
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "fieldJson", schema);

        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.format", String.class)).thenReturn("base64url-encoded-json");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.min-length", Integer.TYPE, 50)).thenReturn(50);
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.max-length", Integer.TYPE, 50)).thenReturn(200);
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");
        authChallenge.setChallenge("eyJmdWxsTmFtZSI6IkthaWYgU2lkZGlxdWUiLCJkYXRlT2ZCaXJ0aCI6IjIwMDAtMDctMjYifQ");

        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);

        assertTrue(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_withInvalidKBIChallenge_thenFail() {
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "idField", "individualId");
        List<Map<String, String>> fieldDetailList = List.of(Map.of("id", "individualId", "type", "text", "format", "string", "regex", "^\\d+$")
        , Map.of("id", "fullName", "type", "", "format", "", "regex", "^[\\p{L} .'-]+$")
        , Map.of("id", "dateOfBirth", "type", "date", "format", "yyyy-MM-dd", "regex", "^(\\\\d{4})-(\\\\d{2})-(\\\\d{2})$"));
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "fieldDetailList", fieldDetailList);
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");
        authChallenge.setChallenge("eyJmdWxsTmFtZSI6IkthaWYgU2lkZGlxdWUiLhfweibjhBDKJBNS");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.format", String.class)).thenReturn("base64url-encoded-json");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.min-length", Integer.TYPE, 50)).thenReturn(50);
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.max-length", Integer.TYPE, 50)).thenReturn(200);
        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        assertFalse(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_withKBIInvalidRegex_thenFail() {
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "idField", "individualId");
        List<Map<String, String>> fieldDetailList = List.of(Map.of("id", "individualId", "type", "text", "format", "string", "regex", "^\\d+$")
        , Map.of("id", "fullName", "type", "text", "format", "", "regex", "^\\d+$")
        , Map.of("id", "dob", "type", "text", "format", "yyyy-MM-dd", "regex", "^\\d+$"));
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "fieldDetailList", fieldDetailList);
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");
        authChallenge.setChallenge("eyJmdWxsTmFtZSI6IkthaWYgU2lkZGlxdWUiLCJkb2IiOiIyMDAwLTA3LTI2In0=");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.format", String.class)).thenReturn("base64url-encoded-json");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.min-length", Integer.TYPE, 50)).thenReturn(50);
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.max-length", Integer.TYPE, 50)).thenReturn(200);
        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        assertFalse(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_withEmptyName_thenFail() {
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "idField", "individualId");
        List<Map<String, String>> fieldDetailList = List.of(Map.of("id", "individualId", "type", "text", "format", "string", "regex", "^\\d+$")
        , Map.of("id", "fullName", "type", "text", "format", "", "regex", "^[\\p{L} .'-]+$")
        , Map.of("id", "dob", "type", "text", "format", "yyyy-MM-dd", "regex", "^(\\\\d{4})-(\\\\d{2})-(\\\\d{2})$"));
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "fieldDetailList", fieldDetailList);
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");
        authChallenge.setChallenge("eyJmdWxsTmFtZSI6IiAiLCJkb2IiOiIyMDAwLTA3LTI2In0=");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.format", String.class)).thenReturn("base64url-encoded-json");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.min-length", Integer.TYPE, 50)).thenReturn(30);
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.max-length", Integer.TYPE, 50)).thenReturn(200);
        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        assertFalse(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_withMaximumLength_thenFail1() {
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "idField", "individualId");
        List<Map<String, String>> fieldDetailList = List.of(Map.of("id", "individualId", "type", "text", "format", "string", "regex", "^\\d+$")
        , Map.of("id", "fullName", "type", "text", "format", "", "regex", "^[\\p{L} .'-]+$", "maxLength", "10")
        , Map.of("id", "dob", "type", "text", "format", "yyyy-MM-dd", "regex", "^(\\\\d{4})-(\\\\d{2})-(\\\\d{2})$"));
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "fieldDetailList", fieldDetailList);
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");
        authChallenge.setChallenge("eyJmdWxsTmFtZSI6ImFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6IiwiZG9iIjoiMjAwMC0wNy0yNiJ9cb");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.format", String.class)).thenReturn("base64url-encoded-json");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.min-length", Integer.TYPE, 50)).thenReturn(30);
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.max-length", Integer.TYPE, 50)).thenReturn(200);
        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        assertFalse(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_withMissingRequiredField_thenFail() {
        ObjectMapper objectMapper = new ObjectMapper();
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "idField", "individualId");

        List<Map<String, String>> fieldDetailList = List.of(
                Map.of("id", "individualId", "type", "text", "format", "string", "regex", "^\\d+$", "required", "true"),
                Map.of("id", "fullName", "type", "text", "format", "", "regex", "^[\\p{L} .'-]+$", "required", "true"),
                Map.of("id", "dob", "type", "text", "format", "yyyy-MM-dd", "regex", "^(\\d{4})-(\\d{2})-(\\d{2})$", "required", "true")
        );

        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "fieldDetailList", fieldDetailList);

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");
        String challengeJson = "{\"dob\":\"2000-07-26\"}";
        String encodedChallenge = Base64.getUrlEncoder().encodeToString(challengeJson.getBytes(StandardCharsets.UTF_8));
        authChallenge.setChallenge(encodedChallenge);

        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.format", String.class)).thenReturn("base64url-encoded-json");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.min-length", Integer.TYPE, 50)).thenReturn(30);
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.max-length", Integer.TYPE, 50)).thenReturn(200);

        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        assertFalse(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_withOptionalFieldMissing_thenPass() throws Exception {
        ObjectMapper objectMapper = new ObjectMapper();
        KBIFormHelperService helperService = Mockito.mock(KBIFormHelperService.class);
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "idField", "individualId");
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "kbiFormHelperService", helperService);
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "kbiFormDetailsUrl", "mock-url");

        String schemaJson = """
                {
                  "schema": [
                    {
                      "id": "individualId",
                      "controlType": "textbox",
                      "required": true
                    },
                    {
                      "id": "fullName",
                      "controlType": "textbox",
                      "required": false
                    }
                  ]
                }\
                """;

        JsonNode mockedSchema = objectMapper.readTree(schemaJson);
        Mockito.when(helperService.fetchKBIFieldDetailsFromResource("mock-url")).thenReturn(mockedSchema);

        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.format", String.class)).thenReturn("base64url-encoded-json");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.min-length", Integer.TYPE, 50)).thenReturn(50);
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.max-length", Integer.TYPE, 50)).thenReturn(200);
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");
        authChallenge.setChallenge("eyJpbmRpdmlkdWFsSWQiOiIrOTExMjMxMjMxMjMiLCJmdWxsTmFtZSI6InNhaSJ9");

        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);

        assertTrue(isValid);
    }

    @Test
    public void authChallengeFactorFormatValidator_withMultilingualFullName_thenPass() throws KBIFormException {
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "idField", "individualId");

        List<Map<String, String>> fieldDetailList = List.of(
                Map.of("id", "individualId", "type", "text", "format", "string", "regex", "^\\+\\d+$"),
                Map.of("id", "phone", "type", "text", "format", "string", "regex", "^\\+\\d+$"),
                Map.of("id", "fullName", "type", "text", "format", "", "regex", "^[\\u1780-\\u17FF\\p{L} .'-]+$"),
                Map.of("id", "dob", "type", "date", "format", "yyyy-MM-dd", "regex", "^(\\d{4})-(\\d{2})-(\\d{2})$")
        );

        KBIFormHelperService helperService = new KBIFormHelperService();
        ReflectionTestUtils.setField(helperService, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(helperService, "resourceLoader", resourceLoader);
        JsonNode schema = helperService.migrateKBIFieldDetails(fieldDetailList);

        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "kbiFormHelperService", helperService);
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "fieldJson", schema);

        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "fieldDetailList", fieldDetailList);
        ReflectionTestUtils.setField(authChallengeFactorFormatValidator, "environment", environment);

        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.format", String.class))
                .thenReturn("base64url-encoded-json");
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.min-length", Integer.TYPE, 50))
                .thenReturn(50);
        Mockito.when(environment.getProperty("mosip.esignet.auth-challenge.KBI.max-length", Integer.TYPE, 50))
                .thenReturn(500);

        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setAuthFactorType("KBI");
        authChallenge.setFormat("base64url-encoded-json");
        authChallenge.setChallenge("eyJpbmRpdmlkdWFsSWQiOiIrOTE4Nzk4ODc5ODg3IiwicGhvbmUiOiIrOTE4Nzk4ODc5ODg3IiwiZnVsbE5hbWUiOlt7Imxhbmd1YWdlIjoiZW5nIiwidmFsdWUiOiLhnoXhnpPhnorhnrwifSx7Imxhbmd1YWdlIjoidGFtIiwidmFsdWUiOiLhnoXhnpPhnorhnrwifSx7Imxhbmd1YWdlIjoiaGluIiwidmFsdWUiOiIifSx7Imxhbmd1YWdlIjoia2FuIiwidmFsdWUiOiIifSx7Imxhbmd1YWdlIjoia2htIiwidmFsdWUiOiIifSx7Imxhbmd1YWdlIjoiYXJhIiwidmFsdWUiOiIifV0sImRvYiI6IjIwMjUtMTAtMDEifQ==");

        boolean isValid = authChallengeFactorFormatValidator.isValid(authChallenge, constraintValidatorContext);
        assertTrue(isValid);
    }

}
