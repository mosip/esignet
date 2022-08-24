/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.TestUtil;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.repositories.ClientDetailRepository;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;

@SpringBootTest
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
public class ClientManagementControllerTest {

    //region Variables
    String OIDC_CLIENT_URI = "/client-mgmt/oidc-client";
    String commaSeparatedURIs = "https://clientapp.com/home,https://clientapp.com/home2";
    String commaSeparatedACRs = "level1,level2";
    String commaSeparatedClaims = "phone_verified,email_verified";
    String grand_type = "authorization_code";
    String commaSeparatedACRs_invalid = "invalidACR1,level1";
    String grand_type_invalid = "invalidGrandType";

    String CLIENT_ID_1 = "C01";
    String CLIENT_NAME_1 = "Client-01";
    String CLIENT_NAME_2 = "Client-02";
    String LOGO_URI = "https://clienapp.com/logo.png";
    String INVALID_LOGO_URI = "httpsasdffas";
    Map<String, Object> PUBLIC_KEY_RSA;
    Map<String, Object> PUBLIC_KEY_EC;
    Map<String, Object> PUBLIC_PRIVATE_KEY_PAIR_RSA;
    Map<String, Object> PUBLIC_PRIVATE_KEY_PAIR_EC;

    Map<String, Object> INVALID_PUBLIC_KEY;

    List<String> LIST_OF_URIS = Arrays.asList(commaSeparatedURIs.split(","));
    List<String> CLAIMS = Arrays.asList(commaSeparatedClaims.split(","));
    List<String> ACRS = Arrays.asList(commaSeparatedACRs.split(","));
    List<String> GRAND_TYPES = List.of(grand_type);

    List<String> ACRS_INVALID = Arrays.asList(commaSeparatedACRs_invalid.split(","));
    List<String> GRAND_TYPES_INVALID = List.of(grand_type_invalid);

    List<String> LIST_WITH_BLANK_STRING = Arrays.asList("".split(","));
    List<String> LIST_WITH_NULL_STRING;
    List<String> EMPTY_LIST = new ArrayList<>();
    String STATUS_ACTIVE = "active";
    String STATUS_INACTIVE = "inactive";
    String STATUS_INVALID = "INVALID_STATUS";
    String RELAYING_PARTY_ID = "RP01";
    String BLANK = "";
    Map<String, Object> BLANK_MAP = new HashMap<>();
    ClientDetailCreateRequest defaultClientDetailCreateRequest;

    //endregion

    ObjectMapper objectMapper = new ObjectMapper();
    protected MockMvc mvc;
    @Autowired
    WebApplicationContext webApplicationContext;
    @Autowired
    ClientDetailRepository clientDetailRepository;

