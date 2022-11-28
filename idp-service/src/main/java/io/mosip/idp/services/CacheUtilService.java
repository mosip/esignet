/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.exception.DuplicateLinkCodeException;
import io.mosip.idp.core.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
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
    @Cacheable(value = Constants.AUTHENTICATED_CACHE, key = "#authCode")
    public IdPTransaction setConsentedTransaction(String authCode, String transactionId,
                                                  IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @CacheEvict(value = Constants.AUTHENTICATED_CACHE, key = "#idPTransaction.getCode()")
    @Cacheable(value = Constants.KYC_CACHE, key = "#accessTokenHash")
    public IdPTransaction setKycTransaction(String accessTokenHash, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    public void setLinkCode(String linkCode, String transactionId) throws DuplicateLinkCodeException {
        Object response = cacheManager.getCache(Constants.LINK_CODE_CACHE).putIfAbsent(linkCode, transactionId);
        if(response != null)
            throw new DuplicateLinkCodeException();
    }

    @CacheEvict(value = Constants.LINK_CODE_CACHE, key = "#linkCode")
    public String getLinkCode(String linkCode) {
        //link-code can be used only once
        return cacheManager.getCache(Constants.LINK_CODE_CACHE).get(linkCode, String.class);
    }

    @CacheEvict(value = Constants.PRE_AUTH_SESSION_CACHE, key = "#transactionId")
    @Cacheable(value = Constants.LINKED_SESSION_CACHE, key = "#transactionId")
    public IdPTransaction setLinkedSession(String transactionId, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    public IdPTransaction getPreAuthTransaction(String transactionId) {
        return cacheManager.getCache(Constants.PRE_AUTH_SESSION_CACHE).get(transactionId, IdPTransaction.class);
    }

    public IdPTransaction getAuthenticatedTransaction(String transactionId) {
        return cacheManager.getCache(Constants.AUTHENTICATED_CACHE).get(transactionId, IdPTransaction.class);
    }

    public IdPTransaction getKycTransaction(String accessTokenHash) {
        return cacheManager.getCache(Constants.KYC_CACHE).get(accessTokenHash, IdPTransaction.class);
    }

    public IdPTransaction getLinkedTransaction(String transactionId) {
        return cacheManager.getCache(Constants.LINKED_SESSION_CACHE).get(transactionId, IdPTransaction.class);
    }
}
