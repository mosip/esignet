/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.dto.LinkTransactionMetadata;
import io.mosip.idp.core.exception.DuplicateLinkCodeException;
import io.mosip.idp.core.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;


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

    @CacheEvict(value = Constants.AUTHENTICATED_CACHE, key = "#transactionId")
    @Cacheable(value = Constants.CONSENTED_CACHE, key = "#idPTransaction.getCodeHash()")
    public IdPTransaction setConsentedTransaction(String transactionId, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @Caching( evict = { @CacheEvict(value = Constants.CONSENTED_CACHE, key = "#idPTransaction.getCodeHash()"),
                        @CacheEvict(value = Constants.LINK_CODE_HASH_CACHE, key = "#idPTransaction.getLinkCodeHash()",
                                condition = "#idPTransaction.getLinkCodeHash() != null")},
              cacheable = { @Cacheable(value = Constants.KYC_CACHE, key = "#accessTokenHash") })
    public IdPTransaction setKycTransaction(String accessTokenHash, IdPTransaction idPTransaction) {
        return idPTransaction;
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
    @Cacheable(value = Constants.CONSENTED_CACHE, key = "#idPTransaction.getCodeHash()")
    public IdPTransaction setLinkedConsentedTransaction(String linkedTransactionId, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    public void setLinkCode(String linkCode, LinkTransactionMetadata transactionMetadata) {
        Object existingValue = cacheManager.getCache(Constants.LINK_CODE_HASH_CACHE).putIfAbsent(linkCode, transactionMetadata);
        if(existingValue != null)
            throw new DuplicateLinkCodeException();
    }

    public void updateLinkCode(String linkCode, LinkTransactionMetadata transactionMetadata) {
        cacheManager.getCache(Constants.LINK_CODE_HASH_CACHE).put(linkCode, transactionMetadata);
    }

    //------------------------------------------------------------------------------------------------------------------

    public IdPTransaction getPreAuthTransaction(String transactionId) {
        return cacheManager.getCache(Constants.PRE_AUTH_SESSION_CACHE).get(transactionId, IdPTransaction.class);
    }

    public IdPTransaction getAuthenticatedTransaction(String transactionId) {
        return cacheManager.getCache(Constants.AUTHENTICATED_CACHE).get(transactionId, IdPTransaction.class);
    }

    public IdPTransaction getConsentedTransaction(String codeHash) {
        return cacheManager.getCache(Constants.CONSENTED_CACHE).get(codeHash, IdPTransaction.class);
    }

    public IdPTransaction getKycTransaction(String accessTokenHash) {
        return cacheManager.getCache(Constants.KYC_CACHE).get(accessTokenHash, IdPTransaction.class);
    }

    public LinkTransactionMetadata getLinkedTransactionMetadata(String linkCode) {
        return cacheManager.getCache(Constants.LINK_CODE_HASH_CACHE).get(linkCode, LinkTransactionMetadata.class);
    }

    public IdPTransaction getLinkedSessionTransaction(String linkTransactionId) {
        return cacheManager.getCache(Constants.LINKED_SESSION_CACHE).get(linkTransactionId, IdPTransaction.class);
    }

    public IdPTransaction getLinkedAuthTransaction(String linkTransactionId) {
        return cacheManager.getCache(Constants.LINKED_AUTH_CACHE).get(linkTransactionId, IdPTransaction.class);
    }
}
