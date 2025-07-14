/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;

import java.io.IOException;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import io.mosip.esignet.core.util.KafkaHelperService;

@ExtendWith(MockitoExtension.class)
public class KafkaHelperServiceTest {
	
	KafkaHelperService kafkaHelperService = new KafkaHelperService();
	
	@Mock
	KafkaTemplate<String,String> kafkaTemplate;
	
	@BeforeEach
    public void setup() throws IOException {
        ReflectionTestUtils.setField(kafkaHelperService, "kafkaTemplate", kafkaTemplate);
	}
	
	@Test
	public void test_publish_withValidValues_thenPass() {
		Assertions.assertNotNull(kafkaTemplate);
		kafkaHelperService.publish("test-topic", "test-message");
	}

}
