/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.core.dto.ApiRateLimit;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.Cache.ValueWrapper;
import org.springframework.cache.support.SimpleValueWrapper;
import org.springframework.cache.CacheManager;

import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.dto.LinkTransactionMetadata;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.DuplicateLinkCodeException;

@ExtendWith(MockitoExtension.class)
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
        transaction.setLinkedTransactionId("987654321");
        transaction.setLinkedCodeHash("68392");
        transaction.setIndividualId("4258935620");

        Mockito.when(cache.get("123456789", OIDCTransaction.class)).thenReturn(transaction);
        Mockito.when(cache.get("123456789", ApiRateLimit.class)).thenReturn(new ApiRateLimit());
        Mockito.when(cacheManager.getCache(Mockito.anyString())).thenReturn(cache);

        Assertions.assertEquals(cacheUtilService.setTransaction("123456789", transaction), transaction);
        Assertions.assertEquals(cacheUtilService.setAuthenticatedTransaction("123456789", transaction), transaction);
        Assertions.assertEquals(cacheUtilService.setAuthCodeGeneratedTransaction("123456789", transaction), transaction);
        Assertions.assertEquals(cacheUtilService.setUserInfoTransaction("123456789", transaction), transaction);

        Assertions.assertNotNull(cacheUtilService.getPreAuthTransaction("123456789"));
        Assertions.assertEquals(cacheUtilService.getPreAuthTransaction("123456789").getIndividualId(), "4258935620");
        Assertions.assertNotNull(cacheUtilService.getAuthenticatedTransaction("123456789"));
        Assertions.assertNotNull(cacheUtilService.getAuthCodeTransaction("123456789"));
        Assertions.assertNotNull(cacheUtilService.getConsentedTransaction("123456789"));
        Assertions.assertNotNull(cacheUtilService.getUserInfoTransaction("123456789"));
        Assertions.assertNotNull(cacheUtilService.getLinkedSessionTransaction("123456789"));
        Assertions.assertNotNull(cacheUtilService.getLinkedAuthTransaction("123456789"));

        Assertions.assertNotNull(cacheUtilService.setLinkedTransaction("123456789", transaction));
        Assertions.assertNotNull(cacheUtilService.setLinkedAuthenticatedTransaction("987654321", transaction));
        Assertions.assertNotNull(cacheUtilService.setLinkedConsentedTransaction("987654321", transaction));
        Assertions.assertNotNull(cacheUtilService.setLinkedAuthCodeTransaction("68392", "987654321", transaction));

        cacheUtilService.removeAuthCodeGeneratedTransaction("68392");
        Assertions.assertNotNull(cacheUtilService.updateTransactionAndEvictLinkCode("123456789", "68392", transaction));

        Assertions.assertNotNull(cacheUtilService.setHaltedTransaction("123456789", transaction));
        cacheUtilService.removeHaltedTransaction("123456789");

        Assertions.assertNotNull(cacheUtilService.saveApiRateLimit("api-rate-limit-id", new ApiRateLimit()));
        Assertions.assertNotNull(cacheUtilService.blockIndividualId("individualIdHash"));

        Assertions.assertNotNull(cacheUtilService.updateIndividualIdHashInPreAuthCache("123456789", "individualIdHash"));
        Assertions.assertNotNull(cacheUtilService.getHaltedTransaction("123456789"));
        Assertions.assertNotNull(cacheUtilService.getApiRateLimitTransaction("123456789"));
        Assertions.assertFalse(cacheUtilService.isIndividualIdBlocked("individualIdHash"));
    }

    @Test
    public void test_LinkTransactionMetadata_cache() {
        LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("123456789", "987654321");

        Mockito.when(cache.get("123456789", LinkTransactionMetadata.class)).thenReturn(linkTransactionMetadata);
        Mockito.when(cacheManager.getCache(Mockito.anyString())).thenReturn(cache);

        Assertions.assertNotNull(cacheUtilService.getLinkedTransactionMetadata("123456789"));
        Assertions.assertNotNull(cacheUtilService.getLinkCodeGenerated("123456789"));

        Assertions.assertNotNull(cacheUtilService.setLinkedCode("987654321", linkTransactionMetadata));
    }

    @Test
    public void test_setLinkCodeGenerated_thenThowException() {
        Assertions.assertThrows(DuplicateLinkCodeException.class, () -> {
            LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("123456789", "987654321");

            Mockito.when(cacheManager.getCache(Constants.LINK_CODE_GENERATED_CACHE)).thenReturn(cache);
            ValueWrapper valueWrapper = new SimpleValueWrapper(new Object());
            Mockito.when(cache.putIfAbsent("1234", linkTransactionMetadata)).thenReturn(valueWrapper);

            cacheUtilService.setLinkCodeGenerated("1234", linkTransactionMetadata);
        });
    }

}
