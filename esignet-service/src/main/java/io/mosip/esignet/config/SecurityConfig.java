/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.config;

import io.mosip.esignet.core.config.LocalAuthenticationEntryPoint;
import lombok.extern.slf4j.Slf4j;
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
import org.springframework.security.config.annotation.web.configurers.ExpressionUrlAuthorizationConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
@Profile(value = {"!test"})
public class SecurityConfig extends WebSecurityConfigurerAdapter {

    @Autowired
    private LocalAuthenticationEntryPoint localAuthenticationEntryPoint;

    @Value("${server.servlet.path}")
    private String servletPath;

    @Value("#{${mosip.esignet.security.auth.post-urls}}")
    private Map<String, List<String>> securePostUrls;

    @Value("#{${mosip.esignet.security.auth.put-urls}}")
    private Map<String, List<String>> securePutUrls;

    @Value("#{${mosip.esignet.security.auth.get-urls}}")
    private Map<String, List<String>> secureGetUrls;

    @Value("${mosip.esignet.security.ignore-auth-urls}")
    private String[] ignoreAuthUrls;

    @Value("${mosip.esignet.security.ignore-csrf-urls}")
    private String[] ignoreCsrfCheckUrls;


    @Override
    protected void configure(HttpSecurity http) throws Exception {

        ExpressionUrlAuthorizationConfigurer<HttpSecurity>.ExpressionInterceptUrlRegistry urlRegistry = http
                .csrf()
                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                .ignoringAntMatchers(ignoreCsrfCheckUrls)
                .and()
                .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                .and()
                .exceptionHandling()
                .authenticationEntryPoint(localAuthenticationEntryPoint)
                .and()
                .authorizeRequests()
                .antMatchers(ignoreAuthUrls).permitAll();

        if(CollectionUtils.isEmpty(securePostUrls) && CollectionUtils.isEmpty(securePutUrls) && CollectionUtils.isEmpty(secureGetUrls)) {
            return;
        }

        urlRegistry = urlRegistry.and().authorizeRequests();

        log.info("Securing the configured URLs");
        for(Map.Entry<String, List<String>> entry : securePostUrls.entrySet()) {
            urlRegistry = urlRegistry.antMatchers(HttpMethod.POST, entry.getKey())
                    .hasAnyAuthority(entry.getValue().toArray(new String[0]));
        }

        for(Map.Entry<String, List<String>> entry : securePutUrls.entrySet()) {
            urlRegistry = urlRegistry.antMatchers(HttpMethod.PUT, entry.getKey())
                    .hasAnyAuthority(entry.getValue().toArray(new String[0]));
        }

        for(Map.Entry<String, List<String>> entry : secureGetUrls.entrySet()) {
            urlRegistry = urlRegistry.antMatchers(HttpMethod.GET, entry.getKey())
                    .hasAnyAuthority(entry.getValue().toArray(new String[0]));
        }

        urlRegistry.anyRequest().authenticated()
                .and().oauth2ResourceServer(oauth2 -> oauth2.jwt());
    }


    @Override
    public void configure(WebSecurity webSecurity) throws Exception {
        //Nullifying security filters on userinfo endpoint.
        //Reason:
        //Even though oidc/** is part of ignore-auth-urls, bearer token is getting validated in the security filters and fails with 401 error.
        //Bearer token of the userinfo endpoint is signed with IDP keys.
        //We currently donot have a way to set 2 different authentication providers in spring security.
        webSecurity.ignoring().antMatchers(servletPath+"/oidc/userinfo", servletPath+"/vci/credential");
    }
}
