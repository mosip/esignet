/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.authwrapper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.mosip.idp.authwrapper.dto.*;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.exception.KycExchangeException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.kernel.crypto.jce.core.CryptoCore;
import io.mosip.kernel.keygenerator.bouncycastle.util.KeyGeneratorUtils;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateRequestDto;
import io.mosip.kernel.keymanagerservice.exception.KeymanagerServiceException;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import io.mosip.kernel.partnercertservice.util.PartnerCertificateManagerUtil;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.X509Certificate;
import java.util.*;

import static io.mosip.idp.core.util.ErrorConstants.*;


@ConditionalOnProperty(value = "mosip.idp.authn.wrapper.impl", havingValue = "IdentityAuthenticationService")
@Component
@Slf4j
public class IdentityAuthenticationService implements AuthenticationWrapper {

    public static final String KYC_EXCHANGE_TYPE = "oidc";
    public static final String SIGNATURE_HEADER_NAME = "signature";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    public static final String WRAPPER_CACHE = "wrapper_ids";
    private static final String WRAPPER_REF_ID = "INDIVIDUAL_ID";
    public static final String INVALID_PARTNER_CERTIFICATE = "invalid_partner_cert";

    @Value("${mosip.idp.authn.wrapper.ida-id:mosip.identity.kycauth}")
    private String kycAuthId;

    @Value("${mosip.idp.authn.wrapper.ida-id:mosip.identity.kycexchange}")
    private String kycExchangeId;

    @Value("${mosip.idp.authn.wrapper.ida-send-otp-id:mosip.identity.otp}")
    private String sendOtpId;

    @Value("${mosip.idp.authn.wrapper.ida-version:1.0}")
    private String idaVersion;

    @Value("${mosip.idp.authn.wrapper.ida-domainUri}")
    private String idaDomainUri;

    @Value("${mosip.idp.authn.wrapper.ida-env:Staging}")
    private String idaEnv;

    @Value("${mosip.kernel.keygenerator.symmetric-algorithm-name}")
    private String symmetricAlgorithm;

    @Value("${mosip.kernel.keygenerator.symmetric-key-length}")
    private int symmetricKeyLength;

    @Value("${mosip.idp.authn.ida.kyc-auth-url}")
    private String kycAuthUrl;

    @Value("${mosip.idp.authn.ida.kyc-exchange-url}")
    private String kycExchangeUrl;

    @Value("${mosip.idp.authn.ida.send-otp-url}")
    private String sendOtpUrl;

    @Value("${mosip.idp.authn.ida.otp-channels}")
    private List<String> otpChannels;

    @Value("${mosip.idp.authn.ida.cert-url}")
    private String idaPartnerCertificateUrl;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private KeymanagerService keymanagerService;

    @Autowired
    private KeymanagerUtil keymanagerUtil;

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private CryptoCore cryptoCore;

    @Autowired
    CacheManager cacheManager;

    private Certificate idaPartnerCertificate;

