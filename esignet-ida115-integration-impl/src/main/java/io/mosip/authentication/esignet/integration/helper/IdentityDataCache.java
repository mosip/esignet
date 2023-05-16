package io.mosip.authentication.esignet.integration.helper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 
 * @author Loganathan S
 *
 */
@Component
public class IdentityDataCache<T> {
	
	private static final String ENCRYPTED_IDENTITY_DATA = "encryptedIdentityData";
	
	@Autowired
	private CacheManager cacheManager;
	
	public void storeToCache(String key, T data) {
		Cache dataCache = cacheManager.getCache(ENCRYPTED_IDENTITY_DATA);
		dataCache.put(key, data);
	}
	
	public T retrieveFromCache(String key, Class<T> clazz) {
		Cache dataCache = cacheManager.getCache(ENCRYPTED_IDENTITY_DATA);
		return dataCache.get(key, clazz);
	}
	

}
