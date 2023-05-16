/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.authentication.esignet.integration.service;

import static org.mockito.ArgumentMatchers.any;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.authentication.esignet.integration.dto.GetAllCertificatesResponse;
import io.mosip.authentication.esignet.integration.dto.IdaKycExchangeResponse;
import io.mosip.authentication.esignet.integration.dto.IdaResponseWrapper;
import io.mosip.authentication.esignet.integration.helper.AuthTransactionHelper;
import io.mosip.esignet.api.dto.KycExchangeDto;
import io.mosip.esignet.api.dto.KycExchangeResult;
import io.mosip.esignet.api.dto.KycSigningCertificateData;
import io.mosip.esignet.api.dto.SendOtpDto;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.exception.KycSigningCertificateException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.kernel.core.exception.ServiceError;
import io.mosip.kernel.core.http.ResponseWrapper;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)
public class IdaAuthenticatorImplTest {

	@InjectMocks
	IdaAuthenticatorImpl idaAuthenticatorImpl;

	@Mock
	ObjectMapper mapper;

	@Mock
	RestTemplate restTemplate;

	@Mock
	HelperService helperService;

	@Mock
	AuthTransactionHelper authTransactionHelper;

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);

		ReflectionTestUtils.setField(helperService, "sendOtpUrl", "https:/");
		ReflectionTestUtils.setField(helperService, "idaPartnerCertificateUrl", "https://test");
		ReflectionTestUtils.setField(helperService, "symmetricAlgorithm", "AES");
		ReflectionTestUtils.setField(helperService, "symmetricKeyLength", 256);

		ReflectionTestUtils.setField(idaAuthenticatorImpl, "kycExchangeUrl", "https://dev.mosip.net");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "idaVersion", "VersionIDA");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "kycAuthUrl", "https://testkycAuthUrl");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "getCertsUrl", "https://testGetCertsUrl");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "otpChannels", Arrays.asList("otp", "pin", "bio"));
	}
