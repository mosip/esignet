package io.mosip.idp.core;


import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.idp.core.dto.AuthenticationFactor;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.core.io.Resource;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.when;

@RunWith(MockitoJUnitRunner.class)
public class AuthContextClassRefUtilTest {

    @InjectMocks
    AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Mock
    ObjectMapper objectMapper;

    @Mock
    Resource mappingFile;

    private static final String amr_acr_mapping = "{\n" +
            "  \"amr\" : {\n" +
            "    \"PIN\" :  [{ \"type\": \"PIN\" }],\n" +
            "    \"OTP\" :  [{ \"type\": \"OTP\" }],\n" +
            "    \"Inji\" :  [{ \"type\": \"INJI\" }],\n" +
            "    \"L1 device\" :  [{ \"type\": \"BIO\", \"count\": 1 }]\n" +
            "  },\n" +
            "  \"acr_amr\" : {\n" +
            "    \"mosip:idp:acr:static-code\" : [\"PIN\"],\n" +
            "    \"mosip:idp:acr:generated-code\" : [\"OTP\"],\n" +
            "    \"mosip:idp:acr:linked-wallet\" : [ \"Inji\" ],\n" +
            "    \"mosip:idp:acr:biometrics\" : [ \"L1 device\" ]\n" +
            "  }\n" +
            "}";

    @Before
    public void setup() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode objectNode = mapper.readValue(amr_acr_mapping, new TypeReference<ObjectNode>(){});
        Map<String, List<AuthenticationFactor>> amrValues = new ObjectMapper().convertValue(objectNode.get("amr"),
                new TypeReference<Map<String, List<AuthenticationFactor>>>(){});
        Map<String, List<String>> acr_amr_Values = new ObjectMapper().convertValue(objectNode.get("acr_amr"),
                new TypeReference<Map<String, List<String>>>(){});

        when(mappingFile.getFile()).thenReturn(new File(""));
        when(objectMapper.readValue(ArgumentMatchers.<File>any(),
                ArgumentMatchers.<TypeReference<ObjectNode>>any())).thenReturn(objectNode);

        //Ongoing stub, returns value in order of call hierarchy
        when(objectMapper.convertValue(ArgumentMatchers.<ObjectNode>any(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any())).thenReturn(amrValues, acr_amr_Values);
    }


    @Test
    public void getSupportedACRValues_test() throws IdPException {
        Set<String> acrValues = authenticationContextClassRefUtil.getSupportedACRValues();
        Assert.assertNotNull(acrValues);
        Assert.assertEquals(4 ,acrValues.size());
    }

    @Test
    public void getAuthFactors_withEmptyAcr() throws IdPException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.getAuthFactors(new String[] {});
        Assert.assertNotNull(authFactors);
        Assert.assertTrue(authFactors.isEmpty());
    }

    @Test
    public void getAuthFactors_withValidAcr() throws IdPException {
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
    public void getAuthFactors_withValidAcr_preserveOrderOfPrecedence() throws IdPException {
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
    public void getAuthFactors_withValidAcr_ignoreUnknown() throws IdPException {
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

}
