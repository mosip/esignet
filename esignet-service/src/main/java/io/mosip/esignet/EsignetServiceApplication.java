/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableKafka
@EnableAsync
@EnableCaching
@SpringBootApplication(scanBasePackages = "io.mosip.esignet," +
		"io.mosip.kernel.crypto," +
		"io.mosip.kernel.keymanager.hsm," +
		"io.mosip.kernel.cryptomanager.util," +
		"io.mosip.kernel.keymanagerservice.helper," +
		"io.mosip.kernel.keymanagerservice.service," +
		"io.mosip.kernel.keymanagerservice.util," +
		"io.mosip.kernel.keygenerator.bouncycastle," +
		"io.mosip.kernel.signature.service," +
		"io.mosip.kernel.partnercertservice.service," +
		"io.mosip.kernel.partnercertservice.helper," +
		"${mosip.esignet.integration.scan-base-package}")
public class EsignetServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EsignetServiceApplication.class, args);
	}
}
