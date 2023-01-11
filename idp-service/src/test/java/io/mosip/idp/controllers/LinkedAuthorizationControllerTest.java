/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.MessageSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.LinkAuthCodeRequest;
import io.mosip.idp.core.dto.LinkCodeRequest;
import io.mosip.idp.core.dto.LinkCodeResponse;
import io.mosip.idp.core.dto.LinkStatusRequest;
import io.mosip.idp.core.dto.LinkTransactionRequest;
import io.mosip.idp.core.dto.LinkTransactionResponse;
import io.mosip.idp.core.dto.LinkedConsentRequest;
import io.mosip.idp.core.dto.LinkedConsentResponse;
import io.mosip.idp.core.dto.LinkedKycAuthRequest;
import io.mosip.idp.core.dto.LinkedKycAuthResponse;
import io.mosip.idp.core.dto.OtpRequest;
import io.mosip.idp.core.dto.OtpResponse;
import io.mosip.idp.core.dto.RequestWrapper;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.validator.OtpChannel;
import io.mosip.idp.services.AuthorizationHelperService;
import io.mosip.idp.services.CacheUtilService;
import io.mosip.idp.services.LinkedAuthorizationServiceImpl;

@RunWith(SpringRunner.class)
@WebMvcTest(value = LinkedAuthorizationController.class)
public class LinkedAuthorizationControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockBean
	LinkedAuthorizationServiceImpl linkedAuthorizationService;
	
	@InjectMocks
	LinkedAuthorizationServiceImpl authorizationService;

	ObjectMapper objectMapper = new ObjectMapper();

	@Value("${mosip.idp.link-code-expire-in-secs}")
	private int linkCodeExpiryInSeconds;

	@MockBean
	AuthorizationHelperService authorizationHelperService;

	@MockBean
	private AuthenticationWrapper authenticationWrapper;

	@Autowired
	MessageSource messageSource;
	
	@MockBean
	private CacheUtilService cacheUtilService;

	@Value("${mosip.idp.link-status-deferred-response-timeout-secs:0}")
	private long linkStatusDeferredResponseTimeout;

	@Test
	public void generateLinkCode_returnSuccessResponse() throws Exception {
		LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
		linkCodeRequest.setTransactionId("5637465368573875875958");

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		ZonedDateTime expireDateTime = null;

		RequestWrapper<LinkCodeRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkCodeRequest);

		expireDateTime = ZonedDateTime.now(ZoneOffset.UTC).plus(linkCodeExpiryInSeconds, ChronoUnit.SECONDS);

		LinkCodeResponse linkCodeResponse = new LinkCodeResponse();
		linkCodeResponse.setLinkCode("123456");
		linkCodeResponse.setTransactionId(linkCodeRequest.getTransactionId());
		linkCodeResponse.setExpireDateTime(expireDateTime == null ? null
				: expireDateTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));

		when(linkedAuthorizationService.generateLinkCode(linkCodeRequest)).thenReturn(linkCodeResponse);

		mockMvc.perform(post("/linked-authorization/link-code").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
	}

	@Test
	public void generateLinkCode_withInvalidTransactionId_returnErrorResponse() throws Exception {
		LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
		linkCodeRequest.setTransactionId(" ");

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<LinkCodeRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkCodeRequest);

		mockMvc.perform(post("/linked-authorization/link-code").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID));
	}

	@Test
	public void linkTransaction_returnSuccessResponse() throws Exception {
		LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
		linkTransactionRequest.setLinkCode("56678");

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<LinkTransactionRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkTransactionRequest);

		LinkTransactionResponse linkTransactionResponse = new LinkTransactionResponse();
		linkTransactionResponse.setLinkTransactionId("645743gfy774t57465");

		when(linkedAuthorizationService.linkTransaction(linkTransactionRequest)).thenReturn(linkTransactionResponse);

		mockMvc.perform(post("/linked-authorization/link-transaction").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
	}

	@Test
	public void linkTransaction_withInvalidLinkCode_returnErrorResponse() throws Exception {
		LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
		linkTransactionRequest.setLinkCode(null);

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<LinkTransactionRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkTransactionRequest);

		mockMvc.perform(post("/linked-authorization/link-transaction").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_LINK_CODE));
	}

	@Test
	public void getLinkStatus_returnSuccessResponse() throws Exception {
		LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
		linkStatusRequest.setLinkCode("4556657");
		linkStatusRequest.setTransactionId("gfyg754587685ghf");

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		DeferredResult deferredResult = new DeferredResult<>(linkStatusDeferredResponseTimeout * 1000);

		RequestWrapper<LinkStatusRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkStatusRequest);
		Mockito.doNothing().when(linkedAuthorizationService).getLinkStatus(any(), any());

		mockMvc.perform(post("/linked-authorization/link-status").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
	}

	@Test
	public void getLinkStatus_withInvalidLinkCode_returnErrorResponse() throws Exception {
		LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
		linkStatusRequest.setLinkCode(" ");
		linkStatusRequest.setTransactionId("gfyg754587685ghf");

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<LinkStatusRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkStatusRequest);
		
		mockMvc.perform(post("/linked-authorization/link-status").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
		.andExpect(jsonPath("$.errors").isNotEmpty())
		.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_LINK_CODE));
				
	}

	@Test
	public void authenticate_returnSuccessResponse() throws Exception {
		LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
		AuthChallenge authChallenge = new AuthChallenge();
		List<AuthChallenge> listAuthChallenge = new ArrayList<AuthChallenge>();
		authChallenge.setAuthFactorType("ghregh");
		authChallenge.setChallenge("53756hhikn");
		listAuthChallenge.add(authChallenge);
		linkedKycAuthRequest.setIndividualId("1234");
		linkedKycAuthRequest.setLinkedTransactionId("hfrehgfu65746t57");
		linkedKycAuthRequest.setChallengeList(listAuthChallenge);

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<LinkedKycAuthRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkedKycAuthRequest);

		LinkedKycAuthResponse authRespDto = new LinkedKycAuthResponse();
		authRespDto.setLinkedTransactionId(linkedKycAuthRequest.getLinkedTransactionId());

		when(linkedAuthorizationService.authenticateUser(linkedKycAuthRequest)).thenReturn(authRespDto);

		mockMvc.perform(post("/linked-authorization/authenticate").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
	}

	@Test
	public void authenticate_withInvalidTransactionId_returnErrorResponse() throws Exception {
		LinkedKycAuthRequest linkedAuthRequest = new LinkedKycAuthRequest();
		AuthChallenge authChallenge = new AuthChallenge();
		List<AuthChallenge> listAuthChallenge = new ArrayList<AuthChallenge>();
		authChallenge.setAuthFactorType("ghregh");
		authChallenge.setChallenge("53756hhikn");
		listAuthChallenge.add(authChallenge);
		linkedAuthRequest.setIndividualId("123456");
		linkedAuthRequest.setLinkedTransactionId(" ");
		linkedAuthRequest.setChallengeList(listAuthChallenge);

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<LinkedKycAuthRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkedAuthRequest);

		mockMvc.perform(post("/linked-authorization/authenticate").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID));
	}

	@Test
	public void sendOtp_returnSuccessResponse() throws Exception {
		List<@OtpChannel String> otpChannel = new ArrayList<>();
		otpChannel.add("email");

		OtpRequest otpRequest = new OtpRequest();
		otpRequest.setIndividualId("12345");
		otpRequest.setTransactionId("gfdfvuy4534");
		otpRequest.setOtpChannels(otpChannel);
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<OtpRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(otpRequest);

		OtpResponse otpResponse = new OtpResponse();
		otpResponse.setTransactionId(otpRequest.getTransactionId());
		otpResponse.setMaskedEmail("xyz****.com");
		otpResponse.setMaskedMobile("********87");

		when(authenticationWrapper.isSupportedOtpChannel(otpChannel.get(0))).thenReturn(true);
		when(linkedAuthorizationService.sendOtp(otpRequest)).thenReturn(otpResponse);

		mockMvc.perform(post("/linked-authorization/send-otp").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
	}

	@Test
	public void sendOtp_withInvalidTransactionId_returnErrorResponse() throws Exception {

		List<@OtpChannel String> otpChannel = new ArrayList<>();
		otpChannel.add("email");

		OtpRequest otpRequest = new OtpRequest();
		otpRequest.setIndividualId("12345");
		otpRequest.setOtpChannels(otpChannel);
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<OtpRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(otpRequest);
		
		when(authenticationWrapper.isSupportedOtpChannel(otpChannel.get(0))).thenReturn(true);

		mockMvc.perform(post("/linked-authorization/send-otp").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID));
	}

	@Test
	public void saveConsent_returnSuccessResponse() throws Exception {

		LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
		linkedConsentRequest.setLinkedTransactionId("ghghf4564365hvdh");
		List<String> acceptedClaims = new ArrayList<String>();
		acceptedClaims.add("token");
		List<String> permittedAuthorizeScopes = new ArrayList<String>();
		permittedAuthorizeScopes.add("local");
		linkedConsentRequest.setPermittedAuthorizeScopes(permittedAuthorizeScopes);

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<LinkedConsentRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkedConsentRequest);

		LinkedConsentResponse authRespDto = new LinkedConsentResponse();
		authRespDto.setLinkedTransactionId(linkedConsentRequest.getLinkedTransactionId());

		when(linkedAuthorizationService.saveConsent(linkedConsentRequest)).thenReturn(authRespDto);

		mockMvc.perform(post("/linked-authorization/consent").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
	}

	@Test
	public void saveConsent_withInvalidTransactionId_returnErrorResponse() throws Exception {

		LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
		linkedConsentRequest.setLinkedTransactionId(" ");
		List<String> acceptedClaims = new ArrayList<String>();
		acceptedClaims.add("token");
		List<String> permittedAuthorizeScopes = new ArrayList<String>();
		permittedAuthorizeScopes.add("local");
		linkedConsentRequest.setPermittedAuthorizeScopes(permittedAuthorizeScopes);

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<LinkedConsentRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkedConsentRequest);

		mockMvc.perform(post("/linked-authorization/consent").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID));
	}

	@Test
	public void getAuthCode_returnSuccessResponse() throws Exception {
		LinkAuthCodeRequest linkAuthCodeRequest = new LinkAuthCodeRequest();
		linkAuthCodeRequest.setLinkedCode("4556657");
		linkAuthCodeRequest.setTransactionId("gfyg754587685ghf");

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<LinkAuthCodeRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkAuthCodeRequest);

		mockMvc.perform(post("/linked-authorization/link-auth-code").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
	}

	@Test
	public void getAuthCode_withInvalidLinkCode_returnErrorResponse() throws Exception {
		LinkAuthCodeRequest linkAuthCodeRequest = new LinkAuthCodeRequest();
		linkAuthCodeRequest.setLinkedCode(" ");
		linkAuthCodeRequest.setTransactionId("gfyg754587685ghf");

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<LinkAuthCodeRequest> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(linkAuthCodeRequest);

		mockMvc.perform(post("/linked-authorization/link-auth-code").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_LINK_CODE));
	}

}
