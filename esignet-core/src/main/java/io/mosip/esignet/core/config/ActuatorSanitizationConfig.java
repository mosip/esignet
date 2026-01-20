/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.SanitizableData;
import org.springframework.boot.actuate.endpoint.SanitizingFunction;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Locale;

/**
 * Custom sanitization rules for Spring Boot Actuator endpoints (e.g. /actuator/env).
 * Any property whose key matches the configured patterns will have its value masked.
 */
@Configuration
public class ActuatorSanitizationConfig implements SanitizingFunction {

    @Value("#{${mosip.esignet.actuator.sanitize.key.endsWith:{'password','secret','key','token','vcap_services','sun.java.command'}}}")
    private List<String> keysToBeSanitizedEndsWith;

    @Value("#{${mosip.esignet.actuator.sanitize.key.contains:{'credentials'}}}")
    private List<String> keysToBeSanitizedContains;

    @Override
    public SanitizableData apply(SanitizableData data) {
        String key = data.getKey().toLowerCase(Locale.ROOT);
        if (keysToBeSanitizedEndsWith.stream().anyMatch(key::endsWith)
                || keysToBeSanitizedContains.stream().anyMatch(key::contains)) return data.withSanitizedValue();
        return data;
    }
}
