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

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;

import io.mosip.idp.binding.TestUtil;
import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.RequestWrapper;
import io.mosip.idp.core.dto.WalletBindingRequest;
import io.mosip.idp.core.dto.WalletBindingResponse;
import io.mosip.idp.core.spi.WalletBindingService;

@RunWith(MockitoJUnitRunner.class)
public class WalletBindingControllerTest {

	MockMvc mockMvc;

	@Mock
	private WalletBindingService walletBindingService;

	ObjectMapper objectMapper = new ObjectMapper();

	private JWK clientJWK = TestUtil.generateJWK_RSA();

	@InjectMocks
	WalletBindingController walletBindingController;

	@Before
	public void setup() {
		mockMvc = MockMvcBuilders.standaloneSetup(this.walletBindingController).build();
	}

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
}
