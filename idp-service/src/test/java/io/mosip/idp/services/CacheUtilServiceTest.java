package io.mosip.idp.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.assertj.core.internal.bytebuddy.asm.Advice.Thrown;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;

import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.dto.LinkTransactionMetadata;
import io.mosip.idp.core.exception.DuplicateLinkCodeException;
import io.mosip.idp.core.util.Constants;

@RunWith(MockitoJUnitRunner.class)
public class CacheUtilServiceTest {

	@InjectMocks
	CacheUtilService cacheUtilService;

	@Mock
	CacheManager cacheManager;
	
	@Mock
	ValueWrapper object;

	@Mock
	Cache cache;

	private IdPTransaction idPTransaction;

	private LinkTransactionMetadata transactionMetadata;

	@Before
	public void setup() {
		idPTransaction = new IdPTransaction();
		idPTransaction.setAuthTransactionId("5637465368573875875958");
		idPTransaction.setIndividualId("345635");

		transactionMetadata = new LinkTransactionMetadata("transactionId", "linkedTransactionId");
	}

	@Test
	public void setTransaction_thenPass() {
		String transactionId = "transactionId";
		IdPTransaction setTransaction = cacheUtilService.setTransaction(transactionId, idPTransaction);
		Assert.assertNotNull(setTransaction);
		Assert.assertEquals(idPTransaction, setTransaction);
	}

	@Test
	public void setAuthenticatedTransaction_thenPass() {
		String transactionId = "transactionId";
		IdPTransaction setAuthenticatedTransaction = cacheUtilService.setAuthenticatedTransaction(transactionId,
				idPTransaction);
		Assert.assertNotNull(setAuthenticatedTransaction);
		Assert.assertEquals(idPTransaction, setAuthenticatedTransaction);
	}

	@Test
	public void setAuthCodeGeneratedTransaction_thenPass() {
		String transactionId = "transactionId";
		IdPTransaction setAuthCodeGeneratedTransaction = cacheUtilService.setAuthCodeGeneratedTransaction(transactionId,
				idPTransaction);
		Assert.assertNotNull(setAuthCodeGeneratedTransaction);
		Assert.assertEquals(idPTransaction, setAuthCodeGeneratedTransaction);
	}

	@Test
	public void setUserInfoTransaction_thenPass() {
		String accessTokenHash = "accessTokenHash";
		IdPTransaction setUserInfoTransaction = cacheUtilService.setUserInfoTransaction(accessTokenHash,
				idPTransaction);
		Assert.assertNotNull(setUserInfoTransaction);
		Assert.assertEquals(idPTransaction, setUserInfoTransaction);
	}

	@Test
	public void setLinkedTransaction_thenPass() {
		String codeHash = "codeHash";
		IdPTransaction setLinkedTransaction = cacheUtilService.setLinkedTransaction(codeHash, idPTransaction);
		Assert.assertNotNull(setLinkedTransaction);
		Assert.assertEquals(idPTransaction, setLinkedTransaction);
	}

	@Test
	public void setLinkedAuthenticatedTransaction_thenPass() {
		String linkedTransactionId = "linkedTransactionId";
		IdPTransaction setAuthenticateLinkedTransaction = cacheUtilService
				.setLinkedAuthenticatedTransaction(linkedTransactionId, idPTransaction);
		Assert.assertNotNull(setAuthenticateLinkedTransaction);
		Assert.assertEquals(idPTransaction, setAuthenticateLinkedTransaction);
	}

	@Test
	@Cacheable(value = Constants.CONSENTED_CACHE, key = "#linkedTransactionId")
	public void setLinkedConsentedTransaction_thenPass() {
		String linkedTransactionId = "linkedTransactionId";
		IdPTransaction setLinkedConsentedTransaction = cacheUtilService
				.setLinkedConsentedTransaction(linkedTransactionId, idPTransaction);
		Assert.assertNotNull(setLinkedConsentedTransaction);
		Assert.assertEquals(idPTransaction, setLinkedConsentedTransaction);
	}

	@Test
	public void setLinkedAuthCodeTransaction_thenPass() {
		String codeHash = "codeHash";
		String linkedTransactionId = "linkedTransactionId";
		IdPTransaction setLinkedAuthCodeTransaction = cacheUtilService.setLinkedAuthCodeTransaction(codeHash,
				linkedTransactionId, idPTransaction);
		Assert.assertNotNull(setLinkedAuthCodeTransaction);
		Assert.assertEquals(idPTransaction, setLinkedAuthCodeTransaction);
	}

	@Test
	public void setLinkedCode_thenPass() {
		String linkCodeHash = "codeHash";
		LinkTransactionMetadata linkedTransactionMeta = cacheUtilService.setLinkedCode(linkCodeHash,
				transactionMetadata);
		Assert.assertNotNull(linkedTransactionMeta);
		Assert.assertEquals(transactionMetadata, linkedTransactionMeta);
	}

	@Test(expected = DuplicateLinkCodeException.class)
	public void setLinkCodeGenerated_thenPass() {

		String linkCodeHash = "linkCodeHash";
		when(cacheManager.getCache("linkcodegenerated")).thenReturn(cache);
		when(cache.putIfAbsent(linkCodeHash, transactionMetadata)).thenReturn(object);

		cacheUtilService.setLinkCodeGenerated(linkCodeHash, transactionMetadata);
	}

