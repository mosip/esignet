package io.mosip.idp.authwrapper.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockitoSession;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Matchers;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.auth0.jwt.JWTCreator;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.JWT;

import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.idp.authwrapper.dto.IdaKycAuthRequest;
import io.mosip.idp.authwrapper.dto.IdaKycAuthRequest.Biometric;
import io.mosip.idp.authwrapper.dto.IdaKycAuthResponse;
import io.mosip.idp.authwrapper.dto.IdaKycExchangeResponse;
import io.mosip.idp.authwrapper.dto.IdaOtpResponse;
import io.mosip.idp.authwrapper.dto.IdaResponseWrapper;
import io.mosip.idp.authwrapper.dto.IdaSendOtpRequest;
import io.mosip.idp.authwrapper.dto.IdaSendOtpResponse;
import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.Error;
import io.mosip.idp.core.dto.KycAuthDto;
import io.mosip.idp.core.dto.KycAuthResult;
import io.mosip.idp.core.dto.KycExchangeDto;
import io.mosip.idp.core.dto.KycExchangeResult;
import io.mosip.idp.core.dto.SendOtpDto;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.exception.KycExchangeException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.kernel.crypto.jce.core.CryptoCore;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import io.mosip.kernel.partnercertservice.util.PartnerCertificateManagerUtil;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import org.junit.Assert;

import java.util.*;

import javax.security.auth.x500.X500Principal;

import io.mosip.idp.core.util.IdentityProviderUtil;

import java.math.BigInteger;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import org.apache.commons.codec.digest.DigestUtils;

@SpringBootTest
@RunWith(MockitoJUnitRunner.class)
public class IdentityAuthenticationServiceTest {
	@InjectMocks
	IdentityAuthenticationService identityAuthenticationService;

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
	DigestUtils digestUtils;

	@Mock
	IdentityProviderUtil identityProviderUtil;

	
	
	
	private JWK clientJWK = TestUtil.generateJWK_RSA();
	

	

	@Before
	public void setUp() {
		MockitoAnnotations.initMocks(this);
		ReflectionTestUtils.setField(identityAuthenticationService, "sendOtpUrl", "https:/");
		ReflectionTestUtils.setField(identityAuthenticationService, "kycExchangeUrl", "https://dev.mosip.net");
		ReflectionTestUtils.setField(identityAuthenticationService, "idaVersion", "VersionIDA");
		ReflectionTestUtils.setField(identityAuthenticationService, "symmetricAlgorithm", "AES");
		ReflectionTestUtils.setField(identityAuthenticationService, "symmetricKeyLength", 256);
		ReflectionTestUtils.setField(identityAuthenticationService, "idaPartnerCertificateUrl", "https://test");
		ReflectionTestUtils.setField(identityAuthenticationService, "kycAuthUrl", "https://testkycAuthUrl");
	}

	@Test
	public void doKycAuth_withInvalidDetails_throwsException() throws Exception {

		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("IND1234");
		kycAuthDto.setTransactionId("TRAN1234");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("PIN");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);

		Mockito.when(mapper.writeValueAsString(Matchers.any())).thenReturn("value");
		
		Mockito.when(restTemplate.getForObject(
		        Mockito.anyString(), Mockito.eq(String.class))).thenReturn("value");
		
		X509Certificate certificate = getCertificate();
		Mockito.when(keymanagerUtil.convertToCertificate(Mockito.anyString())).thenReturn(certificate);
        
		byte[] temp=new byte[2];
		Mockito.when(cryptoCore.asymmetricEncrypt(Mockito.any(PublicKey.class), Mockito.any())).thenReturn(temp);
		
		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");
		
		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(responseDto);
		
		IdaKycAuthResponse idaKycAuthResponse = new IdaKycAuthResponse();
		idaKycAuthResponse.setAuthToken("authToken1234");
		idaKycAuthResponse.setKycToken("kycToken1234");
		idaKycAuthResponse.setKycStatus(true);
		
		IdaResponseWrapper idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycAuthResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>>(idaResponseWrapper,
				HttpStatus.OK);

		Mockito.when(restTemplate.exchange(
                Mockito.<RequestEntity<Void>>any(),
                Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycAuthResponse>>>any()))
                .thenReturn(null);
		

