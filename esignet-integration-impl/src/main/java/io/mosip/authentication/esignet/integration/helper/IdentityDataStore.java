package io.mosip.authentication.esignet.integration.helper;

import java.util.Objects;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
/**
 * 
 * @author Loganathan S
 *
 */
@Component
public class IdentityDataStore {
	
	@Autowired
    private IdentityDataCache<String> identityDataCache;
	
	public void putEncryptedIdentityData(String kycToken, String psut, String encryptedIdentityData) {
		Objects.requireNonNull(encryptedIdentityData);
		identityDataCache.storeToCache(kycToken + psut, encryptedIdentityData);
	}
	
	public String getEncryptedIdentityData(String kycToken, String psut) {
		return identityDataCache.retrieveFromCache(kycToken + psut, String.class);
	}
	

}
