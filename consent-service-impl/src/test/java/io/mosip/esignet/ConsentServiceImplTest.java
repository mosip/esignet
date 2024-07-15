/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.claim.ClaimDetail;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.entity.ConsentDetail;
import io.mosip.esignet.entity.ConsentHistory;
import io.mosip.esignet.mapper.ConsentMapperImpl;
import io.mosip.esignet.repository.ConsentHistoryRepository;
import io.mosip.esignet.repository.ConsentRepository;
import io.mosip.esignet.services.ConsentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;
import static org.mockito.Mockito.doNothing;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ConsentServiceImplTest {


    @Mock
    ConsentRepository consentRepository;

    @Mock
    ConsentHistoryRepository consentHistoryRepository;

    @Mock
    AuditPlugin auditWrapper;

    @InjectMocks
    ConsentServiceImpl consentService;

    @InjectMocks
    ConsentMapperImpl consentMapper;

    @Before
    public void initialize() {
        ReflectionTestUtils.setField(consentMapper, "objectMapper", new ObjectMapper());
        ReflectionTestUtils.setField(consentService, "consentMapper", consentMapper);
    }

    @Test
    public void getUserConsent_withValidDetails_thenPass() throws Exception{
        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setId(UUID.randomUUID());
        consentDetail.setClientId("1234");
        consentDetail.setClaims("{\"userinfo\":{\"given_name\":{\"essential\":true},\"phone_number\":null,\"email\":{\"essential\":true},\"picture\":{\"essential\":false},\"gender\":{\"essential\":false}},\"id_token\":{}}");
        consentDetail.setCreatedtimes(LocalDateTime.now());
        consentDetail.setPsuToken("psuValue");
        consentDetail.setExpiredtimes(LocalDateTime.now());

        Optional<ConsentDetail> consentOptional = Optional.of(consentDetail);
        Mockito.when(consentRepository.findByClientIdAndPsuToken(Mockito.anyString(),Mockito.anyString())).thenReturn(consentOptional);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId("1234");
        userConsentRequest.setPsuToken("psuValue");

        Optional<io.mosip.esignet.core.dto.ConsentDetail> userConsentDto = consentService.getUserConsent(userConsentRequest);
        Assert.assertNotNull(userConsentDto);
        Assert.assertEquals("1234", userConsentDto.get().getClientId());
        Assert.assertEquals("psuValue", userConsentDto.get().getPsuToken());

    }

    @Test
    public void getUserConsent_withInValidClaimsDetails_thenFail() {
        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setId(UUID.randomUUID());
        consentDetail.setClientId("1234");
        consentDetail.setCreatedtimes(LocalDateTime.now());
        consentDetail.setClaims("claims");
        consentDetail.setPsuToken("psuValue");
        consentDetail.setExpiredtimes(LocalDateTime.now());

        Optional<ConsentDetail> consentOptional = Optional.of(consentDetail);
        Mockito.when(consentRepository.findByClientIdAndPsuToken(Mockito.anyString(),Mockito.anyString())).thenReturn(consentOptional);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId("1234");
        userConsentRequest.setPsuToken("psuValue");
        try{
            Optional<io.mosip.esignet.core.dto.ConsentDetail> userConsentDto = consentService.getUserConsent(userConsentRequest);
            Assert.fail();
        }catch (EsignetException e){
            Assert.assertTrue(e.getErrorCode().equals(INVALID_CLAIM));
        }
    }

    @Test
    public void getUserConsent_withNoClaimsDetails_thenPass() {

        Mockito.when(consentRepository.findByClientIdAndPsuToken(Mockito.anyString(),Mockito.anyString())).thenReturn(Optional.empty());
        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId("1234");
        userConsentRequest.setPsuToken("psuValue");

            Optional<io.mosip.esignet.core.dto.ConsentDetail> userConsentDto = consentService.getUserConsent(userConsentRequest);
            Assert.assertEquals(Optional.empty(), userConsentDto);
    }

    @Test
    public void saveUserConsent_withValidDetails_thenPass() throws Exception{
        Claims claims = new Claims();

        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoClaimDetail = new ClaimDetail("value1", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("value2", new String[]{"value2a", "value2b"}, false);
        userinfo.put("userinfoKey", userinfoClaimDetail);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);


        UserConsent userConsent = new UserConsent();
        userConsent.setClientId("1234");
        userConsent.setPsuToken("psuValue");
        userConsent.setClaims(claims);

        Map<String,Boolean> authorizeScopes = Map.of("given_name",true,"email",true);
        userConsent.setAuthorizationScopes(authorizeScopes);
        userConsent.setExpirydtimes(LocalDateTime.now());
        userConsent.setSignature("signature");

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setId(UUID.randomUUID());
        consentDetail.setClientId("1234");
        consentDetail.setClaims("{\"userinfo\":{\"given_name\":{\"essential\":true},\"phone_number\":null,\"email\":{\"essential\":true},\"picture\":{\"essential\":false},\"gender\":{\"essential\":false}},\"id_token\":{}}");
        consentDetail.setCreatedtimes(LocalDateTime.now());
        consentDetail.setPsuToken("psuValue");
        consentDetail.setExpiredtimes(LocalDateTime.now());

        Mockito.when(consentRepository.save(Mockito.any())).thenReturn(consentDetail);
        Mockito.when(consentHistoryRepository.save(Mockito.any())).thenReturn(new ConsentHistory());
        Mockito.when(consentRepository.findByClientIdAndPsuToken(Mockito.any(),Mockito.any())).thenReturn(Optional.empty());
        io.mosip.esignet.core.dto.ConsentDetail userConsentDtoDetail = consentService.saveUserConsent(userConsent);
        Assert.assertNotNull(userConsentDtoDetail);
        Assert.assertEquals("1234", userConsentDtoDetail.getClientId());

    }

    @Test
    public void saveUserConsent_WhenConsentIsAlreadyPresent_withValidDetails_thenPass() throws Exception{
        Claims claims = new Claims();

        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoClaimDetail = new ClaimDetail("value1", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("value2", new String[]{"value2a", "value2b"}, false);
        userinfo.put("userinfoKey", userinfoClaimDetail);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);


        UserConsent userConsent = new UserConsent();
        userConsent.setClientId("1234");
        userConsent.setPsuToken("psuValue");
        userConsent.setClaims(claims);

        Map<String,Boolean> authorizeScopes = Map.of("given_name",true,"email",true);
        userConsent.setAuthorizationScopes(authorizeScopes);
        userConsent.setExpirydtimes(LocalDateTime.now());
        userConsent.setSignature("signature");

        ConsentDetail consentDetail = new ConsentDetail();
        consentDetail.setId(UUID.randomUUID());
        consentDetail.setClientId("1234");
        consentDetail.setClaims("{\"userinfo\":{\"given_name\":{\"essential\":true},\"phone_number\":null,\"email\":{\"essential\":true},\"picture\":{\"essential\":false},\"gender\":{\"essential\":false}},\"id_token\":{}}");
        consentDetail.setCreatedtimes(LocalDateTime.now());
        consentDetail.setPsuToken("psuValue");
        consentDetail.setExpiredtimes(LocalDateTime.now());

        Mockito.when(consentRepository.save(Mockito.any())).thenReturn(consentDetail);
        Mockito.when(consentHistoryRepository.save(Mockito.any())).thenReturn(new ConsentHistory());
        Mockito.when(consentRepository.findByClientIdAndPsuToken(Mockito.any(),Mockito.any())).thenReturn(Optional.of(consentDetail));
        doNothing().when(consentRepository).deleteByClientIdAndPsuToken(Mockito.any(),Mockito.any());
        io.mosip.esignet.core.dto.ConsentDetail userConsentDtoDetail = consentService.saveUserConsent(userConsent);
        Assert.assertNotNull(userConsentDtoDetail);
        Assert.assertEquals("1234", userConsentDtoDetail.getClientId());

    }

    @Test
    public void deleteConsentByClientIdAndPsuToken_thenPass(){
        String clientId = "test-client-id";
        String psuToken = "test-psu-token";
        consentService.deleteUserConsent(clientId,psuToken);
        Mockito.verify(consentRepository).deleteByClientIdAndPsuToken(clientId, psuToken);
    }


}
