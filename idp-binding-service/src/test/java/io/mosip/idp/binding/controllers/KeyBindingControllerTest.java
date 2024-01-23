/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.controllers;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static io.mosip.idp.core.util.ErrorConstants.INVALID_AUTH_FACTOR_TYPE;
import static io.mosip.idp.core.util.ErrorConstants.INVALID_CHALLENGE;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.dto.Error;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;

import io.mosip.idp.binding.TestUtil;
import io.mosip.idp.core.spi.KeyBindingService;
import org.springframework.test.web.servlet.MvcResult;

@RunWith(SpringRunner.class)
@WebMvcTest(value = KeyBindingController.class)
public class KeyBindingControllerTest {

	ObjectMapper objectMapper = new ObjectMapper();

	private JWK clientJWK = TestUtil.generateJWK_RSA();

	@Autowired
	MockMvc mockMvc;

	@MockBean
	private KeyBindingService keyBindingService;

	@InjectMocks
	KeyBindingController keyBindingController;



	@Test
	public void sendBindingOtp_withValidRequest_thenPass() throws Exception {
		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("8267411571");
		otpRequest.setOtpChannels(Arrays.asList("email"));
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(otpRequest);

		OtpResponse otpResponse = new OtpResponse();
		otpResponse.setTransactionId("qwertyId");
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json;charset=UTF-8");
		headers.put("Content-Length", "106");
		when(keyBindingService.sendBindingOtp(otpRequest, headers)).thenReturn(otpResponse);

		mockMvc.perform(post("/binding-otp").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.response.transactionId").value(otpResponse.getTransactionId()));
	}

	@Test
	public void bindWallet_withValidDetails_thenPass() throws Exception {
		WalletBindingRequest walletBindingRequest = getWalletBindingRequest();
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(walletBindingRequest);

		WalletBindingResponse walletBindingResponse = new WalletBindingResponse();
		walletBindingResponse.setTransactionId("123456789");
		Map<String, String> headers = new HashMap<>();
		headers.put("Content-Type", "application/json;charset=UTF-8");
		headers.put("Content-Length", "1877");
		when(keyBindingService.bindWallet(walletBindingRequest, headers)).thenReturn(walletBindingResponse);

		mockMvc.perform(post("/wallet-binding")
						.content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.response.transactionId").value(walletBindingResponse.getTransactionId()));
	}

	@Test
	public void bindWallet_withBlankTransaction_thenFail() throws Exception {
		WalletBindingRequest walletBindingRequest = getWalletBindingRequest();
		walletBindingRequest.setTransactionId("");
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(walletBindingRequest);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID));
	}

