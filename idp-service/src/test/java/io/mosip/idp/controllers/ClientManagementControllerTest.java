/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;

@SpringBootTest
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
public class ClientManagementControllerTest {

    //region Variables
    String OIDC_CLIENT_URI = "/client-mgmt/oidc-client";
    String commaSeparatedURIs = "https://clientapp.com/home,https://clientapp.com/home2";
    String commaSeparatedAMRs = "https://clientapp.com/home,https://clientapp.com/home2";
    String commaSeparatedClaims = "https://clientapp.com/home,https://clientapp.com/home2";
    String CLIENT_ID_1 = "C01";
    String CLIENT_NAME_1 = "Client-01";
    String CLIENT_NAME_2 = "Client-02";
    String LOGO_URI = "https://clienapp.com/logo.png";
    String INVALID_LOGO_URI = "httpsasdffas";
    String PUBLIC_KEY;

    String INVALID_PUBLIC_KEY = "INVALID PUBLIC KEY";
    List<String> LIST_OF_URIS = Arrays.asList(commaSeparatedURIs.split(","));
    List<String> CLAIMS = Arrays.asList(commaSeparatedClaims.split(","));
    List<String> AMRS = Arrays.asList(commaSeparatedAMRs.split(","));
    List<String> LIST_WITH_BLANK_STRING = Arrays.asList("".split(","));
    List<String> LIST_WITH_NULL_STRING;
    List<String> GRAND_TYPES = List.of("authorization_code");
    List<String> EMPTY_LIST = new ArrayList<>();
    String STATUS_ACTIVE = "active";
    String STATUS_INACTIVE = "inactive";
    String STATUS_INVALID = "INVALID_STATUS";
    String RELAYING_PARTY_ID = "RP01";
    String BLANK = "";

    ClientDetailCreateRequest defaultClientDetailCreateRequest;

    //endregion

    ObjectMapper objectMapper = new ObjectMapper();
    protected MockMvc mvc;
    @Autowired
    WebApplicationContext webApplicationContext;
    @Autowired
    ClientDetailRepository clientDetailRepository;

    @Before
    public void setUp() throws NoSuchAlgorithmException {
        mvc = MockMvcBuilders.webAppContextSetup(webApplicationContext).build();

        LIST_WITH_NULL_STRING = new ArrayList<>();
        LIST_WITH_NULL_STRING.add(null);

        PUBLIC_KEY = generateBase64PublicKeyRSAString();

        defaultClientDetailCreateRequest = new ClientDetailCreateRequest();
        defaultClientDetailCreateRequest.setClientId(CLIENT_ID_1);
        defaultClientDetailCreateRequest.setClientName(CLIENT_NAME_1);
        defaultClientDetailCreateRequest.setLogoUri(LOGO_URI);
        defaultClientDetailCreateRequest.setPublicKey(PUBLIC_KEY);
        defaultClientDetailCreateRequest.setRedirectUris(LIST_OF_URIS);
        defaultClientDetailCreateRequest.setUserClaims(CLAIMS);
        defaultClientDetailCreateRequest.setAuthContextRefs(AMRS);
        defaultClientDetailCreateRequest.setStatus(STATUS_ACTIVE);
        defaultClientDetailCreateRequest.setRelayingPartyId(RELAYING_PARTY_ID);
        defaultClientDetailCreateRequest.setGrantTypes(GRAND_TYPES);
    }

