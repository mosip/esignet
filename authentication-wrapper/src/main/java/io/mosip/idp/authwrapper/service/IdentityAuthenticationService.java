/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.authwrapper.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.authwrapper.dto.IdaKycAuthRequest;
import io.mosip.idp.authwrapper.dto.IdaKycExchangeRequest;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.dto.Error;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.HMACUtils2;
import io.mosip.kernel.crypto.jce.core.CryptoCore;
import io.mosip.kernel.keygenerator.bouncycastle.util.KeyGeneratorUtils;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.MessageSource;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static io.mosip.idp.core.util.ErrorConstants.UNKNOWN_ERROR;


@ConditionalOnProperty(value = "mosip.idp.authn.wrapper.impl", havingValue = "IdentityAuthenticationService")
@Component
@Slf4j
public class IdentityAuthenticationService implements AuthenticationWrapper {

    public static final String IDENTITY = "mosip.identity.auth.internal";
    public static final String KYC_EXCHANGE_PATH_NAME = "oidc";
    public static final String SIGNATURE_HEADER_NAME = "signature";

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
    MessageSource messageSource;

    @Override
    public ResponseWrapper<KycAuthResponse> doKycAuth(String relyingPartyId, String clientId, KycAuthRequest kycAuthRequest) {
        log.info("Started to build kyc-auth request with transactionId : {} && clientId : {}",
                kycAuthRequest.getTransactionId(), clientId);
        try {
            IdaKycAuthRequest idaKycAuthRequest = new IdaKycAuthRequest();
            idaKycAuthRequest.setId(IDENTITY);
            idaKycAuthRequest.setVersion(idaVersion);
            idaKycAuthRequest.setRequestTime(IdentityProviderUtil.getUTCDataTime());
            idaKycAuthRequest.setDomainUri(idaDomainUri);
            idaKycAuthRequest.setEnv(idaEnv);
            idaKycAuthRequest.setConsentObtained(true);
            idaKycAuthRequest.setIndividualId(kycAuthRequest.getIndividualId());
            idaKycAuthRequest.setTransactionID(kycAuthRequest.getTransactionId());

            IdaKycAuthRequest.AuthRequest authRequest = new IdaKycAuthRequest.AuthRequest();
            kycAuthRequest.getChallengeList().stream()
                    .filter( auth -> auth != null &&  auth.getAuthFactorType() != null)
                    .forEach( auth -> { buildAuthRequest(auth.getAuthFactorType(), auth.getChallenge(), authRequest); });

            KeyGenerator keyGenerator = KeyGeneratorUtils.getKeyGenerator(symmetricAlgorithm, symmetricKeyLength);
            final SecretKey symmetricKey = keyGenerator.generateKey();
            String request = objectMapper.writeValueAsString(authRequest);
            String hexEncodedHash = HMACUtils2.digestAsPlainText(request.getBytes(StandardCharsets.UTF_8));
            idaKycAuthRequest.setRequest(CryptoUtil.encodeToURLSafeBase64(CryptoUtil.symmetricEncrypt(symmetricKey,
                    request.getBytes(StandardCharsets.UTF_8))));
            idaKycAuthRequest.setRequestHMAC(CryptoUtil.encodeToURLSafeBase64(CryptoUtil.symmetricEncrypt(symmetricKey,
                    hexEncodedHash.getBytes(StandardCharsets.UTF_8))));
            KeyPairGenerateResponseDto responseDto = keymanagerService.getCertificate(Constants.IDP_PARTNER_APP_ID, Optional.empty());
            Certificate certificate = keymanagerUtil.convertToCertificate(responseDto.getCertificate());
            idaKycAuthRequest.setThumbprint(CryptoUtil.encodeToURLSafeBase64(getCertificateThumbprint(certificate)));
            idaKycAuthRequest.setRequestSessionKey(CryptoUtil.encodeToURLSafeBase64(
                    cryptoCore.asymmetricEncrypt(certificate.getPublicKey(), symmetricKey.getEncoded())));

            //set signature header, body and invoke kyc auth endpoint
            String requestBody = objectMapper.writeValueAsString(idaKycAuthRequest);
            RequestEntity requestEntity = RequestEntity
                    .post(UriComponentsBuilder.fromUriString(kycAuthUrl).pathSegment(relyingPartyId, clientId).build().toUri())
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .body(requestBody);
            requestEntity.getHeaders().add(SIGNATURE_HEADER_NAME, getRequestSignature(requestBody));
            ResponseEntity<ResponseWrapper<KycAuthResponse>> responseEntity = restTemplate.exchange(requestEntity,
                    new ParameterizedTypeReference<ResponseWrapper<KycAuthResponse>>() {});
            if(responseEntity.getBody() != null) {
                log.info("Errors in response received from IDA : {}", responseEntity.getBody().getErrors());
                return responseEntity.getBody();
            }

        } catch (Exception e) {
            log.error("KYC-auth failed with transactionId : {} && clientId : {}", kycAuthRequest.getTransactionId(), clientId, e);
        }
        ResponseWrapper<KycAuthResponse> responseWrapper = new ResponseWrapper<KycAuthResponse>();
        responseWrapper.setErrors(new ArrayList<>());
        responseWrapper.getErrors().add(new Error(UNKNOWN_ERROR, messageSource.getMessage(UNKNOWN_ERROR, null, null)));
        return responseWrapper;
    }