		KycAuthResult kycAuthResult = new KycAuthResult();
		Assert.assertThrows(KycAuthException.class, ()-> identityAuthenticationService.doKycAuth("relyingId", "clientId", kycAuthDto));
		

		
	}
	
	
	
	

	
	@Test
	public void doKycAuth_withValidDetails_thenPass() throws Exception {

		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("IND1234");
		kycAuthDto.setTransactionId("TRAN1234");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("OTP");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);

		Mockito.when(mapper.writeValueAsString(Matchers.any())).thenReturn("value");
		
		Mockito.when(restTemplate.getForObject(
		        Mockito.anyString(), Mockito.eq(String.class))).thenReturn("value");
		
		X509Certificate certificate = getCertificate();
		Mockito.when(keymanagerUtil.convertToCertificate(Mockito.anyString())).thenReturn(certificate);
        
		byte[] temp=new byte[2];
		Mockito.when(cryptoCore.asymmetricEncrypt(Mockito.any(PublicKey.class), Mockito.any())).thenReturn(temp);
		
		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");
		
		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(responseDto);
		
		IdaKycAuthResponse idaKycAuthResponse = new IdaKycAuthResponse();
		idaKycAuthResponse.setAuthToken("authToken1234");
		idaKycAuthResponse.setKycToken("kycToken1234");
		idaKycAuthResponse.setKycStatus(true);
		
		IdaResponseWrapper idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycAuthResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>>(idaResponseWrapper,
				HttpStatus.OK);

		Mockito.when(restTemplate.exchange(
                Mockito.<RequestEntity<Void>>any(),
                Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycAuthResponse>>>any()))
                .thenReturn(responseEntity);
		

		KycAuthResult kycAuthResult = new KycAuthResult();

		kycAuthResult = identityAuthenticationService.doKycAuth("relyingId", "clientId", kycAuthDto);

		Assert.assertEquals(kycAuthResult.getKycToken(), kycAuthResult.getKycToken());
	}
	
	
	
	
	@Test
	public void doKycAuth_withValidDetails_BIO_thenPass() throws Exception {

		KycAuthDto kycAuthDto = new KycAuthDto();
		kycAuthDto.setIndividualId("IND1234");
		kycAuthDto.setTransactionId("TRAN1234");
		AuthChallenge authChallenge = new AuthChallenge();
		authChallenge.setAuthFactorType("BIO");
		authChallenge.setChallenge("111111");
		List<AuthChallenge> authChallengeList = new ArrayList();
		authChallengeList.add(authChallenge);
		kycAuthDto.setChallengeList(authChallengeList);
		
		
		
		byte[] decodedBio = new byte[] {-41,93,117,-41};
		Biometric b = new Biometric();
		b.setData("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6IkpvaG4gRG9lIiwiaWF0IjoxNTE2MjM5MDIyfQ.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
		b.setHash("Hash");
		b.setSessionKey("SessionKey");
		b.setSpecVersion("SepecV");
		b.setThumbprint("Thumbprint");
		List<Biometric> bioList=new ArrayList<>();
		bioList.add(b);
		Mockito.when(mapper.readValue(Mockito.any(byte[].class),Mockito.any(TypeReference.class))).thenReturn(bioList);

		Mockito.when(mapper.writeValueAsString(Matchers.any())).thenReturn("value");
		
		Mockito.when(restTemplate.getForObject(
		        Mockito.anyString(), Mockito.eq(String.class))).thenReturn("value");
		
		X509Certificate certificate = getCertificate();
		Mockito.when(keymanagerUtil.convertToCertificate(Mockito.anyString())).thenReturn(certificate);
        
		byte[] temp=new byte[2];
		Mockito.when(cryptoCore.asymmetricEncrypt(Mockito.any(PublicKey.class), Mockito.any())).thenReturn(temp);
		
		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");
		
		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(responseDto);
		
		IdaKycAuthResponse idaKycAuthResponse = new IdaKycAuthResponse();
		idaKycAuthResponse.setAuthToken("authToken1234");
		idaKycAuthResponse.setKycToken("kycToken1234");
		idaKycAuthResponse.setKycStatus(true);
		
		IdaResponseWrapper idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycAuthResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");

		ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>>(idaResponseWrapper,
				HttpStatus.OK);

		Mockito.when(restTemplate.exchange(
                Mockito.<RequestEntity<Void>>any(),
                Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycAuthResponse>>>any()))
                .thenReturn(responseEntity);
		

		KycAuthResult kycAuthResult = new KycAuthResult();

		kycAuthResult = identityAuthenticationService.doKycAuth("relyingId", "clientId", kycAuthDto);

		Assert.assertEquals(kycAuthResult.getKycToken(), kycAuthResult.getKycToken());
	}

	@Test
	public void doKycExchange_withValidDetails_thenPass() throws Exception {

		KycExchangeDto kycExchangeDto = new KycExchangeDto();
		kycExchangeDto.setIndividualId("IND1234");
		kycExchangeDto.setKycToken("KYCT123");
		kycExchangeDto.setTransactionId("TRAN123");
		List<String> acceptedClaims = new ArrayList();
		acceptedClaims.add("claims");
		kycExchangeDto.setAcceptedClaims(acceptedClaims);
		String[] claimsLacales = new String[] { "claims", "locales" };
		kycExchangeDto.setClaimsLocales(claimsLacales);

		Mockito.when(mapper.writeValueAsString(Matchers.any())).thenReturn("value");

		

		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");

		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(responseDto);

		
		
		
		IdaKycExchangeResponse idaKycExchangeResponse = new IdaKycExchangeResponse();
		idaKycExchangeResponse.setEncryptedKyc("ENCRKYC123");
		
		
		IdaResponseWrapper idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(idaKycExchangeResponse);
		idaResponseWrapper.setTransactionID("TRAN123");
		idaResponseWrapper.setVersion("VER1");
		
		
		
		ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>>(idaResponseWrapper,
				HttpStatus.OK);

		Mockito.when(restTemplate.exchange(
                Mockito.<RequestEntity<Void>>any(),
                Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycExchangeResponse>>>any()))
                .thenReturn(responseEntity);

		KycExchangeResult kycExchangeResult = identityAuthenticationService.doKycExchange("relyingPartyId", "clientId",
				kycExchangeDto);

		Assert.assertEquals(idaKycExchangeResponse.getEncryptedKyc(), kycExchangeResult.getEncryptedKyc());

	}
	
	@Test
	public void doKycExchange_withInvalidIndividualId_throwsException() throws KycExchangeException,Exception {

		
		KycExchangeDto kycExchangeDto = new KycExchangeDto();
		kycExchangeDto.setIndividualId("IND1234");
		kycExchangeDto.setKycToken("KYCT123");
		kycExchangeDto.setTransactionId("TRAN123");
		List<String> acceptedClaims = new ArrayList();
		acceptedClaims.add("claims");
		kycExchangeDto.setAcceptedClaims(acceptedClaims);
		String[] claimsLacales = new String[] { "claims", "locales" };
		kycExchangeDto.setClaimsLocales(claimsLacales);

		Mockito.when(mapper.writeValueAsString(Matchers.any())).thenReturn("value");

		

		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");

		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(responseDto);

		
		
		
		IdaKycExchangeResponse idaKycExchangeResponse = new IdaKycExchangeResponse();
		idaKycExchangeResponse.setEncryptedKyc(null);
		
		
		IdaResponseWrapper idaResponseWrapper = new IdaResponseWrapper<>();
		idaResponseWrapper.setResponse(null);
		idaResponseWrapper.setTransactionID(null);
		idaResponseWrapper.setVersion(null);
		
		
		
		ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>> responseEntity = new ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>>(idaResponseWrapper,
				HttpStatus.OK);

		Mockito.when(restTemplate.exchange(
                Mockito.<RequestEntity<Void>>any(),
                Mockito.<ParameterizedTypeReference<IdaResponseWrapper<IdaKycExchangeResponse>>>any()))
                .thenReturn(null);
		
		
		Assert.assertThrows(KycExchangeException.class, ()-> identityAuthenticationService.doKycExchange("relyingId", "clientId", kycExchangeDto));

		
	}

	@Test
	public void sendOtp_withValidDetails_thenPass() throws Exception {

		SendOtpDto sendOtpDto = new SendOtpDto();
		sendOtpDto.setIndividualId("1234");
		sendOtpDto.setTransactionId("4567");
		List<String> otpChannelsList = new ArrayList<>();
		otpChannelsList.add("channel");
		sendOtpDto.setOtpChannels(otpChannelsList);

		Mockito.when(mapper.writeValueAsString(Matchers.any())).thenReturn("value");

		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");

		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(responseDto);

		IdaOtpResponse idaOtpResponse = new IdaOtpResponse();
		idaOtpResponse.setMaskedEmail("a@gmail.com");
		idaOtpResponse.setMaskedMobile("1234567890");

		IdaSendOtpResponse idaSendOtpResponse = new IdaSendOtpResponse();
		idaSendOtpResponse.setTransactionID(sendOtpDto.getTransactionId());
		idaSendOtpResponse.setVersion("Version123");
		idaSendOtpResponse.setId("123");
		idaSendOtpResponse.setErrors(null);
		idaSendOtpResponse.setResponse(idaOtpResponse);

		ResponseEntity<IdaSendOtpResponse> responseEntity = new ResponseEntity<IdaSendOtpResponse>(idaSendOtpResponse,
				HttpStatus.OK);

		Mockito.when(restTemplate.exchange(any(), eq(IdaSendOtpResponse.class))).thenReturn(responseEntity);

		SendOtpResult sendOtpResult = identityAuthenticationService.sendOtp("rly123", "cli123", sendOtpDto);

		Assert.assertEquals(sendOtpDto.getTransactionId(), sendOtpResult.getTransactionId());

	}
	
	@Test
	public void sendOtpTest_withInvalidTransectionId_throwsException() throws SendOtpException,Exception {

		SendOtpDto sendOtpDto = new SendOtpDto();
		sendOtpDto.setIndividualId("1234");
		sendOtpDto.setTransactionId("4567");
		List<String> otpChannelsList = new ArrayList<>();
		otpChannelsList.add("channel");
		sendOtpDto.setOtpChannels(otpChannelsList);

		Mockito.when(mapper.writeValueAsString(Matchers.any())).thenReturn("value");

		JWTSignatureResponseDto responseDto = Mockito.mock(JWTSignatureResponseDto.class);
		responseDto.setJwtSignedData("jwtSignedData");

		Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(responseDto);

		IdaOtpResponse idaOtpResponse = new IdaOtpResponse();
		idaOtpResponse.setMaskedEmail("a@gmail.com");
		idaOtpResponse.setMaskedMobile("1234567890");

		IdaSendOtpResponse idaSendOtpResponse = new IdaSendOtpResponse();
		idaSendOtpResponse.setTransactionID("1324");
		idaSendOtpResponse.setVersion("Version123");
		idaSendOtpResponse.setId("123");
		idaSendOtpResponse.setErrors(null);
		idaSendOtpResponse.setResponse(idaOtpResponse);

		ResponseEntity<IdaSendOtpResponse> responseEntity = new ResponseEntity<IdaSendOtpResponse>(idaSendOtpResponse,
				HttpStatus.OK);

		Mockito.when(restTemplate.exchange(any(), eq(IdaSendOtpResponse.class))).thenReturn(responseEntity);

		Assert.assertThrows(SendOtpException.class, ()->identityAuthenticationService.sendOtp("rly123", "cli123", sendOtpDto));
		
	}
	
	
	
	private X509Certificate getCertificate() throws Exception {
		X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
		X500Principal dnName = new X500Principal("CN=Test");
		generator.setSubjectDN(dnName);
		generator.setIssuerDN(dnName); // use the same
		generator.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
		generator.setNotAfter(new Date(System.currentTimeMillis() + 24 * 365 * 24 * 60 * 60 * 1000));
		generator.setPublicKey(clientJWK.toRSAKey().toPublicKey());
		generator.setSignatureAlgorithm("SHA256WITHRSA");
		generator.setSerialNumber(new BigInteger(String.valueOf(System.currentTimeMillis())));
		return generator.generate(clientJWK.toRSAKey().toPrivateKey());
	}

}