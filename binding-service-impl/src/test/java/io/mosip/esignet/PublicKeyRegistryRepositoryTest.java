/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import static org.junit.Assert.assertEquals;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.validation.ConstraintViolationException;

import io.mosip.esignet.entity.RegistryId;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.esignet.entity.PublicKeyRegistry;

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
		publicKeyRegistry.setAuthFactor("WLA");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setThumbprint("thumbprint");
		publicKeyRegistryRepository.save(publicKeyRegistry);
		publicKeyRegistryRepository.flush();
		Assert.assertNotNull(publicKeyRegistry);

		Optional<PublicKeyRegistry> publicKeyRegistryOptional=publicKeyRegistryRepository.
				findFirstByIdHashAndThumbprintAndExpiredtimesGreaterThanOrderByExpiredtimesDesc("test_id_hash","thumbprint",LocalDateTime.now().plus(4,ChronoUnit.DAYS));
		Assert.assertFalse(publicKeyRegistryOptional.isEmpty());
		Assert.assertEquals(publicKeyRegistryOptional.get(),publicKeyRegistry);

		List<PublicKeyRegistry> list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLA"), LocalDateTime.now());
		Assert.assertFalse(list.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLA"), LocalDateTime.now().plus(4, ChronoUnit.DAYS));
		Assert.assertFalse(list.isEmpty());

		Optional<PublicKeyRegistry> result = publicKeyRegistryRepository.findById(new RegistryId("test_id_hash","WLA"));
		Assert.assertFalse(result.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash_2",
				Set.of("WLA"), LocalDateTime.now());
		Assert.assertTrue(list.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLA"), LocalDateTime.now().plus(5, ChronoUnit.DAYS));
		Assert.assertTrue(list.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLA"), LocalDateTime.now().plus(10, ChronoUnit.DAYS));
		Assert.assertTrue(list.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLQ"), LocalDateTime.now());
		Assert.assertTrue(list.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLQ", "WLA"), LocalDateTime.now());
		Assert.assertFalse(list.isEmpty());
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
		publicKeyRegistry.setAuthFactor("WLA");
		try {
			publicKeyRegistryRepository.save(publicKeyRegistry);
			publicKeyRegistryRepository.flush();
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("psuToken")));
			return;
		} catch (Exception e) {}
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
		publicKeyRegistry.setAuthFactor("WLA");
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
		publicKeyRegistry.setAuthFactor("WLA");
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
		publicKeyRegistry.setAuthFactor("WLA");
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
		publicKeyRegistry.setAuthFactor("WLA");
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
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate(null);
		publicKeyRegistry.setAuthFactor("WLA");
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
		publicKeyRegistry.setWalletBindingId("test_wallet_binding_id");
		publicKeyRegistry.setPublicKeyHash("test_public_key_hash");
		publicKeyRegistry.setCertificate("certificate");
		publicKeyRegistry.setAuthFactor(null);
		try {
			publicKeyRegistryRepository.save(publicKeyRegistry);
			publicKeyRegistryRepository.flush();
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("authFactor")));
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
		publicKeyRegistry.setAuthFactor("WLA");
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
		publicKeyRegistry.setAuthFactor("WLA");
		publicKeyRegistry.setThumbprint("thumbprint");
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
		publicKeyRegistry.setAuthFactor("WLA");
		publicKeyRegistry.setThumbprint("thumbprint");
		publicKeyRegistry = publicKeyRegistryRepository.save(publicKeyRegistry);
		Assert.assertNotNull(publicKeyRegistry);

		Optional<PublicKeyRegistry> result = publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(psu_token, "WLA");
		Assert.assertTrue(result.isPresent());

		result = publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(psu_token+" ", "WLA");
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
		publicKeyRegistry.setAuthFactor("WLA");
		publicKeyRegistry.setThumbprint("thumbprint");
		publicKeyRegistry = publicKeyRegistryRepository.save(publicKeyRegistry);
		Assert.assertNotNull(publicKeyRegistry);

		Optional<PublicKeyRegistry> result = publicKeyRegistryRepository
				.findOptionalByPublicKeyHashAndPsuTokenNot("test_public_key_hash", "test_token_2");
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
		publicKeyRegistry.setAuthFactor("WLA");
		publicKeyRegistry.setThumbprint("thumbprint");
		publicKeyRegistryRepository.save(publicKeyRegistry);
		publicKeyRegistryRepository.flush();
		Assert.assertNotNull(publicKeyRegistry);
		int updatedRows = publicKeyRegistryRepository.updatePublicKeyRegistry("test_public_key_updated",
				"test_public_key_hash_updated",	LocalDateTime.now(), "test_token",
				"certificate2", "WLA");
		assertEquals(1, updatedRows);

		updatedRows = publicKeyRegistryRepository.updatePublicKeyRegistry("test_public_key_updated",
				"test_public_key_hash_updated",	LocalDateTime.now(), "test_token",
				"certificate2", "WLQ");
		assertEquals(0, updatedRows);
	}

}