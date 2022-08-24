/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.module.afterburner.AfterburnerModule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"io.mosip.idp",
		"io.mosip.kernel.crypto",
		"io.mosip.kernel.keymanager.hsm",
		"io.mosip.kernel.cryptomanager.util",
		"io.mosip.kernel.core.keymanager",
		"io.mosip.kernel.keymanagerservice",
		"io.mosip.kernel.keygenerator.bouncycastle",
		"io.mosip.kernel.keymanagerservice.util",
		"io.mosip.kernel.partnercertservice.service",
		"io.mosip.kernel.partnercertservice.helper"})
@EnableCaching
public class IdpServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdpServiceApplication.class, args);
	}

	@Bean
	public ObjectMapper objectMapper() {
		return JsonMapper.builder()
				.addModule(new AfterburnerModule())
				.build();
	}

}
