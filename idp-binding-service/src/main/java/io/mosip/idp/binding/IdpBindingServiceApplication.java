/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;


@EnableCaching
@SpringBootApplication(scanBasePackages = { "io.mosip.idp.binding", "io.mosip.idp.core.config",
		"io.mosip.idp.core.util", "io.mosip.idp.authwrapper",
		"io.mosip.kernel.crypto",
		"io.mosip.kernel.keymanager.hsm",
		"io.mosip.kernel.cryptomanager.util",
		"io.mosip.kernel.keymanagerservice.helper",
		"io.mosip.kernel.keymanagerservice.service",
		"io.mosip.kernel.keymanagerservice.util",
		"io.mosip.kernel.keygenerator.bouncycastle",
		"io.mosip.kernel.signature.service", "io.mosip.kernel.partnercertservice.service",
		"io.mosip.kernel.partnercertservice.helper" })
public class IdpBindingServiceApplication 
{
	public static void main(String[] args) {
		SpringApplication.run(IdpBindingServiceApplication.class, args);
	}
}
