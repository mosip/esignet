package io.mosip.esignet.core;

import java.io.IOException;

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
		kafkaHelperService.publish("test-topic", "test-message");
	}

}
