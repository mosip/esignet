package io.mosip.esignet.advice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.ApiRateLimit;
import io.mosip.esignet.core.dto.ResponseWrapper;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.AuthorizationHelperService;
import io.mosip.esignet.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.NoSuchMessageException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_REQUEST;

@Slf4j
@ControllerAdvice
public class HeaderValidationFilter extends OncePerRequestFilter {

    private static final String HEADER_OAUTH_DETAILS_KEY = "oauth-details-key";
    private static final String HEADER_OAUTH_DETAILS_HASH = "oauth-details-hash";

    @Value("#{${mosip.esignet.header-filter.paths-to-validate}}")
    private List<String> pathsToValidate;

    @Value("${mosip.esignet.send-otp.attempts:3}")
    private int sendOtpAttempts;

    @Value("${mosip.esignet.authenticate.attempts:3}")
    private int authenticateAttempts;

    @Value("${mosip.esignet.send-otp.invocation-gap-secs:1}")
    private int sendOtpInvocationGapInSeconds;

    @Value("${mosip.esignet.authenticate.invocation-gap-secs:1}")
    private int authenticateInvocationGapInSeconds;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private MessageSource messageSource;


    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) throws ServletException {
        final String path = request.getRequestURI();
        return !pathsToValidate.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String path = request.getRequestURI();

        try {
            log.debug("Started to validate {} for oauth-details headers", path);
            final String transactionId = request.getHeader(HEADER_OAUTH_DETAILS_KEY);
            final String hashValue = request.getHeader(HEADER_OAUTH_DETAILS_HASH);
            OIDCTransaction transaction = getTransaction(transactionId, path);
            if(transaction == null) {
                throw new InvalidTransactionException();
            }

            if(transaction.getOauthDetailsHash().equals(hashValue)) {
                validateApiRateLimits(path, transactionId, transaction.getIndividualIdHash());
                filterChain.doFilter(request, response);
                return;
            }
            log.error("oauth-details header validation failed, value in transaction: {}", transaction.getOauthDetailsHash());
            throw new EsignetException(INVALID_REQUEST);

        } catch (EsignetException e) {
            response.setStatus(HttpStatus.OK.value());
            response.getWriter().write(getErrorResponse(e));
        }
    }

    private String getErrorResponse(EsignetException ex) {
        String errorCode = ex.getErrorCode();
        ResponseWrapper responseWrapper = new ResponseWrapper();
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setErrors(Arrays.asList(new Error(errorCode, getMessage(errorCode))));
        try {
            return objectMapper.writeValueAsString(responseWrapper);
        } catch (JsonProcessingException jpe) {
            log.error("Failed to construct error response", jpe);
        }
        return "{\"errors\" : [{\"errorCode\":\""+errorCode+"\"}]}";
    }

    private String getMessage(String errorCode) {
        try {
            messageSource.getMessage(errorCode, null, Locale.getDefault());
        } catch (NoSuchMessageException ex) {
            log.error("Message not found in the i18n bundle", ex);
        }
        return errorCode;
    }

    private void validateApiRateLimits(String path, String transactionId, String individualIdHash) {
        int apiCode = path.endsWith("send-otp") ? 1 : path.endsWith("authenticate")? 2 : 3;

        ApiRateLimit apiRateLimit = null;
        try {
            switch (apiCode) {
                case 1:
                    apiRateLimit = cacheUtilService.getApiRateLimitTransaction(transactionId);
                    apiRateLimit = checkRateLimit(1, apiRateLimit, sendOtpAttempts, sendOtpInvocationGapInSeconds, individualIdHash);
                    break;
                case 2:
                    apiRateLimit = cacheUtilService.getApiRateLimitTransaction(transactionId);
                    apiRateLimit = checkRateLimit(2, apiRateLimit, authenticateAttempts, authenticateInvocationGapInSeconds, individualIdHash);
                    break;
            }
        } finally {
            if(apiRateLimit != null) {
                cacheUtilService.saveApiRateLimit(transactionId, apiRateLimit);
            }
        }
    }

    private ApiRateLimit checkRateLimit(int apiCode, ApiRateLimit apiRateLimit, int attemptsLimit, int invocationGapInSeconds,
                                String individualIdHash) {
        if(apiRateLimit == null) {
            apiRateLimit = new ApiRateLimit();
        }
        apiRateLimit.increment(apiCode);
        if(apiRateLimit.getCount().get(apiCode) > attemptsLimit) {
            blockIndividualId(individualIdHash);
            throw new EsignetException(ErrorConstants.NO_ATTEMPTS_LEFT);
        }

        //Reason for invocation time gap check is to deny bot actions, but if we introduce incremental blockage
        //of the individual on wrong attempts - by itself could be a way to mitigate bot actions. TBD
        //TODO Need enhance this logic to handle invocation gaps w.r.t auth-factor used in authenticate request
        //TODO Logic to check invocation gaps between send-otp and authenticate endpoints
        /*try {
            long currentTimeInSeconds = System.currentTimeMillis()/1000;
            if((currentTimeInSeconds - apiRateLimit.getLastInvocation().get(apiCode)) < invocationGapInSeconds) {
                throw new EsignetException(ErrorConstants.TOO_EARLY_ATTEMPT);
            }
        } finally {
            apiRateLimit.updateLastInvocation(apiCode);
        }*/
        return apiRateLimit;
    }

    private void blockIndividualId(String individualIdHash) {
        if(individualIdHash != null) {
            cacheUtilService.blockIndividualId(individualIdHash);
        }
    }

    private OIDCTransaction getTransaction(String transactionId, String requestUri) {
        if(requestUri.endsWith("auth-code") || requestUri.endsWith("prepare-signup-redirect") ||
                requestUri.endsWith("claim-details")) {
            return cacheUtilService.getAuthenticatedTransaction(transactionId);
        }
        if(requestUri.endsWith("resume")) {
            return cacheUtilService.getHaltedTransaction(transactionId);
        }
        return cacheUtilService.getPreAuthTransaction(transactionId);
    }
}
