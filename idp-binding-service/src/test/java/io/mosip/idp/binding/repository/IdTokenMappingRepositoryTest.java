/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.repository;

import java.util.Optional;

import javax.validation.ConstraintViolationException;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.junit4.SpringRunner;

import io.mosip.idp.binding.entity.IdTokenMapping;



@RunWith(SpringRunner.class)
@DataJpaTest
public class IdTokenMappingRepositoryTest {

	@Autowired
	private IdTokenMappingRepository idTokenMappingRepository;

	@Test
	public void createIdTokenMapping_withValidDetail_thenPass() {
		IdTokenMapping idTokenMapping = new IdTokenMapping();
		idTokenMapping.setIdHash("test_idhash");
		idTokenMapping.setPsuToken("test_token");
		idTokenMapping = idTokenMappingRepository.saveAndFlush(idTokenMapping);
		Assert.assertNotNull(idTokenMapping);

		Optional<IdTokenMapping> result = idTokenMappingRepository.findByIdHash("test_idhash");
		Assert.assertTrue(result.isPresent());

		result = idTokenMappingRepository.findById("test_idhash");
		Assert.assertTrue(result.isPresent());

		result = idTokenMappingRepository.findByIdHash("test_idhash_2");
		Assert.assertFalse(result.isPresent());
	}

	@Test
	public void createIdTokenMapping_withBlankIdHash_thenFail() {
		IdTokenMapping idTokenMapping = new IdTokenMapping();
		idTokenMapping.setIdHash("");
		idTokenMapping.setPsuToken("test_token");
		try {
			idTokenMappingRepository.saveAndFlush(idTokenMapping);
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("idHash")));
			return;
		}
		Assert.fail();
	}

	@Test
	public void createIdTokenMapping_withBlankPsuToken_thenFail() {
		IdTokenMapping idTokenMapping = new IdTokenMapping();
		idTokenMapping.setIdHash("test_idhash");
		idTokenMapping.setPsuToken("");
		try {
			idTokenMappingRepository.saveAndFlush(idTokenMapping);
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("psuToken")));
			return;
		}
		Assert.fail();
	}

	@Test
	public void createPublicKeyRegistry_withNullPsuToken_thenFail() {
		IdTokenMapping idTokenMapping = new IdTokenMapping();
		idTokenMapping.setIdHash("test_idhash");
		idTokenMapping.setPsuToken(null);
		try {
			idTokenMappingRepository.saveAndFlush(idTokenMapping);
		} catch (ConstraintViolationException e) {
			Assert.assertTrue(e.getConstraintViolations().stream()
					.anyMatch(v -> v.getPropertyPath().toString().equals("psuToken")));
			return;
		}
		Assert.fail();
	}
}
