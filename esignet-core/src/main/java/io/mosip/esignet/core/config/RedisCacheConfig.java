package io.mosip.esignet.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.cache.RedisCacheManagerBuilderCustomizer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@ConditionalOnProperty(value = "spring.cache.type", havingValue = "redis")
@Configuration
public class RedisCacheConfig {

    @Value("#{${mosip.esignet.cache.expire-in-seconds}}")
    private Map<String, Integer> cacheNamesWithTTLMap;

    @Value("${mosip.esignet.cache.keyprefix}")
    private String keyPrefix;

    @Bean
    public RedisCacheManagerBuilderCustomizer redisCacheManagerBuilderCustomizer() {
        return (builder) -> {
            Map<String, RedisCacheConfiguration> configurationMap = new HashMap<>();
            cacheNamesWithTTLMap.forEach((cacheName, ttl) -> {
                configurationMap.put(cacheName, RedisCacheConfiguration
                                .defaultCacheConfig()
                                .disableCachingNullValues()
                                .prefixCacheNameWith(keyPrefix+":")
                                .entryTtl(Duration.ofSeconds(ttl)));
            });
            builder.withInitialCacheConfigurations(configurationMap);
        };
    }
}
