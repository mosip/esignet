/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.TestUtil;
import io.mosip.idp.core.dto.ClientDetailCreateRequest;
import io.mosip.idp.core.dto.ClientDetailResponse;
import io.mosip.idp.core.dto.ClientDetailUpdateRequest;
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.entity.ClientDetail;
import io.mosip.idp.repository.ClientDetailRepository;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.junit4.SpringRunner;

import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@SpringBootTest
@ActiveProfiles("test")
@RunWith(SpringRunner.class)
public class ClientManagementServiceTest {

    @Autowired
    ClientManagementService clientDetailService;

    @Autowired
    ClientDetailRepository clientDetailRepository;

    //region Variables

    String commaSeparatedURIs = "https://clientapp.com/home,https://clientapp.com/home2";
    String commaSeparatedACRs = "level1,level2";
    String commaSeparatedClaims = "phone_verified,email_verified";

    String CLIENT_ID_1 = "C01";
    String CLIENT_ID_2 = "C02";
    String CLIENT_NAME_1 = "Client-01";
    String CLIENT_NAME_2 = "Client-02";
    String LOGO_URI = "https://clienapp.com/logo.png";
    Map<String, Object> PUBLIC_KEY;
    List<String> LIST_OF_URIS = Arrays.asList(commaSeparatedURIs.split(","));
    List<String> CLAIMS = Arrays.asList(commaSeparatedClaims.split(","));
    List<String> ACRS = Arrays.asList(commaSeparatedACRs.split(","));
    List<String> GRAND_TYPES = List.of("authorization_code");
    List<String> CLIENT_AUTH_METHODS = Arrays.asList(commaSeparatedACRs.split(","));
    String STATUS_ACTIVE = "ACTIVE";
    String STATUS_INACTIVE = "INACTIVE";
    String RELAYING_PARTY_ID = "RP01";

    //endregion

    @Before
    public void Before() throws NoSuchAlgorithmException {
        clientDetailRepository.deleteAll();
        PUBLIC_KEY = TestUtil.generateJWK_RSA().toJSONObject();
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
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setRelyingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);
        clientCreateReqDto.setClientAuthMethods(CLIENT_AUTH_METHODS);

        ClientDetailResponse respDto = clientDetailService.createOIDCClient(clientCreateReqDto);

        Assert.assertNotNull(respDto);

        Optional<ClientDetail> result = clientDetailRepository.findById(CLIENT_ID_1);
        Assert.assertTrue(result.isPresent());

        result = clientDetailRepository.findByIdAndStatus(CLIENT_ID_1, STATUS_ACTIVE);
        Assert.assertTrue(result.isPresent());
    }

    @Test
    public void createClientDetail_withDuplicateClientId_thenFail() {

        ClientDetailCreateRequest clientCreateReqDto1 = new ClientDetailCreateRequest();
        clientCreateReqDto1.setClientId(CLIENT_ID_1);
        clientCreateReqDto1.setClientName(CLIENT_NAME_1);
        clientCreateReqDto1.setLogoUri(LOGO_URI);
        clientCreateReqDto1.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto1.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto1.setUserClaims(CLAIMS);
        clientCreateReqDto1.setAuthContextRefs(ACRS);
        clientCreateReqDto1.setRelyingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto1.setGrantTypes(GRAND_TYPES);
        clientCreateReqDto1.setClientAuthMethods(CLIENT_AUTH_METHODS);

        ClientDetailResponse respDto1 = null;
        try {
            respDto1 = clientDetailService.createOIDCClient(clientCreateReqDto1);
        } catch (Exception e) {
            Assert.fail();
        }

        Assert.assertNotNull(respDto1);

        ClientDetailCreateRequest clientCreateReqDto2 = new ClientDetailCreateRequest();
        clientCreateReqDto2.setClientId(CLIENT_ID_1);
        clientCreateReqDto2.setClientName(CLIENT_NAME_1);
        clientCreateReqDto2.setLogoUri(LOGO_URI);
        clientCreateReqDto2.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto2.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto2.setUserClaims(CLAIMS);
        clientCreateReqDto2.setAuthContextRefs(ACRS);
        clientCreateReqDto2.setRelyingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto2.setGrantTypes(GRAND_TYPES);
        clientCreateReqDto2.setClientAuthMethods(CLIENT_AUTH_METHODS);

        ClientDetailResponse respDto2 = null;
        try {
            respDto2 = clientDetailService.createOIDCClient(clientCreateReqDto2);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), ErrorConstants.DUPLICATE_CLIENT_ID);
        }

        Assert.assertNull(respDto2);
    }

    //endregion

    //region Update Client Test

    @Test
    public void updateClient_withValidDetail_thenPass() throws Exception {

        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setRelyingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);
        clientCreateReqDto.setClientAuthMethods(CLIENT_AUTH_METHODS);

        clientDetailService.createOIDCClient(clientCreateReqDto);

        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientUpdateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientAuthMethods(CLIENT_AUTH_METHODS);

        ClientDetailResponse respDto = clientDetailService.updateOIDCClient(CLIENT_ID_1, clientUpdateReqDto);

        Assert.assertNotNull(respDto);

        Optional<ClientDetail> result = clientDetailRepository.findById(CLIENT_ID_1);
        Assert.assertTrue(result.isPresent());

        Assert.assertEquals(CLIENT_NAME_2, result.get().getName());
    }

    @Test
    public void updateClient_withInvalidClientId_thenPass() {

        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId(CLIENT_ID_1);
        clientCreateReqDto.setClientName(CLIENT_NAME_1);
        clientCreateReqDto.setLogoUri(LOGO_URI);
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(LIST_OF_URIS);
        clientCreateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientCreateReqDto.setRelyingPartyId(RELAYING_PARTY_ID);
        clientCreateReqDto.setGrantTypes(GRAND_TYPES);
        clientCreateReqDto.setClientAuthMethods(CLIENT_AUTH_METHODS);

        try {
            clientDetailService.createOIDCClient(clientCreateReqDto);
        } catch (Exception e) {
            Assert.fail();
        }

        ClientDetailUpdateRequest clientUpdateReqDto = new ClientDetailUpdateRequest();
        clientUpdateReqDto.setLogoUri(LOGO_URI);
        clientUpdateReqDto.setRedirectUris(LIST_OF_URIS);
        clientUpdateReqDto.setUserClaims(CLAIMS);
        clientCreateReqDto.setAuthContextRefs(ACRS);
        clientUpdateReqDto.setStatus(STATUS_ACTIVE);
        clientUpdateReqDto.setClientName(CLIENT_NAME_2);
        clientUpdateReqDto.setGrantTypes(GRAND_TYPES);
        clientUpdateReqDto.setClientAuthMethods(CLIENT_AUTH_METHODS);

        ClientDetailResponse respDto = null;
        try {
            respDto = clientDetailService.updateOIDCClient(CLIENT_ID_2, clientUpdateReqDto);
            Assert.fail();
        } catch (Exception e) {
            Assert.assertEquals(e.getMessage(), ErrorConstants.INVALID_CLIENT_ID);
        }

        Assert.assertNull(respDto);
    }

    //endregion

}