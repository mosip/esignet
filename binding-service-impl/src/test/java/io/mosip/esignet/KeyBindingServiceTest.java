/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet;

import static io.mosip.esignet.api.util.ErrorConstants.SEND_OTP_FAILED;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.time.LocalDateTime;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KeyBindingResult;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KeyBindingException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.KeyBinder;
import io.mosip.esignet.entity.PublicKeyRegistry;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import io.mosip.esignet.services.KeyBindingHelperService;
import io.mosip.esignet.services.KeyBindingServiceImpl;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import lombok.extern.slf4j.Slf4j;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;

@Slf4j
@RunWith(MockitoJUnitRunner.class)
public class KeyBindingServiceTest {

	@InjectMocks
	KeyBindingServiceImpl keyBindingService;

	@Mock
	private PublicKeyRegistryRepository publicKeyRegistryRepository;

	@Mock
	KeyBindingHelperService keyBindingHelperService;

	@Mock
	KeyBinder mockKeyBindingWrapperService;

	@Mock
	KeymanagerUtil keymanagerUtil;

	private JWK clientJWK = generateJWK_RSA();

	private ObjectMapper objectMapper = new ObjectMapper();

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		ReflectionTestUtils.setField(keyBindingService, "encryptBindingId", true);

		mockKeyBindingWrapperService = mock(KeyBinder.class);
		when(mockKeyBindingWrapperService.getSupportedChallengeFormats(Mockito.anyString()))
				.thenReturn(Arrays.asList("jwt", "alpha-numeric"));
		ReflectionTestUtils.setField(keyBindingService, "keyBindingWrapper", mockKeyBindingWrapperService);

		keyBindingHelperService = mock(KeyBindingHelperService.class);
		ReflectionTestUtils.setField(keyBindingHelperService, "saltLength", 10);
		ReflectionTestUtils.setField(keyBindingHelperService, "publicKeyRegistryRepository", publicKeyRegistryRepository);
		ReflectionTestUtils.setField(keyBindingHelperService, "keymanagerUtil", keymanagerUtil);

