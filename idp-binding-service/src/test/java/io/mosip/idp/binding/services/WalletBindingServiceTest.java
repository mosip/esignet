/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.services;

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.text.ParseException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.proc.BadJOSEException;
import com.nimbusds.jwt.proc.BadJWTException;

import io.mosip.idp.authwrapper.service.MockAuthenticationService;
import io.mosip.idp.binding.TestUtil;
import io.mosip.idp.binding.dto.BindingTransaction;
import io.mosip.idp.binding.entity.PublicKeyRegistry;
import io.mosip.idp.binding.repository.PublicKeyRegistryRepository;
import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.BindingOtpRequest;
import io.mosip.idp.core.dto.KycAuthResult;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.dto.ValidateBindingRequest;
import io.mosip.idp.core.dto.WalletBindingRequest;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;

@RunWith(MockitoJUnitRunner.class)
public class WalletBindingServiceTest {
	
	@InjectMocks
	WalletBindingServiceImpl walletBindingServiceImpl;

	@Mock
	CacheUtilService cacheUtilService;

	@Mock
	private SignatureService signatureService;

	@Mock
	private KeymanagerService keymanagerService;

	@Mock
	ObjectMapper objectMapper;

	@Mock
	private PublicKeyRegistryRepository publicKeyRegistryRepository;

	@Mock
	MockAuthenticationService authenticationWrapper;
	
