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
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateRequestDto;
import io.mosip.kernel.keymanagerservice.dto.SymmetricKeyGenerateRequestDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import lombok.extern.slf4j.Slf4j;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.util.ObjectUtils;
import org.springframework.web.client.RestTemplate;

@Configuration
@Slf4j
public class AppConfig implements ApplicationRunner {


    @Value("${mosip.esignet.default.httpclient.connections.max.per.host:20}")
    private int defaultMaxConnectionPerRoute;

    @Value("${mosip.esignet.default.httpclient.connections.max:100}")
    private int defaultTotalMaxConnection;

    @Value("${mosip.esignet.cache.security.secretkey.reference-id}")
    private String cacheSecretKeyRefId;

    @Autowired
    private KeymanagerService keymanagerService;

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
        HttpClientBuilder httpClientBuilder = HttpClients.custom()
                .setConnectionManager(connectionManager)
                .disableCookieManagement();
        HttpComponentsClientHttpRequestFactory requestFactory = new HttpComponentsClientHttpRequestFactory();
        requestFactory.setHttpClient(httpClientBuilder.build());
        return new RestTemplate(requestFactory);
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
