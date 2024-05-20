package io.mosip.esignet.advice;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.ApiRateLimit;
import io.mosip.esignet.core.dto.Error;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.ResponseWrapper;
import io.mosip.esignet.services.CacheUtilService;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.context.MessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;

import java.io.IOException;
import java.util.Arrays;

import static org.mockito.Mockito.*;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class HeaderValidationFilterTest {

    @Mock
    FilterChain filterChain;

    @InjectMocks
    private HeaderValidationFilter headerValidationFilter;

    @Mock
    CacheUtilService cacheUtilService;

    @Mock
    MessageSource messageSource;

    private ObjectMapper objectMapper = new ObjectMapper();

    @Before
    public void setUp() {
        ReflectionTestUtils.setField(headerValidationFilter, "pathsToValidate",
                Arrays.asList("/v1/esignet/authorization/send-otp",
                        "/v1/esignet/authorization/authenticate"));
        ReflectionTestUtils.setField(headerValidationFilter, "objectMapper", objectMapper);
        ReflectionTestUtils.setField(headerValidationFilter, "authenticateAttempts", 3);
        ReflectionTestUtils.setField(headerValidationFilter, "sendOtpAttempts", 3);
        ReflectionTestUtils.setField(headerValidationFilter, "sendOtpInvocationGapInSeconds", 3);
        ReflectionTestUtils.setField(headerValidationFilter, "authenticateInvocationGapInSeconds", 3);
    }

    @Test
    public void doFilter_withNoHeader_thenFail() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cacheUtilService.getPreAuthTransaction(null)).thenReturn(null);
        headerValidationFilter.doFilterInternal(request, response, filterChain);
        ResponseWrapper responseWrapper = objectMapper.readValue(response.getContentAsString(), ResponseWrapper.class);
        Assert.assertNotNull(responseWrapper.getErrors());
        Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ((Error)responseWrapper.getErrors().get(0)).getErrorCode());
    }

    @Test
    public void doFilter_withInvalidTransactionId_thenFail() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        when(cacheUtilService.getPreAuthTransaction("oauth-details-key")).thenReturn(null);
        request.addHeader("oauth-details-hash", "oauth-details-hash");
        request.addHeader("oauth-details-key", "oauth-details-key");
        headerValidationFilter.doFilterInternal(request, response, filterChain);
        ResponseWrapper responseWrapper = objectMapper.readValue(response.getContentAsString(), ResponseWrapper.class);
        Assert.assertNotNull(responseWrapper.getErrors());
        Assert.assertEquals(ErrorConstants.INVALID_TRANSACTION, ((Error)responseWrapper.getErrors().get(0)).getErrorCode());
    }

    @Test
    public void doFilter_withInvalidHeader_thenFail() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setOauthDetailsHash("oauth-details-hash");
        when(cacheUtilService.getPreAuthTransaction("oauth-details-key")).thenReturn(oidcTransaction);
        request.addHeader("oauth-details-hash", "oauth-details-hash11");
        request.addHeader("oauth-details-key", "oauth-details-key");

        headerValidationFilter.doFilterInternal(request, response, filterChain);
        ResponseWrapper responseWrapper = objectMapper.readValue(response.getContentAsString(), ResponseWrapper.class);
        Assert.assertNotNull(responseWrapper.getErrors());
        Assert.assertEquals(ErrorConstants.INVALID_REQUEST, ((Error)responseWrapper.getErrors().get(0)).getErrorCode());
    }

    @Test
    public void doFilter_withValidHeader_thenPass() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setOauthDetailsHash("oauth-details-hash");
        when(cacheUtilService.getPreAuthTransaction("oauth-details-key")).thenReturn(oidcTransaction);
        request.addHeader("oauth-details-hash", "oauth-details-hash");
        request.addHeader("oauth-details-key", "oauth-details-key");

        headerValidationFilter.doFilterInternal(request, response, filterChain);
        verify(cacheUtilService, times(1)).getPreAuthTransaction("oauth-details-key");
    }

    @Test
    public void doFilter_forGetAuthCode_thenPass() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/esignet/authorization/auth-code");
        MockHttpServletResponse response = new MockHttpServletResponse();

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setOauthDetailsHash("oauth-details-hash");
        when(cacheUtilService.getAuthenticatedTransaction("oauth-details-key")).thenReturn(oidcTransaction);
        request.addHeader("oauth-details-hash", "oauth-details-hash");
        request.addHeader("oauth-details-key", "oauth-details-key");

        headerValidationFilter.doFilterInternal(request, response, filterChain);
        verify(cacheUtilService, times(0)).getPreAuthTransaction("oauth-details-key");
        verify(cacheUtilService, times(1)).getAuthenticatedTransaction("oauth-details-key");
    }

    @Test
    public void doFilter_withinApiRateLimit_thenPass() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/esignet/authorization/send-otp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("oauth-details-hash", "oauth-details-hash");
        request.addHeader("oauth-details-key", "oauth-details-key");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setOauthDetailsHash("oauth-details-hash");
        when(cacheUtilService.getPreAuthTransaction("oauth-details-key")).thenReturn(oidcTransaction);

        headerValidationFilter.doFilterInternal(request, response, filterChain);
        verify(cacheUtilService, times(1)).getPreAuthTransaction("oauth-details-key");
        verify(cacheUtilService, times(0)).getAuthenticatedTransaction("oauth-details-key");
        verify(cacheUtilService, times(1)).getApiRateLimitTransaction("oauth-details-key");
    }

    @Test
    public void doFilter_exceedApiRateLimit_thenFail() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/esignet/authorization/send-otp");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("oauth-details-hash", "oauth-details-hash");
        request.addHeader("oauth-details-key", "oauth-details-key");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setOauthDetailsHash("oauth-details-hash");
        oidcTransaction.setIndividualIdHash("test");
        when(cacheUtilService.getPreAuthTransaction("oauth-details-key")).thenReturn(oidcTransaction);
        ApiRateLimit apiRateLimit = new ApiRateLimit();
        apiRateLimit.increment(1);
        apiRateLimit.increment(1);
        apiRateLimit.increment(1);
        when(cacheUtilService.getApiRateLimitTransaction("oauth-details-key")).thenReturn(apiRateLimit);

        headerValidationFilter.doFilterInternal(request, response, filterChain);
        verify(cacheUtilService, times(1)).getPreAuthTransaction("oauth-details-key");
        verify(cacheUtilService, times(0)).getAuthenticatedTransaction("oauth-details-key");
        verify(cacheUtilService, times(1)).getApiRateLimitTransaction("oauth-details-key");
        verify(cacheUtilService, times(1)).blockIndividualId("test");
        verify(cacheUtilService, times(1)).saveApiRateLimit("oauth-details-key", apiRateLimit);

        ResponseWrapper responseWrapper = objectMapper.readValue(response.getContentAsString(), ResponseWrapper.class);
        Assert.assertNotNull(responseWrapper.getErrors());
        Assert.assertEquals(ErrorConstants.NO_ATTEMPTS_LEFT, ((Error)responseWrapper.getErrors().get(0)).getErrorCode());
    }

    @Ignore
    @Test
    public void doFilter_exceedInvocationGapLimit_thenFail() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/v1/esignet/authorization/authenticate");
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.addHeader("oauth-details-hash", "oauth-details-hash");
        request.addHeader("oauth-details-key", "oauth-details-key");

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setOauthDetailsHash("oauth-details-hash");
        oidcTransaction.setIndividualIdHash("test");
        when(cacheUtilService.getPreAuthTransaction("oauth-details-key")).thenReturn(oidcTransaction);
        ApiRateLimit apiRateLimit = new ApiRateLimit();
        apiRateLimit.increment(2);
        apiRateLimit.updateLastInvocation(2);
        when(cacheUtilService.getApiRateLimitTransaction("oauth-details-key")).thenReturn(apiRateLimit);

        headerValidationFilter.doFilterInternal(request, response, filterChain);
        verify(cacheUtilService, times(1)).getPreAuthTransaction("oauth-details-key");
        verify(cacheUtilService, times(0)).getAuthenticatedTransaction("oauth-details-key");
        verify(cacheUtilService, times(1)).getApiRateLimitTransaction("oauth-details-key");
        verify(cacheUtilService, times(1)).saveApiRateLimit("oauth-details-key", apiRateLimit);

        ResponseWrapper responseWrapper = objectMapper.readValue(response.getContentAsString(), ResponseWrapper.class);
        Assert.assertNotNull(responseWrapper.getErrors());
        Assert.assertEquals(ErrorConstants.TOO_EARLY_ATTEMPT, ((Error)responseWrapper.getErrors().get(0)).getErrorCode());
    }
}
