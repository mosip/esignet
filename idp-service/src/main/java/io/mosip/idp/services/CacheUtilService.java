package io.mosip.idp.services;

import io.mosip.idp.core.dto.IdPTransaction;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Service
public class CacheUtilService {

    @Cacheable(value = "transactions", key = "#transactionId", unless = "#result != null")
    public IdPTransaction getSetTransaction(String transactionId, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @CacheEvict(value = "transactions", key = "#transactionId", condition = "#idPTransaction != null")
    @Cacheable(value = "authenticated", key = "#authCode", unless = "#result != null")
    public IdPTransaction getSetAuthenticatedTransaction(String authCode, String transactionId,
                                                         IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @CacheEvict(value = "authenticated", key = "#idPTransaction.getCode()", condition = "#idPTransaction != null")
    @Cacheable(value = "kyc", key = "#accessToken", unless = "#result != null")
    public IdPTransaction getSetKycTransaction(String accessToken, IdPTransaction idPTransaction) {
        return idPTransaction;
    }
}
