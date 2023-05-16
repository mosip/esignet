/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.authentication.esignet.integration.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.authentication.esignet.integration.dto.IdaKycAuthRequest;
import io.mosip.authentication.esignet.integration.dto.IdaKycAuthRequest.AuthRequest;
import io.mosip.authentication.esignet.integration.dto.IdaSendOtpRequest;
import io.mosip.authentication.esignet.integration.dto.IdaSendOtpResponse;
import io.mosip.authentication.esignet.integration.dto.KeyBindedToken;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.kernel.crypto.jce.core.CryptoCore;
import io.mosip.kernel.cryptomanager.dto.CryptomanagerRequestDto;
import io.mosip.kernel.cryptomanager.dto.CryptomanagerResponseDto;
import io.mosip.kernel.cryptomanager.service.CryptomanagerService;
import io.mosip.kernel.keygenerator.bouncycastle.util.KeyGeneratorUtils;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import io.mosip.kernel.partnercertservice.util.PartnerCertificateManagerUtil;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;

@Service
@Slf4j
public class HelperService {

    public static final String CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    public static final String SIGNATURE_HEADER_NAME = "signature";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    public static final String UTC_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final String INVALID_PARTNER_CERTIFICATE = "invalid_partner_cert";
    public static final String OIDC_PARTNER_APP_ID = "OIDC_PARTNER";
    public static final String BINDING_TRANSACTION = "bindingtransaction";
    private static Base64.Encoder urlSafeEncoder;
    private static Base64.Decoder urlSafeDecoder;

    static {
        urlSafeEncoder = Base64.getUrlEncoder().withoutPadding();
        urlSafeDecoder = Base64.getUrlDecoder();
    }

    @Value("${mosip.esignet.authenticator.ida-send-otp-id:mosip.identity.otp}")
    private String sendOtpId;

    @Value("${mosip.esignet.authenticator.ida-send-otp-version:1.0}")
    private String idaVersion;

    @Value("${mosip.esignet.authenticator.ida.cert-url}")
    private String idaPartnerCertificateUrl;

    @Value("${mosip.esignet.authenticator.ida.send-otp-url}")
    private String sendOtpUrl;

    @Value("${mosip.kernel.keygenerator.symmetric-algorithm-name}")
    private String symmetricAlgorithm;

    @Value("${mosip.kernel.keygenerator.symmetric-key-length}")
    private int symmetricKeyLength;

    @Autowired
    private KeymanagerUtil keymanagerUtil;

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CryptoCore cryptoCore;

    private Certificate idaPartnerCertificate;
    
    @Autowired
    private CryptomanagerService cryptomanagerService;


    @Cacheable(value = BINDING_TRANSACTION, key = "#idHash")
    public String getTransactionId(String idHash) {
        return HelperService.generateTransactionId(10);
    }

    protected void setAuthRequest(List<AuthChallenge> challengeList, IdaKycAuthRequest idaKycAuthRequest) throws Exception {
        IdaKycAuthRequest.AuthRequest authRequest = new IdaKycAuthRequest.AuthRequest();
        authRequest.setTimestamp(HelperService.getUTCDateTime());
        challengeList.stream()
                .filter( auth -> auth != null &&  auth.getAuthFactorType() != null)
                .forEach( auth -> { buildAuthRequest(auth, authRequest, idaKycAuthRequest); });

        KeyGenerator keyGenerator = KeyGeneratorUtils.getKeyGenerator(symmetricAlgorithm, symmetricKeyLength);
        final SecretKey symmetricKey = keyGenerator.generateKey();
        String request = objectMapper.writeValueAsString(authRequest);
        String hexEncodedHash = HMACUtils2.digestAsPlainText(request.getBytes(StandardCharsets.UTF_8));
        idaKycAuthRequest.setRequest(HelperService.b64Encode(cryptoCore.symmetricEncrypt(symmetricKey,
                request.getBytes(StandardCharsets.UTF_8), null)));
        idaKycAuthRequest.setRequestHMAC(HelperService.b64Encode(cryptoCore.symmetricEncrypt(symmetricKey,
                hexEncodedHash.getBytes(StandardCharsets.UTF_8), null)));
        Certificate certificate = getIdaPartnerCertificate();
        idaKycAuthRequest.setThumbprint(HelperService.b64Encode(getCertificateThumbprint(certificate)));
        log.info("IDA certificate thumbprint {}", idaKycAuthRequest.getThumbprint());
        idaKycAuthRequest.setRequestSessionKey(HelperService.b64Encode(
                cryptoCore.asymmetricEncrypt(certificate.getPublicKey(), symmetricKey.getEncoded())));
    }


