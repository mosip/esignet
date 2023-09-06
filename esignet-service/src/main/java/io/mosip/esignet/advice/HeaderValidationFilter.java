package io.mosip.esignet.advice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.ResponseWrapper;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
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
            log.info("Started to validate {} for oauth-details headers", path);
            final String transactionId = request.getHeader(HEADER_OAUTH_DETAILS_KEY);
            final String hashValue = request.getHeader(HEADER_OAUTH_DETAILS_HASH);
            OIDCTransaction transaction = path.endsWith("auth-code") ? cacheUtilService.getAuthenticatedTransaction(transactionId) :
                    cacheUtilService.getPreAuthTransaction(transactionId);
            if(transaction == null) {
                throw new InvalidTransactionException();
            }
            if(transaction.getOauthDetailsHash().equals(hashValue)) {
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
}
