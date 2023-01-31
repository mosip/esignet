/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;

@Slf4j
@Component
public class KafkaHelperService {

    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    public void publish(@NotNull String topic, @NotNull String message) {
        kafkaTemplate.send(topic, message);
        log.info("Published message to topic : {}", topic);
    }

}