    protected SendOtpResult sendOTP(String partnerId, String clientId, IdaSendOtpRequest idaSendOtpRequest)
            throws SendOtpException, JsonProcessingException {
        idaSendOtpRequest.setId(sendOtpId);
        idaSendOtpRequest.setVersion(idaVersion);
        idaSendOtpRequest.setRequestTime(getUTCDateTime());

        //set signature header, body and invoke kyc exchange endpoint
        String requestBody = objectMapper.writeValueAsString(idaSendOtpRequest);
        RequestEntity requestEntity = RequestEntity
                .post(UriComponentsBuilder.fromUriString(sendOtpUrl).pathSegment(partnerId, clientId).build().toUri())
                .contentType(MediaType.APPLICATION_JSON_UTF8)
                .header(SIGNATURE_HEADER_NAME, getRequestSignature(requestBody))
                .header(AUTHORIZATION_HEADER_NAME, AUTHORIZATION_HEADER_NAME)
                .body(requestBody);
        ResponseEntity<IdaSendOtpResponse> responseEntity = restTemplate.exchange(requestEntity, IdaSendOtpResponse.class);
        if(responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
            IdaSendOtpResponse idaSendOtpResponse = responseEntity.getBody();
            if(idaSendOtpRequest.getTransactionID().equals(idaSendOtpResponse.getTransactionID()) && idaSendOtpResponse.getResponse() != null){
                return new SendOtpResult(idaSendOtpResponse.getTransactionID(),
                        idaSendOtpResponse.getResponse().getMaskedEmail(),
                        idaSendOtpResponse.getResponse().getMaskedMobile());
            }
            log.error("Errors in response received from IDA send-otp : {}", idaSendOtpResponse.getErrors());
            throw new SendOtpException(idaSendOtpResponse.getErrors().get(0).getErrorCode());
        }
        log.error("Error response received from IDA (send-otp) with status : {}", responseEntity.getStatusCode());
        throw new SendOtpException();
    }

    protected String getRequestSignature(String request) {
        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(OIDC_PARTNER_APP_ID);
        jwtSignatureRequestDto.setReferenceId("");
        jwtSignatureRequestDto.setIncludePayload(false);
        jwtSignatureRequestDto.setIncludeCertificate(true);
        jwtSignatureRequestDto.setDataToSign(HelperService.b64Encode(request));
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        log.debug("Request signature ---> {}", responseDto.getJwtSignedData());
        return responseDto.getJwtSignedData();
    }
    
    public String decrptData(String identityStr) {
		CryptomanagerRequestDto cryptomanagerRequestDto = new CryptomanagerRequestDto();
		cryptomanagerRequestDto.setApplicationId(OIDC_PARTNER_APP_ID);
		cryptomanagerRequestDto.setData(identityStr);
		cryptomanagerRequestDto.setTimeStamp(DateUtils.getUTCCurrentDateTime());
		CryptomanagerResponseDto cryptomanagerResponseDto = cryptomanagerService.decrypt(cryptomanagerRequestDto);
		return cryptomanagerResponseDto.getData();
	}