    @Before
    public void setUp() throws NoSuchAlgorithmException, InvalidAlgorithmParameterException {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        LIST_WITH_NULL_STRING = new ArrayList<>();
        LIST_WITH_NULL_STRING.add(null);

        PUBLIC_KEY_RSA = TestUtil.generateJWK_RSA().toPublicJWK().toJSONObject();
        PUBLIC_PRIVATE_KEY_PAIR_RSA = TestUtil.generateJWK_RSA().toJSONObject();

        PUBLIC_KEY_EC = TestUtil.generateJWK_EC().toPublicJWK().toJSONObject();
        PUBLIC_PRIVATE_KEY_PAIR_EC = TestUtil.generateJWK_EC().toJSONObject();

        INVALID_PUBLIC_KEY = new HashMap<>();
        INVALID_PUBLIC_KEY.put("kty", "RSA_");//invalid alg
        INVALID_PUBLIC_KEY.put("e", "AQAB");
        INVALID_PUBLIC_KEY.put("use", "sig");
        INVALID_PUBLIC_KEY.put("kid", "c5SG4KHUxbwTbKwNVQQy_iMv3B3mvtWlJuMDD40cqKw");
        INVALID_PUBLIC_KEY.put("alg", "RS256");
        INVALID_PUBLIC_KEY.put("n", "oSh0UB-1TQtwy5iy4twWxBGu0wW-GLUn-3pOTa-W0I1KZ-HBiF20UDtBQQeJKiVGNL3dwv7DY9TlYXfj3481tksAeRMIKUbRtzWlO5XlilskvTIFFOCuZQEWbieg3LonZ0HdJ_UVBy0XFWZTzuTrnCYNe1-D_k7eNxsYIGbo3M_IhWagoN0L9i_4FLZqsZ6mxobrahHILT-36WcH70NCFV2b9dmtum9b6OIgVlD-ok2_XK_1JsBE2AAwAx_DPihkhzPoyiChB9FkoBc0f9TlnQ4HhxE_Y3rwoKNVHCmyn53JbFZXqoXUCNS-7CTqEJPmcKgwlbHJ44X8tsB23Vlfaw");

        defaultClientDetailCreateRequest = new ClientDetailCreateRequest();
        defaultClientDetailCreateRequest.setClientId(CLIENT_ID_1);
        defaultClientDetailCreateRequest.setClientName(CLIENT_NAME_1);
        defaultClientDetailCreateRequest.setLogoUri(LOGO_URI);
        defaultClientDetailCreateRequest.setPublicKey(PUBLIC_KEY_RSA);
        defaultClientDetailCreateRequest.setRedirectUris(LIST_OF_URIS);
        defaultClientDetailCreateRequest.setUserClaims(CLAIMS);
        defaultClientDetailCreateRequest.setAuthContextRefs(ACRS);
        defaultClientDetailCreateRequest.setStatus(STATUS_ACTIVE);
        defaultClientDetailCreateRequest.setRelayingPartyId(RELAYING_PARTY_ID);
        defaultClientDetailCreateRequest.setGrantTypes(GRAND_TYPES);
    }

    @After
    public void clear() {
        clientDetailRepository.deleteAll();
    }

    //region Create Client Test

    @Test
    public void createClient_withValidDetail_PublicRsaKey_thenPass() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        ResponseWrapper<ClientDetailResponse> createRespDto = createClient(clientCreateReqDto);
        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        ClientDetailResponse respDto = createRespDto.getResponse();

