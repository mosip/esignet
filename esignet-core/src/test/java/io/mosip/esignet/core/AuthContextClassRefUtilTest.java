/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;


import java.io.IOException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.esignet.core.dto.AuthenticationFactor;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;

@ExtendWith(MockitoExtension.class)
public class AuthContextClassRefUtilTest {

    AuthenticationContextClassRefUtil authenticationContextClassRefUtil = new AuthenticationContextClassRefUtil();
    ObjectMapper mapper = new ObjectMapper();

    
    private static final String amr_acr_mapping = "{\n" +
            "  \"amr\" : {\n" +
            "    \"PIN\" :  [{ \"type\": \"PIN\" }],\n" +
            "    \"OTP\" :  [{ \"type\": \"OTP\" }],\n" +
            "    \"Inji\" :  [{ \"type\": \"INJI\" }],\n" +
            "    \"L1-bio-device\" :  [{ \"type\": \"BIO\", \"count\": 1 }]\n" +
            "  },\n" +
            "  \"acr_amr\" : {\n" +
            "    \"mosip:idp:acr:static-code\" : [\"PIN\"],\n" +
            "    \"mosip:idp:acr:generated-code\" : [\"OTP\"],\n" +
            "    \"mosip:idp:acr:linked-wallet\" : [ \"Inji\" ],\n" +
            "    \"mosip:idp:acr:biometrics\" : [ \"L1-bio-device\" ]\n" +
            "  }\n" +
            "}";

    @BeforeEach
    public void setup() throws IOException {
        ReflectionTestUtils.setField(authenticationContextClassRefUtil, "objectMapper", mapper);
        ReflectionTestUtils.setField(authenticationContextClassRefUtil, "mappingJson", amr_acr_mapping);
    }


    @Test
    public void getSupportedACRValues_test() throws EsignetException {
        Set<String> acrValues = authenticationContextClassRefUtil.getSupportedACRValues();
        Assertions.assertNotNull(acrValues);
        Assertions.assertEquals(4 ,acrValues.size());
    }

    @Test
    public void getAuthFactors_withEmptyAcr() throws EsignetException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.getAuthFactors(new String[] {});
        Assertions.assertNotNull(authFactors);
        Assertions.assertTrue(authFactors.isEmpty());
    }

    @Test
    public void getAuthFactors_withInvalidMappingJson_throwsException() throws EsignetException {
    	ReflectionTestUtils.setField(authenticationContextClassRefUtil, "mappingJson", "test");         
        Assertions.assertThrows(EsignetException.class, () -> authenticationContextClassRefUtil.getAuthFactors(new String[] {"mosip:idp:acr:linked-wallet"}));
    }
    
    @Test
    public void getAuthFactors_withValidAcr() throws EsignetException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.
                getAuthFactors(new String[] {"mosip:idp:acr:linked-wallet"});

        Assertions.assertNotNull(authFactors);
        Assertions.assertTrue(authFactors.size() == 1);

        List<AuthenticationFactor> firstAuthFactor = authFactors.get(0);
        Assertions.assertNotNull(firstAuthFactor);
        Assertions.assertTrue(firstAuthFactor.size() == 1);
        Assertions.assertTrue(firstAuthFactor.get(0).getType().equals("INJI"));
        Assertions.assertTrue(firstAuthFactor.get(0).getCount() == 0);
        Assertions.assertNull(firstAuthFactor.get(0).getSubTypes());
    }

    @Test
    public void getAuthFactors_withValidAcr_preserveOrderOfPrecedence() throws EsignetException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.getAuthFactors(new String[]
                {"mosip:idp:acr:biometrics", "mosip:idp:acr:static-code"});

        Assertions.assertNotNull(authFactors);
        Assertions.assertTrue(authFactors.size() == 2);

        List<AuthenticationFactor> firstAuthFactor = authFactors.get(0);
        Assertions.assertNotNull(firstAuthFactor);
        Assertions.assertTrue(firstAuthFactor.size() == 1);
        Assertions.assertTrue(firstAuthFactor.get(0).getType().equals("BIO"));
        Assertions.assertTrue(firstAuthFactor.get(0).getCount() == 1);
        Assertions.assertNull(firstAuthFactor.get(0).getSubTypes());

        List<AuthenticationFactor> secondAuthFactor = authFactors.get(1);
        Assertions.assertNotNull(secondAuthFactor);
        Assertions.assertTrue(secondAuthFactor.size() == 1);
        Assertions.assertTrue(secondAuthFactor.get(0).getType().equals("PIN"));
        Assertions.assertTrue(secondAuthFactor.get(0).getCount() == 0);
        Assertions.assertNull(secondAuthFactor.get(0).getSubTypes());
    }

    @Test
    public void getAuthFactors_withValidAcr_ignoreUnknown() throws EsignetException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.getAuthFactors(new String[]
                {"mosip:idp:acr:generated-code", "mosip:idp:acr:metrics"});

        Assertions.assertNotNull(authFactors);
        Assertions.assertTrue(authFactors.size() == 1);

        List<AuthenticationFactor> firstAuthFactor = authFactors.get(0);
        Assertions.assertNotNull(firstAuthFactor);
        Assertions.assertTrue(firstAuthFactor.size() == 1);
        Assertions.assertTrue(firstAuthFactor.get(0).getType().equals("OTP"));
        Assertions.assertTrue(firstAuthFactor.get(0).getCount() == 0);
        Assertions.assertNull(firstAuthFactor.get(0).getSubTypes());
    }
    
    @Test
    public void getACRs_withValidData_thenPass() {
    	Set<List<String>> authFactorTypesSet = new HashSet<>();
    	authFactorTypesSet.add(Arrays.asList("PIN", "OTP", "INJI", "BIO"));
    	
		List<String> acrData = authenticationContextClassRefUtil.getACRs(authFactorTypesSet);
		
		Assertions.assertNotNull(acrData);
        Assertions.assertTrue(acrData.size() == 4);
    }
    
    @Test
    public void getACRs_withEmptyAuthFactorSet_thenFail() {
    	Set<List<String>> authFactorTypesSet = new HashSet<>();    	
		List<String> acrData = authenticationContextClassRefUtil.getACRs(authFactorTypesSet);		
		Assertions.assertTrue(acrData.size() == 0);
    }

}