    protected Certificate getIdaPartnerCertificate() throws KycAuthException {
        if(StringUtils.isEmpty(idaPartnerCertificate)) {
            log.info("Fetching IDA partner certificate from : {}", idaPartnerCertificateUrl);
            idaPartnerCertificate = keymanagerUtil.convertToCertificate(restTemplate.getForObject(idaPartnerCertificateUrl,
                    String.class));
        }
        if(PartnerCertificateManagerUtil.isCertificateDatesValid((X509Certificate)idaPartnerCertificate))
            return idaPartnerCertificate;

        log.info("PARTNER CERTIFICATE IS NOT VALID, Downloading the certificate again");
        idaPartnerCertificate = keymanagerUtil.convertToCertificate(restTemplate.getForObject(idaPartnerCertificateUrl,
                String.class));
        if(PartnerCertificateManagerUtil.isCertificateDatesValid((X509Certificate)idaPartnerCertificate))
            return idaPartnerCertificate;

        throw new KycAuthException(INVALID_PARTNER_CERTIFICATE);
    }

    protected byte[] getCertificateThumbprint(Certificate certificate) {
        try {
            return DigestUtils.sha256(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            log.error("Failed to get cert thumbprint", e);
        }
        return new byte[]{};
    }

    /**
     * Output format : 2022-12-01T03:22:46.720Z
     * @return Formatted datetime
     */
    protected static String getUTCDateTime() {
        return ZonedDateTime
                .now(ZoneOffset.UTC)
                .format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN));
    }

    protected static String b64Encode(byte[] bytes) {
        return urlSafeEncoder.encodeToString(bytes);
    }

    protected static String b64Encode(String value) {
        return urlSafeEncoder.encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    protected static byte[] b64Decode(String value) {
        return urlSafeDecoder.decode(value);
    }

    private void buildAuthRequest(AuthChallenge authChallenge, AuthRequest authRequest, IdaKycAuthRequest kycAauthRequest) {
        log.info("Build kyc-auth request with authFactor : {}",  authChallenge.getAuthFactorType());
        switch (authChallenge.getAuthFactorType().toUpperCase()) {
            case "OTP" : 
            	authRequest.setOtp(authChallenge.getChallenge());
            	kycAauthRequest.getRequestedAuth().put("otp", true);
                break;
            case "PIN" : 
            	authRequest.setStaticPin(authChallenge.getChallenge());
        		kycAauthRequest.getRequestedAuth().put("pin", true);
                break;
            case "BIO" :
        		kycAauthRequest.getRequestedAuth().put("bio", true);
                byte[] decodedBio = HelperService.b64Decode(authChallenge.getChallenge());
                try {
                    List<IdaKycAuthRequest.Biometric> biometrics = objectMapper.readValue(decodedBio,
                            new TypeReference<List<IdaKycAuthRequest.Biometric>>(){});
                    authRequest.setBiometrics(biometrics);
                } catch (Exception e) {
                    log.error("Failed to parse biometric capture response", e);
                }
                break;
            case "WLA" :
                List<KeyBindedToken> list = new ArrayList<>();
                KeyBindedToken keyBindedToken = new KeyBindedToken();
                keyBindedToken.setType(authChallenge.getAuthFactorType());
                keyBindedToken.setToken(authChallenge.getChallenge());
                keyBindedToken.setFormat(authChallenge.getFormat());
                list.add(keyBindedToken);
                authRequest.setKeyBindedTokens(list);
                break;
            default:
                throw new NotImplementedException("KYC auth not implemented");
        }
    }

    protected static String generateTransactionId(int length) {
        StringBuilder builder = new StringBuilder();
        for(int i=0; i<length; i++) {
            int index = ThreadLocalRandom.current().nextInt(CHARACTERS.length());
            builder.append(CHARACTERS.charAt(index));
        }
        return builder.toString();
    }

    protected static String generateHash(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA3-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return urlSafeEncoder.encodeToString(hash);
        } catch (NoSuchAlgorithmException ex) {
            log.error("Hashing failed", ex);
        }
        return value;
    }

}