//TODO fix the test cases
//	@Test
//	public void doKycAuth_withInvalidDetails_throwsException() throws Exception {
//		KycAuthDto kycAuthDto = new KycAuthDto();
//		kycAuthDto.setIndividualId("IND1234");
//		kycAuthDto.setTransactionId("TRAN1234");
//		AuthChallenge authChallenge = new AuthChallenge();
//		authChallenge.setAuthFactorType("PIN");
//		authChallenge.setChallenge("111111");
//		List<AuthChallenge> authChallengeList = new ArrayList<>();
//		authChallengeList.add(authChallenge);
//		kycAuthDto.setChallengeList(authChallengeList);
//
//		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");
//		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
//				Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycAuthResponse>>>any())).thenReturn(null);
//
//		Assert.assertThrows(KycAuthException.class,
//				() -> idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto));
//	}
//
//	@Test
//	public void doKycAuth_withValidDetails_thenPass() throws Exception {
//		KycAuthDto kycAuthDto = new KycAuthDto();
//		kycAuthDto.setIndividualId("IND1234");
//		kycAuthDto.setTransactionId("TRAN1234");
//		AuthChallenge authChallenge = new AuthChallenge();
//		authChallenge.setAuthFactorType("OTP");
//		authChallenge.setChallenge("111111");
//		List<AuthChallenge> authChallengeList = new ArrayList<>();
//		authChallengeList.add(authChallenge);
//		kycAuthDto.setChallengeList(authChallengeList);
//
//		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");
//
//		IdaKycAuthResponse idaKycAuthResponse = new IdaKycAuthResponse();
//		idaKycAuthResponse.setAuthToken("authToken1234");
//		idaKycAuthResponse.setKycToken("kycToken1234");
//		idaKycAuthResponse.setKycStatus(true);
//
//		IdaResponseWrapper<IdaKycAuthResponse> idaResponseWrapper = new IdaResponseWrapper<>();
//		idaResponseWrapper.setResponse(idaKycAuthResponse);
//		idaResponseWrapper.setTransactionID("TRAN123");
//		idaResponseWrapper.setVersion("VER1");
//
//		ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>>(
//				idaResponseWrapper, HttpStatus.OK);
//
//		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
//				Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycAuthResponse>>>any()))
//				.thenReturn(responseEntity);
//
//		KycAuthResult kycAuthResult = idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto);
//
//		Assert.assertEquals(kycAuthResult.getKycToken(), kycAuthResult.getKycToken());
//	}
//
//	@Test
//	public void doKycAuth_withAuthChallengeNull_thenFail() throws Exception {
//		KycAuthDto kycAuthDto = new KycAuthDto();
//		kycAuthDto.setIndividualId("IND1234");
//		kycAuthDto.setTransactionId("TRAN1234");
//		kycAuthDto.setChallengeList(null);
//
//		Assert.assertThrows(KycAuthException.class,
//				() -> idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto));
//	}
//
//	@Test
//	public void doKycAuth_withInvalidAuthChallenge_thenFail() throws Exception {
//		KycAuthDto kycAuthDto = new KycAuthDto();
//		kycAuthDto.setIndividualId("IND1234");
//		kycAuthDto.setTransactionId("TRAN1234");
//		AuthChallenge authChallenge = new AuthChallenge();
//		authChallenge.setAuthFactorType("Test");
//		authChallenge.setChallenge("111111");
//		List<AuthChallenge> authChallengeList = new ArrayList<>();
//		authChallengeList.add(authChallenge);
//		kycAuthDto.setChallengeList(authChallengeList);
//
//		Assert.assertThrows(KycAuthException.class,
//				() -> idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto));
//	}
//
//	@Test
//	public void doKycAuth_withBIOAuthChallenge_thenPass() throws Exception {
//		KycAuthDto kycAuthDto = new KycAuthDto();
//		kycAuthDto.setIndividualId("IND1234");
//		kycAuthDto.setTransactionId("TRAN1234");
//		AuthChallenge authChallenge = new AuthChallenge();
//		authChallenge.setAuthFactorType("BIO");
//		authChallenge.setChallenge("111111");
//		List<AuthChallenge> authChallengeList = new ArrayList<>();
//		authChallengeList.add(authChallenge);
//		kycAuthDto.setChallengeList(authChallengeList);
//
//		Biometric b = new Biometric();
//		b.setData(
//				"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
//		b.setHash("Hash");
//		b.setSessionKey("SessionKey");
//		b.setSpecVersion("SepecV");
//		b.setThumbprint("Thumbprint");
//		List<Biometric> bioList = new ArrayList<>();
//		bioList.add(b);
//		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");
//		IdaKycAuthResponse idaKycAuthResponse = new IdaKycAuthResponse();
//		idaKycAuthResponse.setAuthToken("authToken1234");
//		idaKycAuthResponse.setKycToken("kycToken1234");
//		idaKycAuthResponse.setKycStatus(true);
//
//		IdaResponseWrapper<IdaKycAuthResponse> idaResponseWrapper = new IdaResponseWrapper<>();
//		idaResponseWrapper.setResponse(idaKycAuthResponse);
//		idaResponseWrapper.setTransactionID("TRAN123");
//		idaResponseWrapper.setVersion("VER1");
//
//		ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>>(
//				idaResponseWrapper, HttpStatus.OK);
//
//		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
//				Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycAuthResponse>>>any()))
//				.thenReturn(responseEntity);
//
//		KycAuthResult kycAuthResult = idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto);
//
//		Assert.assertEquals(kycAuthResult.getKycToken(), kycAuthResult.getKycToken());
//	}

	@Test
	public void doKycExchange_withValidDetails_thenPass() throws Exception {
		KycExchangeDto kycExchangeDto = new KycExchangeDto();
		kycExchangeDto.setIndividualId("IND1234");
		kycExchangeDto.setKycToken("KYCT123");
		kycExchangeDto.setTransactionId("TRAN123");
		List<String> acceptedClaims = new ArrayList<>();
		acceptedClaims.add("claims");
		kycExchangeDto.setAcceptedClaims(acceptedClaims);
		String[] claimsLacales = new String[] { "claims", "locales" };
		kycExchangeDto.setClaimsLocales(claimsLacales);

		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");

		IdaKycExchangeResponse idaKycExchangeResponse = new IdaKycExchangeResponse();
		idaKycExchangeResponse.setEncryptedKyc("ENCRKYC123");

		IdaResponseWrapper<IdaKycExchangeResponse> idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycExchangeResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>>(
				idaResponseWrapper, HttpStatus.OK);

		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycExchangeResponse>>>any()))
				.thenReturn(responseEntity);

		KycExchangeResult kycExchangeResult = idaAuthenticatorImpl.doKycExchange("relyingPartyId", "clientId",
				kycExchangeDto);

		Assert.assertEquals(idaKycExchangeResponse.getEncryptedKyc(), kycExchangeResult.getEncryptedKyc());
	}

	@Test
	public void doKycExchange_withInvalidDetails_thenFail() throws Exception {
		KycExchangeDto kycExchangeDto = new KycExchangeDto();
		kycExchangeDto.setIndividualId(null);
		kycExchangeDto.setKycToken("KYCT123");
		kycExchangeDto.setTransactionId("TRAN123");
		List<String> acceptedClaims = new ArrayList<>();
		acceptedClaims.add("claims");
		kycExchangeDto.setAcceptedClaims(acceptedClaims);
		String[] claimsLacales = new String[] { "claims", "locales" };
		kycExchangeDto.setClaimsLocales(claimsLacales);

		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");

		IdaKycExchangeResponse idaKycExchangeResponse = new IdaKycExchangeResponse();
		idaKycExchangeResponse.setEncryptedKyc("ENCRKYC123");

		IdaResponseWrapper<IdaKycExchangeResponse> idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(null);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>>(
				idaResponseWrapper, HttpStatus.OK);

		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycExchangeResponse>>>any()))
				.thenReturn(responseEntity);

		Assert.assertThrows(KycExchangeException.class,
				() -> idaAuthenticatorImpl.doKycExchange("test-relyingPartyId", "test-clientId", kycExchangeDto));
	}

	@Test
	public void doKycExchange_withInvalidIndividualId_throwsException() throws KycExchangeException, Exception {
		KycExchangeDto kycExchangeDto = new KycExchangeDto();
		kycExchangeDto.setIndividualId("IND1234");
		kycExchangeDto.setKycToken("KYCT123");
		kycExchangeDto.setTransactionId("TRAN123");
		List<String> acceptedClaims = new ArrayList<>();
		acceptedClaims.add("claims");
		kycExchangeDto.setAcceptedClaims(acceptedClaims);
		String[] claimsLacales = new String[] { "claims", "locales" };
		kycExchangeDto.setClaimsLocales(claimsLacales);

		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");
		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycExchangeResponse>>>any()))
				.thenReturn(null);

		Assert.assertThrows(KycExchangeException.class,
				() -> idaAuthenticatorImpl.doKycExchange("relyingId", "clientId", kycExchangeDto));
	}

	@Test
	public void sendOtp_withValidDetails_thenPass() throws Exception {
		SendOtpDto sendOtpDto = new SendOtpDto();
		sendOtpDto.setIndividualId("1234");
		sendOtpDto.setTransactionId("4567");
		List<String> otpChannelsList = new ArrayList<>();
		otpChannelsList.add("channel");
		sendOtpDto.setOtpChannels(otpChannelsList);

		Mockito.when(helperService.sendOTP(any(),any(),any())).thenReturn(new SendOtpResult(sendOtpDto.getTransactionId(), "", ""));

		SendOtpResult sendOtpResult = idaAuthenticatorImpl.sendOtp("rly123", "cli123", sendOtpDto);

		Assert.assertEquals(sendOtpDto.getTransactionId(), sendOtpResult.getTransactionId());
	}

	@Test
	public void sendOtp_withErrorResponse_throwsException() throws Exception {
		SendOtpDto sendOtpDto = new SendOtpDto();
		sendOtpDto.setIndividualId(null);
		sendOtpDto.setTransactionId("4567");
		List<String> otpChannelsList = new ArrayList<>();
		otpChannelsList.add("channel");
		sendOtpDto.setOtpChannels(otpChannelsList);

		Mockito.when(helperService.sendOTP(any(),any(),any())).thenThrow(new SendOtpException("error-100"));

		try {
			idaAuthenticatorImpl.sendOtp("rly123", "cli123", sendOtpDto);
			Assert.fail();
		} catch (SendOtpException e) {
			Assert.assertEquals("error-100", e.getErrorCode());
		}
	}

	@Test
	public void isSupportedOtpChannel_withInvalidChannel_thenFail() {
		Assert.assertFalse(idaAuthenticatorImpl.isSupportedOtpChannel("test"));
	}
	
	@Test
	public void isSupportedOtpChannel_withValidChannel_thenPass() {
		Assert.assertTrue(idaAuthenticatorImpl.isSupportedOtpChannel("OTP"));
	}
	
	@Test
	public void getAllKycSigningCertificates_withValidDetails_thenPass() throws Exception {
		Mockito.when(authTransactionHelper.getAuthToken()).thenReturn("test-token");

		GetAllCertificatesResponse getAllCertificatesResponse = new GetAllCertificatesResponse();
		getAllCertificatesResponse.setAllCertificates(new ArrayList<KycSigningCertificateData>());

		ResponseWrapper<GetAllCertificatesResponse> certsResponseWrapper = new ResponseWrapper<GetAllCertificatesResponse>();
		certsResponseWrapper.setId("test-id");
		certsResponseWrapper.setResponse(getAllCertificatesResponse);

		ResponseEntity<ResponseWrapper<GetAllCertificatesResponse>> certsResponseEntity = new ResponseEntity<ResponseWrapper<GetAllCertificatesResponse>>(
				certsResponseWrapper, HttpStatus.OK);

		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<ResponseWrapper<GetAllCertificatesResponse>>>any()))
				.thenReturn(certsResponseEntity);

		List<KycSigningCertificateData> signingCertificates = new ArrayList<>();

		signingCertificates = idaAuthenticatorImpl.getAllKycSigningCertificates();

		Assert.assertSame(signingCertificates, getAllCertificatesResponse.getAllCertificates());
	}

	@Test
	public void getAllKycSigningCertificates_withInvalidResponse_throwsException() throws Exception {
		Mockito.when(authTransactionHelper.getAuthToken()).thenReturn("test-token");

		ResponseWrapper<GetAllCertificatesResponse> certsResponseWrapper = new ResponseWrapper<GetAllCertificatesResponse>();
		certsResponseWrapper.setId("test-id");
		List<ServiceError> errors = new ArrayList<>();
		ServiceError error = new ServiceError("ERR-001", "Certificates not found");
		errors.add(error);
		certsResponseWrapper.setErrors(errors);

		ResponseEntity<ResponseWrapper<GetAllCertificatesResponse>> certsResponseEntity = new ResponseEntity<ResponseWrapper<GetAllCertificatesResponse>>(
				certsResponseWrapper, HttpStatus.OK);

		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<ResponseWrapper<GetAllCertificatesResponse>>>any()))
				.thenReturn(certsResponseEntity);

		Assert.assertThrows(KycSigningCertificateException.class,
				() -> idaAuthenticatorImpl.getAllKycSigningCertificates());
	}
	
	@Test
	public void getAllKycSigningCertificates_withErrorResponse_throwsException() throws Exception {
		Mockito.when(authTransactionHelper.getAuthToken()).thenReturn("test-token");

		ResponseWrapper<GetAllCertificatesResponse> certsResponseWrapper = new ResponseWrapper<GetAllCertificatesResponse>();
		certsResponseWrapper.setId("test-id");
		List<ServiceError> errors = new ArrayList<>();
		ServiceError error = new ServiceError("ERR-001", "Certificates not found");
		errors.add(error);
		certsResponseWrapper.setErrors(errors);

		ResponseEntity<ResponseWrapper<GetAllCertificatesResponse>> certsResponseEntity = new ResponseEntity<ResponseWrapper<GetAllCertificatesResponse>>(
				certsResponseWrapper, HttpStatus.FORBIDDEN);

		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<ResponseWrapper<GetAllCertificatesResponse>>>any()))
				.thenReturn(certsResponseEntity);

		Assert.assertThrows(KycSigningCertificateException.class,
				() -> idaAuthenticatorImpl.getAllKycSigningCertificates());
	}

	@SuppressWarnings("rawtypes")
	@Test
	public void getAllKycSigningCertificates_withInvalidToken_thenFail() throws Exception {
		Mockito.when(authTransactionHelper.getAuthToken()).thenReturn("test-token");

		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<ResponseWrapper>>any())).thenThrow(RuntimeException.class);

		Assert.assertThrows(KycSigningCertificateException.class,
				() -> idaAuthenticatorImpl.getAllKycSigningCertificates());
	}

}
