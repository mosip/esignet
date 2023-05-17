package io.mosip.authentication.esignet.integration.helper;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.authentication.esignet.integration.dto.ClientIdSecretKeyRequest;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseWrapper;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AuthTransactionHelper {
	
    private static final String AUTH_TOKEN_CACHE = "authtokens";
	
    public static final String AUTH_TOKEN_CACHE_KEY = "auth_token";

    @Autowired
    private ObjectMapper objectMapper;
	
    @Autowired
    private RestTemplate restTemplate;
	
    @Value("${mosip.esignet.authenticator.ida.auth-token-url}")
    private String authTokenUrl;
	
    @Value("${mosip.esignet.authenticator.ida.client-id}")
    private String clientId;
    
    @Value("${mosip.esignet.authenticator.ida.secret-key}")
    private String secretKey;
    
    @Value("${mosip.esignet.authenticator.ida.app-id}")
    private String appId;
	
    @Cacheable(value = AUTH_TOKEN_CACHE, key = "#root.target.AUTH_TOKEN_CACHE_KEY")
    public String getAuthToken() throws Exception {
    	log.info("Started to get auth-token with appId : {} && clientId : {}",
                appId, clientId);
    	
    
    	
	RequestWrapper<ClientIdSecretKeyRequest> authRequest = new RequestWrapper<>();
    	authRequest.setRequesttime(LocalDateTime.now());
    	ClientIdSecretKeyRequest clientIdSecretKeyRequest = new ClientIdSecretKeyRequest(clientId, secretKey, appId);
    	authRequest.setRequest(clientIdSecretKeyRequest);
    	
    	String requestBody = objectMapper.writeValueAsString(authRequest);
    	RequestEntity requestEntity = RequestEntity
                 .post(UriComponentsBuilder.fromUriString(authTokenUrl).build().toUri())
                 .contentType(MediaType.APPLICATION_JSON)
                 .body(requestBody);
        ResponseEntity<ResponseWrapper> responseEntity = restTemplate.exchange(requestEntity,
                 new ParameterizedTypeReference<ResponseWrapper>() {});
        
        String authToken = responseEntity.getHeaders().getFirst("authorization");
        return authToken;
     }
    
    @CacheEvict(value = AUTH_TOKEN_CACHE, allEntries = true)
    public void purgeAuthTokenCache() {
    	log.info("Evicting entry from AUTH_TOKEN_CACHE");
    }

}
