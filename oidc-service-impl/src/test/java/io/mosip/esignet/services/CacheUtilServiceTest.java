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
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisScriptingCommands;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class CacheUtilServiceTest {

    @InjectMocks
    private CacheUtilService cacheUtilService;

    @Mock
    private Cache cache;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private RedisConnectionFactory redisConnectionFactory;

    @Mock
    private RedisConnection redisConnection;

    @Mock
    private RedisScriptingCommands redisScriptingCommands;

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

    // -------------------------------- checkNonce() tests --------------------------------

    @Test
    public void checkNonce_simpleCacheType_returnsOneWithoutRedis() {
        ReflectionTestUtils.setField(cacheUtilService, "cacheType", "simple");

        long result = cacheUtilService.checkNonce("test-nonce");

        Assertions.assertEquals(1L, result);
        verifyNoInteractions(redisConnectionFactory);
    }

    @Test
    public void checkNonce_scriptAlreadyLoaded_reusesHashAndClosesConnection() {
        ReflectionTestUtils.setField(cacheUtilService, "cacheType", "redis");
        ReflectionTestUtils.setField(cacheUtilService, "nonceValidity", 86400);
        ReflectionTestUtils.setField(cacheUtilService, "nonceScriptHash", "existing-sha");

        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.scriptingCommands()).thenReturn(redisScriptingCommands);
        when(redisScriptingCommands.scriptExists("existing-sha")).thenReturn(List.of(true));
        when(redisScriptingCommands.evalSha(eq("existing-sha"), eq(ReturnType.INTEGER), eq(1),
                any(byte[].class), any(byte[].class))).thenReturn(1L);

        long result = cacheUtilService.checkNonce("test-nonce");

        Assertions.assertEquals(1L, result);
        verify(redisScriptingCommands, never()).scriptLoad(any(byte[].class));
        verify(redisConnection).close();
    }

    @Test
    public void checkNonce_scriptNotLoaded_loadsScriptAndClosesConnection() {
        ReflectionTestUtils.setField(cacheUtilService, "cacheType", "redis");
        ReflectionTestUtils.setField(cacheUtilService, "nonceValidity", 86400);
        ReflectionTestUtils.setField(cacheUtilService, "nonceScriptHash", null);

        String newHash = "new-sha-hash";
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.scriptingCommands()).thenReturn(redisScriptingCommands);
        when(redisScriptingCommands.scriptLoad(any(byte[].class))).thenReturn(newHash);
        when(redisScriptingCommands.evalSha(eq(newHash), eq(ReturnType.INTEGER), eq(1),
                any(byte[].class), any(byte[].class))).thenReturn(0L);

        long result = cacheUtilService.checkNonce("unique-nonce");

        Assertions.assertEquals(0L, result);
        verify(redisScriptingCommands).scriptLoad(any(byte[].class));
        Assertions.assertEquals(newHash, ReflectionTestUtils.getField(cacheUtilService, "nonceScriptHash"));
        verify(redisConnection).close();
    }

    @Test
    public void checkNonce_scriptExistsReturnsFalse_reloadsScriptAndClosesConnection() {
        ReflectionTestUtils.setField(cacheUtilService, "cacheType", "redis");
        ReflectionTestUtils.setField(cacheUtilService, "nonceValidity", 86400);
        ReflectionTestUtils.setField(cacheUtilService, "nonceScriptHash", "stale-sha");

        String reloadedHash = "reloaded-sha";
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.scriptingCommands()).thenReturn(redisScriptingCommands);
        when(redisScriptingCommands.scriptExists("stale-sha")).thenReturn(List.of(false));
        when(redisScriptingCommands.scriptLoad(any(byte[].class))).thenReturn(reloadedHash);
        when(redisScriptingCommands.evalSha(eq(reloadedHash), eq(ReturnType.INTEGER), eq(1),
                any(byte[].class), any(byte[].class))).thenReturn(1L);

        long result = cacheUtilService.checkNonce("another-nonce");

        Assertions.assertEquals(1L, result);
        verify(redisScriptingCommands).scriptLoad(any(byte[].class));
        verify(redisConnection).close();
    }

    @Test
    public void checkNonce_redisThrowsException_connectionStillClosed() {
        ReflectionTestUtils.setField(cacheUtilService, "cacheType", "redis");
        ReflectionTestUtils.setField(cacheUtilService, "nonceValidity", 86400);
        ReflectionTestUtils.setField(cacheUtilService, "nonceScriptHash", null);

        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.scriptingCommands()).thenReturn(redisScriptingCommands);
        when(redisScriptingCommands.scriptLoad(any(byte[].class)))
                .thenThrow(new RuntimeException("Redis unavailable"));

        Assertions.assertThrows(RuntimeException.class, () ->
                cacheUtilService.checkNonce("fail-nonce")
        );

        verify(redisConnection).close();
    }

    @Test
    public void checkNonce_singleConnectionUsedForAllOperations() {
        ReflectionTestUtils.setField(cacheUtilService, "cacheType", "redis");
        ReflectionTestUtils.setField(cacheUtilService, "nonceValidity", 86400);
        ReflectionTestUtils.setField(cacheUtilService, "nonceScriptHash", null);

        String hash = "test-sha";
        when(redisConnectionFactory.getConnection()).thenReturn(redisConnection);
        when(redisConnection.scriptingCommands()).thenReturn(redisScriptingCommands);
        when(redisScriptingCommands.scriptLoad(any(byte[].class))).thenReturn(hash);
        when(redisScriptingCommands.evalSha(eq(hash), eq(ReturnType.INTEGER), eq(1),
                any(byte[].class), any(byte[].class))).thenReturn(1L);

        cacheUtilService.checkNonce("nonce-123");

        verify(redisConnectionFactory, times(1)).getConnection();
        verify(redisConnection).close();
    }

}
