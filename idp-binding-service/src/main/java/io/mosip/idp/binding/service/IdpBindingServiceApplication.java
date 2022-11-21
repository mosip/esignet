package io.mosip.idp.binding.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;


@EnableCaching
@SpringBootApplication(scanBasePackages = {"io.mosip.idp",
		"io.mosip.kernel.crypto",
		"io.mosip.kernel.keymanager.hsm",
		"io.mosip.kernel.cryptomanager.util",
		"io.mosip.kernel.keymanagerservice.helper",
		"io.mosip.kernel.keymanagerservice.service",
		"io.mosip.kernel.keymanagerservice.util",
		"io.mosip.kernel.keygenerator.bouncycastle",
		"io.mosip.kernel.signature.service"})
public class IdpBindingServiceApplication 
{
	public static void main(String[] args) {
		SpringApplication.run(IdpBindingServiceApplication.class, args);
	}
}
