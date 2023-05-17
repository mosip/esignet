package io.mosip.householdid.esignet.integration.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
@EntityScan(basePackages = "io.mosip.esignet.household.integration.entity")
@EnableJpaRepositories(basePackages = "io.mosip.esignet.household.integration.repository")
public class HouseholdConfig {
}
