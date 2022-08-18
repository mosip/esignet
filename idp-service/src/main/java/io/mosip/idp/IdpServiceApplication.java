/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.context.annotation.Bean;

@SpringBootApplication(scanBasePackages = {"io.mosip.idp"})
@EnableCaching
@RefreshScope
public class IdpServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdpServiceApplication.class, args);
	}

	@Bean
	public ObjectMapper objectMapper() {
		return new ObjectMapper();
	}

}
