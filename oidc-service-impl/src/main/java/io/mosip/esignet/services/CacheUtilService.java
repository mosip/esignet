/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.LinkTransactionMetadata;
import io.mosip.esignet.core.dto.ApiRateLimit;
import io.mosip.esignet.core.exception.DuplicateLinkCodeException;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA3_256;


@Slf4j
@Service
public class CacheUtilService {

    private static final String NONCE_CHECK_SCRIPT = "if redis.call(\"EXISTS\", KEYS[1]) == 0 then\n" +
            "    redis.call(\"SETEX\", KEYS[1], tonumber(ARGV[1]), \"1\")\n" +
            "    return 1\n" +
            "else\n" +
            "    return 0\n" +
            "end";
    private String nonceScriptHash = null;
    private static String NONCE_KEY = "nonce::%s";

    @Value("${mosip.esignet.nonce-expire-seconds:86400}")
    private int nonceValidity;

    @Value("${spring.cache.type}")
    private String cacheType;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    private RedisConnectionFactory redisConnectionFactory;

    @Cacheable(value = Constants.PRE_AUTH_SESSION_CACHE, key = "#transactionId")
    public OIDCTransaction setTransaction(String transactionId, OIDCTransaction oidcTransaction) {
        return oidcTransaction;
    }

    @Caching(evict = {@CacheEvict(value = Constants.PRE_AUTH_SESSION_CACHE, key = "#transactionId"),
            @CacheEvict(value = Constants.HALTED_CACHE, key = "#transactionId")},
    cacheable = {@Cacheable(value = Constants.AUTHENTICATED_CACHE, key = "#transactionId")})
    public OIDCTransaction setAuthenticatedTransaction(String transactionId,
                                                       OIDCTransaction oidcTransaction) {
        return oidcTransaction;
    }

    @CacheEvict(value = Constants.AUTHENTICATED_CACHE, key = "#transactionId", condition = "#transactionId != null")
    @Cacheable(value = Constants.AUTH_CODE_GENERATED_CACHE, key = "#oidcTransaction.getCodeHash()")
    public OIDCTransaction setAuthCodeGeneratedTransaction(String transactionId, OIDCTransaction oidcTransaction) {
        return oidcTransaction;
    }

    @Caching(evict = {@CacheEvict(value = Constants.AUTH_CODE_GENERATED_CACHE, key = "#oidcTransaction.getCodeHash()"),
            @CacheEvict(value = Constants.CONSENTED_CACHE, key = "#oidcTransaction.getLinkedTransactionId()",
                    condition = "#oidcTransaction.getLinkedTransactionId() != null"),
            @CacheEvict(value = Constants.LINKED_CODE_CACHE, key = "#oidcTransaction.getLinkedCodeHash()",
                    condition = "#oidcTransaction.getLinkedCodeHash() != null" )},
            cacheable = {@Cacheable(value = Constants.USERINFO_CACHE, key = "#accessTokenHash")})
    public OIDCTransaction setUserInfoTransaction(String accessTokenHash, OIDCTransaction oidcTransaction) {
        return oidcTransaction;
    }

    @CacheEvict(value = Constants.AUTH_CODE_GENERATED_CACHE, key = "#codeHash", condition = "#codeHash != null")
    public void removeAuthCodeGeneratedTransaction(String codeHash) {
        log.debug("Evicting entry from authCodeGeneratedCache");
    }

    @CacheEvict(value = Constants.AUTHENTICATED_CACHE, key = "#transactionId")
    @Cacheable(value = Constants.HALTED_CACHE, key = "#transactionId")
    public OIDCTransaction setHaltedTransaction(String transactionId, OIDCTransaction oidcTransaction) {
        return oidcTransaction;
    }

    @CacheEvict(value = Constants.HALTED_CACHE, key = "#transactionId")
    public void removeHaltedTransaction(String transactionId) {
        log.debug("Evicting entry from HALTED_CACHE");
    }

