package io.mosip.idp.binding.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;

@TestConfiguration
@Order(1)
public class TestSecurityConfig extends WebSecurityConfigurerAdapter {

  @Override
  protected void configure(HttpSecurity httpSecurity) throws Exception {
      // Disable CSRF
      httpSecurity.csrf().disable()
              // Permit all requests without authentication
              .authorizeRequests().anyRequest().permitAll();
  }
}
