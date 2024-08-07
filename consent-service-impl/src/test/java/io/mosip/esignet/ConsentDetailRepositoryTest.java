/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import io.mosip.esignet.entity.ConsentDetail;
import io.mosip.esignet.repository.ConsentRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@RunWith(SpringRunner.class)
@DataJpaTest
public class ConsentDetailRepositoryTest {

    @Autowired
    private ConsentRepository consentRepository;

    @Test
    public void createConsent_withValidDetail_thenPass() {

        ConsentDetail consentDetail =new ConsentDetail();
        UUID uuid=UUID.randomUUID();
        LocalDateTime date = LocalDateTime.of(2019, 12, 12, 12, 12, 12);
        consentDetail.setClientId("123");
        consentDetail.setPsuToken("abc");
        consentDetail.setClaims("claims");
        consentDetail.setAuthorizationScopes("authorizationScopes");
        consentDetail.setCreatedtimes(date);
        consentDetail.setExpiredtimes(LocalDateTime.now());
        consentDetail.setSignature("signature");
        consentDetail.setHash("hash");
        consentDetail.setAcceptedClaims("claim");
        consentDetail.setPermittedScopes("scope");
        consentDetail =consentRepository.save(consentDetail);
        Assert.assertNotNull(consentDetail);

        Optional<ConsentDetail> result;

        result = consentRepository.findByClientIdAndPsuToken("123", "abc");
        Assert.assertTrue(result.isPresent());

        result = consentRepository.findByClientIdAndPsuToken("123", "abcd");
        Assert.assertFalse(result.isPresent());
    }

    @Test
    public void createConsent_withNullClientId_thenFail() {

        ConsentDetail consentDetail = new ConsentDetail();
        UUID uuid=UUID.randomUUID();
        LocalDateTime date = LocalDateTime.of(2019, 12, 12, 12, 12, 12);
        consentDetail.setId(uuid);
        consentDetail.setClientId(null);
        consentDetail.setPsuToken("abc");
        consentDetail.setClaims("claims");
        consentDetail.setAuthorizationScopes("authorizationScopes");
        consentDetail.setCreatedtimes(date);
        consentDetail.setExpiredtimes(LocalDateTime.now());
        consentDetail.setSignature("signature");
        try {
            consentRepository.saveAndFlush(consentDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("clientId")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createConsent_withNullPsuValue_thenFail() {

        ConsentDetail consentDetail = new ConsentDetail();
        UUID uuid=UUID.randomUUID();
        LocalDateTime date = LocalDateTime.of(2019, 12, 12, 12, 12, 12);
        consentDetail.setId(uuid);
        consentDetail.setClientId("123");
        consentDetail.setPsuToken(null);
        consentDetail.setClaims("claims");
        consentDetail.setAuthorizationScopes("authorizationScopes");
        consentDetail.setCreatedtimes(date);
        consentDetail.setExpiredtimes(LocalDateTime.now());
        consentDetail.setSignature("signature");
        try {
            consentRepository.saveAndFlush(consentDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("psuToken")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createConsent_withNullClaims_thenFail() {

        ConsentDetail consentDetail = new ConsentDetail();
        UUID uuid=UUID.randomUUID();
        LocalDateTime date = LocalDateTime.of(2019, 12, 12, 12, 12, 12);
        consentDetail.setId(uuid);
        consentDetail.setClientId("123");
        consentDetail.setPsuToken("abc");
        consentDetail.setClaims(null);
        consentDetail.setAuthorizationScopes("authorizationScopes");
        consentDetail.setCreatedtimes(date);
        consentDetail.setExpiredtimes(LocalDateTime.now());
        consentDetail.setSignature("signature");
        try {
            consentRepository.saveAndFlush(consentDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("claims")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createConsent_withNullAuthorizationScopes_thenFail() {

        ConsentDetail consentDetail = new ConsentDetail();
        UUID uuid=UUID.randomUUID();
        LocalDateTime date = LocalDateTime.of(2019, 12, 12, 12, 12, 12);
        consentDetail.setId(uuid);
        consentDetail.setClientId("123");
        consentDetail.setPsuToken("abc");
        consentDetail.setClaims("claims");
        consentDetail.setAuthorizationScopes(null);
        consentDetail.setCreatedtimes(date);
        consentDetail.setExpiredtimes(LocalDateTime.now());
        consentDetail.setSignature("signature");
        try {
            consentRepository.saveAndFlush(consentDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("authorizationScopes")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createConsent_withNullCreatedtimes_thenFail() {

        ConsentDetail consentDetail = new ConsentDetail();
        UUID uuid=UUID.randomUUID();
        consentDetail.setId(uuid);
        consentDetail.setClientId("123");
        consentDetail.setPsuToken("abc");
        consentDetail.setClaims("claims");
        consentDetail.setAuthorizationScopes("authorizationScopes");
        consentDetail.setCreatedtimes(null);
        consentDetail.setExpiredtimes(LocalDateTime.now());
        consentDetail.setSignature("signature");
        try {
            consentRepository.saveAndFlush(consentDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("createdtimes")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createAndDeleteConsent_withValidDetail_thenPass() {

        ConsentDetail consentDetail =new ConsentDetail();
        UUID uuid=UUID.randomUUID();
        LocalDateTime date = LocalDateTime.of(2019, 12, 12, 12, 12, 12);
        consentDetail.setClientId("123");
        consentDetail.setPsuToken("abc");
        consentDetail.setClaims("claims");
        consentDetail.setAuthorizationScopes("authorizationScopes");
        consentDetail.setCreatedtimes(date);
        consentDetail.setExpiredtimes(LocalDateTime.now());
        consentDetail.setSignature("signature");
        consentDetail.setHash("hash");
        consentDetail.setAcceptedClaims("claim");
        consentDetail.setPermittedScopes("scope");
        consentDetail =consentRepository.save(consentDetail);
        Assert.assertNotNull(consentDetail);

        Optional<ConsentDetail> result;

        result = consentRepository.findByClientIdAndPsuToken("123", "abc");
        Assert.assertTrue(result.isPresent());

        result = consentRepository.findByClientIdAndPsuToken("123", "abcd");
        Assert.assertFalse(result.isPresent());
        consentRepository.deleteByClientIdAndPsuToken(consentDetail.getClientId(),consentDetail.getPsuToken());
        consentRepository.flush();
        Optional<ConsentDetail> consentDetailOptional = consentRepository.findByClientIdAndPsuToken("123", "abc");
        Assert.assertFalse(consentDetailOptional.isPresent());
    }

}