		ReflectionTestUtils.setField(keyBindingService, "keyBindingHelperService", keyBindingHelperService);
	}

	@Test
	public void sendBindingOtp_withValidDetails_thenPass() throws SendOtpException {
		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("8267411571");
		otpRequest.setOtpChannels(Arrays.asList("OTP"));

		SendOtpResult sendOtpResult = new SendOtpResult(null,"", "");

		Map<String, String> headers = new HashMap<>();
		when(mockKeyBindingWrapperService.sendBindingOtp(anyString(), any(), any())).thenReturn(sendOtpResult);

		BindingOtpResponse otpResponse = keyBindingService.sendBindingOtp(otpRequest, headers);
		Assert.assertNotNull(otpResponse);
	}
	
	@Test(expected = EsignetException.class)
	public void sendBindingOtp_withInvalidRequest_thenFail() throws SendOtpException {
		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("8267411571");
		otpRequest.setOtpChannels(Arrays.asList("OTP"));

		Map<String, String> headers = new HashMap<>();
		when(mockKeyBindingWrapperService.sendBindingOtp(anyString(), any(), any())).thenThrow(SendOtpException.class);

		keyBindingService.sendBindingOtp(otpRequest, headers);
	}

	@Test
	public void sendBindingOtp_withNullResponseFromWrapper_thenFail() throws SendOtpException {
		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("123456789");
		otpRequest.setOtpChannels(Arrays.asList("OTP"));
		when(mockKeyBindingWrapperService.sendBindingOtp(anyString(), any(), any())).thenReturn(null);

		try {
			keyBindingService.sendBindingOtp(otpRequest, new HashMap<>());
			Assert.fail();
		} catch (EsignetException e) {
			Assert.assertTrue(e.getErrorCode().equals(SEND_OTP_FAILED));
		}
	}

	@Test
	public void bindWallet_withValidDetails_thenPass() throws EsignetException, KeyBindingException, JsonProcessingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setAuthFactorType("WLA");
		walletBindingRequest.setFormat("jwt");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		authChallenge.setFormat("alpha-numeric");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest
				.setPublicKey(
						(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));

		KeyBindingResult keyBindingResult = new KeyBindingResult();
		keyBindingResult.setPartnerSpecificUserToken("psutoken123");
		keyBindingResult.setCertificate("certificate");
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any()))
				.thenReturn(keyBindingResult);

		PublicKeyRegistry publicKeyRegistry=new PublicKeyRegistry();
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setIdHash("hCv0zz_jnOkGS17BN1dUOU7tqNri-8XpHokWVQQMfiI");
		publicKeyRegistry.setPsuToken("psutoken123");
		publicKeyRegistry.setWalletBindingId("tXOFGPKly4L_9VI8NYvYXJe5ZNrgOIUnfFdLkNKYdTE");
		when(keyBindingHelperService.storeKeyBindingDetailsInRegistry(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
				Mockito.anyString(), Mockito.anyString())).thenReturn(publicKeyRegistry);

		Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
	}
	
	@Test(expected = EsignetException.class)
	public void bindWallet_withInvalidRequest_thenFail() throws EsignetException, KeyBindingException, JsonProcessingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setAuthFactorType("WLA");
		walletBindingRequest.setFormat("jwt");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		authChallenge.setFormat("alpha-numeric");
		List<AuthChallenge> authChallengeList = new ArrayList<>();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest
				.setPublicKey(
						(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));

		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any()))
				.thenThrow(KeyBindingException.class);

		keyBindingService.bindWallet(walletBindingRequest, new HashMap<>());
	}

	@Test
	public void bindWallet_withUnsupportedFormat_thenFail() throws EsignetException, JsonProcessingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setAuthFactorType("WLA");
		walletBindingRequest.setFormat("wt");

		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		authChallenge.setFormat("alpha-numeric");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (EsignetException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_AUTH_FACTOR_TYPE_OR_CHALLENGE_FORMAT));
		}
	}

	@Test
	public void bindWallet_withInvalidAuthChallenge_thenFail() throws EsignetException, JsonProcessingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setAuthFactorType("WLA");
		walletBindingRequest.setFormat("wt");

		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		authChallenge.setFormat("alpha");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (EsignetException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_AUTH_FACTOR_TYPE_OR_CHALLENGE_FORMAT));
		}
	}

	@Test
	public void bindWallet_withInvalidKeyBindingResult_thenFail() throws IOException, EsignetException, KeyBindingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setAuthFactorType("WLA");
		walletBindingRequest.setFormat("jwt");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		authChallenge.setFormat("alpha-numeric");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));

		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (EsignetException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_FAILED));
		}

		KeyBindingResult keyBindingResult = new KeyBindingResult();
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any()))
				.thenReturn(keyBindingResult);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (EsignetException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_FAILED));
		}

		keyBindingResult = new KeyBindingResult();
		keyBindingResult.setCertificate("data");
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any()))
				.thenReturn(keyBindingResult);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (EsignetException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_FAILED));
		}
	}

	@Test
	public void bindWallet_saveKeyBindingThrowException_thenFail() throws IOException, EsignetException, KeyBindingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setAuthFactorType("WLA");
		walletBindingRequest.setFormat("jwt");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		authChallenge.setFormat("alpha-numeric");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));

		KeyBindingResult keyBindingResult = new KeyBindingResult();
		keyBindingResult.setCertificate("data");
		keyBindingResult.setPartnerSpecificUserToken("psut");
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(),Mockito.anyString(), Mockito.any()))
				.thenReturn(keyBindingResult);

		when(keyBindingHelperService.storeKeyBindingDetailsInRegistry(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
				Mockito.anyString(), Mockito.anyString())).thenThrow(new EsignetException(ErrorConstants.DUPLICATE_PUBLIC_KEY));

		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (EsignetException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.DUPLICATE_PUBLIC_KEY));
		}
	}

	@Test
	public void bindWallet_withAlreadyExistingWalletBindingId_thenPass()
			throws IOException, EsignetException, KeyBindingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setAuthFactorType("WLA");
		walletBindingRequest.setFormat("jwt");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		authChallenge.setFormat("alpha-numeric");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));

		KeyBindingResult keyBindingResult = new KeyBindingResult();
		keyBindingResult.setCertificate("data");
		keyBindingResult.setPartnerSpecificUserToken("psut");
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.anyString(), Mockito.any()))
				.thenReturn(keyBindingResult);

		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setIdHash("hCv0zz_jnOkGS17BN1dUOU7tqNri-8XpHokWVQQMfiI");
		publicKeyRegistry.setPsuToken("psutoken123");
		publicKeyRegistry.setWalletBindingId("tXOFGPKly4L_9VI8NYvYXJe5ZNrgOIUnfFdLkNKYdTE");
		when(keyBindingHelperService.storeKeyBindingDetailsInRegistry(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
				Mockito.anyString(), Mockito.anyString())).thenReturn(publicKeyRegistry);

		Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
	}

	public static JWK generateJWK_RSA() {
		// Generate the RSA key pair
		try {
			KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
			gen.initialize(2048);
			KeyPair keyPair = gen.generateKeyPair();
			// Convert public key to JWK format
			return new RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
					.privateKey((RSAPrivateKey)keyPair.getPrivate())
					.keyUse(KeyUse.SIGNATURE)
					.keyID(UUID.randomUUID().toString())
					.build();
		} catch (Exception e) {
			log.error("generateJWK_RSA failed", e);
		}
		return null;
	}
}