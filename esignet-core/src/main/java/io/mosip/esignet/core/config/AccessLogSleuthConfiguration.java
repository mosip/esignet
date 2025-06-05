package io.mosip.esignet.core.config;

import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;


@Configuration
public class AccessLogSleuthConfiguration implements WebServerFactoryCustomizer<TomcatServletWebServerFactory> {

    private final SleuthValve sleuthValve;

    public AccessLogSleuthConfiguration(SleuthValve sleuthValve) {
        this.sleuthValve = sleuthValve;
        this.sleuthValve.setAsyncSupported(true);
    }

    @Override
    public void customize(TomcatServletWebServerFactory factory) {
        factory.addContextCustomizers(context -> context.getPipeline().addValve(sleuthValve));
    }
}
