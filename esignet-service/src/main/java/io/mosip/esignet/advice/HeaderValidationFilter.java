package io.mosip.esignet.advice;

import io.mosip.esignet.core.dto.IdPTransaction;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;

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

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain) throws ServletException, IOException {
        final String path = request.getRequestURI();

        if(!pathsToValidate.contains(path)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            log.info("Started to validate {} for oauth-details headers", path);
            final String transactionId = request.getHeader(HEADER_OAUTH_DETAILS_KEY);
            final String hashValue = request.getHeader(HEADER_OAUTH_DETAILS_HASH);
            IdPTransaction transaction = path.endsWith("auth-code") ? cacheUtilService.getAuthenticatedTransaction(transactionId) :
                    cacheUtilService.getPreAuthTransaction(transactionId);
            if(transaction == null) {
                throw new InvalidTransactionException();
            }
            if(transaction.getOauthDetailsHash().equals(hashValue)) {
                filterChain.doFilter(request, response);
                return;
            }
            log.error("oauth-details header validation failed, value in transaction: {}", transaction.getOauthDetailsHash());
            throw new IdPException(INVALID_REQUEST);

        } catch (IdPException e) {
            response.setStatus(HttpStatus.BAD_REQUEST.value());
        }
    }
}
