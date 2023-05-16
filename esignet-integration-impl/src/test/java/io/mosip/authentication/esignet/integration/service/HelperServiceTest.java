package io.mosip.authentication.esignet.integration.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.authentication.esignet.integration.dto.*;
import io.mosip.authentication.esignet.integration.dto.Error;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.kernel.crypto.jce.core.CryptoCore;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import org.apache.commons.lang3.NotImplementedException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.security.cert.Certificate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


@RunWith(MockitoJUnitRunner.class)
public class HelperServiceTest {

    @InjectMocks
    private HelperService helperService;

    @Mock
    private KeymanagerUtil keymanagerUtil;

    @Mock
    private SignatureService signatureService;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private CryptoCore cryptoCore;

    String partnerId = "test";
    String partnerAPIKey = "test-api-key";

    ObjectMapper objectMapper = new ObjectMapper();


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(helperService, "sendOtpUrl", "https://test/test");
        ReflectionTestUtils.setField(helperService, "idaPartnerCertificateUrl", "https://test/test");
        ReflectionTestUtils.setField(helperService, "symmetricAlgorithm", "AES");
        ReflectionTestUtils.setField(helperService, "symmetricKeyLength", 256);
        ReflectionTestUtils.setField(helperService, "objectMapper", objectMapper);
    }

    @Test
    public void sendOtp_requestSignatureFailed_thenFail() {
        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData("test-jwt");
        Mockito.when(signatureService.jwtSign(Mockito.any())).thenThrow(RuntimeException.class);
        IdaSendOtpRequest sendOtpRequest = new IdaSendOtpRequest();
        Assert.assertThrows(Exception.class, () -> helperService.sendOTP(partnerId, partnerAPIKey, sendOtpRequest));
    }

    @Test
    public void sendOtp_withNullResponse_thenFail() {
        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData("test-jwt");
        Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(jwtSignatureResponseDto);

        ResponseEntity responseEntity = new ResponseEntity(HttpStatus.OK);
        Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
                Mockito.<Class>any())).thenReturn(responseEntity);
        IdaSendOtpRequest sendOtpRequest = new IdaSendOtpRequest();
        Assert.assertThrows(SendOtpException.class, () -> helperService.sendOTP(partnerId, partnerAPIKey, sendOtpRequest));
    }

    @Test
    public void sendOtp_withValidResponse_thenPass() throws Exception {
        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData("test-jwt");
        Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(jwtSignatureResponseDto);

        IdaSendOtpResponse idaSendOtpResponse = new IdaSendOtpResponse();
        idaSendOtpResponse.setTransactionID("123456788");
        IdaOtpResponse idaOtpResponse = new IdaOtpResponse();
        idaOtpResponse.setMaskedEmail("masked-mail");
        new IdaOtpResponse().setMaskedMobile("masked-mobile");
        idaSendOtpResponse.setResponse(idaOtpResponse);
        ResponseEntity<IdaSendOtpResponse> responseEntity = new ResponseEntity<IdaSendOtpResponse>(
                idaSendOtpResponse, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
                Mockito.<Class>any())).thenReturn(responseEntity);
        IdaSendOtpRequest sendOtpRequest = new IdaSendOtpRequest();
        sendOtpRequest.setTransactionID("123456788");
        SendOtpResult sendOtpResult = helperService.sendOTP(partnerId, partnerAPIKey, sendOtpRequest);
        Assert.assertEquals(idaSendOtpResponse.getTransactionID(), sendOtpResult.getTransactionId());
        Assert.assertEquals(idaOtpResponse.getMaskedEmail(), sendOtpResult.getMaskedEmail());
        Assert.assertEquals(idaOtpResponse.getMaskedMobile(), sendOtpResult.getMaskedMobile());
    }

    @Test
    public void sendOtp_withErrorResponse_thenFail() {
        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData("test-jwt");
        Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(jwtSignatureResponseDto);

        IdaSendOtpResponse idaSendOtpResponse = new IdaSendOtpResponse();
        idaSendOtpResponse.setTransactionID("123456788");
        idaSendOtpResponse.setErrors(Arrays.asList(new Error("otp-error", "otp-error")));
        ResponseEntity<IdaSendOtpResponse> responseEntity = new ResponseEntity<IdaSendOtpResponse>(
                idaSendOtpResponse, HttpStatus.OK);
        Mockito.when(restTemplate.exchange(Mockito.<RequestEntity<Void>>any(),
                Mockito.<Class>any())).thenReturn(responseEntity);

        IdaSendOtpRequest sendOtpRequest = new IdaSendOtpRequest();
        sendOtpRequest.setTransactionID("123456788");
        try {
            helperService.sendOTP(partnerId, partnerAPIKey, sendOtpRequest);
        } catch (SendOtpException e) {
            Assert.assertEquals("otp-error", e.getErrorCode());
        } catch (JsonProcessingException e) {
            Assert.fail();
        }
    }

    @Test
    public void setAuthRequest_withInvalidChallengeType_thenFail() {
        List<AuthChallenge> challengeList = new ArrayList<>();
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("test");
        authChallenge.setAuthFactorType("Test");
        challengeList.add(authChallenge);
        Assert.assertThrows(NotImplementedException.class,
                () -> helperService.setAuthRequest(challengeList, new IdaKycAuthRequest()));
    }

    @Test
    public void setAuthRequest_withOTPChallengeType_thenPass() throws Exception {
        List<AuthChallenge> challengeList = new ArrayList<>();
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("111333");
        authChallenge.setAuthFactorType("otp");
        authChallenge.setFormat("numeric");
        challengeList.add(authChallenge);

        Mockito.when(restTemplate.getForObject("https://test/test", String.class)).thenReturn("test-certificate");
        Mockito.when(keymanagerUtil.convertToCertificate(Mockito.any(String.class))).thenReturn(TestUtil.getCertificate());
        Mockito.when(cryptoCore.asymmetricEncrypt(Mockito.any(), Mockito.any())).thenReturn("test".getBytes());

        IdaKycAuthRequest idaKycAuthRequest = new IdaKycAuthRequest();
        helperService.setAuthRequest(challengeList, idaKycAuthRequest);
        Assert.assertNotNull(idaKycAuthRequest.getRequest());
        Assert.assertNotNull(idaKycAuthRequest.getRequestSessionKey());
        Assert.assertNotNull(idaKycAuthRequest.getRequestHMAC());
        Assert.assertNotNull(idaKycAuthRequest.getThumbprint());
    }

    @Test
    public void setAuthRequest_withPINChallengeType_thenPass() throws Exception {
        List<AuthChallenge> challengeList = new ArrayList<>();
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge("111333");
        authChallenge.setAuthFactorType("pin");
        authChallenge.setFormat("numeric");
        challengeList.add(authChallenge);

        Mockito.when(restTemplate.getForObject("https://test/test", String.class)).thenReturn("test-certificate");
        Mockito.when(keymanagerUtil.convertToCertificate(Mockito.any(String.class))).thenReturn(TestUtil.getCertificate());
        Mockito.when(cryptoCore.asymmetricEncrypt(Mockito.any(), Mockito.any())).thenReturn("test".getBytes());

        IdaKycAuthRequest idaKycAuthRequest = new IdaKycAuthRequest();
        helperService.setAuthRequest(challengeList, idaKycAuthRequest);
        Assert.assertNotNull(idaKycAuthRequest.getRequest());
        Assert.assertNotNull(idaKycAuthRequest.getRequestSessionKey());
        Assert.assertNotNull(idaKycAuthRequest.getRequestHMAC());
        Assert.assertNotNull(idaKycAuthRequest.getThumbprint());
    }

    @Test
    public void setAuthRequest_withBIOChallengeType_thenPass() throws Exception {
        IdaKycAuthRequest.Biometric biometric = new IdaKycAuthRequest.Biometric();
        biometric.setData("eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJ0cmFuc2FjdGlvbklkIjoiMTIzNDU2Nzg5MCIsIm5hbWUiOiJKb2huIERvZSIsImlhdCI6MTUxNjIzOTAyMn0=.SflKxwRJSMeKKF2QT4fwpMeJf36POk6yJV_adQssw5c");
        List<IdaKycAuthRequest.Biometric> list = new ArrayList<>();
        list.add(biometric);
        String value = objectMapper.writeValueAsString(list);

        List<AuthChallenge> challengeList = new ArrayList<>();
        AuthChallenge authChallenge = new AuthChallenge();
        authChallenge.setChallenge(HelperService.b64Encode(value));
        authChallenge.setAuthFactorType("bio");
        authChallenge.setFormat("numeric");
        challengeList.add(authChallenge);

        Mockito.when(restTemplate.getForObject("https://test/test", String.class)).thenReturn("test-certificate");
        Mockito.when(keymanagerUtil.convertToCertificate(Mockito.any(String.class))).thenReturn(TestUtil.getCertificate());
        Mockito.when(cryptoCore.asymmetricEncrypt(Mockito.any(), Mockito.any())).thenReturn("test".getBytes());

        IdaKycAuthRequest idaKycAuthRequest = new IdaKycAuthRequest();
        helperService.setAuthRequest(challengeList, idaKycAuthRequest);
        Assert.assertNotNull(idaKycAuthRequest.getRequest());
        Assert.assertNotNull(idaKycAuthRequest.getRequestSessionKey());
        Assert.assertNotNull(idaKycAuthRequest.getRequestHMAC());
        Assert.assertNotNull(idaKycAuthRequest.getThumbprint());
    }

    @Test
    public void getIdaPartnerCertificate_withUnsetPartnerCertificate_thenPass() throws Exception {
        Mockito.when(restTemplate.getForObject("https://test/test", String.class)).thenReturn("test-certificate");
        Certificate certificate = TestUtil.getCertificate();
        Mockito.when(keymanagerUtil.convertToCertificate(Mockito.any(String.class))).thenReturn(certificate);
        Assert.assertEquals(certificate, helperService.getIdaPartnerCertificate());
    }

    @Test
    public void getIdaPartnerCertificate_withExpiredPartnerCertificate_thenPass() throws Exception {
        Mockito.when(restTemplate.getForObject("https://test/test", String.class)).thenReturn("test-certificate", "test-certificate");
        Certificate certificate = TestUtil.getCertificate();
        Mockito.when(keymanagerUtil.convertToCertificate(Mockito.any(String.class))).thenReturn(TestUtil.getExpiredCertificate(), certificate);
        Assert.assertEquals(certificate, helperService.getIdaPartnerCertificate());
    }

    @Test
    public void getRequestSignature_validation() {
        JWTSignatureResponseDto jwtSignatureResponseDto = new JWTSignatureResponseDto();
        jwtSignatureResponseDto.setJwtSignedData("test-jwt");
        Mockito.when(signatureService.jwtSign(Mockito.any())).thenReturn(jwtSignatureResponseDto);
        Assert.assertEquals("test-jwt", helperService.getRequestSignature("test-request-value"));
    }
}
