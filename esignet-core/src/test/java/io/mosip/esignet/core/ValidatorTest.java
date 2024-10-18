/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchemaFactory;
import io.mosip.esignet.api.dto.claim.ClaimDetail;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.core.dto.OAuthDetailRequestV2;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.validator.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.eq;

@RunWith(MockitoJUnitRunner.class)
public class ValidatorTest {

	@InjectMocks
	ClaimSchemaValidator claimSchemaValidator;

	@Mock
	AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

	@Mock
	Authenticator authenticator;

	@Mock
	Environment environment;


	@Mock
	RestTemplate restTemplate;

	@Mock
	ResourceLoader resourceLoader;

	@Mock
	private JsonSchemaFactory jsonSchemaFactory;

	private final String claimSchema="{\"$id\":\"https://bitbucket.org/openid/ekyc-ida/raw/master/schema/verified_claims_request.json\",\"$schema\":\"https://json-schema.org/draft/2020-12/schema\",\"$defs\":{\"check_details\":{\"type\":\"array\",\"prefixItems\":[{\"check_id\":{\"type\":\"string\"},\"check_method\":{\"type\":\"string\"},\"organization\":{\"type\":\"string\"},\"time\":{\"$ref\":\"#/$defs/datetime_element\"}}]},\"claims_element\":{\"oneOf\":[{\"type\":\"null\"},{\"type\":\"object\",\"additionalProperties\":{\"anyOf\":[{\"type\":\"null\"},{\"type\":\"object\",\"properties\":{\"essential\":{\"type\":\"boolean\"},\"purpose\":{\"type\":\"string\",\"maxLength\":300,\"minLength\":3},\"value\":{\"type\":\"string\",\"minLength\":3},\"values\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"minItems\":1}}}]},\"minProperties\":1}]},\"constrainable_element\":{\"oneOf\":[{\"type\":\"null\"},{\"type\":\"object\",\"properties\":{\"essential\":{\"type\":\"boolean\"},\"purpose\":{\"type\":\"string\",\"maxLength\":300,\"minLength\":3},\"value\":{\"type\":\"string\"},\"values\":{\"type\":\"array\",\"items\":{\"type\":\"string\"},\"minItems\":1}}}]},\"datetime_element\":{\"oneOf\":[{\"type\":\"null\"},{\"type\":\"object\",\"properties\":{\"essential\":{\"type\":\"boolean\"},\"max_age\":{\"type\":\"integer\",\"minimum\":0},\"purpose\":{\"type\":\"string\",\"maxLength\":300,\"minLength\":3}}}]},\"document_details\":{\"type\":\"object\",\"properties\":{\"type\":{\"$ref\":\"#/$defs/constrainable_element\"},\"date_of_expiry\":{\"$ref\":\"#/$defs/datetime_element\"},\"date_of_issuance\":{\"$ref\":\"#/$defs/datetime_element\"},\"document_number\":{\"$ref\":\"#/$defs/simple_element\"},\"issuer\":{\"type\":\"object\",\"properties\":{\"country\":{\"$ref\":\"#/$defs/simple_element\"},\"country_code\":{\"$ref\":\"#/$defs/simple_element\"},\"formatted\":{\"$ref\":\"#/$defs/simple_element\"},\"jurisdiction\":{\"$ref\":\"#/$defs/simple_element\"},\"locality\":{\"$ref\":\"#/$defs/simple_element\"},\"name\":{\"$ref\":\"#/$defs/simple_element\"},\"postal_code\":{\"$ref\":\"#/$defs/simple_element\"},\"region\":{\"$ref\":\"#/$defs/simple_element\"},\"street_address\":{\"$ref\":\"#/$defs/simple_element\"}}},\"personal_number\":{\"$ref\":\"#/$defs/simple_element\"},\"serial_number\":{\"$ref\":\"#/$defs/simple_element\"}}},\"evidence\":{\"type\":\"object\",\"required\":[\"type\"],\"properties\":{\"type\":{\"type\":\"object\",\"properties\":{\"value\":{\"enum\":[\"document\",\"electronic_record\",\"vouch\",\"electronic_signature\"]}}},\"attachments\":{\"$ref\":\"#/$defs/simple_element\"}},\"allOf\":[{\"if\":{\"properties\":{\"type\":{\"value\":\"electronic_signature\"}}},\"then\":{\"properties\":{\"created_at\":{\"$ref\":\"#/$defs/datetime_element\"},\"issuer\":{\"$ref\":\"#/$defs/simple_element\"},\"serial_number\":{\"$ref\":\"#/$defs/simple_element\"},\"signature_type\":{\"$ref\":\"#/$defs/simple_element\"}}},\"else\":true},{\"if\":{\"properties\":{\"type\":{\"value\":\"document\"}}},\"then\":{\"properties\":{\"check_details\":{\"$ref\":\"#/$defs/check_details\"},\"document_details\":{\"$ref\":\"#/$defs/document_details\"},\"method\":{\"$ref\":\"#/$defs/constrainable_element\"},\"time\":{\"$ref\":\"#/$defs/datetime_element\"}}},\"else\":true},{\"if\":{\"properties\":{\"type\":{\"value\":\"electronic_record\"}}},\"then\":{\"properties\":{\"check_details\":{\"$ref\":\"#/$defs/check_details\"},\"record\":{\"type\":\"object\",\"properties\":{\"type\":{\"$ref\":\"#/$defs/constrainable_element\"},\"created_at\":{\"$ref\":\"#/$defs/datetime_element\"},\"date_of_expiry\":{\"$ref\":\"#/$defs/datetime_element\"},\"derived_claims\":{\"$ref\":\"#/$defs/claims_element\"},\"source\":{\"type\":\"object\",\"properties\":{\"country\":{\"$ref\":\"#/$defs/simple_element\"},\"country_code\":{\"$ref\":\"#/$defs/simple_element\"},\"formatted\":{\"$ref\":\"#/$defs/simple_element\"},\"locality\":{\"$ref\":\"#/$defs/simple_element\"},\"name\":{\"$ref\":\"#/$defs/simple_element\"},\"postal_code\":{\"$ref\":\"#/$defs/simple_element\"},\"region\":{\"$ref\":\"#/$defs/simple_element\"},\"street_address\":{\"$ref\":\"#/$defs/simple_element\"}}}}},\"time\":{\"$ref\":\"#/$defs/datetime_element\"}}},\"else\":true},{\"if\":{\"properties\":{\"type\":{\"value\":\"vouch\"}}},\"then\":{\"properties\":{\"attestation\":{\"type\":\"object\",\"properties\":{\"type\":{\"$ref\":\"#/$defs/constrainable_element\"},\"date_of_expiry\":{\"$ref\":\"#/$defs/datetime_element\"},\"date_of_issuance\":{\"$ref\":\"#/$defs/datetime_element\"},\"derived_claims\":{\"$ref\":\"#/$defs/claims_element\"},\"reference_number\":{\"$ref\":\"#/$defs/simple_element\"},\"voucher\":{\"type\":\"object\",\"properties\":{\"birthdate\":{\"$ref\":\"#/$defs/datetime_element\"},\"country\":{\"$ref\":\"#/$defs/simple_element\"},\"formatted\":{\"$ref\":\"#/$defs/simple_element\"},\"locality\":{\"$ref\":\"#/$defs/simple_element\"},\"name\":{\"$ref\":\"#/$defs/simple_element\"},\"occupation\":{\"$ref\":\"#/$defs/simple_element\"},\"organization\":{\"$ref\":\"#/$defs/simple_element\"},\"postal_code\":{\"$ref\":\"#/$defs/simple_element\"},\"region\":{\"$ref\":\"#/$defs/simple_element\"},\"street_address\":{\"$ref\":\"#/$defs/simple_element\"}}}}},\"check_details\":{\"$ref\":\"#/$defs/check_details\"},\"time\":{\"$ref\":\"#/$defs/datetime_element\"}}},\"else\":true}]},\"simple_element\":{\"oneOf\":[{\"type\":\"null\"},{\"type\":\"object\",\"properties\":{\"essential\":{\"type\":\"boolean\"},\"purpose\":{\"type\":\"string\",\"maxLength\":300,\"minLength\":3}}}]},\"verified_claims\":{\"oneOf\":[{\"type\":\"array\",\"items\":{\"anyOf\":[{\"$ref\":\"#/$defs/verified_claims_def\"}]}},{\"$ref\":\"#/$defs/verified_claims_def\"}]},\"verified_claims_def\":{\"type\":\"object\",\"required\":[\"verification\",\"claims\"],\"additionalProperties\":false,\"properties\":{\"claims\":{\"$ref\":\"#/$defs/claims_element\"},\"verification\":{\"type\":\"object\",\"required\":[\"trust_framework\"],\"additionalProperties\":true,\"properties\":{\"assurance_level\":{\"$ref\":\"#/$defs/constrainable_element\"},\"assurance_process\":{\"type\":\"object\",\"properties\":{\"assurance_details\":{\"type\":\"array\",\"items\":{\"oneOf\":[{\"assurance_classification\":{\"$ref\":\"#/$defs/constrainable_element\"},\"assurance_type\":{\"$ref\":\"#/$defs/constrainable_element\"},\"evidence_ref\":{\"type\":\"object\",\"required\":[\"txn\"],\"additionalProperties\":true,\"properties\":{\"evidence_classification\":{\"$ref\":\"#/$defs/constrainable_element\"},\"evidence_metadata\":{\"$ref\":\"#/$defs/constrainable_element\"},\"txn\":{\"$ref\":\"#/$defs/constrainable_element\"}}}}]},\"minItems\":1},\"policy\":{\"$ref\":\"#/$defs/constrainable_element\"},\"procedure\":{\"$ref\":\"#/$defs/constrainable_element\"}}},\"evidence\":{\"type\":\"array\",\"items\":{\"oneOf\":[{\"$ref\":\"#/$defs/evidence\"}]},\"minItems\":1},\"time\":{\"$ref\":\"#/$defs/datetime_element\"},\"trust_framework\":{\"$ref\":\"#/$defs/constrainable_element\"},\"verification_process\":{\"$ref\":\"#/$defs/simple_element\"}}}}}},\"properties\":{\"id_token\":{\"type\":\"object\",\"additionalProperties\":true,\"properties\":{\"verified_claims\":{\"$ref\":\"#/$defs/verified_claims\"}}},\"userinfo\":{\"type\":\"object\",\"additionalProperties\":true,\"properties\":{\"verified_claims\":{\"$ref\":\"#/$defs/verified_claims\"}}}}}";


