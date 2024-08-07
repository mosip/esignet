/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.vci.config;

import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.annotation.RequestScope;


@Slf4j
@Configuration
@ComponentScan(basePackages = {"io.mosip.esignet.vci"})
public class VCIConfig {

    @Bean
    @RequestScope
    public ParsedAccessToken parsedAccessToken() {
        return new ParsedAccessToken();
    }


}