    public long checkNonce(String nonce) {
        if("simple".equalsIgnoreCase(cacheType))
            return 1L;

        if (redisConnectionFactory.getConnection() != null) {
            if (isScriptNotLoaded(nonceScriptHash)) {
                nonceScriptHash = redisConnectionFactory.getConnection().scriptingCommands().scriptLoad(NONCE_CHECK_SCRIPT.getBytes());
            }
            log.info("Running NONCE_CHECK_SCRIPT script: {}", nonceScriptHash);
            final String key = String.format(NONCE_KEY, nonce);
            return redisConnectionFactory.getConnection().scriptingCommands().evalSha(
                    nonceScriptHash,
                    ReturnType.INTEGER,
                    1,  // Number of keys
                    key.getBytes(), // The Redis hash name (key)
                    String.valueOf(nonceValidity).getBytes(StandardCharsets.UTF_8) // ttl
            );
        }
        return 0;
    }

    //---------------------------------------------- Linked authorization ----------------------------------------------

    @CacheEvict(value = Constants.PRE_AUTH_SESSION_CACHE, key = "#transactionId")
    @Cacheable(value = Constants.LINKED_SESSION_CACHE, key = "#oidcTransaction.getLinkedTransactionId()")
    public OIDCTransaction setLinkedTransaction(String transactionId, OIDCTransaction oidcTransaction) {
        return oidcTransaction;
    }

    @CacheEvict(value = Constants.LINKED_SESSION_CACHE, key = "#linkedTransactionId")
    @Cacheable(value = Constants.LINKED_AUTH_CACHE, key = "#linkedTransactionId")
    public OIDCTransaction setLinkedAuthenticatedTransaction(String linkedTransactionId,
                                                             OIDCTransaction oidcTransaction) {
        return oidcTransaction;
    }

    @CacheEvict(value = Constants.LINKED_AUTH_CACHE, key = "#oidcTransaction.getLinkedTransactionId()")
    @Cacheable(value = Constants.CONSENTED_CACHE, key = "#linkedTransactionId")
    public OIDCTransaction setLinkedConsentedTransaction(String linkedTransactionId, OIDCTransaction oidcTransaction) {
        return oidcTransaction;
    }

    @Caching(evict = {@CacheEvict(value = Constants.CONSENTED_CACHE, key = "#oidcTransaction.getLinkedTransactionId()"),
            @CacheEvict(value = Constants.LINKED_CODE_CACHE, key = "#linkCodeHash")},
    cacheable = {@Cacheable(value = Constants.AUTH_CODE_GENERATED_CACHE, key = "#oidcTransaction.getCodeHash()")})
    public OIDCTransaction setLinkedAuthCodeTransaction(String linkCodeHash, String linkedTransactionId, OIDCTransaction oidcTransaction) {
        return oidcTransaction;
    }

    public void setLinkCodeGenerated(String linkCodeHash, LinkTransactionMetadata transactionMetadata) {
        Object existingValue = cacheManager.getCache(Constants.LINK_CODE_GENERATED_CACHE).putIfAbsent(linkCodeHash, transactionMetadata);	//NOSONAR getCache() will not be returning null here.
        if(existingValue != null)
            throw new DuplicateLinkCodeException();
    }

    @CacheEvict(value = Constants.LINK_CODE_GENERATED_CACHE, key = "#linkCodeHash")
    @Cacheable(value = Constants.LINKED_CODE_CACHE, key = "#linkCodeHash")
    public LinkTransactionMetadata setLinkedCode(String linkCodeHash, LinkTransactionMetadata transactionMetadata) {
        return transactionMetadata;
    }

    @CacheEvict(value = Constants.LINK_CODE_GENERATED_CACHE, key = "#linkCodeHash", condition = "#linkCodeHash != null")
    @Cacheable(value = Constants.PRE_AUTH_SESSION_CACHE, key = "#transactionId")
    public OIDCTransaction updateTransactionAndEvictLinkCode(String transactionId, String linkCodeHash, OIDCTransaction oidcTransaction) {
        return oidcTransaction;
    }

    @CachePut(value = Constants.RATE_LIMIT_CACHE, key = "#transactionId")
    public ApiRateLimit saveApiRateLimit(String transactionId, ApiRateLimit apiRateLimit) {
        return apiRateLimit;
    }

    @Cacheable(value = Constants.BLOCKED_CACHE, key = "#individualIdHash")
    public String blockIndividualId(String individualIdHash) {
        return individualIdHash;
    }

