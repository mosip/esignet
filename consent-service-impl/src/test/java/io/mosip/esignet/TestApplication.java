/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import io.mosip.esignet.api.dto.AuditDTO;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;

@Slf4j
@SpringBootApplication
@ActiveProfiles(value = {"test"})
public class TestApplication {
    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public AuditPlugin loggerAuditWrapper() {
        return new AuditPlugin() {
            @Override
            public void logAudit(Action action, ActionStatus status, AuditDTO audit, Throwable t) {
                //do nothing
            }

            @Override
            public void logAudit(String username, Action action, ActionStatus status, AuditDTO audit, Throwable t) {
                //do nothing
            }
        };
    }

}