	private Map<String, Object> discoveryMap = new HashMap<>();

	@Before
	public void setup() throws EsignetException {
		Set<String> mockACRs = new HashSet<>();
		mockACRs.add("level1");
		mockACRs.add("level2");
		mockACRs.add("level3");
		mockACRs.add("level4");
		discoveryMap.put("claims_supported", Arrays.asList("name", "gender", "address"));
		when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(mockACRs);
		when(authenticator.isSupportedOtpChannel("email")).thenReturn(true);
	}

	// ============================ Display Validator =========================

	@Test
	public void test_displayValidator_valid_thenPass() {
		OIDCDisplayValidator validator = new OIDCDisplayValidator();
		ReflectionTestUtils.setField(validator, "supportedDisplays", Arrays.asList("page", "wap"));
		Assert.assertTrue(validator.isValid("wap", null));
	}

	@Test
	public void test_displayValidator_invalid_thenFail() {
		OIDCDisplayValidator validator = new OIDCDisplayValidator();
		ReflectionTestUtils.setField(validator, "supportedDisplays", Arrays.asList("page", "wap"));
		Assert.assertFalse(validator.isValid("wap2", null));
	}

	@Test
	public void test_displayValidator_invalidWithSpace_thenFail() {
		OIDCDisplayValidator validator = new OIDCDisplayValidator();
		ReflectionTestUtils.setField(validator, "supportedDisplays", Arrays.asList("page", "wap"));
		Assert.assertFalse(validator.isValid("page wap", null));
	}

