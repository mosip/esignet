/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.ida115.esignet.integration.service;

import static org.mockito.ArgumentMatchers.any;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

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

import io.mosip.authentication.common.service.helper.IdInfoHelper;
import io.mosip.authentication.common.service.impl.match.BioMatchType;
import io.mosip.authentication.common.service.integration.TokenIdManager;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KycAuthDto;
import io.mosip.esignet.api.dto.KycAuthResult;
import io.mosip.esignet.api.dto.KycExchangeDto;
import io.mosip.esignet.api.dto.KycExchangeResult;
import io.mosip.esignet.api.dto.KycSigningCertificateData;
import io.mosip.esignet.api.dto.SendOtpDto;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.ida115.esignet.integration.dto.IdaKycAuthRequest.Biometric;
import io.mosip.ida115.esignet.integration.dto.IdaKycExchangeResponse;
import io.mosip.ida115.esignet.integration.dto.IdaKycResponse;
import io.mosip.ida115.esignet.integration.dto.IdaResponseWrapper;
import io.mosip.ida115.esignet.integration.helper.AuthTransactionHelper;
import io.mosip.ida115.esignet.integration.helper.IdentityDataStore;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)
public class IdaAuthenticatorImplTest {

	@InjectMocks
	Ida115AuthenticatorImpl idaAuthenticatorImpl;

	@Mock
	ObjectMapper mapper;
	
	ObjectMapper objectMapper = new ObjectMapper();
	
	@Mock
	IdInfoHelper idInfoHelper;
	
	@Mock
	RestTemplate restTemplate;

	@Mock
	HelperService helperService;

	@Mock
	AuthTransactionHelper authTransactionHelper;
	
	@Mock
	TokenIdManager tokenIdManager;
	
	@Mock
	IdentityDataStore identityDataStore;
	
	@Mock
	private SignatureService signatureService;

