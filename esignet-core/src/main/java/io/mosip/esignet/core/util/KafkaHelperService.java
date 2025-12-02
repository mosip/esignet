/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import jakarta.validation.constraints.NotNull;

@Slf4j
@Component
public class KafkaHelperService {

    @Value("${kafka.enabled}")
    private boolean kafkaEnabled;

    @Autowired(required = false)
    private KafkaTemplate<String,String> kafkaTemplate;

    public void publish(@NotNull String topic, @NotNull String message) {
        if(kafkaEnabled) {
            kafkaTemplate.send(topic, message);
            log.info("Published message to topic : {}", topic);
        }
        else
            log.warn("Kafka disabled NOT Publishing message to topic : {}", topic);
    }

}