	@Test
	public void test_displayValidator_nullValue_thenPass() {
		OIDCDisplayValidator validator = new OIDCDisplayValidator();
		ReflectionTestUtils.setField(validator, "supportedDisplays", Arrays.asList("page", "wap"));
		Assert.assertTrue(validator.isValid(null, null));
	}

	@Test
	public void test_displayValidator_EmptyValue_thenFail() {
		OIDCDisplayValidator validator = new OIDCDisplayValidator();
		ReflectionTestUtils.setField(validator, "supportedDisplays", Arrays.asList("page", "wap"));
		Assert.assertFalse(validator.isValid("", null));
	}

	// ============================ GranType Validator =========================

	@Test
	public void test_grantTypeValidator_valid_thenPass() {
		OIDCGrantTypeValidator validator = new OIDCGrantTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedGrantTypes", Arrays.asList("authorization_code"));
		Assert.assertTrue(validator.isValid("authorization_code", null));
	}

	@Test
	public void test_grantTypeValidator_invalid_thenFail() {
		OIDCGrantTypeValidator validator = new OIDCGrantTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedGrantTypes", Arrays.asList("authorization_code"));
		Assert.assertFalse(validator.isValid("code", null));
	}

	@Test
	public void test_grantTypeValidator_invalidWithSpace_thenFail() {
		OIDCGrantTypeValidator validator = new OIDCGrantTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedGrantTypes", Arrays.asList("authorization_code"));
		Assert.assertFalse(validator.isValid(" authorization_code ", null));
	}

	@Test
	public void test_grantTypeValidator_nullValue_thenFail() {
		OIDCGrantTypeValidator validator = new OIDCGrantTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedGrantTypes", Arrays.asList("authorization_code"));
		Assert.assertFalse(validator.isValid(null, null));
	}

	@Test
	public void test_grantTypeValidator_EmptyValue_thenFail() {
		OIDCGrantTypeValidator validator = new OIDCGrantTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedGrantTypes", Arrays.asList("authorization_code"));
		Assert.assertFalse(validator.isValid("", null));
	}

	// ============================ Prompt Validator =========================

	@Test
	public void test_PromptValidator_valid_thenPass() {
		OIDCPromptValidator validator = new OIDCPromptValidator();
		ReflectionTestUtils.setField(validator, "supportedPrompts", Arrays.asList("none", "login", "consent"));
		Assert.assertTrue(validator.isValid("consent", null));
	}

	@Test
	public void test_PromptValidator_invalid_thenFail() {
		OIDCPromptValidator validator = new OIDCPromptValidator();
		ReflectionTestUtils.setField(validator, "supportedPrompts", Arrays.asList("none", "login", "consent"));
		Assert.assertFalse(validator.isValid("pop-up", null));
	}

	@Test
	public void test_PromptValidator_invalidWithSpace_thenFail() {
		OIDCPromptValidator validator = new OIDCPromptValidator();
		ReflectionTestUtils.setField(validator, "supportedPrompts", Arrays.asList("none", "login", "consent"));
		Assert.assertFalse(validator.isValid(" login ", null));
	}

	@Test
	public void test_PromptValidator_nullValue_thenPass() {
		OIDCPromptValidator validator = new OIDCPromptValidator();
		ReflectionTestUtils.setField(validator, "supportedPrompts", Arrays.asList("none", "login", "consent"));
		Assert.assertTrue(validator.isValid(null, null));
	}

	@Test
	public void test_PromptValidator_EmptyValue_thenFail() {
		OIDCPromptValidator validator = new OIDCPromptValidator();
		ReflectionTestUtils.setField(validator, "supportedPrompts", Arrays.asList("none", "login", "consent"));
		Assert.assertFalse(validator.isValid("", null));
	}

	// ============================ ResponseType Validator =========================

	@Test
	public void test_ResponseTypeValidator_valid_thenPass() {
		OIDCResponseTypeValidator validator = new OIDCResponseTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedResponseTypes", Arrays.asList("code"));
		Assert.assertTrue(validator.isValid("code", null));
	}

	@Test
	public void test_ResponseTypeValidator_invalid_thenFail() {
		OIDCResponseTypeValidator validator = new OIDCResponseTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedResponseTypes", Arrays.asList("code"));
		Assert.assertFalse(validator.isValid("code----", null));
	}

	@Test
	public void test_ResponseTypeValidator_invalidWithSpace_thenFail() {
		OIDCResponseTypeValidator validator = new OIDCResponseTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedResponseTypes", Arrays.asList("code"));
		Assert.assertFalse(validator.isValid(" code ", null));
	}

