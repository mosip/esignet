/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.authentication.esignet.integration.service;


import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.authentication.esignet.integration.dto.*;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KeyBindingResult;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KeyBindingException;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.KeyBinder;
import io.mosip.esignet.api.util.ErrorConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.RequestEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ConditionalOnProperty(value = "mosip.esignet.integration.key-binder", havingValue = "IdaKeyBinderImpl")
@Component
@Slf4j
public class IdaKeyBinderImpl implements KeyBinder {

    private static final Map<String, List<String>> supportedFormats = new HashMap<>();
    static {
        supportedFormats.put("OTP", Arrays.asList("alpha-numeric"));
        supportedFormats.put("PIN", Arrays.asList("number"));
        supportedFormats.put("BIO", Arrays.asList("encoded-json"));
        supportedFormats.put("WLA", Arrays.asList("jwt"));
    }

    private static final String PARTNER_ID_HEADER = "partner-id";
    private static final String PARTNER_API_KEY_HEADER = "partner-api-key";
    public static final String SIGNATURE_HEADER_NAME = "signature";
    public static final String AUTHORIZATION_HEADER_NAME = "Authorization";
    public static final String REQUIRED_HEADERS_MISSING = "required_header_missing";

    @Value("${mosip.esignet.binder.ida.key-binding-url}")
    private String keyBinderUrl;

    @Value("${mosip.esignet.binder.ida-binding-id:mosip.identity.keybinding}")
    private String keyBindingId;

    @Value("${mosip.esignet.authenticator.ida-version:1.0}")
    private String idaVersion;

    @Value("${mosip.esignet.authenticator.ida-domainUri}")
    private String idaDomainUri;

    @Value("${mosip.esignet.authenticator.ida-env:Staging}")
    private String idaEnv;

    @Autowired
    private HelperService helperService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RestTemplate restTemplate;

    @Override
    public SendOtpResult sendBindingOtp(String individualId, List<String> otpChannels, Map<String, String> requestHeaders)
            throws SendOtpException {
        log.info("Started to send-binding-otp request");
        try {
            if(StringUtils.isEmpty(requestHeaders.get(PARTNER_ID_HEADER)) || StringUtils.isEmpty(requestHeaders.get(PARTNER_API_KEY_HEADER)))
                throw new SendOtpException(REQUIRED_HEADERS_MISSING);

            IdaSendOtpRequest idaSendOtpRequest = new IdaSendOtpRequest();
            idaSendOtpRequest.setOtpChannel(otpChannels);
            idaSendOtpRequest.setIndividualId(individualId);
            idaSendOtpRequest.setTransactionID(helperService.getTransactionId(HelperService.generateHash(individualId.trim())));
            return helperService.sendOTP(requestHeaders.get(PARTNER_ID_HEADER),
                    requestHeaders.get(PARTNER_API_KEY_HEADER), idaSendOtpRequest);
        } catch (SendOtpException e) {
            throw e;
        } catch (Exception e) {
            log.error("send-binding-otp failed with requestHeaders : {}", requestHeaders, e);
        }
        throw new SendOtpException();
    }

    @Override
    public KeyBindingResult doKeyBinding(String individualId, List<AuthChallenge> challengeList, Map<String, Object> publicKeyJWK,
                                         String bindAuthFactorType, Map<String, String> requestHeaders) throws KeyBindingException {
        log.info("Started to key-binding request for auth-factor-type {}", bindAuthFactorType);
        if(StringUtils.isEmpty(requestHeaders.get(PARTNER_ID_HEADER)) || StringUtils.isEmpty(requestHeaders.get(PARTNER_API_KEY_HEADER)))
            throw new KeyBindingException(REQUIRED_HEADERS_MISSING);

        try {
            KeyBindingRequest keyBindingRequest = new KeyBindingRequest();
            keyBindingRequest.setId(keyBindingId);
            keyBindingRequest.setVersion(idaVersion);
            keyBindingRequest.setRequestTime(HelperService.getUTCDateTime());
            keyBindingRequest.setDomainUri(idaDomainUri);
            keyBindingRequest.setEnv(idaEnv);
            keyBindingRequest.setConsentObtained(true);
            keyBindingRequest.setIndividualId(individualId);
            keyBindingRequest.setTransactionID(helperService.getTransactionId(HelperService.generateHash(individualId.trim())));
            helperService.setAuthRequest(challengeList, keyBindingRequest);

            KeyBindingRequest.IdentityKeyBinding identityKeyBinding = new KeyBindingRequest.IdentityKeyBinding();
            identityKeyBinding.setPublicKeyJWK(publicKeyJWK);
            identityKeyBinding.setAuthFactorType(bindAuthFactorType);
            keyBindingRequest.setIdentityKeyBinding(identityKeyBinding);

            //set signature header, body and invoke kyc auth endpoint
            String requestBody = objectMapper.writeValueAsString(keyBindingRequest);
            RequestEntity requestEntity = RequestEntity
                    .post(UriComponentsBuilder.fromUriString(keyBinderUrl).pathSegment(requestHeaders.getOrDefault(PARTNER_ID_HEADER, PARTNER_ID_HEADER),
                            requestHeaders.getOrDefault(PARTNER_API_KEY_HEADER, PARTNER_API_KEY_HEADER)).build().toUri())
                    .contentType(MediaType.APPLICATION_JSON_UTF8)
                    .header(SIGNATURE_HEADER_NAME, helperService.getRequestSignature(requestBody))
                    .header(AUTHORIZATION_HEADER_NAME, AUTHORIZATION_HEADER_NAME)
                    .body(requestBody);
            ResponseEntity<IdaResponseWrapper<KeyBindingResponse>> responseEntity = restTemplate.exchange(requestEntity,
                    new ParameterizedTypeReference<IdaResponseWrapper<KeyBindingResponse>>() {});

            if(responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
                IdaResponseWrapper<KeyBindingResponse> responseWrapper = responseEntity.getBody();
                if(responseWrapper.getResponse() == null) {
                    log.error("Error response received from IDA (Key-binding) Errors: {}", responseWrapper.getErrors());
                    throw new KeyBindingException(CollectionUtils.isEmpty(responseWrapper.getErrors()) ?
                            ErrorConstants.KEY_BINDING_FAILED : responseWrapper.getErrors().get(0).getErrorCode());
                }

                if(!responseWrapper.getResponse().isBindingAuthStatus()) {
                    log.error("Binding-Auth-status : {}", responseWrapper.getResponse().isBindingAuthStatus());
                    throw new KeyBindingException(ErrorConstants.BINDING_AUTH_FAILED);
                }

                KeyBindingResult keyBindingResult = new KeyBindingResult();
                keyBindingResult.setCertificate(responseWrapper.getResponse().getIdentityCertificate());
                keyBindingResult.setPartnerSpecificUserToken(responseWrapper.getResponse().getAuthToken());
                return keyBindingResult;
            }

            log.error("Error response received from IDA (Key-binding) with status : {}", responseEntity.getStatusCode());
        } catch (KeyBindingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Key-binding failed with headers: {}", requestHeaders, e);
        }
        throw new KeyBindingException(ErrorConstants.KEY_BINDING_FAILED);
    }

    @Override
    public List<String> getSupportedChallengeFormats(String authFactorType) {
        return supportedFormats.getOrDefault(authFactorType, Arrays.asList());
    }

}
