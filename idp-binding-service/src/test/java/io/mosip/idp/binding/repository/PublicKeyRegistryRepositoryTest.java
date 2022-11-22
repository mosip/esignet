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
	public void createPublicKeyRegistryWithValidDetail() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIndividualId("2337511530");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
        publicKeyRegistry.setExpiresOn(LocalDateTime.now());
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
        publicKeyRegistry = publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
        Assert.assertNotNull(publicKeyRegistry);

        Optional<PublicKeyRegistry> result = publicKeyRegistryRepository.findByIndividualId("2337511530");
        Assert.assertTrue(result.isPresent());
        
        result = publicKeyRegistryRepository.findById("2337511530");
        Assert.assertTrue(result.isPresent());

        result = publicKeyRegistryRepository.findByIndividualId("0000000");
        Assert.assertFalse(result.isPresent());
    }
	
	@Test
    public void createPublicKeyRegistryWithBlankIndividualId() {
        PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
        publicKeyRegistry.setIndividualId("");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
        publicKeyRegistry.setExpiresOn(LocalDateTime.now());
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
        try {
            publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
        } catch (ConstraintViolationException e) {
            Assert.assertTrue(e.getConstraintViolations().stream()
                    .anyMatch( v -> v.getPropertyPath().toString().equals("individualId")));
            return;
        }
        Assert.fail();
    }
	
	@Test
    public void createPublicKeyRegistryWithBlankPublicKey() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
        publicKeyRegistry.setIndividualId("2337511530");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("");
        publicKeyRegistry.setExpiresOn(LocalDateTime.now());
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
    public void createPublicKeyRegistryWithNullPublicKey() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
        publicKeyRegistry.setIndividualId("2337511530");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey(null);
        publicKeyRegistry.setExpiresOn(LocalDateTime.now());
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
	public void createPublicKeyRegistryWithValidExpiryDate() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIndividualId("2337511530");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
        publicKeyRegistry.setExpiresOn(LocalDateTime.of(2040, 11, 11, 11, 11));
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
        publicKeyRegistry = publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
        Assert.assertNotNull(publicKeyRegistry);

        PublicKeyRegistry result = publicKeyRegistryRepository.findByIndividualIdAndExpiresOnGreaterThan("2337511530", LocalDateTime.now());
        Assert.assertNotNull(result);

        result = publicKeyRegistryRepository.findByIndividualIdAndExpiresOnGreaterThan("0000000", LocalDateTime.now());
        Assert.assertNull(result);
    }
	
	@Test
	public void createPublicKeyRegistryWithInValidExpiryDate() {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIndividualId("2337511530");
		publicKeyRegistry.setPsuToken("test_token");
		publicKeyRegistry.setPublicKey("test_public_key");
        publicKeyRegistry.setExpiresOn(LocalDateTime.of(2020, 11, 11, 11, 11));
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now());
        publicKeyRegistry = publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
        Assert.assertNotNull(publicKeyRegistry);

        PublicKeyRegistry result = publicKeyRegistryRepository.findByIndividualIdAndExpiresOnGreaterThan("2337511530", LocalDateTime.now());
        Assert.assertNull(result);
    }
}
