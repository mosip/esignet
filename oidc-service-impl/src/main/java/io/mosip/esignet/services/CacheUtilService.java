/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.core.dto.IdPTransaction;
import io.mosip.esignet.core.dto.LinkTransactionMetadata;
import io.mosip.esignet.core.exception.DuplicateLinkCodeException;
import io.mosip.esignet.core.constants.Constants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;


@Slf4j
@Service
public class CacheUtilService {

    @Autowired
    CacheManager cacheManager;

    @Cacheable(value = Constants.PRE_AUTH_SESSION_CACHE, key = "#transactionId")
    public IdPTransaction setTransaction(String transactionId, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @CacheEvict(value = Constants.PRE_AUTH_SESSION_CACHE, key = "#transactionId")
    @Cacheable(value = Constants.AUTHENTICATED_CACHE, key = "#transactionId")
    public IdPTransaction setAuthenticatedTransaction(String transactionId,
                                                         IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @CacheEvict(value = Constants.AUTHENTICATED_CACHE, key = "#transactionId", condition = "#transactionId != null")
    @Cacheable(value = Constants.AUTH_CODE_GENERATED_CACHE, key = "#idPTransaction.getCodeHash()")
    public IdPTransaction setAuthCodeGeneratedTransaction(String transactionId, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @Caching(evict = {@CacheEvict(value = Constants.AUTH_CODE_GENERATED_CACHE, key = "#idPTransaction.getCodeHash()"),
            @CacheEvict(value = Constants.CONSENTED_CACHE, key = "#idPTransaction.getLinkedTransactionId()",
                    condition = "#idPTransaction.getLinkedTransactionId() != null"),
            @CacheEvict(value = Constants.LINKED_CODE_CACHE, key = "#idPTransaction.getLinkedCodeHash()",
                    condition = "#idPTransaction.getLinkedCodeHash() != null" )},
            cacheable = {@Cacheable(value = Constants.USERINFO_CACHE, key = "#accessTokenHash")})
    public IdPTransaction setUserInfoTransaction(String accessTokenHash, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @CacheEvict(value = Constants.AUTH_CODE_GENERATED_CACHE, key = "#codeHash", condition = "#codeHash != null")
    public void removeAuthCodeGeneratedTransaction(String codeHash) {
        log.debug("Evicting entry from authCodeGeneratedCache");
    }

    //---------------------------------------------- Linked authorization ----------------------------------------------

    @CacheEvict(value = Constants.PRE_AUTH_SESSION_CACHE, key = "#transactionId")
    @Cacheable(value = Constants.LINKED_SESSION_CACHE, key = "#idPTransaction.getLinkedTransactionId()")
    public IdPTransaction setLinkedTransaction(String transactionId, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @CacheEvict(value = Constants.LINKED_SESSION_CACHE, key = "#linkedTransactionId")
    @Cacheable(value = Constants.LINKED_AUTH_CACHE, key = "#linkedTransactionId")
    public IdPTransaction setLinkedAuthenticatedTransaction(String linkedTransactionId,
                                                      IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @CacheEvict(value = Constants.LINKED_AUTH_CACHE, key = "#idPTransaction.getLinkedTransactionId()")
    @Cacheable(value = Constants.CONSENTED_CACHE, key = "#linkedTransactionId")
    public IdPTransaction setLinkedConsentedTransaction(String linkedTransactionId, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @Caching(evict = {@CacheEvict(value = Constants.CONSENTED_CACHE, key = "#idPTransaction.getLinkedTransactionId()"),
            @CacheEvict(value = Constants.LINKED_CODE_CACHE, key = "#linkCodeHash")},
    cacheable = {@Cacheable(value = Constants.AUTH_CODE_GENERATED_CACHE, key = "#idPTransaction.getCodeHash()")})
    public IdPTransaction setLinkedAuthCodeTransaction(String linkCodeHash, String linkedTransactionId, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    public void setLinkCodeGenerated(String linkCodeHash, LinkTransactionMetadata transactionMetadata) {
        Object existingValue = cacheManager.getCache(Constants.LINK_CODE_GENERATED_CACHE).putIfAbsent(linkCodeHash, transactionMetadata);
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
    public IdPTransaction updateTransactionAndEvictLinkCode(String transactionId, String linkCodeHash, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    //------------------------------------------------------------------------------------------------------------------

    public IdPTransaction getPreAuthTransaction(String transactionId) {
        return cacheManager.getCache(Constants.PRE_AUTH_SESSION_CACHE).get(transactionId, IdPTransaction.class);
    }

    public IdPTransaction getAuthenticatedTransaction(String transactionId) {
        return cacheManager.getCache(Constants.AUTHENTICATED_CACHE).get(transactionId, IdPTransaction.class);
    }

    public IdPTransaction getAuthCodeTransaction(String codeHash) {
        return cacheManager.getCache(Constants.AUTH_CODE_GENERATED_CACHE).get(codeHash, IdPTransaction.class);
    }

    public IdPTransaction getConsentedTransaction(String linkedTransactionId) {
        return cacheManager.getCache(Constants.CONSENTED_CACHE).get(linkedTransactionId, IdPTransaction.class);
    }

    public IdPTransaction getUserInfoTransaction(String accessTokenHash) {
        return cacheManager.getCache(Constants.USERINFO_CACHE).get(accessTokenHash, IdPTransaction.class);
    }

    public LinkTransactionMetadata getLinkedTransactionMetadata(String linkCodeHash) {
        return cacheManager.getCache(Constants.LINKED_CODE_CACHE).get(linkCodeHash, LinkTransactionMetadata.class);
    }

    public LinkTransactionMetadata getLinkCodeGenerated(String linkCodeHash) {
        return cacheManager.getCache(Constants.LINK_CODE_GENERATED_CACHE).get(linkCodeHash, LinkTransactionMetadata.class);
    }

    public IdPTransaction getLinkedSessionTransaction(String linkTransactionId) {
        return cacheManager.getCache(Constants.LINKED_SESSION_CACHE).get(linkTransactionId, IdPTransaction.class);
    }

    public IdPTransaction getLinkedAuthTransaction(String linkTransactionId) {
        return cacheManager.getCache(Constants.LINKED_AUTH_CACHE).get(linkTransactionId, IdPTransaction.class);
    }
}