	private JWK clientJWK = TestUtil.generateJWK_RSA();

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		ReflectionTestUtils.setField(walletBindingServiceImpl, "authPartnerId", "idp-dev-auth-partner-id");
		ReflectionTestUtils.setField(walletBindingServiceImpl, "apiKey", "idp-dev-api-key");
		ReflectionTestUtils.setField(walletBindingServiceImpl, "expireDays", 2);
		ReflectionTestUtils.setField(walletBindingServiceImpl, "validateBindingIssuerId", "http://localhost:8087/v1/idpbinding/validate-binding");
		ReflectionTestUtils.setField(walletBindingServiceImpl, "saltLength", 16);
	}

	private void initiateMockAuthenticationService() throws IOException {
		authenticationWrapper = new MockAuthenticationService("src/test/resources/mockida/",
				"src/test/resources/mockida/", "src/test/resources/mockida/claims_attributes_mapping.json", 0, false,
				signatureService, objectMapper, keymanagerService);
		ReflectionTestUtils.setField(walletBindingServiceImpl, "authenticationWrapper", authenticationWrapper);
	}
	
	@Test
	public void sendBindingOtp_withValidDetails_thenPass() throws IdPException, IOException {
		initiateMockAuthenticationService();

		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("8267411571");
		otpRequest.setOtpChannels(Arrays.asList("OTP"));
		otpRequest.setCaptchaToken("7893J");

		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(otpRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeType("OTP");

		when(cacheUtilService.setTransaction(Mockito.anyString(), Mockito.any())).thenReturn(transaction);

		Assert.assertNotNull(walletBindingServiceImpl.sendBindingOtp(otpRequest));
	}

	@Test
	public void sendBindingOtp_withInvalidIndividualId_thenFail() throws IOException {
		initiateMockAuthenticationService();

		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("123456789");
		otpRequest.setOtpChannels(Arrays.asList("OTP"));
		otpRequest.setCaptchaToken("7893J");

		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(otpRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeType("OTP");

		when(cacheUtilService.setTransaction(Mockito.anyString(), Mockito.any())).thenReturn(transaction);

		try {
			walletBindingServiceImpl.sendBindingOtp(otpRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals("mock-ida-001"));
		}
	}

	@Test
	public void sendBindingOtp_withMismatchedTransactionId_thenFail() throws SendOtpException {
		BindingOtpRequest otpRequest = new BindingOtpRequest();
		otpRequest.setIndividualId("8267411571");
		otpRequest.setOtpChannels(Arrays.asList("OTP"));
		otpRequest.setCaptchaToken("7893J");

		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(otpRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeType("OTP");

		SendOtpResult sendOtpResult = new SendOtpResult("13901911", "test@gmail.com", "9090909090");

		when(cacheUtilService.setTransaction(Mockito.anyString(), Mockito.any())).thenReturn(transaction);
		when(authenticationWrapper.sendOtp(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
				.thenReturn(sendOtpResult);

		try {
			walletBindingServiceImpl.sendBindingOtp(otpRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.SEND_OTP_FAILED));
		}
	}
	
	@Test
	public void bindWallet_withValidDetails_thenPass() throws IOException, KycAuthException, IdPException {
		ReflectionTestUtils.setField(walletBindingServiceImpl, "authenticationWrapper", authenticationWrapper);
		ObjectMapper objectMappertest = new ObjectMapper();

		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest
				.setPublicKey(
						(Map<String, Object>) objectMappertest.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(walletBindingRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeType("OTP");
		KycAuthResult kycAuthResult = new KycAuthResult();
		kycAuthResult.setKycToken("kyctoken123");
		kycAuthResult.setPartnerSpecificUserToken("psutoken123");
		when(authenticationWrapper.doKycAuth(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
				.thenReturn(kycAuthResult);
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);
		JWTSignatureResponseDto jWTSignatureResponseDto=new JWTSignatureResponseDto();
		jWTSignatureResponseDto.setJwtSignedData("testJwtSignedData");
		PublicKeyRegistry publicKeyRegistry=new PublicKeyRegistry();
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setIdHash("hCv0zz_jnOkGS17BN1dUOU7tqNri-8XpHokWVQQMfiI");
		publicKeyRegistry.setPsuToken("psutoken123");
		publicKeyRegistry.setWalletBindingId("tXOFGPKly4L_9VI8NYvYXJe5ZNrgOIUnfFdLkNKYdTE");
		when(publicKeyRegistryRepository.save(Mockito.any())).thenReturn(publicKeyRegistry);
		Assert.assertNotNull(walletBindingServiceImpl.bindWallet(walletBindingRequest));

	}

	@Test
	public void bindWallet_withInvalidIndividualId_thenFail() throws IOException, KycAuthException, IdPException {
		ReflectionTestUtils.setField(walletBindingServiceImpl, "authenticationWrapper", authenticationWrapper);
		ObjectMapper objectMappertest = new ObjectMapper();

		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMappertest.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId("5678905215");
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeType("OTP");
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);
		try {
			Assert.assertNotNull(walletBindingServiceImpl.bindWallet(walletBindingRequest));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_INDIVIDUAL_ID));
		}

	}

	@Test
	public void bindWallet_withInvalidTransaction_thenFail() throws IOException, KycAuthException, IdPException {
		ReflectionTestUtils.setField(walletBindingServiceImpl, "authenticationWrapper", authenticationWrapper);
		ObjectMapper objectMappertest = new ObjectMapper();

		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMappertest.readValue(clientJWK.toJSONString(), HashMap.class));
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(null);
		try {
			Assert.assertNotNull(walletBindingServiceImpl.bindWallet(walletBindingRequest));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_TRANSACTION));
		}

	}

	@Test
	public void bindWallet_withInvalidAuthChallengeType_thenFail() throws IOException, KycAuthException, IdPException {
		ReflectionTestUtils.setField(walletBindingServiceImpl, "authenticationWrapper", authenticationWrapper);
		ObjectMapper objectMappertest = new ObjectMapper();

		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("demo");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMappertest.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(walletBindingRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeType("OTP");
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);
		try {
			Assert.assertNotNull(walletBindingServiceImpl.bindWallet(walletBindingRequest));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_AUTH_CHALLENGE));
		}

	}

	@Test
	public void bindWallet_withAuthFail_thenFail() throws IOException, KycAuthException, IdPException {
		ReflectionTestUtils.setField(walletBindingServiceImpl, "authenticationWrapper", authenticationWrapper);
		ObjectMapper objectMappertest = new ObjectMapper();

		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMappertest.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(walletBindingRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeType("OTP");
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);
		when(authenticationWrapper.doKycAuth(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
				.thenReturn(null);
		try {
			Assert.assertNotNull(walletBindingServiceImpl.bindWallet(walletBindingRequest));
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.AUTH_FAILED));
		}

	}

	@Test
	public void bindWallet_withAlreadyExistingWalletBindingId_thenPass()
			throws IOException, KycAuthException, IdPException {
		ReflectionTestUtils.setField(walletBindingServiceImpl, "authenticationWrapper", authenticationWrapper);
		ObjectMapper objectMappertest = new ObjectMapper();

		WalletBindingRequest walletBindingRequest = new WalletBindingRequest();
		walletBindingRequest.setIndividualId("8267411571");
		walletBindingRequest.setTransactionId("909422113");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		walletBindingRequest.setChallengeList(authChallengeList);
		walletBindingRequest.setPublicKey(
				(Map<String, Object>) objectMappertest.readValue(clientJWK.toJSONString(), HashMap.class));
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(walletBindingRequest.getIndividualId());
		transaction.setAuthTransactionId("909422113");
		transaction.setAuthChallengeType("OTP");
		KycAuthResult kycAuthResult = new KycAuthResult();
		kycAuthResult.setKycToken("kyctoken123");
		kycAuthResult.setPartnerSpecificUserToken("psutoken123");
		when(authenticationWrapper.doKycAuth(Mockito.anyString(), Mockito.anyString(), Mockito.any()))
				.thenReturn(kycAuthResult);
		when(cacheUtilService.getTransaction(Mockito.anyString())).thenReturn(transaction);
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setExpiredtimes(LocalDateTime.now());
		publicKeyRegistry.setIdHash("hCv0zz_jnOkGS17BN1dUOU7tqNri-8XpHokWVQQMfiI");
		publicKeyRegistry.setPsuToken("psutoken123");
		publicKeyRegistry.setWalletBindingId("tXOFGPKly4L_9VI8NYvYXJe5ZNrgOIUnfFdLkNKYdTE");
		Optional<PublicKeyRegistry> optionalPublicKeyRegistry = Optional.of(publicKeyRegistry);
		when(publicKeyRegistryRepository.findOneByPsuToken(Mockito.any())).thenReturn(optionalPublicKeyRegistry);
		when(publicKeyRegistryRepository.save(Mockito.any())).thenReturn(publicKeyRegistry);
		Assert.assertNotNull(walletBindingServiceImpl.bindWallet(walletBindingRequest));
	}
	
