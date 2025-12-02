/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import jakarta.validation.ConstraintViolationException;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.mosip.esignet.entity.RegistryId;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import io.mosip.esignet.entity.PublicKeyRegistry;

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
		Assertions.assertNotNull(publicKeyRegistry);

		Optional<PublicKeyRegistry> publicKeyRegistryOptional=publicKeyRegistryRepository.
				findFirstByIdHashAndThumbprintAndExpiredtimesGreaterThanOrderByExpiredtimesDesc("test_id_hash","thumbprint",LocalDateTime.now().plus(4,ChronoUnit.DAYS));
		Assertions.assertFalse(publicKeyRegistryOptional.isEmpty());
		Assertions.assertEquals(publicKeyRegistryOptional.get(),publicKeyRegistry);

		List<PublicKeyRegistry> list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLA"), LocalDateTime.now());
		Assertions.assertFalse(list.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLA"), LocalDateTime.now().plus(4, ChronoUnit.DAYS));
		Assertions.assertFalse(list.isEmpty());

		Optional<PublicKeyRegistry> result = publicKeyRegistryRepository.findById(new RegistryId("test_id_hash","WLA"));
		Assertions.assertFalse(result.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash_2",
				Set.of("WLA"), LocalDateTime.now());
		Assertions.assertTrue(list.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLA"), LocalDateTime.now().plus(5, ChronoUnit.DAYS));
		Assertions.assertTrue(list.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLA"), LocalDateTime.now().plus(10, ChronoUnit.DAYS));
		Assertions.assertTrue(list.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLQ"), LocalDateTime.now());
		Assertions.assertTrue(list.isEmpty());

		list = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan("test_id_hash",
				Set.of("WLQ", "WLA"), LocalDateTime.now());
		Assertions.assertFalse(list.isEmpty());
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
			Assertions.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("psuToken")));
			return;
		} catch (Exception e) {}
		Assertions.fail();
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
			Assertions.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("publicKey")));
			return;
		}
		Assertions.fail();
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
			Assertions.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("publicKey")));
			return;
		}
		Assertions.fail();
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
			Assertions.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("walletBindingId")));
			return;
		}
		Assertions.fail();
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
			Assertions.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("walletBindingId")));
			return;
		}
		Assertions.fail();
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
			Assertions.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("certificate")));
			return;
		}
		Assertions.fail();
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
			Assertions.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("authFactor")));
			return;
		}
		Assertions.fail();
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
			Assertions.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("certificate")));
			return;
		}
		Assertions.fail();
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
		Assertions.assertNotNull(publicKeyRegistry);

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
		Assertions.assertNotNull(publicKeyRegistry);

		Optional<PublicKeyRegistry> result = publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(psu_token, "WLA");
		Assertions.assertTrue(result.isPresent());

		result = publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(psu_token+" ", "WLA");
		Assertions.assertFalse(result.isPresent());
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
		Assertions.assertNotNull(publicKeyRegistry);

		Optional<PublicKeyRegistry> result = publicKeyRegistryRepository
				.findOptionalByPublicKeyHashAndPsuTokenNot("test_public_key_hash", "test_token_2");
		Assertions.assertTrue(result.isPresent());
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
		Assertions.assertNotNull(publicKeyRegistry);
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