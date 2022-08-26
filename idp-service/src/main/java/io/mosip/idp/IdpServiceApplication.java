/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import io.mosip.idp.core.util.Constants;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateRequestDto;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@SpringBootApplication(scanBasePackages = {"io.mosip.idp",
		"io.mosip.kernel.crypto",
		"io.mosip.kernel.keymanager.hsm",
		"io.mosip.kernel.cryptomanager.util",
		"io.mosip.kernel.keymanagerservice.helper",
		"io.mosip.kernel.keymanagerservice.service",
		"io.mosip.kernel.keymanagerservice.util",
		"io.mosip.kernel.keygenerator.bouncycastle",
		"io.mosip.kernel.signature.service",
		"io.mosip.kernel.partnercertservice.service",
		"io.mosip.kernel.partnercertservice.helper"})
@EnableJpaRepositories(basePackages = {"io.mosip.idp.repository", "io.mosip.kernel.keymanagerservice.repository"})
@EntityScan(basePackages = {"io.mosip.idp.entity", "io.mosip.kernel.keymanagerservice.entity"})
@EnableCaching
@Slf4j
public class IdpServiceApplication implements ApplicationRunner {

	@Autowired
	private KeymanagerService keymanagerService;

	public static void main(String[] args) {
		SpringApplication.run(IdpServiceApplication.class, args);
	}

	@Bean
	public ObjectMapper objectMapper() {
		return JsonMapper.builder()
				.addModule(new AfterburnerModule())
				.build();
	}

	@Override
	public void run(ApplicationArguments args) throws Exception {
		log.info("===================== IDP_SERVICE ROOT KEY CHECK ========================");
		String objectType = "CSR";
		KeyPairGenerateRequestDto rootKeyRequest = new KeyPairGenerateRequestDto();
		rootKeyRequest.setApplicationId(Constants.ROOT_KEY);
		keymanagerService.generateMasterKey(objectType, rootKeyRequest);
		log.info("===================== IDP_SERVICE MASTER KEY CHECK ========================");
		KeyPairGenerateRequestDto masterKeyRequest = new KeyPairGenerateRequestDto();
		masterKeyRequest.setApplicationId(Constants.IDP_SERVICE_APP_ID);
		keymanagerService.generateMasterKey(objectType, masterKeyRequest);
		log.info("===================== IDP_PARTNER MASTER KEY CHECK ========================");
		KeyPairGenerateRequestDto partnerMasterKeyRequest = new KeyPairGenerateRequestDto();
		partnerMasterKeyRequest.setApplicationId(Constants.IDP_PARTNER_APP_ID);
		keymanagerService.generateMasterKey(objectType, partnerMasterKeyRequest);
		log.info("===================== IDP KEY SETUP COMPLETED ========================");
	}
}
