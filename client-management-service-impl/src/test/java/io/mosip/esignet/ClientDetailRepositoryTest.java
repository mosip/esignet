/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import io.mosip.esignet.entity.ClientDetail;
import io.mosip.esignet.repository.ClientDetailRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.junit4.SpringRunner;

import javax.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Optional;


@RunWith(SpringRunner.class)
@DataJpaTest
public class ClientDetailRepositoryTest {

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Test
    public void createClientDetail_withValidDetail_thenPass() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C01");
        clientDetail.setName("Client-01");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris("[\"https://clientapp.com/home\",\"https://clientapp.com/home2\"]");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[\"authorization_code\"]");
        clientDetail.setClientAuthMethods("[\"private_key_jwt\"]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        clientDetail = clientDetailRepository.saveAndFlush(clientDetail);
        Assert.assertNotNull(clientDetail);

        Optional<ClientDetail> result = clientDetailRepository.findById("C01");
        Assert.assertTrue(result.isPresent());

        result = clientDetailRepository.findById("C02");
        Assert.assertFalse(result.isPresent());

        result = clientDetailRepository.findByIdAndStatus("C01", "ACTIVE");
        Assert.assertTrue(result.isPresent());

        result = clientDetailRepository.findByIdAndStatus("C01", "INACTIVE");
        Assert.assertFalse(result.isPresent());
    }

    @Test
    public void createClientDetail_withBlankClientId_thenFail() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("");
        clientDetail.setName("Client-01");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris("[]");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("id")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createClientDetail_withBlankPublicKey_thenFail() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C01");
        clientDetail.setName("Client-01");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris("[]");
        clientDetail.setPublicKey("");
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("publicKey")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createClientDetail_withNullPublicKey_thenFail() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C01");
        clientDetail.setName("Client-01");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris("[]");
        clientDetail.setPublicKey(null);
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("publicKey")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createClientDetail_withBlankClientName_thenFail() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C01");
        clientDetail.setName(" ");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris("[]");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("name")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createClientDetail_withBlankRP_thenFail() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C01");
        clientDetail.setName("C)1");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris("[]");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setRpId(" ");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("rpId")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createClientDetail_withBlankRedirectUri_thenFail() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C01");
        clientDetail.setName("C01");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris(" ");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setRpId("RP_ID");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("redirectUris")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createClientDetail_withBlankStatus_thenFail() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C01");
        clientDetail.setName("C01");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("");
        clientDetail.setRedirectUris("[]]");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setRpId("RP_ID");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("status")));
            return;
        }
        Assert.fail();
    }

    @Test
    public void createClientDetail_withNullStatus_thenFail() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C01");
        clientDetail.setName("C01");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus(null);
        clientDetail.setRedirectUris("[]]");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setRpId("RP_ID");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException | DataIntegrityViolationException e) {
            Assert.assertTrue(true);
            return;
        }
        Assert.fail();
    }

    @Test
    public void createClientDetail_withInvalidStatus_thenFail() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C01");
        clientDetail.setName("C01");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("active");
        clientDetail.setRedirectUris("[]]");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setRpId("RP_ID");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("status")));
            return;
        }
        Assert.fail();
    }
}
