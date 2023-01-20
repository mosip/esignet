/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.config;

import com.google.common.cache.CacheBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.concurrent.ConcurrentMapCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

@ConditionalOnProperty(value = "spring.cache.type", havingValue = "simple")
@Configuration
public class SimpleCacheConfig extends CachingConfigurerSupport {

    @Value("${mosip.esignet.cache.names}")
    private List<String> cacheNames;

    @Value("#{${mosip.esignet.cache.size}}")
    private Map<String, Integer> cacheMaxSize;

    @Value("#{${mosip.esignet.cache.expire-in-seconds}}")
    private Map<String, Integer> cacheExpireInSeconds;


    @Bean
    @Override
    public CacheManager cacheManager() {
        SimpleCacheManager cacheManager = new SimpleCacheManager();
        List<Cache> caches = new ArrayList<>();
        for(String name : cacheNames) {
            caches.add(buildMapCache(name));
        }
        cacheManager.setCaches(caches);
        return cacheManager;
    }

    private ConcurrentMapCache buildMapCache(String name) {
        return new ConcurrentMapCache(name,
                CacheBuilder.newBuilder()
                        .expireAfterWrite(cacheExpireInSeconds.getOrDefault(name, 60), TimeUnit.SECONDS)
                        .maximumSize(cacheMaxSize.getOrDefault(name, 100))
                        .build()
                        .asMap(), true);
    }
}