	@Test
	public void getConsentedTransaction_thenPass() {
		String linkedTransactionId = "linkedTransactionId";
		when(cacheManager.getCache("consented")).thenReturn(cache);
		when(cache.get(linkedTransactionId, IdPTransaction.class)).thenReturn(idPTransaction);
		IdPTransaction consentedTransaction = cacheUtilService.getConsentedTransaction(linkedTransactionId);
		Assert.assertNotNull(consentedTransaction);
		Assert.assertEquals(idPTransaction, consentedTransaction);

	}

	@Test
	public void getLinkedTransactionMetadata_thenPass() {

		String linkCodeHash = "linkCodeHash";
		when(cacheManager.getCache("linkedcode")).thenReturn(cache);
		when(cache.get(linkCodeHash, LinkTransactionMetadata.class)).thenReturn(transactionMetadata);
		LinkTransactionMetadata linkedTransactionMetadata = cacheUtilService.getLinkedTransactionMetadata(linkCodeHash);
		Assert.assertNotNull(linkedTransactionMetadata);
		Assert.assertEquals(transactionMetadata, linkedTransactionMetadata);
	}

	@Test
	public void getLinkCodeGenerated_thenPass() {

		String linkCodeHash = "linkCodeHash";
		when(cacheManager.getCache("linkcodegenerated")).thenReturn(cache);
		when(cache.get(linkCodeHash, LinkTransactionMetadata.class)).thenReturn(transactionMetadata);
		LinkTransactionMetadata linkCodeGenerated = cacheUtilService.getLinkCodeGenerated(linkCodeHash);
		Assert.assertNotNull(linkCodeGenerated);
		Assert.assertEquals(transactionMetadata, linkCodeGenerated);
	}

	@Test
	public void getLinkedSessionTransaction_thenPass() {

		String linkTransactionId = "linkTransactionId";
		when(cacheManager.getCache("linked")).thenReturn(cache);
		when(cache.get(linkTransactionId, IdPTransaction.class)).thenReturn(idPTransaction);
		IdPTransaction linkedSessionTransaction = cacheUtilService.getLinkedSessionTransaction(linkTransactionId);
		Assert.assertNotNull(linkedSessionTransaction);
		Assert.assertEquals(idPTransaction, linkedSessionTransaction);
	}

	@Test
	public void getLinkedAuthTransaction_thenPass() {

		String linkTransactionId = "linkTransactionId";
		when(cacheManager.getCache("linkedauth")).thenReturn(cache);
		when(cache.get(linkTransactionId, IdPTransaction.class)).thenReturn(idPTransaction);
		IdPTransaction linkedAuthTransaction = cacheUtilService.getLinkedAuthTransaction(linkTransactionId);
		Assert.assertNotNull(linkedAuthTransaction);
		Assert.assertEquals(idPTransaction, linkedAuthTransaction);
	}

	@Test
	public void getPreAuthTransaction_thenPass() {

		String transactionId = "transactionId";
		when(cacheManager.getCache("preauth")).thenReturn(cache);
		when(cache.get(transactionId, IdPTransaction.class)).thenReturn(idPTransaction);
		IdPTransaction preAuthTransaction = cacheUtilService.getPreAuthTransaction(transactionId);
		Assert.assertNotNull(preAuthTransaction);
		Assert.assertEquals(idPTransaction, preAuthTransaction);
	}

	@Test
	public void getAuthenticatedTransaction_thenPass() {

		String transactionId = "transactionId";
		when(cacheManager.getCache("authenticated")).thenReturn(cache);
		when(cache.get(transactionId, IdPTransaction.class)).thenReturn(idPTransaction);
		IdPTransaction authenticatedTransaction = cacheUtilService.getAuthenticatedTransaction(transactionId);
		Assert.assertNotNull(authenticatedTransaction);
		Assert.assertEquals(idPTransaction, authenticatedTransaction);
	}

	@Test
	public void getAuthCodeTransaction_thenPass() {

		String codeHash = "codeHash";
		when(cacheManager.getCache("authcodegenerated")).thenReturn(cache);
		when(cache.get(codeHash, IdPTransaction.class)).thenReturn(idPTransaction);
		IdPTransaction authCodeTransaction = cacheUtilService.getAuthCodeTransaction(codeHash);
		Assert.assertNotNull(authCodeTransaction);
		Assert.assertEquals(idPTransaction, authCodeTransaction);
	}

	@Test
	public void getUserInfoTransaction_thenPass() {

		String accessTokenHash = "accessTokenHash";
		when(cacheManager.getCache("userinfo")).thenReturn(cache);
		when(cache.get(accessTokenHash, IdPTransaction.class)).thenReturn(idPTransaction);
		IdPTransaction userInfoTransaction = cacheUtilService.getUserInfoTransaction(accessTokenHash);
		Assert.assertNotNull(userInfoTransaction);
		Assert.assertEquals(idPTransaction, userInfoTransaction);
	}

}
