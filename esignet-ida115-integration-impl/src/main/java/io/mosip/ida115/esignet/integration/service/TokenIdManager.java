 package io.mosip.ida115.esignet.integration.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.mosip.kernel.tokenidgenerator.dto.TokenIDResponseDto;
import io.mosip.kernel.tokenidgenerator.service.TokenIDGeneratorService;
import lombok.extern.slf4j.Slf4j;


/**
 * This Class will call an rest api which accepts uin, partnerId and will return
 * authTokenId.
 * 
 * @author Prem Kumar
 *
 */
@Component
@Slf4j
public class TokenIdManager {

	@Autowired(required = false)
	TokenIDGeneratorService tokenIDGeneratorService;

	public String generateTokenId(String uin, String partnerId) throws Exception {
			
		try {
			TokenIDResponseDto response = tokenIDGeneratorService.generateTokenID(uin, partnerId);
			return response.getTokenID();
		} catch (Exception e) {
			log.error(e.getMessage());
			throw e;
		}
	}
}
