/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.TokenService;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CacheUtilService {

    private Cache pre_auth_cache = null;
    private Cache authenticate_cache = null;
    private Cache kyc_cache = null;

    @Autowired
    CacheManager cacheManager;

    @Autowired
    TokenService tokenService;

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

    public IdPTransaction getPreAuthTransaction(String transactionId) {
        if(pre_auth_cache == null)
            pre_auth_cache = cacheManager.getCache(Constants.PRE_AUTH_SESSION_CACHE);

        return pre_auth_cache.get(transactionId, IdPTransaction.class);
    }

    public IdPTransaction getAuthenticatedTransaction(String transactionId) {
        if(authenticate_cache == null)
            authenticate_cache = cacheManager.getCache(Constants.AUTHENTICATED_CACHE);

        return authenticate_cache.get(transactionId, IdPTransaction.class);
    }

    public IdPTransaction getKycTransaction(String accessTokenHash) {
        if(kyc_cache == null)
            kyc_cache = cacheManager.getCache(Constants.KYC_CACHE);

        return kyc_cache.get(accessTokenHash, IdPTransaction.class);
    }
}
