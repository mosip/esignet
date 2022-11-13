/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core;

import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.validator.*;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class ValidatorTest {

    @Mock
    AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Mock
    AuthenticationWrapper authenticationWrapper;

    @Before
    public void setup() throws IdPException {
        Set<String> mockACRs = new HashSet<>();
        mockACRs.add("level1");
        mockACRs.add("level2");
        mockACRs.add("level3");
        mockACRs.add("level4");
        when(authenticationContextClassRefUtil.getSupportedACRValues()).thenReturn(mockACRs);
        when(authenticationWrapper.isSupportedOtpChannel("email")).thenReturn(true);
    }

    //============================ Display Validator =========================

    @Test
    public void test_displayValidator_valid() {
        OIDCDisplayValidator validator = new OIDCDisplayValidator();
        ReflectionTestUtils.setField(validator, "supportedDisplays", Arrays.asList("page", "wap"));
        Assert.assertTrue(validator.isValid("wap", null));
    }

    @Test
    public void test_displayValidator_invalid() {
        OIDCDisplayValidator validator = new OIDCDisplayValidator();
        ReflectionTestUtils.setField(validator, "supportedDisplays", Arrays.asList("page", "wap"));
        Assert.assertFalse(validator.isValid("wap2", null));
    }

    @Test
    public void test_displayValidator_invalidWithSpace() {
        OIDCDisplayValidator validator = new OIDCDisplayValidator();
        ReflectionTestUtils.setField(validator, "supportedDisplays", Arrays.asList("page", "wap"));
        Assert.assertFalse(validator.isValid("page wap", null));
    }

    @Test
    public void test_displayValidator_nullValue() {
        OIDCDisplayValidator validator = new OIDCDisplayValidator();
        ReflectionTestUtils.setField(validator, "supportedDisplays", Arrays.asList("page", "wap"));
        Assert.assertTrue(validator.isValid(null, null));
    }

    @Test
    public void test_displayValidator_EmptyValue() {
        OIDCDisplayValidator validator = new OIDCDisplayValidator();
        ReflectionTestUtils.setField(validator, "supportedDisplays", Arrays.asList("page", "wap"));
        Assert.assertTrue(validator.isValid("", null));
    }

    //============================ GranType Validator =========================

    @Test
    public void test_grantTypeValidator_valid() {
        OIDCGrantTypeValidator validator = new OIDCGrantTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedGrantTypes",
                Arrays.asList("authorization_code"));
        Assert.assertTrue(validator.isValid("authorization_code", null));
    }

    @Test
    public void test_grantTypeValidator_invalid() {
        OIDCGrantTypeValidator validator = new OIDCGrantTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedGrantTypes",
                Arrays.asList("authorization_code"));
        Assert.assertFalse(validator.isValid("code", null));
    }

    @Test
    public void test_grantTypeValidator_invalidWithSpace() {
        OIDCGrantTypeValidator validator = new OIDCGrantTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedGrantTypes",
                Arrays.asList("authorization_code"));
        Assert.assertFalse(validator.isValid(" authorization_code ", null));
    }

    @Test
    public void test_grantTypeValidator_nullValue() {
        OIDCGrantTypeValidator validator = new OIDCGrantTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedGrantTypes",
                Arrays.asList("authorization_code"));
        Assert.assertFalse(validator.isValid(null, null));
    }

    @Test
    public void test_grantTypeValidator_EmptyValue() {
        OIDCGrantTypeValidator validator = new OIDCGrantTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedGrantTypes",
                Arrays.asList("authorization_code"));
        Assert.assertFalse(validator.isValid("", null));
    }

    //============================ Prompt Validator =========================

    @Test
    public void test_PromptValidator_valid() {
        OIDCPromptValidator validator = new OIDCPromptValidator();
        ReflectionTestUtils.setField(validator, "supportedPrompts",
                Arrays.asList("none","login","consent"));
        Assert.assertTrue(validator.isValid("consent", null));
    }

    @Test
    public void test_PromptValidator_invalid() {
        OIDCPromptValidator validator = new OIDCPromptValidator();
        ReflectionTestUtils.setField(validator, "supportedPrompts",
                Arrays.asList("none","login","consent"));
        Assert.assertFalse(validator.isValid("pop-up", null));
    }

    @Test
    public void test_PromptValidator_invalidWithSpace() {
        OIDCPromptValidator validator = new OIDCPromptValidator();
        ReflectionTestUtils.setField(validator, "supportedPrompts",
                Arrays.asList("none","login","consent"));
        Assert.assertFalse(validator.isValid(" login ", null));
    }

    @Test
    public void test_PromptValidator_nullValue() {
        OIDCPromptValidator validator = new OIDCPromptValidator();
        ReflectionTestUtils.setField(validator, "supportedPrompts",
                Arrays.asList("none","login","consent"));
        Assert.assertTrue(validator.isValid(null, null));
    }

    @Test
    public void test_PromptValidator_EmptyValue() {
        OIDCPromptValidator validator = new OIDCPromptValidator();
        ReflectionTestUtils.setField(validator, "supportedPrompts",
                Arrays.asList("none","login","consent"));
        Assert.assertTrue(validator.isValid("", null));
    }

    //============================ ResponseType Validator =========================

    @Test
    public void test_ResponseTypeValidator_valid() {
        OIDCResponseTypeValidator validator = new OIDCResponseTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedResponseTypes",
                Arrays.asList("code"));
        Assert.assertTrue(validator.isValid("code", null));
    }

    @Test
    public void test_ResponseTypeValidator_invalid() {
        OIDCResponseTypeValidator validator = new OIDCResponseTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedResponseTypes",
                Arrays.asList("code"));
        Assert.assertFalse(validator.isValid("code----", null));
    }

    @Test
    public void test_ResponseTypeValidator_invalidWithSpace() {
        OIDCResponseTypeValidator validator = new OIDCResponseTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedResponseTypes",
                Arrays.asList("code"));
        Assert.assertFalse(validator.isValid(" code ", null));
    }

    @Test
    public void test_ResponseTypeValidator_nullValue() {
        OIDCResponseTypeValidator validator = new OIDCResponseTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedResponseTypes",
                Arrays.asList("code"));
        Assert.assertFalse(validator.isValid(null, null));
    }

    @Test
    public void test_ResponseTypeValidator_EmptyValue() {
        OIDCResponseTypeValidator validator = new OIDCResponseTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedResponseTypes",
                Arrays.asList("code"));
        Assert.assertFalse(validator.isValid("", null));
    }

    //============================ Client Assertion type Validator =========================

    @Test
    public void test_ClientAssertionTypeValidator_valid() {
        OIDCClientAssertionTypeValidator validator = new OIDCClientAssertionTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedAssertionTypes",
                Arrays.asList("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
        Assert.assertTrue(validator.isValid("urn:ietf:params:oauth:client-assertion-type:jwt-bearer", null));
    }

    @Test
    public void test_ClientAssertionTypeValidator_invalid() {
        OIDCClientAssertionTypeValidator validator = new OIDCClientAssertionTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedAssertionTypes",
                Arrays.asList("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
        Assert.assertFalse(validator.isValid("jwt-bearer", null));
    }

    @Test
    public void test_ClientAssertionTypeValidator_invalidWithSpace() {
        OIDCClientAssertionTypeValidator validator = new OIDCClientAssertionTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedAssertionTypes",
                Arrays.asList("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
        Assert.assertFalse(validator.isValid("urn:ietf:params:oauth:client-assertion-type:jwt-bearer ", null));
    }

    @Test
    public void test_ClientAssertionTypeValidator_nullValue() {
        OIDCClientAssertionTypeValidator validator = new OIDCClientAssertionTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedAssertionTypes",
                Arrays.asList("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
        Assert.assertFalse(validator.isValid(null, null));
    }

    @Test
    public void test_ClientAssertionTypeValidator_EmptyValue() {
        OIDCClientAssertionTypeValidator validator = new OIDCClientAssertionTypeValidator();
        ReflectionTestUtils.setField(validator, "supportedAssertionTypes",
                Arrays.asList("urn:ietf:params:oauth:client-assertion-type:jwt-bearer"));
        Assert.assertFalse(validator.isValid("", null));
    }

    //============================ Optional ACR Validator =========================

    @Test
    public void test_OptionalACRValidator_valid() {
        AuthContextRefValidator validator = new AuthContextRefValidator();
        ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
        Assert.assertTrue(validator.isValid(null, null));
    }

    @Test
    public void test_OptionalACRValidator_EmptyValue() {
        AuthContextRefValidator validator = new AuthContextRefValidator();
        ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
        Assert.assertFalse(validator.isValid("", null));
    }

    @Test
    public void test_OptionalACRValidator_SingleValue() {
        AuthContextRefValidator validator = new AuthContextRefValidator();
        ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
        Assert.assertTrue(validator.isValid("level2", null));
    }

    @Test
    public void test_OptionalACRValidator_MultipleValue() {
        AuthContextRefValidator validator = new AuthContextRefValidator();
        ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
        Assert.assertTrue(validator.isValid("level4 level2", null));
    }

    @Test
    public void test_OptionalACRValidator_InvalidMultipleValue() {
        AuthContextRefValidator validator = new AuthContextRefValidator();
        ReflectionTestUtils.setField(validator, "acrUtil", authenticationContextClassRefUtil);
        Assert.assertFalse(validator.isValid("level5 level1", null));
    }

    //============================ Request time Validator =========================

    @Test
    public void test_RequestTimeValidator_nullValue() {
        RequestTimeValidator validator = new RequestTimeValidator();
        Assert.assertFalse(validator.isValid(null, null));
        Assert.assertFalse(validator.isValid("", null));
        Assert.assertFalse(validator.isValid("  ", null));
    }

    @Test
    public void test_RequestTimeValidator_validValue() {
        RequestTimeValidator validator = new RequestTimeValidator();
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        Assert.assertTrue(validator.isValid(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)), null));
    }

    @Test
    public void test_RequestTimeValidator_futureDateValue() {
        RequestTimeValidator validator = new RequestTimeValidator();
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        requestTime = requestTime.plusMinutes(4);
        Assert.assertFalse(validator.isValid(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)), null));
    }

    @Test
    public void test_RequestTimeValidator_oldDateValue() {
        RequestTimeValidator validator = new RequestTimeValidator();
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        requestTime = requestTime.minusMinutes(5);
        Assert.assertFalse(validator.isValid(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)), null));
    }

    @Test
    public void test_RequestTimeValidator_invalidFormat() {
        RequestTimeValidator validator = new RequestTimeValidator();
        ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
        String DATETIME_PATTERN = "yyyy-MM-dd HH:mm:ss.SSS";
        Assert.assertFalse(validator.isValid(requestTime.format(DateTimeFormatter.ofPattern(DATETIME_PATTERN)), null));
    }

    //============================ Otp channel Validator =========================

    @Test
    public void test_OtpChannelValidator_valid() {
        OtpChannelValidator validator = new OtpChannelValidator();
        ReflectionTestUtils.setField(validator, "authenticationWrapper", authenticationWrapper);
        Assert.assertTrue(validator.isValid("email", null));
    }

    @Test
    public void test_OtpChannelValidator_null() {
        OtpChannelValidator validator = new OtpChannelValidator();
        ReflectionTestUtils.setField(validator, "authenticationWrapper", authenticationWrapper);
        Assert.assertFalse(validator.isValid(null, null));
    }

    @Test
    public void test_OtpChannelValidator_invalid() {
        OtpChannelValidator validator = new OtpChannelValidator();
        ReflectionTestUtils.setField(validator, "authenticationWrapper", authenticationWrapper);
        Assert.assertFalse(validator.isValid("mobile", null));
    }

    @Test
    public void test_OtpChannelValidator_blank() {
        OtpChannelValidator validator = new OtpChannelValidator();
        ReflectionTestUtils.setField(validator, "authenticationWrapper", authenticationWrapper);
        Assert.assertFalse(validator.isValid("   ", null));
    }

    @Test
    public void test_OtpChannelValidator_spaceAppended() {
        OtpChannelValidator validator = new OtpChannelValidator();
        ReflectionTestUtils.setField(validator, "authenticationWrapper", authenticationWrapper);
        Assert.assertFalse(validator.isValid("   email ", null));
    }
}
