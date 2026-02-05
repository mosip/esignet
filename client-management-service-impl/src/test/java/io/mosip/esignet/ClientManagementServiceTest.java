/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;


import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.entity.ClientDetail;
import io.mosip.esignet.repository.ClientDetailRepository;
import io.mosip.esignet.services.ClientManagementServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.*;

import static io.mosip.esignet.core.constants.Constants.CLIENT_ACTIVE_STATUS;

@Slf4j
@ExtendWith(MockitoExtension.class)
public class ClientManagementServiceTest {

    @InjectMocks
    ClientManagementServiceImpl clientManagementService;

    @Mock
    ClientDetailRepository clientDetailRepository;

    @Spy
    ObjectMapper objectMapper = new ObjectMapper();

    @Mock
    AuditPlugin auditWrapper;

    @Mock
    IdentityProviderUtil identityProviderUtil;

    Map<String, Object> PUBLIC_KEY;
    Map<String, Object> ENC_PUBLIC_KEY;

    @BeforeEach
    public void Before() {
        PUBLIC_KEY = generateJWK_RSA().toJSONObject();
        ENC_PUBLIC_KEY = generateEncryptionJWK_RSA().toJSONObject();
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
        Assertions.assertNotNull(clientDetailResponse);
        Assertions.assertTrue(clientDetailResponse.getClientId().equals("mock_id_v1"));
        Assertions.assertTrue(clientDetailResponse.getStatus().equals("active"));
    }