	@Test
	public void bindWallet_withBlankIndividualId_thenFail() throws Exception {
		WalletBindingRequest walletBindingRequest = getWalletBindingRequest();
		walletBindingRequest.setIndividualId("");
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(walletBindingRequest);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_INDIVIDUAL_ID));
	}

	@Test
	public void bindWallet_withNullPublicKey_thenFail() throws Exception {
		WalletBindingRequest walletBindingRequest = getWalletBindingRequest();
		walletBindingRequest.setPublicKey(null);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(walletBindingRequest);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_PUBLIC_KEY));
	}

	@Test
	public void bindWallet_withEmptyPublicKey_thenFail() throws Exception {
		WalletBindingRequest walletBindingRequest = getWalletBindingRequest();
		walletBindingRequest.setPublicKey(new HashMap<>());
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(walletBindingRequest);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_PUBLIC_KEY));
	}

	@Test
	public void bindWallet_withNullAuthChallenge_thenFail() throws Exception {
		WalletBindingRequest walletBindingRequest = getWalletBindingRequest();
		walletBindingRequest.setChallengeList(null);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(walletBindingRequest);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_CHALLENGE_LIST));
	}

	@Test
	public void bindWallet_withEmptyAuthChallenge_thenFail() throws Exception {
		WalletBindingRequest walletBindingRequest = getWalletBindingRequest();
		walletBindingRequest.setChallengeList(new ArrayList<>());
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(walletBindingRequest);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_CHALLENGE_LIST));
	}

	@Test
	public void bindWallet_withAuthChallengeEmptyFactorAndEmptyChallenge_thenFail() throws Exception {
		WalletBindingRequest walletBindingRequest = getWalletBindingRequest();
		walletBindingRequest.getChallengeList().get(0).setChallenge("");
		walletBindingRequest.getChallengeList().get(0).setAuthFactorType("");
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(walletBindingRequest);

		MvcResult mvcResult = mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty()).andReturn();

		List<String> errorCodes = Arrays.asList(INVALID_AUTH_FACTOR_TYPE, INVALID_CHALLENGE);
		ResponseWrapper responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
		Assert.assertTrue(responseWrapper.getErrors().size() == 2);
		Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));
		Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(1)).getErrorCode()));
	}

	@Test
	public void validateBinding_withValidRequest_thenPass() throws Exception {
		ValidateBindingRequest validateBindingRequest = getValidateBindingRequest();
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(validateBindingRequest);

		ValidateBindingResponse bindingResponse = new ValidateBindingResponse();
		bindingResponse.setTransactionId(validateBindingRequest.getTransactionId());
		bindingResponse.setIndividualId(validateBindingRequest.getIndividualId());
		when(keyBindingService.validateBinding(validateBindingRequest)).thenReturn(bindingResponse);

		mockMvc.perform(post("/validate-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.response.transactionId").value(validateBindingRequest.getTransactionId()));
	}

	@Test
	public void validateBinding_withInvalidIndividualId_thenFail() throws Exception {
		ValidateBindingRequest validateBindingRequest = getValidateBindingRequest();
		validateBindingRequest.setIndividualId("");
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(validateBindingRequest);

		mockMvc.perform(post("/validate-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_INDIVIDUAL_ID));
	}

	@Test
	public void validateBinding_withNullChallenge_thenFail() throws Exception {
		ValidateBindingRequest validateBindingRequest = getValidateBindingRequest();
		validateBindingRequest.setChallenge(null);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(validateBindingRequest);

		mockMvc.perform(post("/validate-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_AUTH_CHALLENGE));
	}

	@Test
	public void validateBinding_withInvalidChallenge_thenFail() throws Exception {
		ValidateBindingRequest validateBindingRequest = getValidateBindingRequest();
		validateBindingRequest.getChallenge().setAuthFactorType(null);
		validateBindingRequest.getChallenge().setChallenge(null);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(validateBindingRequest);

		MvcResult mvcResult = mockMvc.perform(post("/validate-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty()).andReturn();

		List<String> errorCodes = Arrays.asList(INVALID_AUTH_FACTOR_TYPE, INVALID_CHALLENGE);
		ResponseWrapper responseWrapper = objectMapper.readValue(mvcResult.getResponse().getContentAsString(), ResponseWrapper.class);
		Assert.assertTrue(responseWrapper.getErrors().size() == 2);
		Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(0)).getErrorCode()));
		Assert.assertTrue(errorCodes.contains(((Error)responseWrapper.getErrors().get(1)).getErrorCode()));
	}

	@Test
	public void validateBinding_withInvalidTransactionId_thenFail() throws Exception {
		ValidateBindingRequest validateBindingRequest = getValidateBindingRequest();
		validateBindingRequest.setTransactionId(null);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());
		wrapper.setRequest(validateBindingRequest);

		mockMvc.perform(post("/validate-binding").content(objectMapper.writeValueAsString(wrapper))
						.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID));
	}

	private WalletBindingRequest getWalletBindingRequest() throws JsonProcessingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("id1");
		walletBindingRequest.setPublicKey((Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));
		walletBindingRequest.setTransactionId("123456789");
		walletBindingRequest.setAuthFactorTypes(Arrays.asList("WLA"));
		KeyBindingAuthChallenge authChallenge = new KeyBindingAuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("12345");
		List<KeyBindingAuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		return walletBindingRequest;
	}


	private ValidateBindingRequest getValidateBindingRequest() {
		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("9043211571");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("JWT");
		authChallenge.setChallenge("eyJzdWIiOiIxM");
		validateBindingRequest.setChallenge(authChallenge);
		return validateBindingRequest;
	}

}