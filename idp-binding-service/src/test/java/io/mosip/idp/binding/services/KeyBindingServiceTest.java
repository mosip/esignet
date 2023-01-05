/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.services;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.StringWriter;
import java.math.BigInteger;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.mosip.idp.authwrapper.service.MockKeyBindingWrapperService;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.KeyBindingException;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.keymanagerservice.exception.KeymanagerServiceException;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.jose4j.jws.JsonWebSignature;
import org.json.simple.JSONObject;
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

import io.mosip.idp.binding.TestUtil;
import io.mosip.idp.binding.dto.BindingTransaction;
import io.mosip.idp.binding.entity.PublicKeyRegistry;
import io.mosip.idp.binding.repository.PublicKeyRegistryRepository;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;

import javax.security.auth.x500.X500Principal;

@RunWith(MockitoJUnitRunner.class)
public class KeyBindingServiceTest {

	@InjectMocks
	KeyBindingServiceImpl keyBindingService;

	@Mock
	CacheUtilService cacheUtilService;

	@Mock
	private SignatureService signatureService;

	@Mock
	private KeymanagerService keymanagerService;

	ObjectMapper objectMapper = new ObjectMapper();

	@Mock
	private PublicKeyRegistryRepository publicKeyRegistryRepository;

	@Mock
	MockKeyBindingWrapperService mockKeyBindingWrapperService;

	@Mock
	KeymanagerUtil keymanagerUtil;

	private JWK clientJWK = TestUtil.generateJWK_RSA();

	private String validateBindingUrl = "http://localhost:8087/v1/idpbinding/validate-binding";

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		ReflectionTestUtils.setField(keyBindingService, "expireDays", 2);
		ReflectionTestUtils.setField(keyBindingService, "validateBindingUrl", validateBindingUrl);
		ReflectionTestUtils.setField(keyBindingService, "saltLength", 16);
		ReflectionTestUtils.setField(keyBindingService, "encryptBindingId", true);
		ReflectionTestUtils.setField(keyBindingService, "objectMapper", objectMapper);

