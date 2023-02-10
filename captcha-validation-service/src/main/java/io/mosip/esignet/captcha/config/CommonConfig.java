package io.mosip.esignet.captcha.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.web.client.RestTemplate;

@Configuration
public class CommonConfig {

	@Primary
	@Bean
	public RestTemplate restTemplate() {
		return new RestTemplate();
	}
}
