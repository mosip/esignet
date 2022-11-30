/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.util.ErrorConstants;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.protocol.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
import org.springframework.kafka.config.KafkaListenerEndpointRegistry;
import org.springframework.kafka.config.MethodKafkaListenerEndpoint;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.kafka.listener.MessageListenerContainer;
import org.springframework.kafka.listener.adapter.RecordFilterStrategy;
import org.springframework.messaging.handler.annotation.support.DefaultMessageHandlerMethodFactory;
import org.springframework.stereotype.Component;

import javax.validation.constraints.NotNull;

@Slf4j
@Component
public class KafkaHelperService {

    @Autowired
    private KafkaTemplate<String,String> kafkaTemplate;

    @Autowired
    private KafkaListenerEndpointRegistry kafkaListenerEndpointRegistry;

    @Autowired
    private KafkaListenerContainerFactory kafkaListenerContainerFactory;

    public void publish(@NotNull String topic, @NotNull String message) {
        kafkaTemplate.send(topic, message);
        log.info("Published message to topic : {}", topic);
    }

    public void subscribe(@NotNull String topic, @NotNull String id,
                          @NotNull MessageListener messageListener, RecordFilterStrategy recordFilterStrategy)
            throws IdPException {
        MethodKafkaListenerEndpoint<String, String> kafkaListenerEndpoint = new MethodKafkaListenerEndpoint<>();
        kafkaListenerEndpoint.setId(id);
        kafkaListenerEndpoint.setTopics(topic);
        kafkaListenerEndpoint.setAutoStartup(true);
        kafkaListenerEndpoint.setMessageHandlerMethodFactory(new DefaultMessageHandlerMethodFactory());
        kafkaListenerEndpoint.setBean(messageListener);
        kafkaListenerEndpoint.setRecordFilterStrategy(recordFilterStrategy);

        try {
            kafkaListenerEndpoint.setMethod(messageListener.getClass().getMethod("onMessage", ConsumerRecord.class));
        } catch (NoSuchMethodException e) {
             log.error("Failed to find method 'onMessage' in the provided MessageListener instance!", e);
             throw new IdPException(ErrorConstants.SUBSCRIBE_FAILED);
        }
        kafkaListenerEndpointRegistry.registerListenerContainer(kafkaListenerEndpoint,
                kafkaListenerContainerFactory, true);
    }

    public void unsubscribe(@NotNull String id) {
        MessageListenerContainer listenerContainer = kafkaListenerEndpointRegistry.getListenerContainer(id);
        if(listenerContainer != null) {
            listenerContainer.stop();
            log.info("Stopped the kafka listener with id : {}", id);
        }
    }

}
