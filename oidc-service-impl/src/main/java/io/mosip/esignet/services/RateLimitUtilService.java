package io.mosip.esignet.services;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.services.CacheUtilService;
import io.mosip.kernel.core.util.StringUtils;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class RateLimitUtilService {
	
	private static String SEND_OTP = "send-otp";
	
    @Autowired
    private CacheUtilService cacheUtilService;
    
    @Autowired
    private AuditPlugin auditWrapper;
    
    @Value("${mosip.esignet.allowed-failed-auth:3}")
    private int numofAllowedFailedAttempts;
    
    @Value("${mosip.esignet.user-freeze:100}")
    private long userfreeze;
    
    @Value("#{${mosip.esignet.attempts.time.interval}}")
    private Map<String, Integer> minInterval;
    

    public void rateLimitCheck(String transactionId, String individualId, Optional<List<AuthChallenge>> authfactors) {
    	LocalDateTime freezeTime = cacheUtilService.getUserFreeze(individualId);
		if(freezeTime!=null) {
			log.info( String.format("User with individualId - %s has been blocked for %s seconds", individualId,
	        		String.valueOf(Duration.between(freezeTime,LocalDateTime.now(ZoneId.of("UTC"))).getSeconds()))); 
			throw new EsignetException(ErrorConstants.ACCESS_DENIED);
		}
    	OIDCTransaction oidcTransaction = cacheUtilService.getPreAuthTransaction(transactionId);
    	if(oidcTransaction == null)
            throw new InvalidTransactionException();
    	String rateLimitFactor = !authfactors.isPresent()? SEND_OTP:authfactors.get().get(0).getAuthFactorType();
    	if(StringUtils.isEmpty(oidcTransaction.getLastAccessedMethod())) {
    		cacheUtilService.updateAuthTransaction(transactionId,rateLimitFactor.toLowerCase(),LocalDateTime.now(ZoneId.of("UTC")), oidcTransaction);
    		return;
    	}
    	if(minInterval.get(oidcTransaction.getLastAccessedMethod()) >  Duration.between(oidcTransaction.getLastAccessedTime(),LocalDateTime.now(ZoneId.of("UTC"))).toSeconds()) {
			cacheUtilService.updateFailedAuthTransaction(transactionId,rateLimitFactor.toLowerCase(),LocalDateTime.now(ZoneId.of("UTC")), oidcTransaction);
			if(oidcTransaction.getNumOfFailedAttempts()>=numofAllowedFailedAttempts-1) {
				cacheUtilService.setUserFreeze(individualId, Duration.ofSeconds(userfreeze));
				log.warn(String.format("User with individualId - %s has been blocked for %s seconds ", individualId , String.valueOf(userfreeze)));
				auditWrapper.logAudit(Action.USER_FREEZE, ActionStatus.SUCCESS, AuditHelper.buildAuditDtoWithIndividualId(individualId), null);
			}
			log.error(String.format("Too Many request from user, Next request is allowed after : %s ",String.valueOf(Duration.ofSeconds(Duration.between(oidcTransaction.getLastAccessedTime(),LocalDateTime.now(ZoneId.of("UTC"))).getSeconds()).getSeconds())));
			throw new EsignetException(ErrorConstants.TOO_MANY_REQUESTS);
		}
    }
    
}
