/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.repository;

import static org.junit.Assert.assertEquals;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
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
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now().plus(5, ChronoUnit.DAYS));
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors("[]");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistryRepository.save(publicKeyRegistry);
		publicKeyRegistryRepository.flush();
		Assert.assertNotNull(publicKeyRegistry);

		Optional<PublicKeyRegistry> result = publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan("test_id_hash",
				LocalDateTime.now());
		Assert.assertTrue(result.isPresent());

		result = publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan("test_id_hash",
				LocalDateTime.now().plus(4, ChronoUnit.DAYS));
		Assert.assertTrue(result.isPresent());

		result = publicKeyRegistryRepository.findById("test_id_hash");
		Assert.assertTrue(result.isPresent());

		result = publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan("test_id_hash_2", LocalDateTime.now());
		Assert.assertFalse(result.isPresent());

		result = publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan("test_id_hash",
				LocalDateTime.now().plus(5, ChronoUnit.DAYS));
		Assert.assertFalse(result.isPresent());

		result = publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan("test_id_hash",
				LocalDateTime.now().plus(10, ChronoUnit.DAYS));
		Assert.assertFalse(result.isPresent());
	}

	@Test
	public void createPublicKeyRegistry_withBlankPsuToken_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors("[]");
		try {
			publicKeyRegistryRepository.save(publicKeyRegistry);
			publicKeyRegistryRepository.flush();
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("psuToken")));
			return;
		} catch (Exception e) {
			System.out.println("message" + e.getMessage());
			return;
		}
		Assert.fail();
	}

	@Test
	public void createPublicKeyRegistry_withBlankPublicKey_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors("[]");
		try {
			publicKeyRegistryRepository.save(publicKeyRegistry);
			publicKeyRegistryRepository.flush();
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("publicKey")));
			return;
		}
		Assert.fail();
	}

	@Test
	public void createPublicKeyRegistry_withNullPublicKey_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey(null);
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors("[]");
		try {
			publicKeyRegistryRepository.save(publicKeyRegistry);
			publicKeyRegistryRepository.flush();
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("publicKey")));
			return;
		}
		Assert.fail();
	}

	@Test
	public void createPublicKeyRegistry_withBlankWalletBindingId_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors("[]");
		try {
			publicKeyRegistryRepository.save(publicKeyRegistry);
			publicKeyRegistryRepository.flush();
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
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId(null);
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors("[]");
		try {
			publicKeyRegistryRepository.save(publicKeyRegistry);
			publicKeyRegistryRepository.flush();
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("walletBindingId")));
			return;
		}
		Assert.fail();
	}

	@Test
	public void createPublicKeyRegistry_withNullCertificate_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId(null);
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate(null);
		publicKeyRegistry.setAuthFactors("[]");
		try {
			publicKeyRegistryRepository.save(publicKeyRegistry);
			publicKeyRegistryRepository.flush();
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("certificate")));
			return;
		}
		Assert.fail();
	}

	@Test
	public void createPublicKeyRegistry_withNullAuthFactors_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId(null);
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors(null);
		try {
			publicKeyRegistryRepository.save(publicKeyRegistry);
			publicKeyRegistryRepository.flush();
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("authFactors")));
			return;
		}
		Assert.fail();
	}

	@Test
	public void createPublicKeyRegistry_withBlankCertificate_thenFail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId(null);
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("");
		publicKeyRegistry.setAuthFactors("[\"WLA\"]");
		try {
			publicKeyRegistryRepository.save(publicKeyRegistry);
			publicKeyRegistryRepository.flush();
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("certificate")));
			return;
		}
		Assert.fail();
	}

	@Test
	public void findWalletBindingIdWithPsuToken_withValidDetail_thenPass() {
		String psu_token = "test_token";
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken(psu_token);
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors("[\"WLA\"]");
		publicKeyRegistry = publicKeyRegistryRepository.save(publicKeyRegistry);
		Assert.assertNotNull(publicKeyRegistry);

		publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash_2");
		publicKeyRegistry.setPsuToken(psu_token);
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors("[\"WLA\"]");
		publicKeyRegistry = publicKeyRegistryRepository.save(publicKeyRegistry);
		Assert.assertNotNull(publicKeyRegistry);

		Optional<PublicKeyRegistry> result = publicKeyRegistryRepository.findOneByPsuToken(psu_token);
		Assert.assertTrue(result.isPresent());

		result = publicKeyRegistryRepository.findOneByPsuToken(psu_token+" ");
		Assert.assertFalse(result.isPresent());
	}

	@Test
	public void findByPublicKeyHashWithPsuToken_withValidDetail_thenPass() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors("[\"WLA\"]");
		publicKeyRegistry = publicKeyRegistryRepository.save(publicKeyRegistry);
		Assert.assertNotNull(publicKeyRegistry);

		Optional<PublicKeyRegistry> result = publicKeyRegistryRepository
				.findByPublicKeyHashNotEqualToPsuToken("test_public_key_hash", "test_token_2");
		Assert.assertTrue(result.isPresent());
	}

	@Test
	public void updatePublicKeyRegistry_withValidDetail_thenPass() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash("test_id_hash");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactors("[\"WLA\"]");
		publicKeyRegistryRepository.save(publicKeyRegistry);
		publicKeyRegistryRepository.flush();
		Assert.assertNotNull(publicKeyRegistry);
		int updatedRows = publicKeyRegistryRepository.updatePublicKeyRegistry("test_public_key_updated",
				"test_public_key_hash_updated",	LocalDateTime.now(), "test_token",
				"certificate2", "[\"WLA\",\"WLA1\"]");
		assertEquals(1, updatedRows);
	}

}