package io.mosip.idp.authwrapper.service;

import static io.mosip.idp.core.util.ErrorConstants.SEND_OTP_FAILED;
import static io.mosip.idp.core.util.IdentityProviderUtil.ALGO_SHA3_256;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.File;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.PublicKey;
import java.security.SignatureException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

import org.apache.commons.codec.digest.DigestUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.client.RestTemplate;

import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;

import io.mosip.idp.authwrapper.dto.IdaOtpResponse;
import io.mosip.idp.authwrapper.dto.IdaSendOtpResponse;
import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.KycAuthDto;
import io.mosip.idp.core.dto.KycAuthResult;
import io.mosip.idp.core.dto.KycExchangeDto;
import io.mosip.idp.core.dto.KycExchangeResult;
import io.mosip.idp.core.dto.SendOtpDto;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.dto.TokenResponse;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.core.util.FileUtils;
import io.mosip.kernel.crypto.jce.core.CryptoCore;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)

public class MockAuthenticationServiceTest {
	
	@Rule
    public TemporaryFolder folder = new TemporaryFolder();
	
	@Mock
	ObjectMapper mapper;

	@Mock
	RestTemplate restTemplate;

	@Mock
	KeymanagerService keymanagerService;

	@Mock
	KeymanagerUtil keymanagerUtil;

	@Mock
	SignatureService signatureService;

	@Mock
	CryptoCore cryptoCore;
	
	@Mock
	JWT jwt;

	@Mock
	DigestUtils digestUtils;

	@Mock
	IdentityProviderUtil identityProviderUtil;
	

	
	
	@Mock
	MockAuthenticationService mockAuthenticationService;
	
	private void initiateMockAuthenticationService() throws IOException {
		mockAuthenticationService = new MockAuthenticationService("src/test/resources/mockida/",
				"src/test/resources/mockida/", "src/test/resources/mockida/claims_attributes_mapping.json", 0, false,
				signatureService, mapper, keymanagerService);
	}
	
	@Before
	public void setUp() throws IOException {
		initiateMockAuthenticationService();
	}
	
	
	

	

	@Test
	public void doKycAuth_withValidOTP_thenPass() throws Exception {
		
		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("8267411571");
		kycAuthDto.setTransactionId("TRAN1234");
		
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		
		
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);
		
		Mockito.when(mapper.writeValueAsString(Matchers.any())).thenReturn("value");
		
		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");
		
		Certificate certificate=Mockito.mock(Certificate.class);
		

		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(responseDto);
		
		KycAuthResult kycAuthResult = mockAuthenticationService.doKycAuth("relying123", "clientId123", kycAuthDto);
		
		Assert.assertNotNull(kycAuthResult);
	}
	
	@Test
	public void doKycAuth_withValidPin_thenPass() throws Exception {
		
		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("8267411571");
		kycAuthDto.setTransactionId("TRAN1234");
		
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("PIN");
		authChallenge.setChallenge("34789");
		
		
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);
		
		Mockito.when(mapper.writeValueAsString(Matchers.any())).thenReturn("value");
		
		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");
		
		Certificate certificate=Mockito.mock(Certificate.class);
		

		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(responseDto);
		
		KycAuthResult kycAuthResult = mockAuthenticationService.doKycAuth("relying123", "clientId123", kycAuthDto);
		
		Assert.assertNotNull(kycAuthResult);
	}
	
	@Test
	public void doKycAuth_withValidBIO_thenPass() throws Exception {
		
		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("8267411571");
		kycAuthDto.setTransactionId("TRAN1234");
		
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("BIO");
		authChallenge.setChallenge("111111");
		
		
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);
		
		Mockito.when(mapper.writeValueAsString(Matchers.any())).thenReturn("value");
		
		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");
		
		Certificate certificate=Mockito.mock(Certificate.class);
		

		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(responseDto);
		
		KycAuthResult kycAuthResult = mockAuthenticationService.doKycAuth("relying123", "clientId123", kycAuthDto);
		
		Assert.assertNotNull(kycAuthResult);
	}
	
	@Test
	public void doKycAuth_withInvalidIndividualId_throwsException() throws KycAuthException, IOException {
		
		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("123456789");
		kycAuthDto.setTransactionId("TRAN1234");
		
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		
		
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);
		
		
		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");

		
		Assert.assertThrows(KycAuthException.class, ()-> mockAuthenticationService.doKycAuth("relying123", "clientId123", kycAuthDto));
		
		
	}
	
	@Test
	@Ignore
	public void doKycExchange_withValidDetails_thenPass() throws Exception {
		
		KycExchangeDto kycExchangeDto = new KycExchangeDto();
		kycExchangeDto.setIndividualId("8267411571");
		
		kycExchangeDto.setKycToken("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
		kycExchangeDto.setTransactionId("TRAN123");
		List<String> acceptedClaims = new ArrayList();
		acceptedClaims.add("claims");
		kycExchangeDto.setAcceptedClaims(acceptedClaims);
		String[] claimsLacales = new String[] { "claims", "locales" };
		kycExchangeDto.setClaimsLocales(claimsLacales);
		
		JWTSignatureVerifyResponseDto responseDto = new JWTSignatureVerifyResponseDto();
		responseDto.setSignatureValid(true);
		
		//KYC TOKEN is not in valid format , line no 234 MockAuthentication Services
		

		Mockito.when(signatureService.jwtVerify(Mockito.any())).thenReturn(responseDto);
		
		
		KycExchangeResult kycExchangeResult= mockAuthenticationService.doKycExchange("relying123", "client123", kycExchangeDto);
		
		
	}
	
	@Test
    public void sendOtp_withValidIndividualId_thenPass()
            throws SendOtpException, Exception {
		
		SendOtpDto sendOtpDto = new SendOtpDto();
		sendOtpDto.setIndividualId("8267411571");
		sendOtpDto.setTransactionId("4567");
		List<String> otpChannelsList = new ArrayList<>();
		otpChannelsList.add("channel");
		sendOtpDto.setOtpChannels(otpChannelsList);
		
       SendOtpResult sendOtpResult=mockAuthenticationService.sendOtp("relyingPartyId", "clientId", sendOtpDto);
       
       Assert.assertEquals(sendOtpDto.getTransactionId(), sendOtpResult.getTransactionId());
    }
	
	@Test
    public void sendOtp_withInalidIndividualId_throwsException()
            throws SendOtpException, Exception {
		
		SendOtpDto sendOtpDto = new SendOtpDto();
		sendOtpDto.setIndividualId("1234567");
		sendOtpDto.setTransactionId("4567");
		List<String> otpChannelsList = new ArrayList<>();
		otpChannelsList.add("channel");
		sendOtpDto.setOtpChannels(otpChannelsList);
       
       Assert.assertThrows(SendOtpException.class, ()->mockAuthenticationService.sendOtp("relyingPartyId", "clientId", sendOtpDto));
    }
	

}
