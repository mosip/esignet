/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;

import java.io.IOException;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import io.mosip.esignet.core.util.KafkaHelperService;

@RunWith(MockitoJUnitRunner.class)
public class KafkaHelperServiceTest {
	
	KafkaHelperService kafkaHelperService = new KafkaHelperService();
	
	@Mock
	KafkaTemplate<String,String> kafkaTemplate;
	
	@Before
    public void setup() throws IOException {
        ReflectionTestUtils.setField(kafkaHelperService, "kafkaTemplate", kafkaTemplate);
	}
	
	@Test
	public void test_publish_withValidValues_thenPass() {
		Assert.assertNotNull(kafkaTemplate);
		kafkaHelperService.publish("test-topic", "test-message");
	}

}