//	@Test
//	public void validateBinding_withValidDetails_thenPass() throws IdPException, IOException, ParseException, BadJOSEException, JOSEException {
//		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("8267411571", "test-psu-token", clientJWK.toJSONString(),
//				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash", LocalDateTime.now());
//		Optional<PublicKeyRegistry> optionalRegistry = Optional.of(publicKeyRegistry);
//		when(publicKeyRegistryRepository.findByIdHash(Mockito.anyString())).thenReturn(optionalRegistry);
//		
//		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
//		validateBindingRequest.setTransactionId("909422113");
//		validateBindingRequest.setIndividualId("8267411571");
//		validateBindingRequest.setWlaToken("eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzI1NiJ9.eyJpc3MiOiJJc3N1ZXIiLCJzdWIiOiI4MjY3NDExNTcxIiwiYXVkIjoiaHR0cDovL2xvY2FsaG9zdDo4MDg3L3YxL2lkcGJpbmRpbmcvdmFsaWRhdGUtYmluZGluZyIsImlhdCI6MTY3MDMzNTA4NywiZXhwIjoxNjcwMzM1Njg3fQ.pUiGaWKqdBzj7AK-dcnp07ghgXIf_gZk7oyVnnOHNB0fmjtXVAZz8lh_8-SRbknitm7KhjE9RaMh9o1o1jwbZkajd_iDwM8aip4QDihTLefQddirXVhZn-mCSXwMYLWnfwqGmXMadT6Kx5EHlRUIQt55XsNP3RyIhaCowp2wNbNKGFfJizRJvdLIwTrxBskl5rBu2ZspkLGpD33HSsA48qVatsQRvNKLifcJ4ZeQ4hHHNNZK1SAPm3Mq2oQG04n1cvMjPM2D9bnISedwPeykWuXfnEmbjnkyRLsZOjIFvui2K5ISHWj_hmsB0GhYPN9Gl_cFhwH1zkHdSnVN4dwpfg");
//		Assert.assertEquals(walletBindingServiceImpl.validateBinding(validateBindingRequest).getTransactionId(), 909422113);
//	}
	
	@Test
	public void validateBinding_withInvalidIndividualId_thenFail() throws IdPException, IOException, BadJWTException {
		when(publicKeyRegistryRepository.findByIdHash(Mockito.anyString())).thenReturn(Optional.empty());
		
		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setIndividualId("8267411571");
		validateBindingRequest.setWlaToken("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJpZHAtZGV2LWlzc3Vlci1pZCIsInN1YiI6IjgyNjc0MTE1NzEiLCJpc3MiOiJJc3N1ZXIiLCJleHAiOjE3MzMzODQ2MjUsImlhdCI6MTY3MDIyNjIyNX0.9qHJg2jT27ju4OWXUxgzaAfTO9Cs4JrEEJHehY4x9Qs");
		
		try {
			walletBindingServiceImpl.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_INDIVIDUAL_ID));
		}
	}
	
	@Test
	public void validateBinding_withInvalidSignature_thenFail() throws IdPException, IOException, BadJWTException {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("8267411571", "test-psu-token", "test-public-key",
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash", LocalDateTime.now());
		Optional<PublicKeyRegistry> optionalRegistry = Optional.of(publicKeyRegistry);
		when(publicKeyRegistryRepository.findByIdHash(Mockito.anyString())).thenReturn(optionalRegistry);
		
		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setIndividualId("8267411571");
		validateBindingRequest.setWlaToken("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJpZHAtZGV2LWlzc3Vlci1pZCIsInN1YiI6IjgyNjc0MTE1NzEiLCJpc3MiOiJJc3N1ZXIiLCJleHAiOjE3MzMzODQ2MjUsImlhdCI6MTY3MDIyNjIyNX0.9qHJg2jT27ju4OWXUxgzaAfTO9Cs4JrEEJHehY4x9Qs");

		try {
			walletBindingServiceImpl.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_AUTH_TOKEN));
		}
	}
	
	@Test
	public void validateBinding_withInvalidClaims_thenFail() throws IdPException, IOException, BadJWTException {
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry("8267411571", "test-psu-token", "test-public-key",
				LocalDateTime.now().plusDays(4), "test-binding-id", "test-public-key-hash", LocalDateTime.now());
		Optional<PublicKeyRegistry> optionalRegistry = Optional.of(publicKeyRegistry);
		when(publicKeyRegistryRepository.findByIdHash(Mockito.anyString())).thenReturn(optionalRegistry);
		
		ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
		validateBindingRequest.setIndividualId("8267411571");
		validateBindingRequest.setWlaToken("eyJhbGciOiJIUzI1NiJ9.eyJhdWQiOiJpZHAtZGV2LWlzc3Vlci1pZCIsInN.9qHJg2jT27ju4OWXUxgzaAfTO9Cs4JrEEJHehY4x9Qs");

		try {
			walletBindingServiceImpl.validateBinding(validateBindingRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_AUTH_TOKEN));
		}
	}

}
