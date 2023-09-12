package io.mosip.esignet.vci.services;

import io.mosip.esignet.core.dto.vci.VCIssuanceTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.test.util.AopTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ContextConfiguration
@ExtendWith(SpringExtension.class)
public class VCICacheServiceTest {

    private static VCICacheService mock;

    private VCIssuanceTransaction vcIssuanceTransaction = new VCIssuanceTransaction();

    @Autowired
    VCICacheService vciCacheService;

    @EnableCaching
    @Configuration
    public static class CachingTestConfig {

        @Bean
        public VCICacheService vciCacheService() {
            return mock(VCICacheService.class);
        }

        @Bean
        public CacheManager cacheManager() {
            return new ConcurrentMapCacheManager("vcissuance");
        }

    }

    @BeforeEach
    void setUp() {
        //VCICacheService is a proxy around our mock. So, in order to use Mockito validations, we retrieve the actual
        // mock via AopTestUtils.getTargetObject
        mock = AopTestUtils.getTargetObject(vciCacheService);

        when(mock.setVCITransaction("access-token-hash", vcIssuanceTransaction))
                .thenReturn(vcIssuanceTransaction);

        when(mock.getVCITransaction("access-token-hash"))
                .thenReturn(vcIssuanceTransaction);
    }

    @Test
    public void test_setVCITransaction_thenPass() {
        assertEquals(vcIssuanceTransaction, vciCacheService.setVCITransaction("access-token-hash", vcIssuanceTransaction));
        assertEquals(vcIssuanceTransaction, vciCacheService.setVCITransaction("access-token-hash", vcIssuanceTransaction));
        assertEquals(vcIssuanceTransaction, vciCacheService.setVCITransaction("access-token-hash", vcIssuanceTransaction));
        verify(vciCacheService, times(3)).setVCITransaction("access-token-hash", vcIssuanceTransaction);
    }

    @Test
    public void test_getVCITransaction_thenPass() {
        assertEquals(vcIssuanceTransaction, vciCacheService.getVCITransaction("access-token-hash"));
        assertEquals(vcIssuanceTransaction, vciCacheService.getVCITransaction("access-token-hash"));
        assertEquals(null, vciCacheService.getVCITransaction("11access-token-hash"));
    }
}
