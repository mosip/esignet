/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.services;

import static org.mockito.Mockito.when;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
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
import com.nimbusds.jose.jwk.JWK;

import io.mosip.idp.authwrapper.service.MockAuthenticationService;
import io.mosip.idp.binding.TestUtil;
import io.mosip.idp.binding.dto.BindingTransaction;
import io.mosip.idp.binding.entity.PublicKeyRegistry;
import io.mosip.idp.binding.repository.PublicKeyRegistryRepository;
import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.KycAuthResult;
import io.mosip.idp.core.dto.WalletBindingRequest;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.spi.TokenService;
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
	private TokenService tokenService;

	@Mock
	private ClientManagementService clientManagementService;

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
		ReflectionTestUtils.setField(walletBindingServiceImpl, "issuerId", "http://localhost:8087/v1/idpbinding");
		ReflectionTestUtils.setField(walletBindingServiceImpl, "saltLength", 16);
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
	public void bindWallet_withAuthFail() throws IOException, KycAuthException, IdPException {
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

}
