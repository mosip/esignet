package io.mosip.idp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;

@SpringBootApplication
@EnableCaching
public class IdpServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(IdpServiceApplication.class, args);
	}

}
