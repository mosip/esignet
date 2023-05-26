package io.mosip.ida115.esignet.integration.runner;

import java.util.Date;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateRequestDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import lombok.extern.slf4j.Slf4j;

@Component
@Order(Ordered.LOWEST_PRECEDENCE)
@Slf4j
public class KeyInitializationRunner implements ApplicationRunner {
	
	@Autowired
    private KeymanagerService keymanagerService;
    
	@Value("${mosip.ida.kyc.auth.partner.applicationid}")
    private String kycAuthPartnerKeyAppId;
	
	@Autowired
    private ThreadPoolTaskScheduler taskScheduler;
	
	@Value("${mosip.esignet.auth.wrapper.key.generation.initial.delay.milliseconds:10000}")
    private int authPartnerKeyGenerationDelay;

	@Override
	public void run(ApplicationArguments args) throws Exception {
		log.info("Scheduling key generation to run after: " + authPartnerKeyGenerationDelay + " milliseconds..");
		taskScheduler.schedule(() -> {
            setUpSigningKey();
        }, new Date(System.currentTimeMillis() + authPartnerKeyGenerationDelay));
	}
	
	private void setUpSigningKey() {
		log.info("Setting up auth partner key. This will ignore key generation if already generated.");
		KeyPairGenerateRequestDto partnerMasterKeyRequest = new KeyPairGenerateRequestDto();
		partnerMasterKeyRequest.setApplicationId(kycAuthPartnerKeyAppId);
		keymanagerService.generateMasterKey("CSR", partnerMasterKeyRequest);
	}

}