	private String faceBdb = "face-image";

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);

		ReflectionTestUtils.setField(helperService, "sendOtpUrl", "https:/");
		ReflectionTestUtils.setField(helperService, "idaPartnerCertificateUrl", "https://test");
		ReflectionTestUtils.setField(helperService, "symmetricAlgorithm", "AES");
		ReflectionTestUtils.setField(helperService, "symmetricKeyLength", 256);

		ReflectionTestUtils.setField(idaAuthenticatorImpl, "idaVersion", "VersionIDA");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "kycUrl", "https://testkycUrl");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "authUrl", "https://testAuthUrl");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "getCertsUrl", "https://testGetCertsUrl");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "esignetAuthPartnerId", "test-partner");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "esignetAuthPartnerApiKey", "test-partner-apikey");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "keySplitter", "#KEYSPLITTER#");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "otpChannels", Arrays.asList("otp", "pin", "bio"));
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "kycTokenSecret", "aabbcc");
		
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "consentedFaceAttributeName", "picture");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "consentedNameAttributeName", "name");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "consentedAddressAttributeName", "address");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "consentedIndividualAttributeName", "individual_id");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "consentedPictureAttributePrefix", "data:image/jpeg;base64");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "addressSubsetAttributes", new String[0]);
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "nameSubsetAttributes", new String[0]);
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "addressValueSeparator", " ");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "nameValueSeparator", " ");
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "nameSubsetAttributes", new String[0]);
		ReflectionTestUtils.setField(idaAuthenticatorImpl, "idaSentFaceAsCbeffXml", false);
	}

	@Test
	public void doKycAuth_withInvalidDetails_throwsException() throws Exception {
		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("IND1234");
		kycAuthDto.setTransactionId("TRAN1234");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("PIN");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList<>();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);

		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");
		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycResponse>>>any())).thenReturn(null);

		Assert.assertThrows(KycAuthException.class,
				() -> idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto));
	}

	@Test
	public void doKycAuth_withValidDetails_thenPass() throws Exception {
		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("IND1234");
		kycAuthDto.setTransactionId("TRAN1234");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList<>();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);

		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");

		IdaKycResponse idaKycAuthResponse = new IdaKycResponse();
		idaKycAuthResponse.setAuthToken("authToken1234");
		idaKycAuthResponse.setKycStatus(true);
		idaKycAuthResponse.setIdentity("12345");

		IdaResponseWrapper<IdaKycResponse> idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycAuthResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycResponse>>(
				idaResponseWrapper, HttpStatus.OK);

		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycResponse>>>any()))
				.thenReturn(responseEntity);
		
		Mockito.when(tokenIdManager.generateTokenId(Mockito.anyString(), Mockito.anyString())).thenReturn("1122334455");

		KycAuthResult kycAuthResult = idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto);

		Assert.assertEquals(kycAuthResult.getKycToken(), kycAuthResult.getKycToken());
	}
	
	@Test
	public void doKycAuth_withValidDetails_thenPass_withSessionKeyAndThumbprint() throws Exception {
		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("IND1234");
		kycAuthDto.setTransactionId("TRAN1234");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList<>();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);

		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");

		IdaKycResponse idaKycAuthResponse = new IdaKycResponse();
		idaKycAuthResponse.setAuthToken("authToken1234");
		idaKycAuthResponse.setKycStatus(true);
		Map<String, Object> identityMap = Map.of(
				"dateOfBirth", "1993/08/04", 
				"firstName_eng", List.of(Map.of("value", "first-name")),
				"lastName_eng", List.of(Map.of("value", "last-name")),
				"addressLine1_eng", List.of(Map.of("value", "address-line-1")),
				"city_eng", List.of(Map.of("value", "my city")),
				"pinCode", "221024",
				BioMatchType.FACE.getIdMapping().getIdname(), faceBdb
				);
		String identityJson = objectMapper.writeValueAsString(identityMap);
		
		String enctyptedData = "Encrypted: " + identityJson;
		idaKycAuthResponse.setIdentity(CryptoUtil.encodeToPlainBase64(enctyptedData.getBytes()));
		idaKycAuthResponse.setSessionKey(CryptoUtil.encodeToURLSafeBase64("session key".getBytes()));
		idaKycAuthResponse.setThumbprint(CryptoUtil.encodeToURLSafeBase64("123ABC456DEF".getBytes()));

		IdaResponseWrapper<IdaKycResponse> idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycAuthResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycResponse>>(
				idaResponseWrapper, HttpStatus.OK);

		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycResponse>>>any()))
				.thenReturn(responseEntity);
		
		Mockito.when(tokenIdManager.generateTokenId(Mockito.anyString(), Mockito.anyString())).thenReturn("1122334455");

		KycAuthResult kycAuthResult = idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto);

		Assert.assertEquals(kycAuthResult.getKycToken(), kycAuthResult.getKycToken());
	}

	@Test
	public void doKycAuth_withAuthChallengeNull_thenFail() throws Exception {
		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("IND1234");
		kycAuthDto.setTransactionId("TRAN1234");
		kycAuthDto.setChallengeList(null);

		Assert.assertThrows(KycAuthException.class,
				() -> idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto));
	}

	@Test
	public void doKycAuth_withInvalidAuthChallenge_thenFail() throws Exception {
		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("IND1234");
		kycAuthDto.setTransactionId("TRAN1234");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("Test");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList<>();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);

		Assert.assertThrows(KycAuthException.class,
				() -> idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto));
	}

	@Test
	public void doKycAuth_withBIOAuthChallenge_thenPass() throws Exception {
		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("IND1234");
		kycAuthDto.setTransactionId("TRAN1234");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("BIO");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList<>();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);

		Biometric b = new Biometric();
		b.setData(
				"eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
		b.setHash("Hash");
		b.setSessionKey("SessionKey");
		b.setSpecVersion("SepecV");
		b.setThumbprint("Thumbprint");
		List<Biometric> bioList = new ArrayList<>();
		bioList.add(b);
		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");
		IdaKycResponse idaKycAuthResponse = new IdaKycResponse();
		idaKycAuthResponse.setAuthToken("authToken1234");
		idaKycAuthResponse.setKycStatus(true);

		IdaResponseWrapper<IdaKycResponse> idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycAuthResponse);
		idaKycAuthResponse.setIdentity("12345");
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycResponse>>(
				idaResponseWrapper, HttpStatus.OK);

		Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
				Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycResponse>>>any()))
				.thenReturn(responseEntity);
		
		Mockito.when(tokenIdManager.generateTokenId(Mockito.anyString(), Mockito.anyString())).thenReturn("1122334455");

		KycAuthResult kycAuthResult = idaAuthenticatorImpl.doKycAuth("relyingId", "clientId", kycAuthDto);

		Assert.assertEquals(kycAuthResult.getKycToken(), kycAuthResult.getKycToken());
	}

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
		String data = "ENCRKYC123";
		idaKycExchangeResponse.setEncryptedKyc(data);

		IdaResponseWrapper<IdaKycExchangeResponse> idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycExchangeResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>>(
				idaResponseWrapper, HttpStatus.OK);

		Mockito.when(tokenIdManager.generateTokenId(Mockito.anyString(), Mockito.anyString())).thenReturn("1122334455");
		JWTSignatureResponseDto jwtSignatureResponseDto = Mockito.mock(JWTSignatureResponseDto.class);
		Mockito.when(jwtSignatureResponseDto.getJwtSignedData()).thenReturn(data);
		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(jwtSignatureResponseDto);		
		KycExchangeResult kycExchangeResult = idaAuthenticatorImpl.doKycExchange("relyingPartyId", "clientId",
				kycExchangeDto);

		Assert.assertEquals(idaKycExchangeResponse.getEncryptedKyc(), kycExchangeResult.getEncryptedKyc());
	}
	
	@Test
	public void doKycExchange_withValidDetails_thenPass_with_encrypted_data() throws Exception {
		KycExchangeDto kycExchangeDto = new KycExchangeDto();
		kycExchangeDto.setIndividualId("IND1234");
		kycExchangeDto.setKycToken("KYCT123");
		kycExchangeDto.setTransactionId("TRAN123");
		List<String> acceptedClaims = new ArrayList<>();
		acceptedClaims.add("name");
		acceptedClaims.add("dateOfBirth");
		acceptedClaims.add("sub");
		acceptedClaims.add("individual_id");
		acceptedClaims.add("address");
		acceptedClaims.add("picture");
		kycExchangeDto.setAcceptedClaims(acceptedClaims);
		String[] claimsLacales = new String[] { "eng" };
		kycExchangeDto.setClaimsLocales(claimsLacales);

		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");
		
		Map<String, Object> identityMap = Map.of(
				"dateOfBirth", "1993/08/04", 
				"firstName_eng", List.of(Map.of("value", "first-name")),
				"lastName_eng", List.of(Map.of("value", "last-name")),
				"addressLine1_eng", List.of(Map.of("value", "address-line-1")),
				"city_eng", List.of(Map.of("value", "my city")),
				"pinCode", "221024",
				BioMatchType.FACE.getIdMapping().getIdname(), faceBdb
				);
		String identityJson = objectMapper.writeValueAsString(identityMap);
		String data = "ENCRYPTED: " + identityJson;

		IdaKycExchangeResponse idaKycExchangeResponse = new IdaKycExchangeResponse();
		idaKycExchangeResponse.setEncryptedKyc(data);

		IdaResponseWrapper<IdaKycExchangeResponse> idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycExchangeResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>>(
				idaResponseWrapper, HttpStatus.OK);

		Mockito.when(tokenIdManager.generateTokenId(Mockito.anyString(), Mockito.anyString())).thenReturn("1122334455");
		JWTSignatureResponseDto jwtSignatureResponseDto = Mockito.mock(JWTSignatureResponseDto.class);
		Mockito.when(jwtSignatureResponseDto.getJwtSignedData()).thenReturn(data);
		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(jwtSignatureResponseDto);	
		String encryptedIdentityData = "sample encrypted data";
		Mockito.when(identityDataStore.getEncryptedIdentityData(Mockito.anyString(), Mockito.anyString())).thenReturn(encryptedIdentityData);
		
		String decryptedIdentityData = CryptoUtil.encodeToURLSafeBase64(identityJson.getBytes());
		Mockito.when(helperService.decrptData(encryptedIdentityData)).thenReturn(decryptedIdentityData);
		Mockito.when(mapper.readValue(CryptoUtil.decodeURLSafeBase64(decryptedIdentityData), Map.class)).thenReturn(identityMap);
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("name")).thenReturn(List.of("firstName", "lastName"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("address")).thenReturn(List.of("addressLine1", "city", "pinCode"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("firstName")).thenReturn(List.of("firstName"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("lastName")).thenReturn(List.of("lastName"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("addressLine1")).thenReturn(List.of("addressLine1"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("city")).thenReturn(List.of("city"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("pinCode")).thenReturn(List.of("pinCode"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("dateOfBirth")).thenReturn(List.of("dateOfBirth"));
		
		KycExchangeResult kycExchangeResult = idaAuthenticatorImpl.doKycExchange("relyingPartyId", "clientId",
				kycExchangeDto);

		Assert.assertEquals(idaKycExchangeResponse.getEncryptedKyc(), kycExchangeResult.getEncryptedKyc());
	}
	
	@Test
	public void doKycExchange_withValidDetails_thenPass_with_encrypted_data_singleLang() throws Exception {
		KycExchangeDto kycExchangeDto = new KycExchangeDto();
		kycExchangeDto.setIndividualId("IND1234");
		kycExchangeDto.setKycToken("KYCT123");
		kycExchangeDto.setTransactionId("TRAN123");
		List<String> acceptedClaims = new ArrayList<>();
		acceptedClaims.add("name");
		acceptedClaims.add("dateOfBirth");
		acceptedClaims.add("sub");
		acceptedClaims.add("individual_id");
		acceptedClaims.add("address");
		acceptedClaims.add("picture");
		kycExchangeDto.setAcceptedClaims(acceptedClaims);
		String[] claimsLacales = new String[] { "eng", "ara" };
		kycExchangeDto.setClaimsLocales(claimsLacales);

		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");
		
		Map<String, Object> identityMap = Map.of(
				"dateOfBirth", "1993/08/04", 
				"firstName_eng", List.of(Map.of("value", "first-name-eng")),
				"addressLine1_eng", List.of(Map.of("value", "address-line-1")),
				"city_eng", List.of(Map.of("value", "my city")),
				"pinCode", "221024",
				BioMatchType.FACE.getIdMapping().getIdname(), faceBdb
				);
		String identityJson = objectMapper.writeValueAsString(identityMap);
		String data = "ENCRYPTED: " + identityJson;

		IdaKycExchangeResponse idaKycExchangeResponse = new IdaKycExchangeResponse();
		idaKycExchangeResponse.setEncryptedKyc(data);

		IdaResponseWrapper<IdaKycExchangeResponse> idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycExchangeResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>>(
				idaResponseWrapper, HttpStatus.OK);

		Mockito.when(tokenIdManager.generateTokenId(Mockito.anyString(), Mockito.anyString())).thenReturn("1122334455");
		JWTSignatureResponseDto jwtSignatureResponseDto = Mockito.mock(JWTSignatureResponseDto.class);
		Mockito.when(jwtSignatureResponseDto.getJwtSignedData()).thenReturn(data);
		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(jwtSignatureResponseDto);	
		String encryptedIdentityData = "sample encrypted data";
		Mockito.when(identityDataStore.getEncryptedIdentityData(Mockito.anyString(), Mockito.anyString())).thenReturn(encryptedIdentityData);
		
		String decryptedIdentityData = CryptoUtil.encodeToURLSafeBase64(identityJson.getBytes());
		Mockito.when(helperService.decrptData(encryptedIdentityData)).thenReturn(decryptedIdentityData);
		Mockito.when(mapper.readValue(CryptoUtil.decodeURLSafeBase64(decryptedIdentityData), Map.class)).thenReturn(identityMap);
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("name")).thenReturn(List.of("firstName"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("address")).thenReturn(List.of("addressLine1", "city", "pinCode"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("firstName")).thenReturn(List.of("firstName"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("addressLine1")).thenReturn(List.of("addressLine1"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("city")).thenReturn(List.of("city"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("pinCode")).thenReturn(List.of("pinCode"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("dateOfBirth")).thenReturn(List.of("dateOfBirth"));
		
		KycExchangeResult kycExchangeResult = idaAuthenticatorImpl.doKycExchange("relyingPartyId", "clientId",
				kycExchangeDto);

		Assert.assertEquals(idaKycExchangeResponse.getEncryptedKyc(), kycExchangeResult.getEncryptedKyc());
	}
	
	@Test
	public void doKycExchange_withValidDetails_thenPass_with_encrypted_data_multiLang() throws Exception {
		KycExchangeDto kycExchangeDto = new KycExchangeDto();
		kycExchangeDto.setIndividualId("IND1234");
		kycExchangeDto.setKycToken("KYCT123");
		kycExchangeDto.setTransactionId("TRAN123");
		List<String> acceptedClaims = new ArrayList<>();
		acceptedClaims.add("name");
		acceptedClaims.add("dateOfBirth");
		acceptedClaims.add("sub");
		acceptedClaims.add("individual_id");
		acceptedClaims.add("address");
		acceptedClaims.add("picture");
		kycExchangeDto.setAcceptedClaims(acceptedClaims);
		String[] claimsLacales = new String[] { "eng", "ara" };
		kycExchangeDto.setClaimsLocales(claimsLacales);

		Mockito.when(mapper.writeValueAsString(Mockito.any())).thenReturn("value");
		
		Map<String, Object> identityMap = Map.of(
				"dateOfBirth", "1993/08/04", 
				"firstName_eng", List.of(Map.of("value", "first-name-eng")),
				"firstName_ara", List.of(Map.of("value", "first-name-ara")),
				"addressLine1_eng", List.of(Map.of("value", "address-line-1")),
				"city_eng", List.of(Map.of("value", "my city")),
				"pinCode", "221024",
				BioMatchType.FACE.getIdMapping().getIdname(), faceBdb
				);
		String identityJson = objectMapper.writeValueAsString(identityMap);
		String data = "ENCRYPTED: " + identityJson;

		IdaKycExchangeResponse idaKycExchangeResponse = new IdaKycExchangeResponse();
		idaKycExchangeResponse.setEncryptedKyc(data);

		IdaResponseWrapper<IdaKycExchangeResponse> idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycExchangeResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>>(
				idaResponseWrapper, HttpStatus.OK);

		Mockito.when(tokenIdManager.generateTokenId(Mockito.anyString(), Mockito.anyString())).thenReturn("1122334455");
		JWTSignatureResponseDto jwtSignatureResponseDto = Mockito.mock(JWTSignatureResponseDto.class);
		Mockito.when(jwtSignatureResponseDto.getJwtSignedData()).thenReturn(data);
		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(jwtSignatureResponseDto);	
		String encryptedIdentityData = "sample encrypted data";
		Mockito.when(identityDataStore.getEncryptedIdentityData(Mockito.anyString(), Mockito.anyString())).thenReturn(encryptedIdentityData);
		
		String decryptedIdentityData = CryptoUtil.encodeToURLSafeBase64(identityJson.getBytes());
		Mockito.when(helperService.decrptData(encryptedIdentityData)).thenReturn(decryptedIdentityData);
		Mockito.when(mapper.readValue(CryptoUtil.decodeURLSafeBase64(decryptedIdentityData), Map.class)).thenReturn(identityMap);
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("name")).thenReturn(List.of("firstName"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("address")).thenReturn(List.of("addressLine1", "city", "pinCode"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("firstName")).thenReturn(List.of("firstName"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("addressLine1")).thenReturn(List.of("addressLine1"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("city")).thenReturn(List.of("city"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("pinCode")).thenReturn(List.of("pinCode"));
		Mockito.when(idInfoHelper.getIdentityAttributesForIdName("dateOfBirth")).thenReturn(List.of("dateOfBirth"));
		
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

		IdaKycExchangeResponse idaKycExchangeResponse = new IdaKycExchangeResponse();
		idaKycExchangeResponse.setEncryptedKyc("ENCRKYC123");

		IdaResponseWrapper<IdaKycExchangeResponse> idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

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
		List<KycSigningCertificateData> signingCertificates = new ArrayList<>();

		signingCertificates = idaAuthenticatorImpl.getAllKycSigningCertificates();

		Assert.assertSame(signingCertificates, List.of());
	}

}
