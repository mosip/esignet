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
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.util.CollectionUtils;

import java.util.List;
import java.util.Map;

import static org.springframework.security.config.Customizer.withDefaults;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private LocalAuthenticationEntryPoint localAuthenticationEntryPoint;

    @Value("${spring.mvc.servlet.path}")
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

    @Autowired
    private Environment environment;


    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {

        http.csrf(csrf -> csrf
                        .csrfTokenRepository(new CookieCsrfTokenRepository())
                        .ignoringRequestMatchers(ignoreCsrfCheckUrls))
                .sessionManagement(management -> management.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(localAuthenticationEntryPoint))
                .authorizeHttpRequests(requests -> {
                            requests.requestMatchers(ignoreAuthUrls).permitAll();
                            if (CollectionUtils.isEmpty(secureGetUrls) && CollectionUtils.isEmpty(securePostUrls) && CollectionUtils.isEmpty(securePutUrls)) {
                                requests.anyRequest().permitAll();
                                return;
                            }

                            if (securePostUrls != null) {
                                securePostUrls.forEach((pattern, roles) ->
                                        requests.requestMatchers(HttpMethod.POST, pattern)
                                                .hasAnyAuthority(roles.toArray(new String[0]))
                                );
                            }

                            if (securePutUrls != null) {
                                securePutUrls.forEach((pattern, roles) ->
                                        requests.requestMatchers(HttpMethod.PUT, pattern)
                                                .hasAnyAuthority(roles.toArray(new String[0]))
                                );
                            }

                            if (secureGetUrls != null) {
                                secureGetUrls.forEach((pattern, roles) ->
                                        requests.requestMatchers(HttpMethod.GET, pattern)
                                                .hasAnyAuthority(roles.toArray(new String[0]))
                                );
                            }
                            requests.anyRequest().authenticated();
                        }
                );

        if (!(CollectionUtils.isEmpty(secureGetUrls) && CollectionUtils.isEmpty(securePostUrls) && CollectionUtils.isEmpty(securePutUrls))) {
            http.oauth2ResourceServer(oauth2 -> oauth2.jwt(withDefaults()));
        }

        return http.build();
    }


    @Bean
    WebSecurityCustomizer webSecurityCustomizer() {
        return (webSecurity) -> {
            //Nullifying security filters on userinfo endpoint.
            //Reason:
            //Even though oidc/** is part of ignore-auth-urls, bearer token is getting validated in the security filters and fails with 401 error.
            //Bearer token of the userinfo endpoint is signed with IDP keys.
            //We currently donot have a way to set 2 different authentication providers in spring security.
            webSecurity.ignoring().requestMatchers(servletPath + "/oidc/userinfo", servletPath + "/vci/credential");
        };
    }
}