    @Override
    public KycAuthResult doKycAuth(String relyingPartyId, String clientId, KycAuthRequest kycAuthRequest)
            throws KycAuthException {
        log.info("Started to build kyc-auth request with transactionId : {} && clientId : {}",
                kycAuthRequest.getTransactionId(), clientId);
        try {
            IdaKycAuthRequest idaKycAuthRequest = new IdaKycAuthRequest();
            idaKycAuthRequest.setId(kycAuthId);
            idaKycAuthRequest.setVersion(idaVersion);
            idaKycAuthRequest.setRequestTime(IdentityProviderUtil.getUTCDateTime());
            idaKycAuthRequest.setDomainUri(idaDomainUri);
            idaKycAuthRequest.setEnv(idaEnv);
            idaKycAuthRequest.setConsentObtained(true);
            idaKycAuthRequest.setIndividualId(kycAuthRequest.getIndividualId());
            idaKycAuthRequest.setTransactionID(kycAuthRequest.getTransactionId());

            IdaKycAuthRequest.AuthRequest authRequest = new IdaKycAuthRequest.AuthRequest();
            authRequest.setTimestamp(IdentityProviderUtil.getUTCDateTime());
            kycAuthRequest.getChallengeList().stream()
                    .filter( auth -> auth != null &&  auth.getAuthFactorType() != null)
                    .forEach( auth -> { buildAuthRequest(auth.getAuthFactorType(), auth.getChallenge(), authRequest, idaKycAuthRequest); });

            KeyGenerator keyGenerator = KeyGeneratorUtils.getKeyGenerator(symmetricAlgorithm, symmetricKeyLength);
            final SecretKey symmetricKey = keyGenerator.generateKey();
            String request = objectMapper.writeValueAsString(authRequest);
            String hexEncodedHash = HMACUtils2.digestAsPlainText(request.getBytes(StandardCharsets.UTF_8));
            idaKycAuthRequest.setRequest(IdentityProviderUtil.b64Encode(CryptoUtil.symmetricEncrypt(symmetricKey,
                    request.getBytes(StandardCharsets.UTF_8))));
            idaKycAuthRequest.setRequestHMAC(IdentityProviderUtil.b64Encode(CryptoUtil.symmetricEncrypt(symmetricKey,
                    hexEncodedHash.getBytes(StandardCharsets.UTF_8))));
            Certificate certificate = getIdaPartnerCertificate();
            idaKycAuthRequest.setThumbprint(IdentityProviderUtil.b64Encode(getCertificateThumbprint(certificate)));
            log.info("IDA certificate thumbprint {}", idaKycAuthRequest.getThumbprint());
            idaKycAuthRequest.setRequestSessionKey(IdentityProviderUtil.b64Encode(
                    cryptoCore.asymmetricEncrypt(certificate.getPublicKey(), symmetricKey.getEncoded())));

            //set signature header, body and invoke kyc auth endpoint
            String requestBody = objectMapper.writeValueAsString(idaKycAuthRequest);
            RequestEntity requestEntity = RequestEntity
                    .post(UriComponentsBuilder.fromUriString(kycAuthUrl).pathSegment(relyingPartyId, clientId).build().toUri())
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .header(SIGNATURE_HEADER_NAME, getRequestSignature(requestBody))
                    .header(AUTHORIZATION_HEADER_NAME, AUTHORIZATION_HEADER_NAME)
                    .body(requestBody);
            ResponseEntity<IdaResponseWrapper<IdaKycAuthResponse>> responseEntity = restTemplate.exchange(requestEntity,
                    new ParameterizedTypeReference<IdaResponseWrapper<IdaKycAuthResponse>>() {});

            if(responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                IdaResponseWrapper<IdaKycAuthResponse> responseWrapper = responseEntity.getBody();
                if(responseWrapper.getResponse() != null && responseWrapper.getResponse().isKycStatus() && responseWrapper.getResponse().getKycToken() != null) {
                    return new KycAuthResult(responseEntity.getBody().getResponse().getKycToken(),
                            responseEntity.getBody().getResponse().getAuthToken());
                }
                log.error("Error response received from IDA KycStatus : {} && Errors: {}",
                        responseWrapper.getResponse().isKycStatus(), responseWrapper.getErrors());
                throw new KycAuthException(CollectionUtils.isEmpty(responseWrapper.getErrors()) ?
                         AUTH_FAILED : responseWrapper.getErrors().get(0).getErrorCode());
            }

            log.error("Error response received from IDA (Kyc-auth) with status : {}", responseEntity.getStatusCode());
        } catch (KycAuthException e) { throw e; } catch (Exception e) {
            log.error("KYC-auth failed with transactionId : {} && clientId : {}", kycAuthRequest.getTransactionId(),
                    clientId, e);
        }
        throw new KycAuthException();
    }

    @Override
    public KycExchangeResult doKycExchange(String relyingPartyId, String clientId, KycExchangeRequest kycExchangeRequest)
            throws KycExchangeException {
        log.info("Started to build kyc-exchange request with transactionId : {} && clientId : {}",
                kycExchangeRequest.getTransactionId(), clientId);
        try {
            IdaKycExchangeRequest idaKycExchangeRequest = new IdaKycExchangeRequest();
            idaKycExchangeRequest.setId(kycExchangeId);
            idaKycExchangeRequest.setVersion(idaVersion);
            idaKycExchangeRequest.setRequestTime(IdentityProviderUtil.getUTCDateTime());
            idaKycExchangeRequest.setKycToken(kycExchangeRequest.getKycToken());
            idaKycExchangeRequest.setConsentObtained(kycExchangeRequest.getAcceptedClaims());
            idaKycExchangeRequest.setLocales(Arrays.asList(kycExchangeRequest.getClaimsLocales()));
            idaKycExchangeRequest.setRespType(KYC_EXCHANGE_TYPE);
            idaKycExchangeRequest.setIndividualId(kycExchangeRequest.getIndividualId());

            //set signature header, body and invoke kyc exchange endpoint
            String requestBody = objectMapper.writeValueAsString(idaKycExchangeRequest);
            RequestEntity requestEntity = RequestEntity
                    .post(UriComponentsBuilder.fromUriString(kycExchangeUrl).pathSegment(relyingPartyId,
                            clientId).build().toUri())
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .header(SIGNATURE_HEADER_NAME, getRequestSignature(requestBody))
                    .header(AUTHORIZATION_HEADER_NAME, AUTHORIZATION_HEADER_NAME)
                    .body(requestBody);
            ResponseEntity<IdaResponseWrapper<IdaKycExchangeResponse>> responseEntity = restTemplate.exchange(requestEntity,
                    new ParameterizedTypeReference<IdaResponseWrapper<IdaKycExchangeResponse>>() {});

            if(responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                IdaResponseWrapper<IdaKycExchangeResponse> responseWrapper = responseEntity.getBody();
                if(responseWrapper.getResponse() != null && responseWrapper.getResponse().getEncryptedKyc() != null) {
                    return new KycExchangeResult(responseWrapper.getResponse().getEncryptedKyc());
                }
                log.error("Errors in response received from IDA Kyc Exchange: {}", responseWrapper.getErrors());
                throw new KycExchangeException(CollectionUtils.isEmpty(responseWrapper.getErrors()) ?
                        AUTH_FAILED : responseWrapper.getErrors().get(0).getErrorCode());
            }

            log.error("Error response received from IDA (Kyc-exchange) with status : {}", responseEntity.getStatusCode());
        } catch (KycExchangeException e) { throw e; } catch (Exception e) {
            log.error("IDA Kyc-exchange failed with clientId : {}", clientId, e);
        }
        throw new KycExchangeException();
    }

