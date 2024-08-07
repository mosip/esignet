/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;

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
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.env.Environment;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ValidatorTest {

	@Mock
	AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

	@Mock
	Authenticator authenticator;

	@Mock
	Environment environment;

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

}
