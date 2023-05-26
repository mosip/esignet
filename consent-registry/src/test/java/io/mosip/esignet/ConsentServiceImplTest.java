package io.mosip.esignet;

import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.ConsentRequest;
import io.mosip.esignet.core.dto.UserConsentRequest;
import io.mosip.esignet.entity.Consent;
import io.mosip.esignet.repository.ConsentRepository;
import io.mosip.esignet.services.ConsentServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class ConsentServiceImplTest {


    @Mock
    ConsentRepository consentRepository;

    @Mock
    AuditPlugin auditWrapper;

    @InjectMocks
    ConsentServiceImpl consentService;



    @Test
    public void getUserConsent_withValidDetails_thenPass() throws Exception{
        log.info("Test");

        Consent consent = new Consent();
        consent.setId(UUID.randomUUID());
        consent.setClientId("1234");
        consent.setClaims("{\"userinfo\":{\"given_name\":{\"essential\":true},\"phone_number\":null,\"email\":{\"essential\":true},\"picture\":{\"essential\":false},\"gender\":{\"essential\":false}},\"id_token\":{}}");
        consent.setCreatedOn(LocalDateTime.now());
        consent.setHash("hash");
        consent.setPsuValue("psuValue");
        consent.setExpiration(LocalDateTime.now());

        Optional<Consent> consentOptional = Optional.of(consent);
        Mockito.when(consentRepository.findFirstByClientIdAndPsuValueOrderByCreatedOnDesc(Mockito.anyString(),Mockito.anyString())).thenReturn(consentOptional);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId("1234");
        userConsentRequest.setPsu_token("psuValue");

        Optional<io.mosip.esignet.core.dto.Consent> userConsentDto = consentService.getUserConsent(userConsentRequest);
        Assert.assertNotNull(userConsentDto);
        Assert.assertEquals("1234", userConsentDto.get().getClientId());
        Assert.assertEquals("psuValue", userConsentDto.get().getPsuValue());

    }

    @Test
    public void getUserConsent_withInValidClaimsDetails_thenFail() throws Exception{
        log.info("Test");

        Consent consent = new Consent();
        consent.setId(UUID.randomUUID());
        consent.setClientId("1234");
        consent.setCreatedOn(LocalDateTime.now());
        consent.setClaims("claims");
        consent.setHash("hash");
        consent.setPsuValue("psuValue");
        consent.setExpiration(LocalDateTime.now());

        Optional<Consent> consentOptional = Optional.of(consent);
        Mockito.when(consentRepository.findFirstByClientIdAndPsuValueOrderByCreatedOnDesc(Mockito.anyString(),Mockito.anyString())).thenReturn(consentOptional);

        UserConsentRequest userConsentRequest = new UserConsentRequest();
        userConsentRequest.setClientId("1234");
        userConsentRequest.setPsu_token("psuValue");
        try{
            Optional<io.mosip.esignet.core.dto.Consent> userConsentDto = consentService.getUserConsent(userConsentRequest);
            Assert.fail();
        }catch (Exception e){
            Assert.assertTrue(true);
        }
    }

    @Test
    public void saveUserConsent_withValidDetails_thenPass() throws Exception{
        log.info("Test");

        Claims claims = new Claims();

        Map<String, ClaimDetail> userinfo = new HashMap<>();
        Map<String, ClaimDetail> id_token = new HashMap<>();
        ClaimDetail userinfoClaimDetail = new ClaimDetail("value1", new String[]{"value1a", "value1b"}, true);
        ClaimDetail idTokenClaimDetail = new ClaimDetail("value2", new String[]{"value2a", "value2b"}, false);
        userinfo.put("userinfoKey", userinfoClaimDetail);
        id_token.put("idTokenKey", idTokenClaimDetail);
        claims.setUserinfo(userinfo);
        claims.setId_token(id_token);


        ConsentRequest consentRequest = new ConsentRequest();
        consentRequest.setClientId("1234");
        consentRequest.setHash("hash");
        consentRequest.setPsu_token("psuValue");
        consentRequest.setClaims(claims);

        Map<String,Boolean> authorizeScopes = Map.of("given_name",true,"email",true);
        consentRequest.setAuthorizeScopes(authorizeScopes);
        consentRequest.setExpiration(LocalDateTime.now());
        consentRequest.setSignature("signature");

        Consent consent = new Consent();
        consent.setId(UUID.randomUUID());
        consent.setClientId("1234");
        consent.setClaims("{\"userinfo\":{\"given_name\":{\"essential\":true},\"phone_number\":null,\"email\":{\"essential\":true},\"picture\":{\"essential\":false},\"gender\":{\"essential\":false}},\"id_token\":{}}");
        consent.setCreatedOn(LocalDateTime.now());
        consent.setHash("hash");
        consent.setPsuValue("psuValue");
        consent.setExpiration(LocalDateTime.now());

        Mockito.when(consentRepository.save(Mockito.any())).thenReturn(consent);

        io.mosip.esignet.core.dto.Consent userConsentDto = consentService.saveUserConsent(consentRequest);
        Assert.assertNotNull(userConsentDto);
        Assert.assertEquals("1234", userConsentDto.getClientId());

    }

    @Test
    public void saveUserConsent_withInValidDetails_thenFail() throws Exception{
        log.info("Test");
        try{
            io.mosip.esignet.core.dto.Consent userConsentDto = consentService.saveUserConsent(null);
            Assert.fail();
        }catch (Exception e) {
            Assert.assertTrue(true);
        }
    }


}
