/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.config;

import io.mosip.idp.advice.IdpAuthenticationEntryPoint;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableGlobalMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.builders.WebSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityConfigurerAdapter;
import org.springframework.security.config.http.SessionCreationPolicy;

@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Profile(value = {"!test"})
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private IdpAuthenticationEntryPoint idpAuthenticationEntryPoint;

    @Value("${server.servlet.path}")
    private String servletPath;

    @Value("${mosip.idp.post-req-client-mgmt-scope}")
    private String postApiClientMgmtScope;

    @Value("${mosip.idp.put-req-client-mgmt-scope}")
    private String putApiClientMgmtScope;

    @Value("${mosip.idp.auth-ignore-urls}")
    private String[] authIgnoreUrls;


    @Override
    protected void configure(HttpSecurity http) throws Exception {
        http.csrf().disable()
                .anonymous().disable()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .authorizeRequests()
                .antMatchers(HttpMethod.POST, servletPath+"/client-mgmt/**").hasAuthority(postApiClientMgmtScope)
                .antMatchers(HttpMethod.PUT, servletPath+"/client-mgmt/**").hasAuthority(putApiClientMgmtScope)
                .anyRequest().authenticated()
                .and()
                .oauth2ResourceServer(oauth2 -> oauth2.jwt())
                .exceptionHandling()
                .authenticationEntryPoint(idpAuthenticationEntryPoint);
    }


    @Override
    public void configure(WebSecurity webSecurity) throws Exception {
        //Nullifying security filters
        webSecurity.ignoring().antMatchers(authIgnoreUrls);
    }
}