	@Test
	public void test_ResponseTypeValidator_nullValue_thenFail() {
		OIDCResponseTypeValidator validator = new OIDCResponseTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedResponseTypes", Arrays.asList("code"));
		Assert.assertFalse(validator.isValid(null, null));
	}

	@Test
	public void test_ResponseTypeValidator_EmptyValue_thenFail() {
		OIDCResponseTypeValidator validator = new OIDCResponseTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedResponseTypes", Arrays.asList("code"));
		Assert.assertFalse(validator.isValid("", null));
	}

	// ============================ Client Assertion type Validator
	// =========================

	@Test
	public void test_ClientAssertionTypeValidator_valid_thenPass() {
		OIDCClientAssertionTypeValidator validator = new OIDCClientAssertionTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedAssertionTypes",
				Arrays.asList("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
		Assert.assertTrue(validator.isValid("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", null));
	}

	@Test
	public void test_ClientAssertionTypeValidator_invalid_thenFail() {
		OIDCClientAssertionTypeValidator validator = new OIDCClientAssertionTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedAssertionTypes",
				Arrays.asList("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
		Assert.assertFalse(validator.isValid("jwt-bearer", null));
	}

	@Test
	public void test_ClientAssertionTypeValidator_invalidWithSpace_thenFail() {
		OIDCClientAssertionTypeValidator validator = new OIDCClientAssertionTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedAssertionTypes",
				Arrays.asList("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
		Assert.assertFalse(validator.isValid("urn:ietf:params:oauth:client-assertion-type:jwt-bearer ", null));
	}

	@Test
	public void test_ClientAssertionTypeValidator_nullValue_thenFail() {
		OIDCClientAssertionTypeValidator validator = new OIDCClientAssertionTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedAssertionTypes",
				Arrays.asList("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
		Assert.assertFalse(validator.isValid(null, null));
	}

	@Test
	public void test_ClientAssertionTypeValidator_EmptyValue_thenFail() {
		OIDCClientAssertionTypeValidator validator = new OIDCClientAssertionTypeValidator();
		ReflectionTestUtils.setField(validator, "supportedAssertionTypes",
				Arrays.asList("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
		Assert.assertFalse(validator.isValid("", null));
	}

	// ============================ Optional ACR Validator =========================

	@Test
	public void test_OptionalACRValidator_valid_thenPass() {
		AuthContextRefValidator validator = new AuthContextRefValidator();
		ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
		Assert.assertTrue(validator.isValid(null, null));
	}

	@Test
	public void test_OptionalACRValidator_EmptyValue_thenFail() {
		AuthContextRefValidator validator = new AuthContextRefValidator();
		ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
		Assert.assertFalse(validator.isValid("", null));
	}

	@Test
	public void test_OptionalACRValidator_SingleValue_thenPass() {
		AuthContextRefValidator validator = new AuthContextRefValidator();
		ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
		Assert.assertTrue(validator.isValid("level2", null));
	}

	@Test
	public void test_OptionalACRValidator_MultipleValue_thenPass() {
		AuthContextRefValidator validator = new AuthContextRefValidator();
		ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
		Assert.assertTrue(validator.isValid("level4 level2", null));
	}

	@Test
	public void test_OptionalACRValidator_InvalidMultipleValue_thenFail() {
		AuthContextRefValidator validator = new AuthContextRefValidator();
		ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
		Assert.assertFalse(validator.isValid("level5 level1", null));
	}

	@Test
	public void test_OptionalACRValidator_throwsException_thenFail() {
		AuthContextRefValidator validator = new AuthContextRefValidator();
		ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
		when(authenticationContextClassRefUtil.getSupportedACRValues()).thenThrow(EsignetException.class);
		Assert.assertFalse(validator.isValid("level5 level1", null));
	}

	// ============================ Request time Validator =========================

	@Test
	public void test_RequestTimeValidator_nullValue_thenFail() {
		RequestTimeValidator validator = new RequestTimeValidator();
		Assert.assertFalse(validator.isValid(null, null));
		Assert.assertFalse(validator.isValid("", null));
		Assert.assertFalse(validator.isValid("  ", null));
	}

	@Test
	public void test_RequestTimeValidator_validValue_thenPass() {
		RequestTimeValidator validator = new RequestTimeValidator();
		ReflectionTestUtils.setField(validator, "leewayInMinutes", 2);
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		Assert.assertTrue(validator
				.isValid(requestTime.format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN)), null));

		requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		requestTime = requestTime.plusMinutes(1);
		Assert.assertTrue(validator
				.isValid(requestTime.format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN)), null));

		requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		requestTime = requestTime.minusMinutes(1);
		Assert.assertTrue(validator
				.isValid(requestTime.format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN)), null));
	}