    @Test
    public void createClient_withExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(new ClientDetail()));
        ClientDetailCreateRequest clientCreateReqDto = new ClientDetailCreateRequest();
        clientCreateReqDto.setClientId("client_id_v1");
        try {
            clientManagementService.createOIDCClient(clientCreateReqDto);
        } catch (EsignetException ex) {
            Assertions.assertEquals(ex.getErrorCode(), ErrorConstants.DUPLICATE_CLIENT_ID);
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
        Assertions.assertNotNull(clientDetailResponse);
        Assertions.assertTrue(clientDetailResponse.getClientId().equals("mock_id_v1"));
        Assertions.assertTrue(clientDetailResponse.getStatus().equals("active"));
    }

    @Test
    public void createClientV2_withExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.existsById("client_id_v1")).thenReturn(true);
        ClientDetailCreateRequestV2 clientCreateV2ReqDto = new ClientDetailCreateRequestV2();
        clientCreateV2ReqDto.setClientId("client_id_v1");
        try {
            clientManagementService.createOAuthClient(clientCreateV2ReqDto);
        } catch (EsignetException ex) {
            Assertions.assertEquals(ex.getErrorCode(), ErrorConstants.DUPLICATE_CLIENT_ID);
        }
    }

    @Test
    public void createClientV3_withValidDetail_thenPass() {
        ClientDetailCreateRequestV3 clientCreateV3ReqDto = new ClientDetailCreateRequestV3();
        Map<String, String> clientnameLangMap = new HashMap<>();
        clientnameLangMap.put("eng", "client_name_v1");
        clientCreateV3ReqDto.setClientId("mock_id_v1");
        clientCreateV3ReqDto.setClientName("client_name_v1");
        clientCreateV3ReqDto.setClientNameLangMap(clientnameLangMap);
        clientCreateV3ReqDto.setLogoUri("http://service.com/logo.png");
        clientCreateV3ReqDto.setPublicKey(PUBLIC_KEY);
        clientCreateV3ReqDto.setRedirectUris(Arrays.asList("http://service.com/home"));
        clientCreateV3ReqDto.setUserClaims(Arrays.asList("given_name"));
        clientCreateV3ReqDto.setAuthContextRefs(Arrays.asList("mosip:idp:acr:static-code"));
        clientCreateV3ReqDto.setRelyingPartyId("RELYING_PARTY_ID");
        clientCreateV3ReqDto.setGrantTypes(Arrays.asList("authorization_code"));
        clientCreateV3ReqDto.setClientAuthMethods(Arrays.asList("private_key_jwt"));
        clientCreateV3ReqDto.setAdditionalConfig(objectMapper.createObjectNode());

        ClientDetail entity = new ClientDetail();
        entity.setId("mock_id_v1");
        entity.setStatus("active");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(entity);
        ClientDetailResponseV2 clientDetailResponseV2 = clientManagementService.createClient(clientCreateV3ReqDto);
        Assertions.assertNotNull(clientDetailResponseV2);
        Assertions.assertTrue(clientDetailResponseV2.getClientId().equals("mock_id_v1"));
        Assertions.assertTrue(clientDetailResponseV2.getStatus().equals("active"));
    }

    @Test
    public void createClientV3_withExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.existsById("client_id_v1")).thenReturn(true);
        ClientDetailCreateRequestV3 clientCreateV3ReqDto = new ClientDetailCreateRequestV3();
        clientCreateV3ReqDto.setClientId("client_id_v1");
        try {
            clientManagementService.createClient(clientCreateV3ReqDto);
        } catch (EsignetException ex) {
            Assertions.assertEquals(ex.getErrorCode(), ErrorConstants.DUPLICATE_CLIENT_ID);
        }
    }

    @Test
    public void updateClient_withNonExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.empty());
        try {
            clientManagementService.updateOIDCClient("client_id_v1", null);
        } catch (EsignetException ex) {
            Assertions.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_CLIENT_ID);
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
        Assertions.assertNotNull(clientDetailResponse);
        Assertions.assertTrue(clientDetailResponse.getClientId().equals("client_id_v1"));
        Assertions.assertTrue(clientDetailResponse.getStatus().equals("inactive"));
    }

    @Test
    public void updateClientV2_withNonExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.empty());
        try {
            clientManagementService.updateOAuthClient("client_id_v1", null);
        } catch (EsignetException ex) {
            Assertions.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_CLIENT_ID);
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
        Assertions.assertNotNull(clientDetailResponse);
        Assertions.assertTrue(clientDetailResponse.getClientId().equals("client_id_v1"));
        Assertions.assertTrue(clientDetailResponse.getStatus().equals("inactive"));
    }

    @Test
    public void updateClientV3_withValidClientId_thenPass() throws EsignetException {
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

        ClientDetailUpdateRequestV3 updateV3Request = new ClientDetailUpdateRequestV3();
        updateV3Request.setClientNameLangMap(new HashMap<>());
        updateV3Request.setClientName("client_name_v1");
        updateV3Request.setLogoUri("http://service.com/logo.png");
        updateV3Request.setRedirectUris(Arrays.asList("http://service.com/home"));
        updateV3Request.setUserClaims(Arrays.asList("given_name"));
        updateV3Request.setAuthContextRefs(Arrays.asList("mosip:idp:acr:static-code"));
        updateV3Request.setGrantTypes(Arrays.asList("authorization_code"));
        updateV3Request.setClientAuthMethods(Arrays.asList("private_key_jwt"));
        updateV3Request.setAdditionalConfig(objectMapper.createObjectNode());

        ClientDetail entity = new ClientDetail();
        entity.setId("client_id_v1");
        entity.setStatus("inactive");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(entity);
        ClientDetailResponseV2 clientDetailResponseV2 = clientManagementService.updateClient("client_id_v1", updateV3Request);
        Assertions.assertNotNull(clientDetailResponseV2);
        Assertions.assertTrue(clientDetailResponseV2.getClientId().equals("client_id_v1"));
        Assertions.assertTrue(clientDetailResponseV2.getStatus().equals("inactive"));
    }

    @Test
    public void updateClientV3_withNonExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.empty());
        try {
            clientManagementService.updateClient("client_id_v1", null);
        } catch (EsignetException ex) {
            Assertions.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_CLIENT_ID);
        }
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
        Assertions.assertNotNull(dto);
    }

    @Test
    public void getClient_withInvalidClientId_thenFail() throws EsignetException {
        Mockito.when(clientDetailRepository.findByIdAndStatus("client_id_v1", CLIENT_ACTIVE_STATUS))
                .thenReturn(Optional.empty());

        try {
            clientManagementService.getClientDetails("client_id_v1");
        } catch (EsignetException ex) {
            Assertions.assertEquals(ex.getErrorCode(), ErrorConstants.INVALID_CLIENT_ID);
        }
    }

    @Test
    public void patchClient_withValidClientId_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setClientName("updated_client_name");
        patchRequest.setLogoUri("http://service.com/new_logo.png");
        patchRequest.setRedirectUris(Arrays.asList("http://service.com/callback"));
        patchRequest.setStatus("ACTIVE");

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
        Assertions.assertEquals("ACTIVE", response.getStatus());
    }

    @Test
    public void patchClient_withNonExistingClientId_thenFail() {
        Mockito.when(clientDetailRepository.findById("non_existing_client")).thenReturn(Optional.empty());

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setClientName("updated_name");

        try {
            clientManagementService.patchClient("non_existing_client", patchRequest);
            Assertions.fail();
        } catch (EsignetException ex) {
            Assertions.assertEquals(ErrorConstants.INVALID_CLIENT_ID, ex.getErrorCode());
        }
    }

    @Test
    public void patchClient_withEncPublicKey_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));
        Mockito.when(identityProviderUtil.computePublicKeyHash(Mockito.any())).thenReturn("mock_hash_value");

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setEncPublicKey(ENC_PUBLIC_KEY);

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        savedEntity.setEncPublicKey("{\"kty\":\"RSA\"}");
        savedEntity.setEncPublicKeyHash("mock_hash_value");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());

        Mockito.verify(identityProviderUtil, Mockito.times(1)).computePublicKeyHash(Mockito.any());
    }

    @Test
    public void patchClient_withEmptyEncPublicKey_thenFail() {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setEncPublicKey(new HashMap<>()); // Empty map - should throw exception

        EsignetException exception = Assertions.assertThrows(EsignetException.class, () ->
                clientManagementService.patchClient("client_id_v1", patchRequest)
        );

        Assertions.assertEquals(ErrorConstants.INVALID_PUBLIC_KEY, exception.getErrorCode());
    }

    @Test
    public void patchClient_withNullEncPublicKey_thenNoChange() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Mockito.verify(identityProviderUtil, Mockito.never()).computePublicKeyHash(Mockito.any());
    }

    @Test
    public void patchClient_withAdditionalConfig_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        ObjectNode additionalConfig = objectMapper.createObjectNode();
        additionalConfig.put("userinfo_response_type", "JWS");
        additionalConfig.put("signup_banner_required", true);
        additionalConfig.put("forgot_pwd_link_required", true);
        additionalConfig.put("consent_expire_in_mins", 20);
        additionalConfig.put("require_pushed_authorization_requests", false);
        additionalConfig.put("dpop_bound_access_tokens", false);
        additionalConfig.put("require_pkce", true);
        patchRequest.setAdditionalConfig(additionalConfig);

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    @Test
    public void patchClient_withPartialUpdate_onlyLogoUri_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setLogoUri("http://service.com/updated_logo.png");

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    @Test
    public void patchClient_withPartialUpdate_onlyStatus_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setStatus("INACTIVE");

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("INACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
        Assertions.assertEquals("INACTIVE", response.getStatus());
    }

    @Test
    public void patchClient_withPartialUpdate_onlyRedirectUris_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setRedirectUris(Arrays.asList("http://new-service.com/callback", "http://new-service.com/home"));

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    @Test
    public void patchClient_withPartialUpdate_onlyUserClaims_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setUserClaims(Arrays.asList("name", "email", "phone_number"));

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    @Test
    public void patchClient_withPartialUpdate_onlyAuthContextRefs_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setAuthContextRefs(Arrays.asList("mosip:idp:acr:generated-code", "mosip:idp:acr:password"));

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    @Test
    public void patchClient_withPartialUpdate_onlyGrantTypes_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setGrantTypes(Arrays.asList("authorization_code"));

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    @Test
    public void patchClient_withPartialUpdate_onlyClientAuthMethods_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setClientAuthMethods(Arrays.asList("private_key_jwt"));

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    @Test
    public void patchClient_withClientNameLangMap_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        clientDetail.setName("{\"@none\":\"original_name\",\"eng\":\"Original Name\"}");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setClientName("updated_client_name");
        Map<String, String> langMap = new HashMap<>();
        langMap.put("eng", "Updated Client Name");
        langMap.put("fra", "Nom du Client Mis à Jour");
        patchRequest.setClientNameLangMap(langMap);

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    @Test
    public void patchClient_withOnlyClientName_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        clientDetail.setName("{\"@none\":\"original_name\"}");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setClientName("new_client_name");

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    @Test
    public void patchClient_withInvalidExistingClientName_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        clientDetail.setName("not_valid_json"); // Invalid JSON for name
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setClientName("new_client_name");

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    @Test
    public void patchClient_withAllFields_thenPass() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));
        Mockito.when(identityProviderUtil.computePublicKeyHash(Mockito.any())).thenReturn("mock_hash");

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        patchRequest.setLogoUri("http://service.com/new_logo.png");
        patchRequest.setRedirectUris(Arrays.asList("http://service.com/callback"));
        patchRequest.setUserClaims(Arrays.asList("name", "email"));
        patchRequest.setAuthContextRefs(Arrays.asList("mosip:idp:acr:generated-code"));
        patchRequest.setStatus("ACTIVE");
        patchRequest.setGrantTypes(Arrays.asList("authorization_code"));
        patchRequest.setClientName("full_update_name");
        Map<String, String> langMap = new HashMap<>();
        langMap.put("eng", "Full Update Name");
        patchRequest.setClientNameLangMap(langMap);
        patchRequest.setClientAuthMethods(Arrays.asList("private_key_jwt"));
        patchRequest.setAdditionalConfig(objectMapper.createObjectNode().put("key", "value"));
        patchRequest.setEncPublicKey(ENC_PUBLIC_KEY);

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
        Assertions.assertEquals("ACTIVE", response.getStatus());
        Mockito.verify(identityProviderUtil, Mockito.times(1)).computePublicKeyHash(Mockito.any());
    }

    @Test
    public void patchClient_withEmptyPatchRequest_thenOnlyUpdateTimestamp() throws EsignetException {
        ClientDetail clientDetail = createMockClientDetail("client_id_v1");
        Mockito.when(clientDetailRepository.findById("client_id_v1")).thenReturn(Optional.of(clientDetail));

        ClientDetailPatchRequest patchRequest = new ClientDetailPatchRequest();
        // All fields are null - only timestamp should be updated

        ClientDetail savedEntity = new ClientDetail();
        savedEntity.setId("client_id_v1");
        savedEntity.setStatus("ACTIVE");
        Mockito.when(clientDetailRepository.save(Mockito.any(ClientDetail.class))).thenReturn(savedEntity);

        ClientDetailResponse response = clientManagementService.patchClient("client_id_v1", patchRequest);

        Assertions.assertNotNull(response);
        Assertions.assertEquals("client_id_v1", response.getClientId());
    }

    private ClientDetail createMockClientDetail(String clientId) {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId(clientId);
        clientDetail.setName("{\"@none\":\"test_client\"}");
        clientDetail.setLogoUri("http://service.com/logo.png");
        clientDetail.setClaims("[\"given_name\", \"birthdate\"]");
        clientDetail.setAcrValues("[\"mosip:idp:acr:static-code\"]");
        clientDetail.setClientAuthMethods("[\"private_key_jwt\"]");
        clientDetail.setGrantTypes("[\"authorization_code\"]");
        clientDetail.setRedirectUris("[\"https://service.com/home\",\"https://service.com/dashboard\"]");
        clientDetail.setStatus("ACTIVE");
        return clientDetail;
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
                    .algorithm(new Algorithm("RS256"))
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            log.error("generateJWK_RSA failed", e);
        }
        return null;
    }

    public static JWK generateEncryptionJWK_RSA() {
        // Generate the RSA key pair for encryption
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();
            // Convert public key to JWK format for encryption
            return new RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
                    .privateKey((RSAPrivateKey)keyPair.getPrivate())
                    .keyUse(KeyUse.ENCRYPTION)
                    .algorithm(new Algorithm("RSA-OAEP-256"))
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (NoSuchAlgorithmException e) {
            log.error("generateEncryptionJWK_RSA failed", e);
        }
        return null;
    }
}