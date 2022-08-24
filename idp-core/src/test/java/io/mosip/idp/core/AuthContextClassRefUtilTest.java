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
            "  \"amr_values\" : {\n" +
            "    \"only_otp\" :  [{ \"name\": \"otp\" }],\n" +
            "    \"only_finger\" :  [{ \"name\": \"fpt\", \"count\": 1, \"bioSubTypes\" : [\"leftThumb\"] }],\n" +
            "    \"only_iris\" :  [{ \"name\": \"iris\", \"count\": 2 }],\n" +
            "    \"five_fingers\" : [{ \"name\": \"fpt\", \"count\": 4 , \"bioSubTypes\" : [\"unknown\", \"unknown\", \"unknown\", \"unknown\"]}],\n" +
            "    \"otp_one_finger\" : [{ \"name\": \"otp\" },{ \"name\": \"fpt\", \"count\": 1 , \"bioSubTypes\" : [\"rightThumb\"]}],\n" +
            "    \"otp_all_fingers\" : [{ \"name\": \"otp\" },{ \"name\": \"fpt\", \"count\": 10 }],\n" +
            "    \"iris_otp\" :  [{ \"name\": \"iris\", \"count\": 2 }, { \"name\": \"otp\" }]\n" +
            "  },\n" +
            "  \"acr_values\" : {\n" +
            "    \"level1\" : \"1\",\n" +
            "    \"level2\" : \"2\",\n" +
            "    \"level3\" : \"3\",\n" +
            "    \"level4\" : \"4\",\n" +
            "    \"level5\" : \"5\"\n" +
            "  },\n" +
            "  \"acr_amr\" : {\n" +
            "                \"1\" :  [\"only_otp\"],\n" +
            "                \"2\" :  [\"only_otp\", \"only_finger\"],\n" +
            "                \"3\" :  [\"five_fingers\", \"otp_one_finger\"],\n" +
            "                \"4\" :  [\"otp_all_fingers\", \"only_iris\"],\n" +
            "                \"5\" :  [\"iris_otp\"]\n" +
            "              }\n" +
            "}";

    @Before
    public void setup() throws IOException {
        ObjectMapper mapper = new ObjectMapper();
        ObjectNode objectNode = mapper.readValue(amr_acr_mapping, new TypeReference<ObjectNode>(){});
        Map<String, String> acrValues = mapper.convertValue(objectNode.get("acr_values"), new TypeReference<Map<String, String>>(){});
        Map<String, List<AuthenticationFactor>> amrValues = new ObjectMapper().convertValue(objectNode.get("amr_values"),
                new TypeReference<Map<String, List<AuthenticationFactor>>>(){});
        Map<String, List<String>> acr_amr_Values = new ObjectMapper().convertValue(objectNode.get("acr_amr"),
                new TypeReference<Map<String, List<String>>>(){});

        when(mappingFile.getFile()).thenReturn(new File(""));
        when(objectMapper.readValue(ArgumentMatchers.<File>any(),
                ArgumentMatchers.<TypeReference<ObjectNode>>any())).thenReturn(objectNode);

        //Ongoing stub, returns value in order of call hierarchy
        when(objectMapper.convertValue(ArgumentMatchers.<ObjectNode>any(),
                ArgumentMatchers.<TypeReference<Map<String, Object>>>any())).thenReturn(acrValues, amrValues, acr_amr_Values);
    }


    @Test
    public void getSupportedACRValues_test() throws IdPException {
        authenticationContextClassRefUtil.getSupportedACRValues();
    }

    @Test
    public void getAuthFactors_withEmptyAcr() throws IdPException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.getAuthFactors(new String[] {});
        Assert.assertNotNull(authFactors);
        Assert.assertTrue(authFactors.isEmpty());
    }

    @Test
    public void getAuthFactors_withValidAcr() throws IdPException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.getAuthFactors(new String[] {"level3"});

        Assert.assertNotNull(authFactors);
        Assert.assertTrue(authFactors.size() == 2);

        List<AuthenticationFactor> five_fingers = authFactors.get(0);
        Assert.assertNotNull(five_fingers);
        Assert.assertTrue(five_fingers.size() == 1);
        Assert.assertTrue(five_fingers.get(0).getName().equals("fpt"));
        Assert.assertTrue(five_fingers.get(0).getCount() == 4);
        Assert.assertNotNull(five_fingers.get(0).getBioSubTypes());
        Assert.assertNotNull(five_fingers.get(0).getBioSubTypes().size() == 4);

        List<AuthenticationFactor> otp_one_finger = authFactors.get(1);
        Assert.assertNotNull(otp_one_finger);
        Assert.assertTrue(otp_one_finger.size() == 2);
        Assert.assertTrue(otp_one_finger.get(0).getName().equals("otp"));
        Assert.assertTrue(otp_one_finger.get(1).getName().equals("fpt"));
        Assert.assertTrue(otp_one_finger.get(1).getCount() == 1);
        Assert.assertNotNull(otp_one_finger.get(1).getBioSubTypes());
        Assert.assertNotNull(otp_one_finger.get(1).getBioSubTypes().size() == 1);
    }

    @Test
    public void getAuthFactors_withValidAcr_preserveOrderOfPrecedence() throws IdPException {
        List<List<AuthenticationFactor>> authFactors = authenticationContextClassRefUtil.getAuthFactors(new String[] {"level3", "level1"});

        Assert.assertNotNull(authFactors);
        Assert.assertTrue(authFactors.size() == 3);

        List<AuthenticationFactor> five_fingers = authFactors.get(0);
        Assert.assertNotNull(five_fingers);
        Assert.assertTrue(five_fingers.size() == 1);
        Assert.assertTrue(five_fingers.get(0).getName().equals("fpt"));
        Assert.assertTrue(five_fingers.get(0).getCount() == 4);
        Assert.assertNotNull(five_fingers.get(0).getBioSubTypes());
        Assert.assertNotNull(five_fingers.get(0).getBioSubTypes().size() == 4);

        List<AuthenticationFactor> otp_one_finger = authFactors.get(1);
        Assert.assertNotNull(otp_one_finger);
        Assert.assertTrue(otp_one_finger.size() == 2);
        Assert.assertTrue(otp_one_finger.get(0).getName().equals("otp"));
        Assert.assertTrue(otp_one_finger.get(1).getName().equals("fpt"));
        Assert.assertTrue(otp_one_finger.get(1).getCount() == 1);
        Assert.assertNotNull(otp_one_finger.get(1).getBioSubTypes());
        Assert.assertNotNull(otp_one_finger.get(1).getBioSubTypes().size() == 1);

        List<AuthenticationFactor> only_otp = authFactors.get(2);
        Assert.assertNotNull(only_otp);
        Assert.assertTrue(only_otp.size() == 1);
        Assert.assertTrue(only_otp.get(0).getName().equals("otp"));
    }

}
