package io.mosip.idp.config;

import com.google.common.cache.CacheBuilder;
import io.mosip.idp.core.util.Constants;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(value = "spring.cache.type", havingValue = "SIMPLE")
@Configuration
public class SimpleCacheConfig extends CachingConfigurerSupport {

    @Value("#{${mosip.idp.cache.size}}")
    private Map<String, Integer> cacheMaxSize;

    @Value("#{${mosip.idp.cache.expire-in-seconds}}")
    private Map<String, Integer> cacheExpireInSeconds;


    @Bean
    @Override
    public CacheManager cacheManager() {
        ConcurrentMapCacheManager cacheManager = new ConcurrentMapCacheManager() {

            @Override
            protected Cache createConcurrentMapCache(final String name) {
                return new ConcurrentMapCache(name,
                        CacheBuilder.newBuilder()
                                .expireAfterWrite(cacheExpireInSeconds.getOrDefault(name, 60), TimeUnit.SECONDS)
                                .maximumSize(cacheMaxSize.getOrDefault(name, 100))
                                .build()
                                .asMap(), true);
            }
        };
        cacheManager.setCacheNames(Arrays.asList(Constants.PRE_AUTH_SESSION_CACHE,
                Constants.AUTHENTICATED_CACHE, Constants.KYC_CACHE));
        return cacheManager;
    }
}
