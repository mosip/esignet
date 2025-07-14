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
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;

import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@Profile("!test")
public class SecurityConfig {

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

    @Autowired
    private Environment environment;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .ignoringRequestMatchers(ignoreCsrfCheckUrls)
                )
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(localAuthenticationEntryPoint))
                .authorizeHttpRequests(auth -> {
                    if (environment.acceptsProfiles(Profiles.of("local"))) {
                        auth.anyRequest().permitAll();
                        return;
                    }

                    auth.requestMatchers(ignoreAuthUrls).permitAll();

                    if (securePostUrls != null) {
                        securePostUrls.forEach((pattern, roles) ->
                                auth.requestMatchers(HttpMethod.POST, pattern)
                                        .hasAnyAuthority(roles.toArray(new String[0]))
                        );
                    }

                    if (securePutUrls != null) {
                        securePutUrls.forEach((pattern, roles) ->
                                auth.requestMatchers(HttpMethod.PUT, pattern)
                                        .hasAnyAuthority(roles.toArray(new String[0]))
                        );
                    }

                    if (secureGetUrls != null) {
                        secureGetUrls.forEach((pattern, roles) ->
                                auth.requestMatchers(HttpMethod.GET, pattern)
                                        .hasAnyAuthority(roles.toArray(new String[0]))
                        );
                    }

                    auth.anyRequest().authenticated();
                })
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }

    @Bean
    public WebSecurityCustomizer webSecurityCustomizer() {
        return web -> web.ignoring()
                .requestMatchers("/oidc/userinfo", "/vci/credential");
    }
}
