package io.mosip.esignet.captcha;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

@SpringBootApplication
@ComponentScan(basePackages = { "io.mosip.esignet.captcha.*", })
public class EsignetCaptchaServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(EsignetCaptchaServiceApplication.class, args);
	}

}