		mockKeyBindingWrapperService = mock(MockKeyBindingWrapperService.class);
		ReflectionTestUtils.setField(keyBindingService, "keyBindingWrapper", mockKeyBindingWrapperService);
	}

	@Test
	public void sendBindingOtp_withValidDetails_thenPass() throws IdPException, SendOtpException {
		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("8267411571");
		otpRequest.setOtpChannels(Arrays.asList("OTP"));

		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(otpRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeTypes(Arrays.asList("OTP"));

		SendOtpResult sendOtpResult = new SendOtpResult(transaction.getAuthTransactionId(), "", "");

		Map<String, String> headers = new HashMap<>();
		when(cacheUtilService.setTransaction(Mockito.anyString(), Mockito.any())).thenReturn(transaction);
		when(mockKeyBindingWrapperService.sendBindingOtp(anyString(), anyString(), any(), any())).thenReturn(sendOtpResult);

		OtpResponse otpResponse = keyBindingService.sendBindingOtp(otpRequest, headers);
		Assert.assertNotNull(otpResponse);
		Assert.assertNotNull(otpResponse.getTransactionId());
	}

	@Test
	public void sendBindingOtp_withNullResponseFromWrapper_thenFail() throws SendOtpException {
		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("123456789");
		otpRequest.setOtpChannels(Arrays.asList("OTP"));

		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(otpRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeTypes(Arrays.asList("OTP"));

		when(cacheUtilService.setTransaction(Mockito.anyString(), Mockito.any())).thenReturn(transaction);
		when(mockKeyBindingWrapperService.sendBindingOtp(anyString(), anyString(), any(), any())).thenReturn(null);

		try {
			keyBindingService.sendBindingOtp(otpRequest, new HashMap<>());
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.SEND_OTP_FAILED));
		}
	}

	@Test
	public void sendBindingOtp_withMismatchedTransactionId_thenFail() throws SendOtpException {
		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("8267411571");
		otpRequest.setOtpChannels(Arrays.asList("OTP"));

		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(otpRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeTypes(Arrays.asList("OTP"));

		SendOtpResult sendOtpResult = new SendOtpResult("13901911", "test@gmail.com", "9090909090");
		when(cacheUtilService.setTransaction(Mockito.anyString(), Mockito.any())).thenReturn(transaction);
		when(mockKeyBindingWrapperService.sendBindingOtp(anyString(), anyString(), any(), any())).thenReturn(sendOtpResult);

		try {
			keyBindingService.sendBindingOtp(otpRequest, new HashMap<>());
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.SEND_OTP_FAILED));
		}
	}

	@Test
	public void bindWallet_withValidDetails_thenPass() throws IdPException, JsonProcessingException, KeyBindingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		KeyBindingAuthChallenge authChallenge = new KeyBindingAuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<KeyBindingAuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest
				.setPublicKey(
						(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(walletBindingRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeTypes(Arrays.asList("OTP"));

		KeyBindingResult keyBindingResult = new KeyBindingResult();
		keyBindingResult.setPartnerSpecificUserToken("psutoken123");
		keyBindingResult.setCertificate("certificate");
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(keyBindingResult);

		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);
		JWTSignatureResponseDto jWTSignatureResponseDto=new JWTSignatureResponseDto();
		jWTSignatureResponseDto.setJwtSignedData("testJwtSignedData");
		PublicKeyRegistry publicKeyRegistry=new PublicKeyRegistry();
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setIdHash("hCv0zz_jnOkGS17BN1dUOU7tqNri-8XpHokWVQQMfiI");
		publicKeyRegistry.setPsuToken("psutoken123");
		publicKeyRegistry.setWalletBindingId("tXOFGPKly4L_9VI8NYvYXJe5ZNrgOIUnfFdLkNKYdTE");
		when(publicKeyRegistryRepository.save(Mockito.any())).thenReturn(publicKeyRegistry);
		Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
	}

	@Test
	public void bindWallet_withInvalidIndividualId_thenFail() throws IdPException, JsonProcessingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		KeyBindingAuthChallenge authChallenge = new KeyBindingAuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<KeyBindingAuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId("5678905215");
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeTypes(Arrays.asList("OTP"));
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_INDIVIDUAL_ID));
		}
	}

	@Test
	public void bindWallet_withInvalidTransaction_thenFail() throws IdPException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setTransactionId("909422113");
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(null);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_TRANSACTION));
		}
	}

	@Test
	public void bindWallet_withInvalidAuthFactorType_thenFail() throws IOException, IdPException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		KeyBindingAuthChallenge authChallenge = new KeyBindingAuthChallenge();
		authChallenge.setAuthFactorType("demo");
		authChallenge.setChallenge("111111");
		List<KeyBindingAuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(walletBindingRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeTypes(Arrays.asList("OTP"));
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.AUTH_FACTOR_MISMATCH));
		}
	}

	@Test
	public void bindWallet_withInvalidKeyBindingResult_thenFail() throws IOException, IdPException, KeyBindingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		KeyBindingAuthChallenge authChallenge = new KeyBindingAuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<KeyBindingAuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(walletBindingRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeTypes(Arrays.asList("OTP"));
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);

		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(null);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_FAILED));
		}

		KeyBindingResult keyBindingResult = new KeyBindingResult();
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(keyBindingResult);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_FAILED));
		}

		keyBindingResult = new KeyBindingResult();
		keyBindingResult.setCertificate("data");
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(keyBindingResult);
		try {
			Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_FAILED));
		}
	}

	@Test
	public void bindWallet_withDuplicatePublicKey_thenFail() throws IOException, IdPException, KeyBindingException {
		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		KeyBindingAuthChallenge authChallenge = new KeyBindingAuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<KeyBindingAuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(walletBindingRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeTypes(Arrays.asList("OTP"));
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);

		KeyBindingResult keyBindingResult = new KeyBindingResult();
		keyBindingResult.setCertificate("data");
		keyBindingResult.setPartnerSpecificUserToken("psut");
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(keyBindingResult);

		when(publicKeyRegistryRepository.findByPublicKeyHashNotEqualToPsuToken(anyString(), any())).thenReturn(Optional.of(new PublicKeyRegistry()));
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
		walletBindingRequest.setTransactionId("909422113");
		KeyBindingAuthChallenge authChallenge = new KeyBindingAuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<KeyBindingAuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMapper.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(walletBindingRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeTypes(Arrays.asList("OTP"));
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);

		KeyBindingResult keyBindingResult = new KeyBindingResult();
		keyBindingResult.setCertificate("data");
		keyBindingResult.setPartnerSpecificUserToken("psut");
		when(mockKeyBindingWrapperService.doKeyBinding(Mockito.anyString(), Mockito.anyString(), Mockito.any(), Mockito.any(), Mockito.any()))
				.thenReturn(keyBindingResult);

		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setIdHash("hCv0zz_jnOkGS17BN1dUOU7tqNri-8XpHokWVQQMfiI");
		publicKeyRegistry.setPsuToken("psutoken123");
		publicKeyRegistry.setWalletBindingId("tXOFGPKly4L_9VI8NYvYXJe5ZNrgOIUnfFdLkNKYdTE");
		Optional<PublicKeyRegistry> optionalPublicKeyRegistry = Optional.of(publicKeyRegistry);
		when(publicKeyRegistryRepository.findLatestByPsuToken(Mockito.any())).thenReturn(optionalPublicKeyRegistry);
		when(publicKeyRegistryRepository.save(Mockito.any())).thenReturn(publicKeyRegistry);
		Assert.assertNotNull(keyBindingService.bindWallet(walletBindingRequest, new HashMap<>()));
	}

	@Test
	public void validateBinding_withValidDetails_thenPass() throws Exception {
		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("JWT");

		X509Certificate certificate = getCertificate();
		String wlaToken = signJwt(validateBindingRequest.getIndividualId(), certificate);
		authChallenge.setChallenge(wlaToken);
		validateBindingRequest.setChallenge(authChallenge);

		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash",
				getPemData(certificate), LocalDateTime.now(), "[\"WLA\"]");
		when(publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan(Mockito.anyString(), any())).thenReturn(Optional.of(publicKeyRegistry));
		when(keymanagerUtil.convertToCertificate(anyString())).thenReturn(certificate);

		ValidateBindingResponse validateBindingResponse = keyBindingService.validateBinding(validateBindingRequest);
		Assert.assertEquals(validateBindingResponse.getTransactionId(), validateBindingRequest.getTransactionId());
	}

	@Test
	public void validateBinding_withUnBoundIndividualId_thenFail() throws IdPException {
		when(publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan(Mockito.anyString(), any())).thenReturn(Optional.empty());

		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("JWT");
		authChallenge.setChallenge("wlaToken");
		validateBindingRequest.setChallenge(authChallenge);

		try {
			keyBindingService.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.KEY_BINDING_NOT_FOUND));
		}
	}

	@Test
	public void validateBinding_withUnBoundAuthFactors_thenFail() throws IdPException {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash",
				"certificate", LocalDateTime.now(), null);
		when(publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan(Mockito.anyString(), any())).thenReturn(Optional.of(publicKeyRegistry));

		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("JWT");
		authChallenge.setChallenge("wlaToken");
		validateBindingRequest.setChallenge(authChallenge);

		try {
			keyBindingService.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.UNBOUND_AUTH_FACTOR));
		}
	}

	@Test
	public void validateBinding_withBoundInvalidCertificate_thenFail() throws IdPException {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash",
				"certificate", LocalDateTime.now(), "[\"WLA\"]");
		when(publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan(Mockito.anyString(), any())).thenReturn(Optional.of(publicKeyRegistry));
		when(keymanagerUtil.convertToCertificate(anyString())).thenThrow(KeymanagerServiceException.class);

		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat("JWT");
		authChallenge.setChallenge("wlaToken");
		validateBindingRequest.setChallenge(authChallenge);

		try {
			keyBindingService.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_CERTIFICATE));
		}
	}

	@Test
	public void validateBinding_withInvalidAuthFactor_thenFail() throws IdPException {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash",
				"certificate", LocalDateTime.now(), "[\"WLA1\"]");
		when(publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan(Mockito.anyString(), any())).thenReturn(Optional.of(publicKeyRegistry));

		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA1");
		authChallenge.setFormat("JWT");
		authChallenge.setChallenge("wlaToken");
		validateBindingRequest.setChallenge(authChallenge);

		try {
			keyBindingService.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.UNKNOWN_BINDING_CHALLENGE));
		}
	}

	@Test
	public void validateBinding_withInvalidChallengeFormat_thenFail() throws Exception {
		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setTransactionId("909422113");
		validateBindingRequest.setIndividualId("8267411571");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("WLA");
		authChallenge.setFormat(null);
		X509Certificate certificate = getCertificate();
		String wlaToken = signJwt(validateBindingRequest.getIndividualId(), certificate);
		authChallenge.setChallenge(wlaToken);
		validateBindingRequest.setChallenge(authChallenge);

		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("id-hash", "test-psu-token", clientJWK.toJSONString(),
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash",
				getPemData(certificate), LocalDateTime.now(), "[\"WLA\"]");
		when(publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan(Mockito.anyString(), any())).thenReturn(Optional.of(publicKeyRegistry));
		when(keymanagerUtil.convertToCertificate(anyString())).thenReturn(certificate);

		ValidateBindingResponse validateBindingResponse = keyBindingService.validateBinding(validateBindingRequest);
		Assert.assertEquals(validateBindingResponse.getTransactionId(), validateBindingRequest.getTransactionId());

		authChallenge.setFormat("null");
		try {
			keyBindingService.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.UNKNOWN_WLA_FORMAT));
		}
	}

	private X509Certificate getCertificate() throws Exception {
		X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
		X500Principal dnName = new X500Principal("CN=Test");
		generator.setSubjectDN(dnName);
		generator.setIssuerDN(dnName); // use the same
		generator.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
		generator.setNotAfter(new Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000));
		generator.setPublicKey(clientJWK.toRSAKey().toPublicKey());
		generator.setSignatureAlgorithm("SHA256WITHRSA");
		generator.setSerialNumber(new BigInteger(String.valueOf(System.currentTimeMillis())));
		return generator.generate(clientJWK.toRSAKey().toPrivateKey());
	}

	private String signJwt(String individualId, X509Certificate certificate) throws Exception {
		JSONObject payload = new JSONObject();
		payload.put("iss", "test-app");
		payload.put("aud", validateBindingUrl);
		payload.put("sub", individualId);
		payload.put("iat", IdentityProviderUtil.getEpochSeconds());
		payload.put("exp", IdentityProviderUtil.getEpochSeconds()+3600);

		JsonWebSignature jwSign = new JsonWebSignature();
		jwSign.setCertificateChainHeaderValue(certificate);
		jwSign.setX509CertSha1ThumbprintHeaderValue(certificate);
		jwSign.setKeyIdHeaderValue(clientJWK.getKeyID());
		jwSign.setPayload(payload.toJSONString());
		jwSign.setAlgorithmHeaderValue("RS256");
		jwSign.setKey(clientJWK.toRSAKey().toPrivateKey());
		jwSign.setDoKeyValidation(false);
		return jwSign.getCompactSerialization();
	}

	private String getPemData(Object anyObject) throws IOException {
		StringWriter stringWriter = new StringWriter();
		try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
			pemWriter.writeObject(anyObject);
			pemWriter.flush();
			return stringWriter.toString();
		}
	}

}