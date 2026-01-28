/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import io.mosip.esignet.entity.ClientDetail;
import io.mosip.esignet.repository.ClientDetailRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;


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
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[\"authorization_code\"]");
        clientDetail.setClientAuthMethods("[\"private_key_jwt\"]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        clientDetail = clientDetailRepository.saveAndFlush(clientDetail);
        Assertions.assertNotNull(clientDetail);

        Optional<ClientDetail> result = clientDetailRepository.findById("C01");
        Assertions.assertTrue(result.isPresent());

        result = clientDetailRepository.findById("C02");
        Assertions.assertFalse(result.isPresent());

        result = clientDetailRepository.findByIdAndStatus("C01", "ACTIVE");
        Assertions.assertTrue(result.isPresent());

        result = clientDetailRepository.findByIdAndStatus("C01", "INACTIVE");
        Assertions.assertFalse(result.isPresent());
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
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assertions.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("id")));
            return;
        }
        Assertions.fail();
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
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assertions.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("publicKey")));
            return;
        }
        Assertions.fail();
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
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assertions.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("publicKey")));
            return;
        }
        Assertions.fail();
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
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP01");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assertions.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("name")));
            return;
        }
        Assertions.fail();
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
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId(" ");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assertions.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("rpId")));
            return;
        }
        Assertions.fail();
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
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP_ID");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assertions.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("redirectUris")));
            return;
        }
        Assertions.fail();
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
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP_ID");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assertions.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("status")));
            return;
        }
        Assertions.fail();
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
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP_ID");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException | DataIntegrityViolationException e) {
            Assertions.assertTrue(true);
            return;
        }
        Assertions.fail();
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
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP_ID");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[]");
        clientDetail.setClientAuthMethods("[]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        try {
            clientDetailRepository.saveAndFlush(clientDetail);
        } catch (ConstraintViolationException e) {
            Assertions.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("status")));
            return;
        }
        Assertions.fail();
    }

    @Test
    public void createClientDetail_withEncPublicKey_thenPass() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C02");
        clientDetail.setName("Client-02");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris("[\"https://clientapp.com/home\"]");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setEncPublicKey("DUMMY ENC PUBLIC KEY");
        clientDetail.setEncPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP02");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[\"authorization_code\"]");
        clientDetail.setClientAuthMethods("[\"private_key_jwt\"]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        clientDetail = clientDetailRepository.saveAndFlush(clientDetail);
        Assertions.assertNotNull(clientDetail);

        Optional<ClientDetail> result = clientDetailRepository.findById("C02");
        Assertions.assertTrue(result.isPresent());
        Assertions.assertNotNull(result.get().getEncPublicKey());
        Assertions.assertNotNull(result.get().getEncPublicKeyHash());
        Assertions.assertEquals("DUMMY ENC PUBLIC KEY", result.get().getEncPublicKey());
    }

    @Test
    public void createClientDetail_withNullEncPublicKey_thenPass() {
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C03");
        clientDetail.setName("Client-03");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris("[\"https://clientapp.com/home\"]");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        // encPublicKey and encPublicKeyHash are null (optional fields)
        clientDetail.setRpId("RP03");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[\"authorization_code\"]");
        clientDetail.setClientAuthMethods("[\"private_key_jwt\"]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        clientDetail = clientDetailRepository.saveAndFlush(clientDetail);
        Assertions.assertNotNull(clientDetail);

        Optional<ClientDetail> result = clientDetailRepository.findById("C03");
        Assertions.assertTrue(result.isPresent());
        Assertions.assertNull(result.get().getEncPublicKey());
        Assertions.assertNull(result.get().getEncPublicKeyHash());
    }

    @Test
    public void updateClientDetail_withEncPublicKey_thenPass() {
        // First create a client without enc keys
        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setId("C04");
        clientDetail.setName("Client-04");
        clientDetail.setLogoUri("https://clienapp.com/logo.png");
        clientDetail.setStatus("ACTIVE");
        clientDetail.setRedirectUris("[\"https://clientapp.com/home\"]");
        clientDetail.setPublicKey("DUMMY PEM CERT");
        clientDetail.setPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setRpId("RP04");
        clientDetail.setClaims("[]");
        clientDetail.setAcrValues("[]");
        clientDetail.setGrantTypes("[\"authorization_code\"]");
        clientDetail.setClientAuthMethods("[\"private_key_jwt\"]");
        clientDetail.setCreatedtimes(LocalDateTime.now());
        clientDetail = clientDetailRepository.saveAndFlush(clientDetail);
        Assertions.assertNotNull(clientDetail);
        Assertions.assertNull(clientDetail.getEncPublicKey());

        // Now update with enc keys
        clientDetail.setEncPublicKey("UPDATED ENC PUBLIC KEY");
        clientDetail.setEncPublicKeyHash(UUID.randomUUID().toString());
        clientDetail.setUpdatedtimes(LocalDateTime.now());
        clientDetail = clientDetailRepository.saveAndFlush(clientDetail);

        Optional<ClientDetail> result = clientDetailRepository.findById("C04");
        Assertions.assertTrue(result.isPresent());
        Assertions.assertNotNull(result.get().getEncPublicKey());
        Assertions.assertEquals("UPDATED ENC PUBLIC KEY", result.get().getEncPublicKey());
    }
}
