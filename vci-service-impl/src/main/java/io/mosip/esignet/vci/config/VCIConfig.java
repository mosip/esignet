package io.mosip.esignet.vci.config;

import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import io.mosip.esignet.vci.filter.AccessTokenValidationFilter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;


@Slf4j
@Configuration
@ComponentScan(basePackages = {"io.mosip.esignet.vci"})
public class VCIConfig {

    @Value("#{${mosip.esignet.vci.authn.filter-urls}}")
    private String[] urlPatterns;

    @Autowired
    private AccessTokenValidationFilter accessTokenValidationFilter;

    @Bean
    @RequestScope
    public ParsedAccessToken parsedAccessToken() {
        return new ParsedAccessToken();
    }

    @Bean
    public FilterRegistrationBean<AccessTokenValidationFilter> accessTokenValidationFilterBean() {
        FilterRegistrationBean<AccessTokenValidationFilter> registrationBean = new FilterRegistrationBean<>();
        registrationBean.setFilter(accessTokenValidationFilter);
        registrationBean.addUrlPatterns(urlPatterns); // Specify the URL patterns to filter
        return registrationBean;
    }
}
