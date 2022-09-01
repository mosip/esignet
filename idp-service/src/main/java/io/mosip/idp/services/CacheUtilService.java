package io.mosip.idp.services;

import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.util.Constants;
import org.springframework.beans.factory.annotation.Autowired;
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
    @Cacheable(value = Constants.AUTHENTICATED_CACHE, key = "#authCode")
    public IdPTransaction setAuthenticatedTransaction(String authCode, String transactionId,
                                                         IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @CacheEvict(value = Constants.AUTHENTICATED_CACHE, key = "#idPTransaction.getCode()")
    @Cacheable(value = Constants.KYC_CACHE, key = "#accessTokenHash")
    public IdPTransaction setKycTransaction(String accessTokenHash, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    public IdPTransaction getPreAuthTransaction(String transactionId) {
        return cacheManager.getCache(Constants.PRE_AUTH_SESSION_CACHE).get(transactionId, IdPTransaction.class);
    }

    public IdPTransaction getAuthenticatedTransaction(String authCode) {
        return cacheManager.getCache(Constants.AUTHENTICATED_CACHE).get(authCode, IdPTransaction.class);
    }

    public IdPTransaction getKycTransaction(String accessTokenHash) {
        return cacheManager.getCache(Constants.KYC_CACHE).get(accessTokenHash, IdPTransaction.class);
    }
}