    @Override
    public ResponseWrapper<KycExchangeResult> doKycExchange(KycExchangeRequest kycExchangeRequest) {
        try {
            RequestWrapper<IdaKycExchangeRequest> requestWrapper = new RequestWrapper<>();
            IdaKycExchangeRequest idaKycExchangeRequest = new IdaKycExchangeRequest();
            idaKycExchangeRequest.setKycToken(kycExchangeRequest.getKycToken());
            idaKycExchangeRequest.setConsentObtained(kycExchangeRequest.getAcceptedClaims());
            idaKycExchangeRequest.setLocales(Arrays.asList(kycExchangeRequest.getClaimsLocales()));
            requestWrapper.setId(IDENTITY);
            requestWrapper.setVersion(idaVersion);
            requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDataTime());
            requestWrapper.setRequest(idaKycExchangeRequest);

            //set signature header, body and invoke kyc exchange endpoint
            String requestBody = objectMapper.writeValueAsString(requestWrapper);
            RequestEntity requestEntity = RequestEntity
                    .post(UriComponentsBuilder.fromUriString(kycExchangeUrl).pathSegment(kycExchangeRequest.getRelyingPartyId(),
                            kycExchangeRequest.getClientId(), KYC_EXCHANGE_PATH_NAME).build().toUri())
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .body(requestBody);
            requestEntity.getHeaders().add(SIGNATURE_HEADER_NAME, getRequestSignature(requestBody));
            ResponseEntity<ResponseWrapper<KycExchangeResult>> responseEntity = restTemplate.exchange(requestEntity,
                    new ParameterizedTypeReference<ResponseWrapper<KycExchangeResult>>() {});

            if(responseEntity.getBody() != null) {
                log.info("Errors in response received from IDA : {}", responseEntity.getBody().getErrors());
                return responseEntity.getBody();
            }

        } catch (Exception e) {
            log.error("KYC-exchange failed with clientId : {}", kycExchangeRequest.getClientId(), e);
        }
        ResponseWrapper<KycExchangeResult> responseWrapper = new ResponseWrapper<KycExchangeResult>();
        responseWrapper.setErrors(new ArrayList<>());
        responseWrapper.getErrors().add(new Error(UNKNOWN_ERROR, messageSource.getMessage(UNKNOWN_ERROR, null, null)));
        return responseWrapper;
    }

    @Override
    public SendOtpResult sendOtp(String individualId, String channel) {
        throw new NotImplementedException("Send OTP not implemented");
    }

    private void buildAuthRequest(String authFactor, String authChallenge,
                                  IdaKycAuthRequest.AuthRequest authRequest) {
        log.info("Build kyc-auth request with authFactor : {}",  authFactor);
        switch (authFactor.toUpperCase()) {
            case "OTP" : authRequest.setOtp(authChallenge);
                break;
            case "PIN" : authRequest.setStaticPin(authChallenge);
                break;
            case "BIO" :
                byte[] decodedBio = IdentityProviderUtil.B64Decode(authChallenge);
                try {
                    List<IdaKycAuthRequest.Biometric> biometrics = objectMapper.readValue(decodedBio,
                            new TypeReference<List<IdaKycAuthRequest.Biometric>>(){});
                    authRequest.setBiometrics(biometrics);
                } catch (IOException e) {
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
        jwtSignatureRequestDto.setIncludeCertificate(false);
        jwtSignatureRequestDto.setDataToSign(IdentityProviderUtil.B64Encode(request));
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }
}
