/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import io.mosip.esignet.core.dto.LinkTransactionMetadata;
import io.mosip.esignet.core.dto.OIDCTransaction;

@RunWith(MockitoJUnitRunner.class)
public class CacheUtilServiceTest {
	
	@InjectMocks
	private CacheUtilService cacheUtilService;
	
	@Mock
	private Cache cache;
	
	@Mock
    private CacheManager cacheManager;
	
	@Test
	public void test_OIDCTransaction_cache() {
		OIDCTransaction transaction = new OIDCTransaction();
		transaction.setAuthTransactionId("123456789");
		transaction.setIndividualId("4258935620");

        Mockito.when(cache.get("123456789", OIDCTransaction.class)).thenReturn(transaction);
        Mockito.when(cacheManager.getCache(Mockito.anyString())).thenReturn(cache);
        
        Assert.assertEquals(cacheUtilService.setTransaction("123456789", transaction), transaction);
        Assert.assertEquals(cacheUtilService.setAuthenticatedTransaction("123456789", transaction), transaction);
        Assert.assertEquals(cacheUtilService.setAuthCodeGeneratedTransaction("123456789", transaction), transaction);
        Assert.assertEquals(cacheUtilService.setUserInfoTransaction("123456789", transaction), transaction);

        Assert.assertNotNull(cacheUtilService.getPreAuthTransaction("123456789"));
        Assert.assertEquals(cacheUtilService.getPreAuthTransaction("123456789").getIndividualId(), "4258935620");
        Assert.assertNotNull(cacheUtilService.getAuthenticatedTransaction("123456789"));
        Assert.assertNotNull(cacheUtilService.getAuthCodeTransaction("123456789"));
        Assert.assertNotNull(cacheUtilService.getConsentedTransaction("123456789"));
        Assert.assertNotNull(cacheUtilService.getUserInfoTransaction("123456789"));
        Assert.assertNotNull(cacheUtilService.getLinkedSessionTransaction("123456789"));
        Assert.assertNotNull(cacheUtilService.getLinkedAuthTransaction("123456789"));
	}
	
	@Test
	public void test_LinkTransactionMetadata_cache() {
		LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("123456789", "987654321");
		
		Mockito.when(cache.get("123456789", LinkTransactionMetadata.class)).thenReturn(linkTransactionMetadata);
        Mockito.when(cacheManager.getCache(Mockito.anyString())).thenReturn(cache);
        
        Assert.assertNotNull(cacheUtilService.getLinkedTransactionMetadata("123456789"));
        Assert.assertNotNull(cacheUtilService.getLinkCodeGenerated("123456789"));
	}
	
}