    private String generateBase64PublicKeyRSAString() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("RSA");
        keyGen.initialize(512);
        byte[] publicKey = keyGen.genKeyPair().getPublic().getEncoded();
        return Base64.getEncoder().encodeToString(publicKey);
    }

    @After
    public void clear() {
        clientDetailRepository.deleteAll();
    }

    //region Create Client Test

    @Test
    public void createClient_withValidDetail_thenPass() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
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
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
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
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withBlankClientName_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(BLANK);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withBlankLogoURI_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(BLANK);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withInvalidLogoURI_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(INVALID_LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must be a valid URL"));
    }

    @Test
    public void createClientDetail_withBlankPublicKey_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(BLANK);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
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
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        ResponseWrapper<ClientDetailResponse> createRespDto = createClient(clientCreateReqDto);

        Assert.assertNull(createRespDto.getResponse());
        Assert.assertNotNull(createRespDto.getErrors());
        Assert.assertTrue(createRespDto.getErrors().size() > 0);
        Assert.assertEquals(createRespDto.getErrors().get(0).getErrorCode(), ErrorConstants.INVALID_BASE64_RSA_PUBLIC_KEY);
    }

    @Test
    public void createClientDetail_withBlankClaims_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(EMPTY_LIST);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorCode().contains("size must be between 1 and"));
    }

    @Test
    public void createClientDetail_wittNullACR_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
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
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(EMPTY_LIST);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorCode().contains("size must be between 1 and"));
    }

    @Test
    public void createClientDetail_withAMRBlankString_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(LIST_WITH_BLANK_STRING);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withAMRNULLString_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(LIST_WITH_NULL_STRING);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withBlankRP_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(BLANK);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
    }

    @Test
    public void createClientDetail_withBlankStatus_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(BLANK);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().stream().anyMatch(x -> x.getErrorMessage().contains("must match \"^(ACTIVE)|(INACTIVE)$\"")));
    }

    @Test
    public void createClientDetail_withInvalidStatus_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_INVALID);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must match \"^(ACTIVE)|(INACTIVE)$\""));
    }

    @Test
    public void createClientDetail_withBlankRedirectUri_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(EMPTY_LIST);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("size must be between 1 and 2147483647"));
    }

    @Test
    public void createClientDetail_withRedirectUriBLANK_ENTRY_thenFail() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_WITH_BLANK_STRING);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(AMRS);
        clientCreateReqDto.setStatus(STATUS_ACTIVE);
        clientCreateReqDto.setRelayingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);

        var respDto = createClient(clientCreateReqDto);

        Assert.assertNull(respDto.getResponse());
        Assert.assertNotNull(respDto.getErrors());
        Assert.assertTrue(respDto.getErrors().size() > 0);
        Assert.assertTrue(respDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
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
        clientUpdateReqDto.setAuthContextRefs(AMRS);
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
        clientUpdateReqDto.setAuthContextRefs(AMRS);
        clientUpdateReqDto.setStatus(STATUS_INACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(BLANK);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertTrue(updateRespDto.getErrors().size() > 0);
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
        clientUpdateReqDto.setAuthContextRefs(AMRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertTrue(updateRespDto.getErrors().size() > 0);
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
        clientUpdateReqDto.setAuthContextRefs(AMRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertTrue(updateRespDto.getErrors().size() > 0);
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
        clientUpdateReqDto.setAuthContextRefs(AMRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertTrue(updateRespDto.getErrors().size() > 0);
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
        Assert.assertTrue(updateRespDto.getErrors().size() > 0);
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
        Assert.assertTrue(updateRespDto.getErrors().size() > 0);
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
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
        clientUpdateReqDto.setAuthContextRefs(AMRS);
        clientUpdateReqDto.setStatus(BLANK);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertTrue(updateRespDto.getErrors().size() > 0);
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
        clientUpdateReqDto.setAuthContextRefs(AMRS);
        clientUpdateReqDto.setStatus(STATUS_INVALID);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertTrue(updateRespDto.getErrors().size() > 0);
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
        clientUpdateReqDto.setAuthContextRefs(AMRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertTrue(updateRespDto.getErrors().size() > 0);
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
        clientUpdateReqDto.setAuthContextRefs(AMRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);

        var updateRespDto = updateClient(createRespDto.getResponse().getClientId(), clientUpdateReqDto);

        Assert.assertNull(updateRespDto.getResponse());
        Assert.assertNotNull(updateRespDto.getErrors());
        Assert.assertTrue(updateRespDto.getErrors().size() > 0);
        Assert.assertTrue(updateRespDto.getErrors().get(0).getErrorMessage().contains("must not be blank"));
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