    @CachePut(value = Constants.PRE_AUTH_SESSION_CACHE, key = "#transactionId")
    public OIDCTransaction updateIndividualIdHashInPreAuthCache(String transactionId, String individualId) {
        OIDCTransaction oidcTransaction = cacheManager.getCache(Constants.PRE_AUTH_SESSION_CACHE).get(transactionId, OIDCTransaction.class);//NOSONAR getCache() will not be returning null here.
        if (oidcTransaction != null) {
            oidcTransaction.setIndividualIdHash(IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, individualId));
        }
        return oidcTransaction;
    }

    //------------------------------------------------------------------------------------------------------------------

    public OIDCTransaction getPreAuthTransaction(String transactionId) {
        return cacheManager.getCache(Constants.PRE_AUTH_SESSION_CACHE).get(transactionId, OIDCTransaction.class); //NOSONAR getCache() will not be returning null here.
    }

    public OIDCTransaction getAuthenticatedTransaction(String transactionId) {
        return cacheManager.getCache(Constants.AUTHENTICATED_CACHE).get(transactionId, OIDCTransaction.class);	//NOSONAR getCache() will not be returning null here.
    }

    public OIDCTransaction getAuthCodeTransaction(String codeHash) {
        return cacheManager.getCache(Constants.AUTH_CODE_GENERATED_CACHE).get(codeHash, OIDCTransaction.class);	//NOSONAR getCache() will not be returning null here.
    }

    public OIDCTransaction getConsentedTransaction(String linkedTransactionId) {
        return cacheManager.getCache(Constants.CONSENTED_CACHE).get(linkedTransactionId, OIDCTransaction.class);	//NOSONAR getCache() will not be returning null here.
    }

    public OIDCTransaction getUserInfoTransaction(String accessTokenHash) {
        return cacheManager.getCache(Constants.USERINFO_CACHE).get(accessTokenHash, OIDCTransaction.class);	//NOSONAR getCache() will not be returning null here.
    }

    public LinkTransactionMetadata getLinkedTransactionMetadata(String linkCodeHash) {
        return cacheManager.getCache(Constants.LINKED_CODE_CACHE).get(linkCodeHash, LinkTransactionMetadata.class);	//NOSONAR getCache() will not be returning null here.
    }

    public LinkTransactionMetadata getLinkCodeGenerated(String linkCodeHash) {
        return cacheManager.getCache(Constants.LINK_CODE_GENERATED_CACHE).get(linkCodeHash, LinkTransactionMetadata.class);	//NOSONAR getCache() will not be returning null here.
    }

    public OIDCTransaction getLinkedSessionTransaction(String linkTransactionId) {
        return cacheManager.getCache(Constants.LINKED_SESSION_CACHE).get(linkTransactionId, OIDCTransaction.class);	//NOSONAR getCache() will not be returning null here.
    }

    public OIDCTransaction getLinkedAuthTransaction(String linkTransactionId) {
        return cacheManager.getCache(Constants.LINKED_AUTH_CACHE).get(linkTransactionId, OIDCTransaction.class);	//NOSONAR getCache() will not be returning null here.
    }

    public OIDCTransaction getHaltedTransaction(String transactionId) {
        return cacheManager.getCache(Constants.HALTED_CACHE).get(transactionId, OIDCTransaction.class);	//NOSONAR getCache() will not be returning null here.
    }

    public ApiRateLimit getApiRateLimitTransaction(String transactionId) {
        return cacheManager.getCache(Constants.RATE_LIMIT_CACHE).get(transactionId, ApiRateLimit.class); //NOSONAR getCache() will not be returning null here.
    }

    public boolean isIndividualIdBlocked(String individualIdHash) {
        String idHash = cacheManager.getCache(Constants.BLOCKED_CACHE).get(individualIdHash, String.class); //NOSONAR getCache() will not be returning null here.
        return idHash != null;
    }

    public String getSharedIDVResult(String transactionId) {
        return cacheManager.getCache(Constants.SHARED_IDV_RESULT).get(transactionId, String.class); //NOSONAR getCache() will not be returning null here.
    }

    private boolean isScriptNotLoaded(String scriptHash) {
        if(scriptHash == null) return true;
        List<Boolean> scriptExists = redisConnectionFactory.getConnection().scriptingCommands().scriptExists(scriptHash);
        return scriptExists == null || !scriptExists.get(0);
    }
}
