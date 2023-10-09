/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.entity.ClientDetail;
import io.mosip.esignet.repository.ClientDetailRepository;
import io.mosip.esignet.services.ClientManagementServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import static io.mosip.esignet.core.constants.Constants.CLIENT_ACTIVE_STATUS;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ClientManagementServiceTest {

    @InjectMocks
    ClientManagementServiceImpl clientManagementService;

    @Mock
    ClientDetailRepository clientDetailRepository;

    @Mock
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    AuditPlugin auditWrapper;

    Map<String, Object> PUBLIC_KEY;

    @Before
    public void Before() {
        PUBLIC_KEY = generateJWK_RSA().toJSONObject();
    }

    @Test
    public void createClient_withValidDetail_thenPass() throws Exception {
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId("mock_id_v1");
        clientCreateReqDto.setClientName("client_name_v1");
        clientCreateReqDto.setLogoUri("http://service.com/logo.png");
        clientCreateReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateReqDto.setRedirectUris(Arrays.asList("http://service.com/home"));
        clientCreateReqDto.setUserClaims(Arrays.asList("given_name"));
        clientCreateReqDto.setAuthContextRefs(Arrays.asList("mosip:idp:acr:static-code"));
        clientCreateReqDto.setRelyingPartyId("RELYING_PARTY_ID");
        clientCreateReqDto.setGrantTypes(Arrays.asList("authorization_code"));
        clientCreateReqDto.setClientAuthMethods(Arrays.asList("private_key_jwt"));

        ClientDetail entity = new ClientDetail();
        entity.setId("mock_id_v1");
        entity.setStatus("active");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(entity);
        ClientDetailResponse clientDetailResponse = clientManagementService.createOIDCClient(clientCreateReqDto);
        Assert.assertNotNull(clientDetailResponse);
        Assert.assertTrue(clientDetailResponse.getClientId().equals("mock_id_v1"));
        Assert.assertTrue(clientDetailResponse.getStatus().equals("active"));
    }

    @Test
    public void createClient_withExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(new ClientDetail()));
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId("client_id_v1");
        try {
            clientManagementService.createOIDCClient(clientCreateReqDto);
        } catch (EsignetException ex) {
            Assert.assertEquals(ex.getErrorCode(), ErrorConstants.DUPLICATE_CLIENT_ID);
        }
    }

    @Test
    public void createClientV2_withValidDetail_thenPass() throws Exception {
        ClientDetailCreateRequestV2 clientCreateV2ReqDto = new ClientDetailCreateRequestV2();
        Map<String, String> clientnameLangMap = new HashMap<>();
        clientnameLangMap.put("eng", "client_name_v1");
        clientCreateV2ReqDto.setClientId("mock_id_v1");
        clientCreateV2ReqDto.setClientName("client_name_v1");
        clientCreateV2ReqDto.setClientNameLangMap(clientnameLangMap);
        clientCreateV2ReqDto.setLogoUri("http://service.com/logo.png");
        clientCreateV2ReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateV2ReqDto.setRedirectUris(Arrays.asList("http://service.com/home"));
        clientCreateV2ReqDto.setUserClaims(Arrays.asList("given_name"));
        clientCreateV2ReqDto.setAuthContextRefs(Arrays.asList("mosip:idp:acr:static-code"));
        clientCreateV2ReqDto.setRelyingPartyId("RELYING_PARTY_ID");
        clientCreateV2ReqDto.setGrantTypes(Arrays.asList("authorization_code"));
        clientCreateV2ReqDto.setClientAuthMethods(Arrays.asList("private_key_jwt"));

        ClientDetail entity = new ClientDetail();
        entity.setId("mock_id_v1");
        entity.setStatus("active");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(entity);
        ClientDetailResponse clientDetailResponse = clientManagementService.createOAuthClient(clientCreateV2ReqDto);
        Assert.assertNotNull(clientDetailResponse);
        Assert.assertTrue(clientDetailResponse.getClientId().equals("mock_id_v1"));
        Assert.assertTrue(clientDetailResponse.getStatus().equals("active"));
    }

    @Test
    public void createClientV2_withExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(new ClientDetail()));
        ClientDetailCreateRequestV2 clientCreateV2ReqDto = new ClientDetailCreateRequestV2();
        clientCreateV2ReqDto.setClientId("client_id_v1");
        try {
            clientManagementService.createOAuthClient(clientCreateV2ReqDto);
        } catch (EsignetException ex) {
            Assert.assertEquals(ex.getErrorCode(), ErrorConstants.DUPLICATE_CLIENT_ID);
        }
    }

    @Test
    public void updateClient_withNonExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.empty());
        try {
            clientManagementService.updateOIDCClient("client_id_v1", null);
        } catch (EsignetException ex) {
            Assert.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_CLIENT_ID);
        }
    }

    @Test
    public void updateClient_withValidClientId_thenPass() throws EsignetException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName("client_id_v1");
        clientDetail.setId("client_id_v1");
        clientDetail.setLogoUri("http://service.com/logo.png");
        clientDetail.setClaims("[\"given_name\", \"birthdate\"]");
        clientDetail.setAcrValues("[\"mosip:idp:acr:static-code\"]");
        clientDetail.setClientAuthMethods("[\"private_key_jwt\"]");
        clientDetail.setGrantTypes("[\"authorization_code\"]");
        clientDetail.setRedirectUris("[\"https://service.com/home\",\"https://service.com/dashboard\", \"v1/idp\"]");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailUpdateRequest updateRequest = new ClientDetailUpdateRequest();
        updateRequest.setClientName("client_name_v1");
        updateRequest.setLogoUri("http://service.com/logo.png");
        updateRequest.setRedirectUris(Arrays.asList("http://service.com/home"));
        updateRequest.setUserClaims(Arrays.asList("given_name"));
        updateRequest.setAuthContextRefs(Arrays.asList("mosip:idp:acr:static-code"));
        updateRequest.setGrantTypes(Arrays.asList("authorization_code"));
        updateRequest.setClientAuthMethods(Arrays.asList("private_key_jwt"));

        ClientDetail entity = new ClientDetail();
        entity.setId("client_id_v1");
        entity.setStatus("inactive");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(entity);
        ClientDetailResponse clientDetailResponse = clientManagementService.updateOIDCClient("client_id_v1", updateRequest);
        Assert.assertNotNull(clientDetailResponse);
        Assert.assertTrue(clientDetailResponse.getClientId().equals("client_id_v1"));
        Assert.assertTrue(clientDetailResponse.getStatus().equals("inactive"));
    }

    @Test
    public void updateClientV2_withNonExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.empty());
        try {
            clientManagementService.updateOAuthClient("client_id_v1", null);
        } catch (EsignetException ex) {
            Assert.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_CLIENT_ID);
        }
    }

    @Test
    public void updateClientV2_withValidClientId_thenPass() throws EsignetException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName("client_id_v1");
        clientDetail.setId("client_id_v1");
        clientDetail.setLogoUri("http://service.com/logo.png");
        clientDetail.setClaims("[\"given_name\", \"birthdate\"]");
        clientDetail.setAcrValues("[\"mosip:idp:acr:static-code\"]");
        clientDetail.setClientAuthMethods("[\"private_key_jwt\"]");
        clientDetail.setGrantTypes("[\"authorization_code\"]");
        clientDetail.setRedirectUris("[\"https://service.com/home\",\"https://service.com/dashboard\", \"v1/idp\"]");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailUpdateRequestV2 updateV2Request = new ClientDetailUpdateRequestV2();
        updateV2Request.setClientNameLangMap(new HashMap<>());
        updateV2Request.setClientName("client_name_v1");
        updateV2Request.setLogoUri("http://service.com/logo.png");
        updateV2Request.setRedirectUris(Arrays.asList("http://service.com/home"));
        updateV2Request.setUserClaims(Arrays.asList("given_name"));
        updateV2Request.setAuthContextRefs(Arrays.asList("mosip:idp:acr:static-code"));
        updateV2Request.setGrantTypes(Arrays.asList("authorization_code"));
        updateV2Request.setClientAuthMethods(Arrays.asList("private_key_jwt"));

        ClientDetail entity = new ClientDetail();
        entity.setId("client_id_v1");
        entity.setStatus("inactive");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(entity);
        ClientDetailResponse clientDetailResponse = clientManagementService.updateOAuthClient("client_id_v1", updateV2Request);
        Assert.assertNotNull(clientDetailResponse);
        Assert.assertTrue(clientDetailResponse.getClientId().equals("client_id_v1"));
        Assert.assertTrue(clientDetailResponse.getStatus().equals("inactive"));
    }

    @Test
    public void getClient_withValidClientId_thenPass() throws EsignetException {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setName("client_id_v1");
        clientDetail.setId("client_id_v1");
        clientDetail.setLogoUri("http://service.com/logo.png");
        clientDetail.setClaims("[\"given_name\", \"birthdate\"]");
        clientDetail.setAcrValues("[\"mosip:idp:acr:static-code\"]");
        clientDetail.setClientAuthMethods("[\"private_key_jwt\"]");
        clientDetail.setGrantTypes("[\"authorization_code\"]");
        clientDetail.setRedirectUris("[\"https://service.com/home\",\"https://service.com/dashboard\", \"v1/idp\"]");

        Mockito.when(clientDetailRepository.findByIdAndStatus("client_id_v1", CLIENT_ACTIVE_STATUS))
                .thenReturn(Optional.of(clientDetail));

        io.mosip.esignet.core.dto.ClientDetail dto = clientManagementService.getClientDetails("client_id_v1");
        Assert.assertNotNull(dto);
    }

    @Test
    public void getClient_withInvalidClientId_thenFail() throws EsignetException {
        Mockito.when(clientDetailRepository.findByIdAndStatus("client_id_v1", CLIENT_ACTIVE_STATUS))
                .thenReturn(Optional.empty());

        try {
            clientManagementService.getClientDetails("client_id_v1");
        } catch (EsignetException ex) {
            Assert.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_CLIENT_ID);
        }
    }

    public static JWK generateJWK_RSA() {
        // Generate the RSA key pair
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();
            // Convert public key to JWK format
            return new RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
                    .privateKey((RSAPrivateKey)keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            log.error("generateJWK_RSA failed", e);
        }
        return null;
    }

}