	@Test
	public void test_RequestTimeValidator_futureDateValue_thenFail() {
		RequestTimeValidator validator = new RequestTimeValidator();
		ReflectionTestUtils.setField(validator, "leewayInMinutes", 2);
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		requestTime = requestTime.plusMinutes(4);
		Assert.assertFalse(validator
				.isValid(requestTime.format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN)), null));
	}

	@Test
	public void test_RequestTimeValidator_oldDateValue_thenFail() {
		RequestTimeValidator validator = new RequestTimeValidator();
		ReflectionTestUtils.setField(validator, "leewayInMinutes", 2);
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		requestTime = requestTime.minusMinutes(5);
		Assert.assertFalse(validator
				.isValid(requestTime.format(DateTimeFormatter.ofPattern(Constants.UTC_DATETIME_PATTERN)), null));
	}

	@Test
	public void test_RequestTimeValidator_invalidFormat_thenFail() {
		RequestTimeValidator validator = new RequestTimeValidator();
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
		Assert.assertFalse(validator.isValid(requestTime.format(DateTimeFormatter.ofPattern(DATETIME_PATTERN)), null));
	}

	// ============================ Otp channel Validator =========================

	@Test
	public void test_OtpChannelValidator_valid_thenPass() {
		OtpChannelValidator validator = new OtpChannelValidator();
		ReflectionTestUtils.setField(validator, "authenticationWrapper", authenticator);
		Assert.assertTrue(validator.isValid("email", null));
	}

	@Test
	public void test_OtpChannelValidator_null_thenFail() {
		OtpChannelValidator validator = new OtpChannelValidator();
		ReflectionTestUtils.setField(validator, "authenticationWrapper", authenticator);
		Assert.assertFalse(validator.isValid(null, null));
	}

	@Test
	public void test_OtpChannelValidator_invalid_thenFail() {
		OtpChannelValidator validator = new OtpChannelValidator();
		ReflectionTestUtils.setField(validator, "authenticationWrapper", authenticator);
		Assert.assertFalse(validator.isValid("mobile", null));
	}

	@Test
	public void test_OtpChannelValidator_blank_thenFail() {
		OtpChannelValidator validator = new OtpChannelValidator();
		ReflectionTestUtils.setField(validator, "authenticationWrapper", authenticator);
		Assert.assertFalse(validator.isValid("   ", null));
	}

	@Test
	public void test_OtpChannelValidator_spaceAppended_thenFail() {
		OtpChannelValidator validator = new OtpChannelValidator();
		ReflectionTestUtils.setField(validator, "authenticationWrapper", authenticator);
		Assert.assertFalse(validator.isValid("   email ", null));
	}

	// ============================ Format Validator =========================

	@Test
	public void test_FormatValidator_nullValue_thenFail() {
		IdFormatValidator validator = new IdFormatValidator();
		Assert.assertFalse(validator.isValid(null, null));
		Assert.assertFalse(validator.isValid("", null));
		Assert.assertFalse(validator.isValid("  ", null));
	}

	@Test
	public void test_FormatValidator_validValue_thenPass() {
		IdFormatValidator validator = new IdFormatValidator();
		Assert.assertTrue(validator.isValid("id-#4_$%", null));
	}

	@Test
	public void test_FormatValidator_withValidValue_thenPass() {
		IdFormatValidator validator = new IdFormatValidator();
		ReflectionTestUtils.setField(validator, "supportedRegex", "\\S*");
		Assert.assertTrue(validator.isValid("id-#4_$%", null));
	}

	@Test
	public void test_FormatValidator_withInvalidValue_thenFail() {
		IdFormatValidator validator = new IdFormatValidator();
		Assert.assertFalse(validator.isValid("  id#4$%", null));
		Assert.assertFalse(validator.isValid("id#4$% ", null));
		Assert.assertFalse(validator.isValid("id #4$%", null));
		Assert.assertFalse(validator.isValid("id #4$    %", null));
	}

	// ============================ OIDC Claim Validator =========================

	@Test
	public void test_OIDCClaimValidator_withValidClaim_thenPass() {
		OIDCClaimValidator validator = new OIDCClaimValidator();
		ReflectionTestUtils.setField(validator, "discoveryMap", discoveryMap);
		Assert.assertTrue(validator.isValid("name", null));
	}

	@Test
	public void test_OIDCClaimValidator_withInvalidClaim_thenFail() {
		OIDCClaimValidator validator = new OIDCClaimValidator();
		ReflectionTestUtils.setField(validator, "discoveryMap", discoveryMap);
		Assert.assertFalse(validator.isValid("email", null));
	}

	@Test
	public void test_OIDCClaimValidator_emptyValue_thenFail() {
		OIDCClaimValidator validator = new OIDCClaimValidator();
		ReflectionTestUtils.setField(validator, "discoveryMap", discoveryMap);
		Assert.assertFalse(validator.isValid("", null));
	}

	@Test
	public void test_OIDCClaimValidator_nullValue_thenFail() {
		OIDCClaimValidator validator = new OIDCClaimValidator();
		ReflectionTestUtils.setField(validator, "discoveryMap", discoveryMap);
		Assert.assertFalse(validator.isValid(null, null));
	}

	// ============================ OIDC Client Auth Validator =========================

	@Test
	public void test_OIDCClientAuthValidator_withValidAuth_thenPass() {
		OIDCClientAuthValidator validator = new OIDCClientAuthValidator();
		ReflectionTestUtils.setField(validator, "supportedClientAuthMethods", Arrays.asList("pwd"));
		Assert.assertTrue(validator.isValid("pwd", null));
	}
	
	@Test
	public void test_OIDCClientAuthValidator_withInvalidAuth_thenFail() {
		OIDCClientAuthValidator validator = new OIDCClientAuthValidator();
		ReflectionTestUtils.setField(validator, "supportedClientAuthMethods", Arrays.asList("pwd"));
		Assert.assertFalse(validator.isValid("OTP", null));
	}
	
	@Test
	public void test_OIDCClientAuthValidator_withEmptyAuth_thenFail() {
		OIDCClientAuthValidator validator = new OIDCClientAuthValidator();
		ReflectionTestUtils.setField(validator, "supportedClientAuthMethods", Arrays.asList("pwd"));
		Assert.assertFalse(validator.isValid("", null));
	}
	
	@Test
	public void test_OIDCClientAuthValidator_withNullAuth_thenFail() {
		OIDCClientAuthValidator validator = new OIDCClientAuthValidator();
		ReflectionTestUtils.setField(validator, "supportedClientAuthMethods", Arrays.asList("pwd"));
		Assert.assertFalse(validator.isValid(null, null));
	}
	
	// ============================ OIDC Scope Validator =========================
	
	@Test
	public void test_OIDCScopeValidator_withValidScopes_thenPass() {
		OIDCScopeValidator validator = new OIDCScopeValidator();
		ReflectionTestUtils.setField(validator, "authorizeScopes", Arrays.asList("resident-service"));
		ReflectionTestUtils.setField(validator, "openidScopes", Arrays.asList("profile", "email", "phone"));
		ReflectionTestUtils.setField(validator, "credentialScopes", Arrays.asList("sample_ldp_vc", "mosip_identity_json_vc"));
		Assert.assertTrue(validator.isValid("resident-service email openid", null));
		Assert.assertTrue(validator.isValid("resident-service", null));
		Assert.assertTrue(validator.isValid("mosip_identity_json_vc", null));
	}
	
	@Test
	public void test_OIDCScopeValidator_withInvalidScopes_thenFail() {
		OIDCScopeValidator validator = new OIDCScopeValidator();
		ReflectionTestUtils.setField(validator, "authorizeScopes", Arrays.asList("resident-service"));
		ReflectionTestUtils.setField(validator, "openidScopes", Arrays.asList("profile", "email", "phone"));
		ReflectionTestUtils.setField(validator, "credentialScopes", Arrays.asList("sample_ldp_vc", "mosip_identity_json_vc"));
		Assert.assertFalse(validator.isValid("test scope", null));
		Assert.assertFalse(validator.isValid("resident-service sample_ldp_vc", null));
	}
	
	@Test
	public void test_OIDCScopeValidator_withoutOpenidScope_thenFail() {
		OIDCScopeValidator validator = new OIDCScopeValidator();
		ReflectionTestUtils.setField(validator, "authorizeScopes", Arrays.asList("resident-service"));
		ReflectionTestUtils.setField(validator, "openidScopes", Arrays.asList("profile", "email", "phone"));
		ReflectionTestUtils.setField(validator, "credentialScopes", Arrays.asList("sample_ldp_vc", "mosip_identity_json_vc"));
		Assert.assertFalse(validator.isValid("email", null));
	}
	
	@Test
	public void test_OIDCScopeValidator_withEmptyScope_thenFail() {
		OIDCScopeValidator validator = new OIDCScopeValidator();
		Assert.assertFalse(validator.isValid("", null));		
	}
	
	@Test
	public void test_OIDCScopeValidator_withNullScope_thenFail() {
		OIDCScopeValidator validator = new OIDCScopeValidator();
		Assert.assertFalse(validator.isValid(null, null));		
	}

	@Test
	public void test_OIDCScopeValidator_withBothOpenIdAndCredentialScope_thenFail() {
		OIDCScopeValidator validator = new OIDCScopeValidator();
		ReflectionTestUtils.setField(validator, "authorizeScopes", Arrays.asList("resident-service"));
		ReflectionTestUtils.setField(validator, "openidScopes", Arrays.asList("profile", "email", "phone"));
		ReflectionTestUtils.setField(validator, "credentialScopes", Arrays.asList("sample_ldp_vc", "mosip_identity_json_vc"));
		Assert.assertFalse(validator.isValid("profile sample_ldp_vc", null));
	}

	// ============================ PKCECodeChallengeMethodValidator Validator =========================

	@Test
	public void test_challengeMethodValidator_withValidValues_thenPass() {
		PKCECodeChallengeMethodValidator validator = new PKCECodeChallengeMethodValidator();
		ReflectionTestUtils.setField(validator, "supportedMethods", Arrays.asList("S256", "plain"));
		Assert.assertTrue(validator.isValid("S256", null));
		Assert.assertTrue(validator.isValid("plain", null));
		Assert.assertTrue(validator.isValid(null, null));
	}

	@Test
	public void test_challengeMethodValidator_withInvalidValues_thenFail() {
		PKCECodeChallengeMethodValidator validator = new PKCECodeChallengeMethodValidator();
		ReflectionTestUtils.setField(validator, "supportedMethods", Arrays.asList("S256", "plain"));
		Assert.assertFalse(validator.isValid("s256", null));
		Assert.assertFalse(validator.isValid("PLAIN", null));
		Assert.assertFalse(validator.isValid("null", null));
		Assert.assertFalse(validator.isValid("", null));
		Assert.assertFalse(validator.isValid(" ", null));
	}

	// ============================ RedirectURLValidator Validator =========================

	@Test
	public void test_redirectURLValidator_withValidValues_thenPass() {
		RedirectURLValidator validator = new RedirectURLValidator();
		Assert.assertTrue(validator.isValid("https://domain.com/test", null));
		Assert.assertTrue(validator.isValid("http://localhost:9090/png", null));
		Assert.assertTrue(validator.isValid("http://domain.com/*", null));
		Assert.assertTrue(validator.isValid("https://domain.com/test/*", null));
		Assert.assertTrue(validator.isValid("io.mosip.residentapp://oauth", null));
		Assert.assertTrue(validator.isValid("residentapp://oauth/*", null));
	}

	@Test
	public void test_redirectURLValidator_withInvalidValues_thenFail() {
		RedirectURLValidator validator = new RedirectURLValidator();
		Assert.assertFalse(validator.isValid("*", null));
		Assert.assertFalse(validator.isValid("https://domain*", null));
		Assert.assertFalse(validator.isValid("io.mosip.residentapp://*", null));
		Assert.assertFalse(validator.isValid("residentapp*", null));
		Assert.assertFalse(validator.isValid("http*", null));
	}

