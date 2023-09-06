package io.mosip.esignet.vci.services;

import io.mosip.esignet.core.dto.vci.VCIssuanceTransaction;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class VCICacheService {

    private static final String VCISSUANCE_CACHE = "vcissuance";

    @Cacheable(value = VCISSUANCE_CACHE, key = "#accessTokenHash", unless = "#vcIssuanceTransaction != null")
    public VCIssuanceTransaction getSetVCITransaction(String accessTokenHash, VCIssuanceTransaction vcIssuanceTransaction) {
        return vcIssuanceTransaction;
    }
}

