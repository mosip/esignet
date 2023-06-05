package io.mosip.householdid.esignet.integration.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "io.mosip.householdid.esignet.integration.entity")
@EnableJpaRepositories(basePackages = "io.mosip.householdid.esignet.integration.repository")
public class HouseholdConfig {
}
