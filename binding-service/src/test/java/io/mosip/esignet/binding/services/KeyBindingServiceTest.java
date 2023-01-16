/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.binding.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mosip.esignet.binding.entity.PublicKeyRegistry;
import io.mosip.esignet.core.dto.*;
import io.mosip.idp.authwrapper.service.MockKeyBindingWrapperService;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.exception.KeyBindingException;
import io.mosip.esignet.core.exception.SendOtpException;
import io.mosip.esignet.core.util.ErrorConstants;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
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

import io.mosip.esignet.binding.TestUtil;
import io.mosip.esignet.binding.repository.PublicKeyRegistryRepository;

@RunWith(MockitoJUnitRunner.class)
public class KeyBindingServiceTest {

	@InjectMocks
	KeyBindingServiceImpl keyBindingService;

	@Mock
	private PublicKeyRegistryRepository publicKeyRegistryRepository;

	@Mock
	MockKeyBindingWrapperService mockKeyBindingWrapperService;

	@Mock
	KeyBindingHelperService keyBindingHelperService;

	@Mock
	KeymanagerUtil keymanagerUtil;

	private JWK clientJWK = TestUtil.generateJWK_RSA();

	private ObjectMapper objectMapper = new ObjectMapper();

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		ReflectionTestUtils.setField(keyBindingService, "encryptBindingId", true);

		mockKeyBindingWrapperService = mock(MockKeyBindingWrapperService.class);
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

	@Test
	public void sendBindingOtp_withNullResponseFromWrapper_thenFail() throws SendOtpException {
		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("123456789");
		otpRequest.setOtpChannels(Arrays.asList("OTP"));
		when(mockKeyBindingWrapperService.sendBindingOtp(anyString(), any(), any())).thenReturn(null);

		try {
			keyBindingService.sendBindingOtp(otpRequest, new HashMap<>());
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.SEND_OTP_FAILED));
		}
	}

	@Test
	public void bindWallet_withValidDetails_thenPass() throws IdPException, KeyBindingException, JsonProcessingException {
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
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
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

	@Test
	public void bindWallet_withUnsupportedFormat_thenFail() throws IdPException, JsonProcessingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setAuthFactorType("WLA");
		walletBindingRequest.setFormat("wt");

		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_CHALLENGE_FORMAT));
		}
	}

	@Test
	public void bindWallet_withInvalidKeyBindingResult_thenFail() throws IOException, IdPException, KeyBindingException {
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

		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(null);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_FAILED));
		}

		KeyBindingResult keyBindingResult = new KeyBindingResult();
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(keyBindingResult);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_FAILED));
		}

		keyBindingResult = new KeyBindingResult();
		keyBindingResult.setCertificate("data");
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(keyBindingResult);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_FAILED));
		}
	}

	@Test
	public void bindWallet_saveKeyBindingThrowException_thenFail() throws IOException, IdPException, KeyBindingException {
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
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(keyBindingResult);

		when(keyBindingHelperService.storeKeyBindingDetailsInRegistry(Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
				Mockito.anyString(), Mockito.anyString())).thenThrow(new IdPException(ErrorConstants.DUPLICATE_PUBLIC_KEY));

		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.DUPLICATE_PUBLIC_KEY));
		}
	}

	@Test
	public void bindWallet_withAlreadyExistingWalletBindingId_thenPass()
			throws IOException, IdPException, KeyBindingException {
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
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
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
}