        Assert.assertEquals(respDto.getClientId(), CLIENT_ID_1);
        Assert.assertEquals(respDto.getStatus(), STATUS_ACTIVE);
    }

    @Test
    public void createClient_withPublicPrivateRsaKeyPair_thenPass() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_PRIVATE_KEY_PAIR_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        ResponseWrapper<ClientDetailResponse> createRespDto = createClient(clientCreateReqDto);
        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        ClientDetailResponse respDto = createRespDto.getResponse();

        Assert.assertEquals(respDto.getClientId(), CLIENT_ID_1);
        Assert.assertEquals(respDto.getStatus(), STATUS_ACTIVE);
    }

    @Test
    public void createClient_withValidDetail_PublicECKey_thenPass() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_EC);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        ResponseWrapper<ClientDetailResponse> createRespDto = createClient(clientCreateReqDto);
        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        ClientDetailResponse respDto = createRespDto.getResponse();

        Assert.assertEquals(respDto.getClientId(), CLIENT_ID_1);
        Assert.assertEquals(respDto.getStatus(), STATUS_ACTIVE);
    }

    @Test
    public void createClient_withPublicPrivateECKeyPair_thenPass() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_PRIVATE_KEY_PAIR_EC);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        ResponseWrapper<ClientDetailResponse> createRespDto = createClient(clientCreateReqDto);
        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        ClientDetailResponse respDto = createRespDto.getResponse();

        Assert.assertEquals(respDto.getClientId(), CLIENT_ID_1);
        Assert.assertEquals(respDto.getStatus(), STATUS_ACTIVE);
    }


    @Test
    public void createClientDetail_withInactiveStatus_thenPass() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_INACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        ResponseWrapper<ClientDetailResponse> createRespDto = createClient(clientCreateReqDto);
        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        ClientDetailResponse respDto = createRespDto.getResponse();

        Assert.assertEquals(respDto.getClientId(), CLIENT_ID_1);
        Assert.assertEquals(respDto.getStatus(), STATUS_INACTIVE);
    }


    @Test
    public void createClientDetail_withBlankClientId_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(BLANK);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withBlankClientName_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(BLANK);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withBlankLogoURI_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(BLANK);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withInvalidLogoURI_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(INVALID_LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must be a valid URL"));
    }

    @Test
    public void createClientDetail_withBlankPublicKey_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(BLANK_MAP);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorCode().contains("size must be between 1 and"));
    }

    @Test
    public void createClientDetail_withNullPublicKey_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(null);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be null"));
    }

    @Test
    public void createClientDetail_withInvalidPublicKey_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(INVALID_PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        ResponseWrapper<ClientDetailResponse> respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertEquals(respDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_JWK_KEY);
    }

    @Test
    public void createClientDetail_withPrivateKey_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(INVALID_PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        ResponseWrapper<ClientDetailResponse> respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertEquals(respDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_JWK_KEY);
    }

    @Test
    public void createClientDetail_withBlankClaims_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(EMPTY_LIST);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorCode().contains("size must be between 1 and"));
    }

    @Test
    public void createClientDetail_withNullACR_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(null);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be null"));
    }

    @Test
    public void createClientDetail_withBlankAMR_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(EMPTY_LIST);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorCode().contains("size must be between 1 and"));
    }

    @Test
    public void createClientDetail_withAMRBlankString_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(LIST_WITH_BLANK_STRING);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertEquals(respDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_ACR);
    }

    @Test
    public void createClientDetail_withAMRNULLString_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(LIST_WITH_NULL_STRING);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be null"));
    }

    @Test
    public void createClientDetail_withInvalidAMRString_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS_INVALID);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertEquals(respDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_ACR);
    }

    @Test
    public void createClientDetail_withBlankRP_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(BLANK);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withBlankStatus_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(BLANK);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(2, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().stream().anyMatch(x -> x.getErrorMessage().contains("must match \"^(ACTIVE)|(INACTIVE)$\"")));
    }

    @Test
    public void createClientDetail_withInvalidStatus_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_INVALID);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must match \"^(ACTIVE)|(INACTIVE)$\""));
    }

    @Test
    public void createClientDetail_withBlankRedirectUri_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(EMPTY_LIST);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("size must be between 1 and 2147483647"));
    }

    @Test
    public void createClientDetail_withRedirectUriBLANK_ENTRY_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_WITH_BLANK_STRING);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withRedirectUriNull_ENTRY_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_WITH_NULL_STRING);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withInvalidGrandType_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES_INVALID);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertEquals(respDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_GRANT_TYPE);
    }

    @Test
    public void createClientDetail_withGrandTypeNullEntry_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(LIST_WITH_NULL_STRING);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertEquals(respDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_GRANT_TYPE);
    }

    @Test
    public void createClientDetail_withGrandTypeBlankEntry_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY_RSA);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(LIST_WITH_BLANK_STRING);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertEquals(1, respDto.getErrors().size());
        Assert.assertEquals(respDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_GRANT_TYPE);
    }

    //endregion

    //region Update Client Test

    @Test
    public void updateClient_withValidDetail_thenPass() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNotNull(updateRespDto.getResponse());
        Assert.assertNull(updateRespDto.getErrors());
        Assert.assertEquals(updateRespDto.getResponse().getClientId(), CLIENT_ID_1);
    }

    @Test
    public void updateClientDetail_withBlankClientName_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_INACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(BLANK);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void updateClientDetail_withBlankLogoURI_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(BLANK);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void updateClientDetail_withInvalidLogoURI_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(INVALID_LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorMessage().contains("must be a valid URL"));
    }

    @Test
    public void updateClientDetail_withBlankClaims_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(EMPTY_LIST);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorCode().contains("size must be between 1 and"));
    }

    @Test
    public void updateClientDetail_withBlankAMR_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(EMPTY_LIST);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorMessage().contains("size must be between 1 and 2147483647"));
    }

    @Test
    public void updateClientDetail_withAMRBlankEntry_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(LIST_WITH_BLANK_STRING);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertEquals(updateRespDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_ACR);
    }

    @Test
    public void updateClientDetail_withInvalidACR_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS_INVALID);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertEquals(updateRespDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_ACR);
    }

    @Test
    public void updateClientDetail_withBlankStatus_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(BLANK);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(2, updateRespDto.getErrors().size());
        Assert.assertTrue(updateRespDto.getErrors().stream().anyMatch(x -> x.getErrorMessage().contains("must match \"^(ACTIVE)|(INACTIVE)$\"")));
    }

    @Test
    public void updateClientDetail_withInvalidStatus_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_INVALID);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorMessage().contains("must match \"^(ACTIVE)|(INACTIVE)$\""));
    }

    @Test
    public void updateClientDetail_withBlankRedirectUri_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(EMPTY_LIST);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorMessage().contains("size must be between 1 and 2147483647"));
    }

    @Test
    public void updateClientDetail_withRedirectUriBlankEntry_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_WITH_BLANK_STRING);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void updateClientDetail_withRedirectUriNullEntry_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_WITH_NULL_STRING);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void updateClientDetail_withInvalidGrandType_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES_INVALID);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertEquals(updateRespDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_GRANT_TYPE);
    }

    @Test
    public void updateClientDetail_withGrandTypeNullEntry_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(LIST_WITH_NULL_STRING);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertEquals(updateRespDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_GRANT_TYPE);
    }

    @Test
    public void updateClientDetail_withGrandTypeBlankEntry_thenFail() throws Exception {
        //Create
        var createRespDto = createClient(defaultClientDetailCreateRequest);

        Assert.assertNotNull(createRespDto.getResponse());
        Assert.assertNull(createRespDto.getErrors());

        //update
        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(LIST_WITH_BLANK_STRING);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertEquals(1, updateRespDto.getErrors().size());
        Assert.assertEquals(updateRespDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_GRANT_TYPE);
    }

    //endregion

    //region private methods
    private ResponseWrapper<ClientDetailResponse> createClient(ClientDetailCreateRequest clientCreateReqDto) throws Exception {
        String createRequestJson = buildRequest(clientCreateReqDto);

        MvcResult mvcCreateResult = mvc.perform(MockMvcRequestBuilders.post(OIDC_CLIENT_URI)
                .contentType(MediaType.APPLICATION_JSON_VALUE).content(createRequestJson)).andReturn();

        int createStatus = mvcCreateResult.getResponse().getStatus();
        Assert.assertEquals(200, createStatus);

        return getResponse(mvcCreateResult.getResponse().getContentAsString());
    }

    private ResponseWrapper<ClientDetailResponse> updateClient(String clientId, ClientDetailUpdateRequest clientUpdateReqDto) throws Exception {
        String updateRequestJson = buildRequest(clientUpdateReqDto);

        MvcResult mvcUpdateResult = mvc.perform(MockMvcRequestBuilders.put(OIDC_CLIENT_URI + "/" + clientId)
                .contentType(MediaType.APPLICATION_JSON_VALUE).content(updateRequestJson)).andReturn();

        int updateStatus = mvcUpdateResult.getResponse().getStatus();
        Assert.assertEquals(200, updateStatus);

        return getResponse(mvcUpdateResult.getResponse().getContentAsString());
    }

    private <T> String buildRequest(T request) throws JsonProcessingException {

        String requestTime = ZonedDateTime
                .now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN));

        RequestWrapper<T> requestWrapper = new RequestWrapper<>();
        requestWrapper.setId("string");
        requestWrapper.setVersion("string");
        requestWrapper.setRequest(request);
        requestWrapper.setRequestTime(requestTime);

        return objectMapper.writeValueAsString(requestWrapper);
    }

    private ResponseWrapper<ClientDetailResponse> getResponse(String responseContent) throws IOException {
        return objectMapper.readValue(responseContent, new TypeReference<>() {
        });
    }

    //endregion
}