    @Override
    public SendOtpResult sendOtp(String relyingPartyId, String clientId, SendOtpRequest sendOtpRequest)  throws SendOtpException {
        log.info("Started to build send-otp request with transactionId : {} && clientId : {}",
                sendOtpRequest.getTransactionId(), clientId);
        try {
            IdaSendOtpRequest idaSendOtpRequest = new IdaSendOtpRequest();
            idaSendOtpRequest.setOtpChannel(sendOtpRequest.getOtpChannels());
            idaSendOtpRequest.setIndividualId(sendOtpRequest.getIndividualId());
            idaSendOtpRequest.setTransactionID(sendOtpRequest.getTransactionId());
            idaSendOtpRequest.setId(sendOtpId);
            idaSendOtpRequest.setVersion(idaVersion);
            idaSendOtpRequest.setRequestTime(IdentityProviderUtil.getUTCDateTime());

            //set signature header, body and invoke kyc exchange endpoint
            String requestBody = objectMapper.writeValueAsString(idaSendOtpRequest);
            RequestEntity requestEntity = RequestEntity
                    .post(UriComponentsBuilder.fromUriString(sendOtpUrl).pathSegment(relyingPartyId, clientId).build().toUri())
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .header(SIGNATURE_HEADER_NAME, getRequestSignature(requestBody))
                    .header(AUTHORIZATION_HEADER_NAME, AUTHORIZATION_HEADER_NAME)
                    .body(requestBody);
            ResponseEntity<IdaSendOtpResponse> responseEntity = restTemplate.exchange(requestEntity,
                            IdaSendOtpResponse.class);

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
        } catch (SendOtpException e) { throw e; } catch (Exception e) {
            log.error("send-otp failed with clientId : {}", clientId, e);
        }
        throw new SendOtpException();
    }

    @Override
    public boolean isSupportedOtpChannel(String channel) {
        return channel != null && otpChannels.contains(channel.toLowerCase());
    }

    @Override
    public List<KycSigningCertificateData> getAllKycSigningCertificates() {
        List<KycSigningCertificateData> certs = new ArrayList<>();
        return certs;
    }

    private void buildAuthRequest(String authFactor, String authChallenge,
                                  IdaKycAuthRequest.AuthRequest authRequest, IdaKycAuthRequest idaKycAuthRequest) {
        log.info("Build kyc-auth request with authFactor : {}",  authFactor);
        switch (authFactor.toUpperCase()) {
            case "OTP" : authRequest.setOtp(authChallenge);
                break;
            case "PIN" : authRequest.setStaticPin(authChallenge);
                break;
            case "BIO" :
                byte[] decodedBio = IdentityProviderUtil.b64Decode(authChallenge);
                try {
                    List<IdaKycAuthRequest.Biometric> biometrics = objectMapper.readValue(decodedBio,
                            new TypeReference<List<IdaKycAuthRequest.Biometric>>(){});
                    authRequest.setBiometrics(biometrics);
                    if(biometrics != null && !biometrics.isEmpty()) {
                        JWT jwt = JWTParser.parse(authRequest.getBiometrics().get(0).getData());
                        idaKycAuthRequest.setTransactionID(jwt.getJWTClaimsSet().getStringClaim("transactionId"));
                    }
                } catch (Exception e) {
                    log.error("Failed to parse biometric capture response", e);
                }
                break;
            default:
                throw new NotImplementedException("KYC auth not implemented");
        }
    }

    private byte[] getCertificateThumbprint(Certificate certificate) {
        try {
            return DigestUtils.sha256(certificate.getEncoded());
        } catch (CertificateEncodingException e) {
            log.error("Failed to get cert thumbprint", e);
        }
        return new byte[]{};
    }

    private String getRequestSignature(String request) {
        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(Constants.IDP_PARTNER_APP_ID);
        jwtSignatureRequestDto.setReferenceId("");
        jwtSignatureRequestDto.setIncludePayload(false);
        jwtSignatureRequestDto.setIncludeCertificate(true);
        jwtSignatureRequestDto.setDataToSign(IdentityProviderUtil.b64Encode(request));
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        log.debug("Request signature ---> {}", responseDto.getJwtSignedData());
        return responseDto.getJwtSignedData();
    }

    private Certificate getIdaPartnerCertificate() throws KycAuthException {
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
}