// ============================ Signature Format Validator =========================

	@Test
	public void test_Signature_FormatValidator_nullValue_thenFail() {
		SignatureFormatValidator validator = new SignatureFormatValidator();
		Assert.assertFalse(validator.isValid(null, null));
		Assert.assertFalse(validator.isValid("", null));
		Assert.assertFalse(validator.isValid("  ", null));
	}

	@Test
	public void test_Signature_FormatValidator_validValue_thenPass() {
		SignatureFormatValidator validator = new SignatureFormatValidator();
		Assert.assertTrue(validator.isValid("ea12d.iba13", null));
	}
	@Test
	public void test_Signature_FormatValidator_withInvalidValue_thenFail() {
		SignatureFormatValidator validator = new SignatureFormatValidator();
		Assert.assertFalse(validator.isValid("eab234", null));
		Assert.assertFalse(validator.isValid("eabd2314.123cad.123d ", null));
		Assert.assertFalse(validator.isValid("akf.ia*..aha", null));
		Assert.assertFalse(validator.isValid("ajjf", null));
	}

	//=========================== CodeChallengeValidator ==============================//

	@Test
	public void test_ValidCodeChallengeValidator_withValidDetails_thenPass(){
		CodeChallengeValidator validator=new CodeChallengeValidator();
		OAuthDetailRequestV2 request=new OAuthDetailRequestV2();
		request.setCodeChallenge("codeChallenge");
		request.setCodeChallengeMethod("codeChallengeMethod");
		Assert.assertTrue(validator.isValid(request,null));
		request.setCodeChallenge(null);
		request.setCodeChallengeMethod(null);
		Assert.assertTrue(validator.isValid(request,null));
		request.setCodeChallenge("");
		request.setCodeChallengeMethod("");
		Assert.assertTrue(validator.isValid(request,null));
	}

	@Test
	public void test_ValidCodeChallengeValidator_withInvalidDetails_thenFail(){
		CodeChallengeValidator validator=new CodeChallengeValidator();
		OAuthDetailRequestV2 request=new OAuthDetailRequestV2();
		request.setCodeChallenge("codeChallenge");
		request.setCodeChallengeMethod(null);
		Assert.assertFalse(validator.isValid(request,null));
		request.setCodeChallenge(null);
		request.setCodeChallengeMethod("codeChallengeMethod");
		Assert.assertFalse(validator.isValid(request,null));
		request.setCodeChallenge("");
		request.setCodeChallengeMethod("codeChallengeMethod");
		Assert.assertFalse(validator.isValid(request,null));
	}

	// ============================ ClientNameLang Validator =========================

	@Test
	public void test_ClientNameLangValidator_WithValidDetails_thenPass(){
		ClientNameLangValidator validator=new ClientNameLangValidator();
		Assert.assertTrue(validator.isValid("eng", null));
	}

	@Test
	public void test_ClientNameLangValidator_WithInValidDetail_thenFail(){
		ClientNameLangValidator validator=new ClientNameLangValidator();
		Assert.assertFalse(validator.isValid("abc", null));
	}

	// =============================ClaimSchemaValidator=============================//

	@Test
	public void test_ClaimSchemaValidator_withValidDetails_thenPass() throws IOException {

		ObjectMapper mapper = new ObjectMapper();

		ReflectionTestUtils.setField(claimSchemaValidator,"objectMapper",mapper);
		ReflectionTestUtils.setField(claimSchemaValidator,"schemaUrl","http://localhost:8080/claims/schema");

		String address="{\"essential\":true}";
		String verifiedClaims="[{\"verification\":{\"trust_framework\":{\"value\":\"income-tax\"}},\"claims\":{\"name\":null,\"email\":{\"essential\":true}}},{\"verification\":{\"trust_framework\":{\"value\":\"pwd\"}},\"claims\":{\"birthdate\":{\"essential\":true},\"address\":null}},{\"verification\":{\"trust_framework\":{\"value\":\"kaif\"}},\"claims\":{\"gender\":{\"essential\":true},\"email\":{\"essential\":true}}}]";

		JsonNode addressNode = mapper.readValue(address, JsonNode.class);
		JsonNode verifiedClaimNode = mapper.readValue(verifiedClaims, JsonNode.class);

		Map<String, JsonNode> userinfoMap = new HashMap<>();
		userinfoMap.put("address", addressNode);
		userinfoMap.put("verified_claims", verifiedClaimNode);
		Map<String, ClaimDetail> idTokenMap = new HashMap<>();

		ClaimDetail claimDetail = new ClaimDetail("claim_value", null, true, "secondary");

		idTokenMap.put("some_claim", claimDetail);

		ClaimsV2 claimsV2 = new ClaimsV2();
		claimsV2.setUserinfo(userinfoMap);
		claimsV2.setId_token(idTokenMap);

		Resource resource = Mockito.mock(Resource.class);
		Mockito.when(resourceLoader.getResource(Mockito.anyString())).thenReturn(resource);
		Mockito.when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(claimSchema.getBytes()));

		Assert.assertTrue(claimSchemaValidator.isValid(claimsV2, null));
	}

	@Test
	public void test_ClaimSchemaValidator_withTrustFrameWorkAsNull_thenFail() throws IOException {

		ObjectMapper mapper = new ObjectMapper();

		ReflectionTestUtils.setField(claimSchemaValidator,"objectMapper",mapper);
		ReflectionTestUtils.setField(claimSchemaValidator,"schemaUrl","http://localhost:8080/claims/schema");

		String address="{\"essential\":true}";
		String verifiedClaims="[{\"verification\":{\"trust_framework\":{\"value\":null}},\"claims\":{\"name\":null,\"email\":{\"essential\":true}}},{\"verification\":{\"trust_framework\":{\"value\":\"pwd\"}},\"claims\":{\"birthdate\":{\"essential\":true},\"address\":null}},{\"verification\":{\"trust_framework\":{\"value\":\"kaif\"}},\"claims\":{\"gender\":{\"essential\":true},\"email\":{\"essential\":true}}}]";

		JsonNode addressNode = mapper.readValue(address, JsonNode.class);
		JsonNode verifiedClaimNode = mapper.readValue(verifiedClaims, JsonNode.class);

		Map<String, JsonNode> userinfoMap = new HashMap<>();
		userinfoMap.put("address", addressNode);
		userinfoMap.put("verified_claims", verifiedClaimNode);
		Map<String, ClaimDetail> idTokenMap = new HashMap<>();


		ClaimDetail claimDetail = new ClaimDetail("claim_value", null, true, "secondary");

		idTokenMap.put("some_claim", claimDetail);
		ClaimsV2 claimsV2 = new ClaimsV2();
		claimsV2.setUserinfo(userinfoMap);
		claimsV2.setId_token(idTokenMap);

		Resource resource = Mockito.mock(Resource.class);
		Mockito.when(resourceLoader.getResource(Mockito.anyString())).thenReturn(resource);
		Mockito.when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(claimSchema.getBytes()));

		Assert.assertFalse(claimSchemaValidator.isValid(claimsV2, null));

	}

	@Test
	public void test_ClaimSchemaValidator_withEssentialAsInteger_thenFail() throws IOException {

		ObjectMapper mapper = new ObjectMapper();

		ReflectionTestUtils.setField(claimSchemaValidator,"objectMapper",mapper);
		ReflectionTestUtils.setField(claimSchemaValidator,"schemaUrl","http://localhost:8080/claims/schema");

		String address="{\"essential\":true}";
		String verifiedClaims="[{\"verification\":{\"trust_framework\":{\"value\":\"pwd\"}},\"claims\":{\"name\":null,\"email\":{\"essential\":1}}},{\"verification\":{\"trust_framework\":{\"value\":\"pwd\"}},\"claims\":{\"birthdate\":{\"essential\":true},\"address\":null}},{\"verification\":{\"trust_framework\":{\"value\":\"kaif\"}},\"claims\":{\"gender\":{\"essential\":true},\"email\":{\"essential\":true}}}]";

		JsonNode addressNode = mapper.readValue(address, JsonNode.class);
		JsonNode verifiedClaimNode = mapper.readValue(verifiedClaims, JsonNode.class);

		Map<String, JsonNode> userinfoMap = new HashMap<>();
		userinfoMap.put("address", addressNode);
		userinfoMap.put("verified_claims", verifiedClaimNode);
		Map<String, ClaimDetail> idTokenMap = new HashMap<>();


		ClaimDetail claimDetail = new ClaimDetail("claim_value", null, true, "secondary");

		idTokenMap.put("some_claim", claimDetail);
		ClaimsV2 claimsV2 = new ClaimsV2();
		claimsV2.setUserinfo(userinfoMap);
		claimsV2.setId_token(idTokenMap);

		Resource resource = Mockito.mock(Resource.class);
		Mockito.when(resourceLoader.getResource(Mockito.anyString())).thenReturn(resource);
		Mockito.when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(claimSchema.getBytes()));

		Assert.assertFalse(claimSchemaValidator.isValid(claimsV2, null));

	}

	@Test
	public void test_ClaimSchemaValidator_withInvalidValue_thenFail() throws IOException {

		ObjectMapper mapper = new ObjectMapper();

		ReflectionTestUtils.setField(claimSchemaValidator,"objectMapper",mapper);
		ReflectionTestUtils.setField(claimSchemaValidator,"schemaUrl","http://localhost:8080/claims/schema");

		String address="{\"essential\":true}";
		String verifiedClaims="[{\"verification\":{\"trust_framework\":{\"value\":\"pwd\"}},\"claims\":{\"name\":null,\"email\":{\"essential\":1}}},{\"verification\":{\"trust_framework\":{\"value\":\"pwd\"}},\"claims\":{\"birthdate\":{\"essential\":true},\"address\":null}},{\"verification\":{\"trust_framework\":{\"value\":\"kf\"}},\"claims\":{\"gender\":{\"essential\":true},\"email\":{\"essential\":true}}}]";

		JsonNode addressNode = mapper.readValue(address, JsonNode.class);
		JsonNode verifiedClaimNode = mapper.readValue(verifiedClaims, JsonNode.class);

		Map<String, JsonNode> userinfoMap = new HashMap<>();
		userinfoMap.put("address", addressNode);
		userinfoMap.put("verified_claims", verifiedClaimNode);
		Map<String, ClaimDetail> idTokenMap = new HashMap<>();


		ClaimDetail claimDetail = new ClaimDetail("claim_value", null, true, "secondary");

		idTokenMap.put("some_claim", claimDetail);
		ClaimsV2 claimsV2 = new ClaimsV2();
		claimsV2.setUserinfo(userinfoMap);
		claimsV2.setId_token(idTokenMap);

		Resource resource = Mockito.mock(Resource.class);
		Mockito.when(resourceLoader.getResource(Mockito.anyString())).thenReturn(resource);
		Mockito.when(resource.getInputStream()).thenReturn(new ByteArrayInputStream(claimSchema.getBytes()));

		Assert.assertFalse(claimSchemaValidator.isValid(claimsV2, null));

	}
}
