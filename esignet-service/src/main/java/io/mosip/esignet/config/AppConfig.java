/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.dto.ServerProfile;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.repository.ServerProfileRepository;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateRequestDto;
import io.mosip.kernel.keymanagerservice.dto.SymmetricKeyGenerateRequestDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import lombok.extern.slf4j.Slf4j;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadPoolExecutor;

@Configuration
@Slf4j
public class AppConfig implements ApplicationRunner {

    private static final List<String> SERVER_PROFILE_FEATURES = List.of("PAR", "DPOP", "PKCE", "JWE");
    private static final List<String> SERVER_PROFILE_ADDITIONAL_CONFIG_KEYS = List.of(
            "dpop_bound_access_tokens",
            "require_pkce",
            "userinfo_response_type",
            "require_pushed_authorization_requests"
    );


    @Value("${mosip.esignet.default.httpclient.connections.max.per.host:20}")
    private int defaultMaxConnectionPerRoute;

    @Value("${mosip.esignet.default.httpclient.connections.max:100}")
    private int defaultTotalMaxConnection;

    @Value("${mosip.esignet.default.httpclient.connection.timeout:5000}")
    private long defaultConnectionTimeout;

    @Value("${mosip.esignet.default.httpclient.socket.timeout:10000}")
    private long defaultSocketTimeout;

    @Value("${mosip.esignet.default.httpclient.connection.idle.timeout:30}")
    private long defaultIdleTimeout;

    @Value("${mosip.esignet.audit.executor.core-pool-size:5}")
    private int auditExecutorCorePoolSize;

    @Value("${mosip.esignet.audit.executor.max-pool-size:20}")
    private int auditExecutorMaxPoolSize;

    @Value("${mosip.esignet.audit.executor.queue-capacity:500}")
    private int auditExecutorQueueCapacity;

    @Value("${mosip.esignet.audit.executor.keep-alive-seconds:60}")
    private int auditExecutorKeepAliveSeconds;

    @Value("${mosip.esignet.audit.executor.thread-name-prefix:audit-}")
    private String auditExecutorThreadNamePrefix;

    @Value("${mosip.esignet.cache.security.secretkey.reference-id}")
    private String cacheSecretKeyRefId;

    @Value("${mosip.esignet.server.profile:none}")
    private String serverProfile;

    @Autowired
    private KeymanagerService keymanagerService;

    @Autowired
    private ServerProfileRepository serverProfileRepository;

    @Bean
    public ObjectMapper objectMapper() {
        return JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    @Bean
    public RestTemplate restTemplate() {
        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
        connectionManager.setMaxTotal(defaultTotalMaxConnection);
        connectionManager.setDefaultMaxPerRoute(defaultMaxConnectionPerRoute);
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.ofSeconds(defaultConnectionTimeout))
                .setSocketTimeout(Timeout.ofSeconds(defaultSocketTimeout))
                .build();
        connectionManager.setDefaultConnectionConfig(connectionConfig);
        HttpClientBuilder httpClientBuilder = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableCookieManagement()
                .evictIdleConnections(TimeValue.ofSeconds(defaultIdleTimeout));
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClientBuilder.build());
        return new RestTemplate(requestFactory);
    }

    @Bean("auditTaskExecutor")
    public ThreadPoolTaskExecutor auditTaskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(auditExecutorCorePoolSize);
        executor.setMaxPoolSize(auditExecutorMaxPoolSize);
        executor.setQueueCapacity(auditExecutorQueueCapacity);
        executor.setThreadNamePrefix(auditExecutorThreadNamePrefix);
        executor.setKeepAliveSeconds(auditExecutorKeepAliveSeconds);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.initialize();
        return executor;
    }

    /**
     * Get the features associated with the profile
     * name of the profile - fapi2.0. nisdsp, gov, none, etc.
     */
    @Bean
    public ServerProfile serverProfile() throws EsignetException {
        ServerProfile profile = new ServerProfile();
        profile.setName(serverProfile);
        final Map<String, String> profileDataMap = new HashMap<>();
        profile.setFeatureMap(profileDataMap);

        if("none".equalsIgnoreCase(serverProfile)) {
            return profile;
        }

        List<io.mosip.esignet.entity.ServerProfile> profiles = serverProfileRepository.findByProfileName(serverProfile);
        if (profiles == null || profiles.isEmpty()) {
            log.error("**** No features found for the configured server profile: {} ****", serverProfile);
            throw new EsignetException("INVALID_SERVER_PROFILE");
        }

        for (io.mosip.esignet.entity.ServerProfile serverProfileEntity : profiles) {
            String feature = serverProfileEntity.getFeature();
            String additionalConfigKey = serverProfileEntity.getAdditionalConfigKey();

            if (!SERVER_PROFILE_FEATURES.contains(feature.toUpperCase())) {
                log.error("Invalid feature '{}' in ServerProfile. Valid features are: {}", feature, SERVER_PROFILE_FEATURES);
                throw new EsignetException("INVALID_SERVER_PROFILE");
            }
            if (!SERVER_PROFILE_ADDITIONAL_CONFIG_KEYS.contains(additionalConfigKey)) {
                log.error("Invalid additionalConfigKey '{}' in ServerProfile. Valid keys are: {}",
                        additionalConfigKey, SERVER_PROFILE_ADDITIONAL_CONFIG_KEYS);
                throw new EsignetException("INVALID_SERVER_PROFILE");
            }

            profileDataMap.put(additionalConfigKey, feature);
        }
        return profile;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        log.info("===================== IDP_SERVICE ROOT KEY CHECK ========================");
        String objectType = "CSR";
        KeyPairGenerateRequestDto rootKeyRequest = new KeyPairGenerateRequestDto();
        rootKeyRequest.setReferenceId("");
        rootKeyRequest.setApplicationId(Constants.ROOT_KEY);
        keymanagerService.generateMasterKey(objectType, rootKeyRequest);
        log.info("===================== IDP_SERVICE MASTER KEY CHECK ========================");
        KeyPairGenerateRequestDto masterKeyRequest = new KeyPairGenerateRequestDto();
        masterKeyRequest.setReferenceId("");
        masterKeyRequest.setApplicationId(Constants.OIDC_SERVICE_APP_ID);
        keymanagerService.generateMasterKey(objectType, masterKeyRequest);

        if(!ObjectUtils.isEmpty(cacheSecretKeyRefId)) {
            SymmetricKeyGenerateRequestDto symmetricKeyGenerateRequestDto = new SymmetricKeyGenerateRequestDto();
            symmetricKeyGenerateRequestDto.setApplicationId(Constants.OIDC_SERVICE_APP_ID);
            symmetricKeyGenerateRequestDto.setReferenceId(cacheSecretKeyRefId);
            symmetricKeyGenerateRequestDto.setForce(false);
            keymanagerService.generateSymmetricKey(symmetricKeyGenerateRequestDto);
            log.info("============= IDP_SERVICE CACHE SYMMETRIC KEY CHECK COMPLETED =============");
        }

        log.info("===================== IDP_PARTNER MASTER KEY CHECK ========================");
        KeyPairGenerateRequestDto partnerMasterKeyRequest = new KeyPairGenerateRequestDto();
        partnerMasterKeyRequest.setReferenceId("");
        partnerMasterKeyRequest.setApplicationId(Constants.OIDC_PARTNER_APP_ID);
        keymanagerService.generateMasterKey(objectType, partnerMasterKeyRequest);
        log.info("===================== IDP KEY SETUP COMPLETED ========================");
    }
}
