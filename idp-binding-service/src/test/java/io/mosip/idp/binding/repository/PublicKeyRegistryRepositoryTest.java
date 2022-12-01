/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.repository;

import java.time.LocalDateTime;
import java.util.Optional;

import javax.validation.ConstraintViolationException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.idp.binding.entity.PublicKeyRegistry;

@RunWith(SpringRunner.class)
@DataJpaTest
public class PublicKeyRegistryRepositoryTest {

	@Autowired
    private PublicKeyRegistryRepository publicKeyRegistryRepository;
	
	@Test
	public void createPublicKeyRegistry_withValidDetail_thenPass() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
        publicKeyRegistry = publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
        Assert.assertNotNull(publicKeyRegistry);

		Optional<PublicKeyRegistry> result = publicKeyRegistryRepository.findByPsuToken("test_token");
        Assert.assertTrue(result.isPresent());
        
		result = publicKeyRegistryRepository.findById("test_token");
        Assert.assertTrue(result.isPresent());

		result = publicKeyRegistryRepository.findByPsuToken("test_token_2");
        Assert.assertFalse(result.isPresent());
    }
	
	@Test
    public void createPublicKeyRegistry_withBlankIndividualId_thenFail() {
        PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setPsuToken("");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
        try {
            publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("psuToken")));
            return;
        }
        Assert.fail();
    }
	
	@Test
    public void createPublicKeyRegistry_withBlankPublicKey_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
        try {
        	publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("publicKey")));
            return;
        }
        Assert.fail();
    }
	
	@Test
    public void createPublicKeyRegistry_withNullPublicKey_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey(null);
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
        try {
        	publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("publicKey")));
            return;
        }
        Assert.fail();
    }
	
	@Test
	public void createPublicKeyRegistry_withValidExpiryDate_thenPass() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.of(2040, 11, 11, 11, 11));
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
        publicKeyRegistry = publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
        Assert.assertNotNull(publicKeyRegistry);

		PublicKeyRegistry result = publicKeyRegistryRepository.findByPsuTokenAndExpiredtimesGreaterThan("test_token",
				LocalDateTime.now());
        Assert.assertNotNull(result);

		result = publicKeyRegistryRepository.findByPsuTokenAndExpiredtimesGreaterThan("0000000", LocalDateTime.now());
        Assert.assertNull(result);
    }
	
	@Test
	public void createPublicKeyRegistry_withInValidExpiryDate_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.of(2020, 11, 11, 11, 11));
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
        publicKeyRegistry = publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
        Assert.assertNotNull(publicKeyRegistry);

		PublicKeyRegistry result = publicKeyRegistryRepository.findByPsuTokenAndExpiredtimesGreaterThan("2337511530",
				LocalDateTime.now());
        Assert.assertNull(result);
    }

	@Test
	public void createPublicKeyRegistry_withBlankWalletBindingId_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("");
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		try {
			publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("walletBindingId")));
			return;
		}
		Assert.fail();
	}

	@Test
	public void createPublicKeyRegistry_withNullWalletBindingId__thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId(null);
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		try {
			publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("walletBindingId")));
			return;
		}
		Assert.fail();
	}
}
