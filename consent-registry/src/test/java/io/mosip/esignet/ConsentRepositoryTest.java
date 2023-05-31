package io.mosip.esignet;

import io.mosip.esignet.entity.Consent;
import io.mosip.esignet.repository.ConsentRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@RunWith(SpringRunner.class)
@DataJpaTest
public class ConsentRepositoryTest {

    @Autowired
    private ConsentRepository consentRepository;

    @Test
    public void createConsent_withValidDetail_thenPass() {

        Consent consent=new Consent();
        UUID uuid=UUID.randomUUID();
        LocalDateTime date = LocalDateTime.of(2019, 12, 12, 12, 12, 12);
        consent.setClientId("123");
        consent.setPsuValue("abc");
        consent.setClaims("claims");
        consent.setAuthorizationScopes("authorizationScopes");
        consent.setCreatedOn(date);
        consent.setExpiration(LocalDateTime.now());
        consent.setHash("hash");
        consent.setSignature("signature");
        consent=consentRepository.saveAndFlush(consent);
        Assert.assertNotNull(consent);

        Optional<Consent> result;

        result = consentRepository.findFirstByClientIdAndPsuValueOrderByCreatedOnDesc("123", "abc");
        Assert.assertTrue(result.isPresent());

        result = consentRepository.findFirstByClientIdAndPsuValueOrderByCreatedOnDesc("123", "abcd");
        Assert.assertFalse(result.isPresent());
    }
}
