package io.mosip.esignet;

import io.mosip.esignet.core.constants.Action;
import io.mosip.esignet.core.constants.ActionStatus;
import io.mosip.esignet.core.dto.AuditDTO;
import io.mosip.esignet.core.spi.AuditWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.client.RestTemplate;

@Slf4j
@SpringBootApplication
@ActiveProfiles(value = {"test"})
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestApplication.class, args);
    }

    @Bean
    public AuditWrapper loggerAuditWrapper() {
        return new AuditWrapper() {
            @Override
            public void logAudit(Action action, ActionStatus status, AuditDTO audit, Throwable t) {
                //do nothing
            }
        };
    }

    @Bean
    public RestTemplate restTemplate() {
        return new RestTemplate();
    }

}
