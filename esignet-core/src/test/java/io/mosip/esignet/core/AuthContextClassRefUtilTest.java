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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.esignet.core.dto.AuthenticationFactor;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;

@RunWith(MockitoJUnitRunner.class)
public class AuthContextClassRefUtilTest {

    AuthenticationContextClassRefUtil authenticationContextClassRefUtil = new AuthenticationContextClassRefUtil();
    ObjectMapper mapper = new ObjectMapper();

    @Mock
    RestTemplate restTemplate;
    
    @Mock
    ObjectMapper objectMapper;
    
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

    @Before
    public void setup() throws IOException {
        ReflectionTestUtils.setField(authenticationContextClassRefUtil, "objectMapper", mapper);
        ReflectionTestUtils.setField(authenticationContextClassRefUtil, "mappingJson", amr_acr_mapping);
    }


    @Test
    public void getSupportedACRValues_test() throws EsignetException {
        Set<String> acrValues = authenticationContextClassRefUtil.getSupportedACRValues();
        Assert.assertNotNull(acrValues);
        Assert.assertEquals(4 ,acrValues.size());
    }

    @Test
    public void getAuthFactors_withEmptyAcr() throws EsignetException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.getAuthFactors(new String[] {});
        Assert.assertNotNull(authFactors);
        Assert.assertTrue(authFactors.isEmpty());
    }

    @Test(expected = EsignetException.class)
    public void getAuthFactors_withInvalidMappingJson_throwsException() throws EsignetException {
    	ReflectionTestUtils.setField(authenticationContextClassRefUtil, "mappingJson", "test");         
        authenticationContextClassRefUtil.getAuthFactors(new String[] {"mosip:idp:acr:linked-wallet"});
    }
    
    @Test
    public void getAuthFactors_withValidAcr() throws EsignetException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.
                getAuthFactors(new String[] {"mosip:idp:acr:linked-wallet"});

        Assert.assertNotNull(authFactors);
        Assert.assertTrue(authFactors.size() == 1);

        List<AuthenticationFactor> firstAuthFactor = authFactors.get(0);
        Assert.assertNotNull(firstAuthFactor);
        Assert.assertTrue(firstAuthFactor.size() == 1);
        Assert.assertTrue(firstAuthFactor.get(0).getType().equals("INJI"));
        Assert.assertTrue(firstAuthFactor.get(0).getCount() == 0);
        Assert.assertNull(firstAuthFactor.get(0).getSubTypes());
    }

    @Test
    public void getAuthFactors_withEmptyMappingJson() throws EsignetException {
    	ReflectionTestUtils.setField(authenticationContextClassRefUtil, "mappingJson", null);
    	ReflectionTestUtils.setField(authenticationContextClassRefUtil, "mappingFileUrl", "https://test-url");
    	ReflectionTestUtils.setField(authenticationContextClassRefUtil, "restTemplate", restTemplate);
    	
    	Mockito.when(restTemplate.getForObject(Mockito.anyString(), Mockito.any())).thenReturn(amr_acr_mapping);
    	
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.
                getAuthFactors(new String[] {"mosip:idp:acr:linked-wallet"});

        Assert.assertNotNull(authFactors);
        Assert.assertTrue(authFactors.size() == 1);

        List<AuthenticationFactor> firstAuthFactor = authFactors.get(0);
        Assert.assertNotNull(firstAuthFactor);
        Assert.assertTrue(firstAuthFactor.size() == 1);
        Assert.assertTrue(firstAuthFactor.get(0).getType().equals("INJI"));
        Assert.assertTrue(firstAuthFactor.get(0).getCount() == 0);
        Assert.assertNull(firstAuthFactor.get(0).getSubTypes());
    }

    @Test
    public void getAuthFactors_withValidAcr_preserveOrderOfPrecedence() throws EsignetException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.getAuthFactors(new String[]
                {"mosip:idp:acr:biometrics", "mosip:idp:acr:static-code"});

        Assert.assertNotNull(authFactors);
        Assert.assertTrue(authFactors.size() == 2);

        List<AuthenticationFactor> firstAuthFactor = authFactors.get(0);
        Assert.assertNotNull(firstAuthFactor);
        Assert.assertTrue(firstAuthFactor.size() == 1);
        Assert.assertTrue(firstAuthFactor.get(0).getType().equals("BIO"));
        Assert.assertTrue(firstAuthFactor.get(0).getCount() == 1);
        Assert.assertNull(firstAuthFactor.get(0).getSubTypes());

        List<AuthenticationFactor> secondAuthFactor = authFactors.get(1);
        Assert.assertNotNull(secondAuthFactor);
        Assert.assertTrue(secondAuthFactor.size() == 1);
        Assert.assertTrue(secondAuthFactor.get(0).getType().equals("PIN"));
        Assert.assertTrue(secondAuthFactor.get(0).getCount() == 0);
        Assert.assertNull(secondAuthFactor.get(0).getSubTypes());
    }

    @Test
    public void getAuthFactors_withValidAcr_ignoreUnknown() throws EsignetException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.getAuthFactors(new String[]
                {"mosip:idp:acr:generated-code", "mosip:idp:acr:metrics"});

        Assert.assertNotNull(authFactors);
        Assert.assertTrue(authFactors.size() == 1);

        List<AuthenticationFactor> firstAuthFactor = authFactors.get(0);
        Assert.assertNotNull(firstAuthFactor);
        Assert.assertTrue(firstAuthFactor.size() == 1);
        Assert.assertTrue(firstAuthFactor.get(0).getType().equals("OTP"));
        Assert.assertTrue(firstAuthFactor.get(0).getCount() == 0);
        Assert.assertNull(firstAuthFactor.get(0).getSubTypes());
    }
    
    @Test
    public void getACRs_withValidData_thenPass() {
    	Set<List<String>> authFactorTypesSet = new HashSet<>();
    	authFactorTypesSet.add(Arrays.asList("PIN", "OTP", "INJI", "BIO"));
    	
		List<String> acrData = authenticationContextClassRefUtil.getACRs(authFactorTypesSet);
		
		Assert.assertNotNull(acrData);
        Assert.assertTrue(acrData.size() == 4);
    }
    
    @Test
    public void getACRs_withEmptyAuthFactorSet_thenFail() {
    	Set<List<String>> authFactorTypesSet = new HashSet<>();    	
		List<String> acrData = authenticationContextClassRefUtil.getACRs(authFactorTypesSet);		
		Assert.assertTrue(acrData.size() == 0);
    }

}
