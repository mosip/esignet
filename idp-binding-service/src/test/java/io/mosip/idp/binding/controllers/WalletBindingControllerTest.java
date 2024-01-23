package io.mosip.idp.binding.controllers;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

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
import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.RequestWrapper;
import io.mosip.idp.core.dto.WalletBindingRequest;
import io.mosip.idp.core.dto.WalletBindingResponse;
import io.mosip.idp.core.spi.WalletBindingService;
import io.mosip.idp.core.util.ErrorConstants;

@RunWith(SpringRunner.class)
@WebMvcTest(value = WalletBindingController.class)
public class WalletBindingControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockBean
	private WalletBindingService walletBindingService;

	ObjectMapper objectMapper = new ObjectMapper();

	private JWK clientJWK = TestUtil.generateJWK_RSA();

	@InjectMocks
	WalletBindingController walletBindingController;


	@Test
	public void bindWallet_returnSuccessResponse() throws Exception {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("id1");
		walletBindingRequest
				.setPublicKey((Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));

		walletBindingRequest.setTransactionId("123456789");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("12345");
		walletBindingRequest.setAuthChallenge(authChallenge);
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(walletBindingRequest);

		WalletBindingResponse walletBindingResponse = new WalletBindingResponse();
		walletBindingResponse.setTransactionId("123456789");
		when(walletBindingService.bindWallet(walletBindingRequest)).thenReturn(walletBindingResponse);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.response.transactionId").value("123456789"));
	}

	@Test
	public void bindWallet_withInvalidTransaction_returnErrorResponse() throws Exception {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("id1");
		walletBindingRequest
				.setPublicKey((Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));

		walletBindingRequest.setTransactionId("");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("12345");
		walletBindingRequest.setAuthChallenge(authChallenge);
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(walletBindingRequest);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
		.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_TRANSACTION_ID));
	}

	@Test
	public void bindWallet_withInvalidIndividualId_returnErrorResponse() throws Exception {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("");
		walletBindingRequest
				.setPublicKey((Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));

		walletBindingRequest.setTransactionId("123456789");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("12345");
		walletBindingRequest.setAuthChallenge(authChallenge);
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(walletBindingRequest);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_INDIVIDUAL_ID));
	}

	@Test
	public void bindWallet_withInvalidPublickKey_returnErrorResponse() throws Exception {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("id1");
		walletBindingRequest
				.setPublicKey(null);

		walletBindingRequest.setTransactionId("123456789");

		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("12345");
		walletBindingRequest.setAuthChallenge(authChallenge);
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(walletBindingRequest);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_PUBLIC_KEY));
	}

	@Test
	public void bindWallet_withInvalidAuthChallenge_returnErrorResponse() throws Exception {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("id1");
		walletBindingRequest
				.setPublicKey((Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));

		walletBindingRequest.setTransactionId("123456789");
		walletBindingRequest.setAuthChallenge(null);
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);
		RequestWrapper wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(walletBindingRequest);

		mockMvc.perform(post("/wallet-binding").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty())
				.andExpect(jsonPath("$.errors[0].errorCode").value(ErrorConstants.INVALID_AUTH_CHALLENGE));
	}
}
