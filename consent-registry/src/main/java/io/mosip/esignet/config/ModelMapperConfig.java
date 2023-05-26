package io.mosip.esignet.config;


import io.mosip.esignet.mapper.ConsentMapper;
import io.mosip.esignet.services.ConsentServiceImpl;
import org.modelmapper.ModelMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@Configuration
public class ModelMapperConfig {

    @Bean
    public ModelMapper modelMapper() {
        return new ModelMapper();
    }

    @Bean
    public ConsentMapper consentMapper() {
        return new ConsentMapper();
    }

}
