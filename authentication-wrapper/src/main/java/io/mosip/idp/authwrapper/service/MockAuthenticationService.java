/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.authwrapper.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.dto.Error;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.NotAuthenticatedException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.TokenService;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.json.simple.JSONObject;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.idp.core.util.ErrorConstants.INVALID_INPUT;
import static io.mosip.idp.core.util.ErrorConstants.KYC_FAILED;

@Slf4j
public class MockAuthenticationService implements AuthenticationWrapper {

    private static final String APPLICATION_ID = "MOCK_IDA_SERVICES";
    private static final String CID_CLAIM = "cid";
    private static final String RID_CLAIM = "rid";
    private static final String INDIVIDUAL_FILE_NAME_FORMAT = "%s.json";
    private static final String POLICY_FILE_NAME_FORMAT = "%s_policy.json";
    private static Map<String, List<String>> policyContextMap;
    private static Set<String> REQUIRED_CLAIMS;
    private int tokenExpireInSeconds;
    private SignatureService signatureService;
    private TokenService tokenService;
    private ObjectMapper objectMapper;
    private DocumentContext mappingDocumentContext;
    private File personaDir;
    private File policyDir;


    static {
        REQUIRED_CLAIMS = new HashSet<>();
        REQUIRED_CLAIMS.add("sub");
        REQUIRED_CLAIMS.add("aud");
        REQUIRED_CLAIMS.add("iss");
        REQUIRED_CLAIMS.add("iat");
        REQUIRED_CLAIMS.add("exp");
        REQUIRED_CLAIMS.add(CID_CLAIM);
        REQUIRED_CLAIMS.add(RID_CLAIM);

        policyContextMap = new HashMap<>();
    }

    public MockAuthenticationService(String personaDirPath, String policyDirPath, String claimsMappingFilePath,
                                     int kycTokenExpireSeconds, SignatureService signatureService,
                                     TokenService tokenService, ObjectMapper objectMapper)
            throws IOException {

        this.signatureService = signatureService;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;

        log.info("Started to setup MOCK IDA");
        personaDir = new File(personaDirPath);
        policyDir = new File(policyDirPath);
        mappingDocumentContext = JsonPath.parse(new File(claimsMappingFilePath));
        tokenExpireInSeconds = kycTokenExpireSeconds;
        log.info("Completed MOCK IDA setup with {}, {}, {}", personaDirPath, policyDirPath,
                claimsMappingFilePath);
    }

    @Validated
    @Override
    public ResponseWrapper<KycAuthResponse> doKycAuth(@NotBlank String licenseKey, @NotBlank String relayingPartnerId,
                                         @NotBlank String clientId,
                                         @NotNull @Valid KycAuthRequest kycAuthRequest) {

        ResponseWrapper responseWrapper = new ResponseWrapper<KycAuthResponse>();
        responseWrapper.setErrors(new ArrayList<>());

        List<String> authMethods = resolveAuthMethods(relayingPartnerId);

        boolean result = kycAuthRequest.getChallengeList()
                .stream()
                .allMatch(authChallenge -> authMethods.contains(authChallenge.getType()) &&
                        authenticateUser(kycAuthRequest.getIndividualId(), authChallenge, responseWrapper));

        log.info("Auth methods as per partner policy : {}, KYC auth result : {}",authMethods, result);

        if(!result) {
            return responseWrapper;
        }

        String kycToken = getKycToken(kycAuthRequest.getIndividualId(), clientId, relayingPartnerId);
        KycAuthResponse kycAuthResponse = new KycAuthResponse();
        kycAuthResponse.setKycToken(kycToken);
        kycAuthResponse.setUserAuthToken(UUID.randomUUID().toString());
        responseWrapper.setResponse(kycAuthResponse);
        return responseWrapper;
    }

    private String getKycToken(String individualId, String clientId, String relayingPartyId) {
        JSONObject payload = new JSONObject();
        payload.put(TokenService.ISS, APPLICATION_ID);
        payload.put(TokenService.SUB, individualId);
        payload.put(CID_CLAIM, clientId);
        payload.put(RID_CLAIM, relayingPartyId);
        payload.put(TokenService.AUD, Constants.IDP_SERVICE_APP_ID);
        long issueTime = IdentityProviderUtil.getEpochSeconds();
        payload.put(TokenService.IAT, issueTime);
        payload.put(TokenService.EXP, issueTime +tokenExpireInSeconds);
        return tokenService.getSignedJWT(APPLICATION_ID, payload);
    }

    private JWTClaimsSet verifyAndGetClaims(String kycToken) throws IdPException {
        JWTSignatureVerifyRequestDto signatureVerifyRequestDto = new JWTSignatureVerifyRequestDto();
        signatureVerifyRequestDto.setApplicationId(APPLICATION_ID);
        signatureVerifyRequestDto.setReferenceId("");
        signatureVerifyRequestDto.setJwtSignatureData(kycToken);
        JWTSignatureVerifyResponseDto responseDto = signatureService.jwtVerify(signatureVerifyRequestDto);
        if(!responseDto.isSignatureValid()) {
            log.error("Kyc token verification failed");
            throw new IdPException(INVALID_INPUT);
        }
        try {
            JWT jwt = JWTParser.parse(kycToken);
            JWTClaimsSetVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    .audience(Constants.IDP_SERVICE_APP_ID)
                    .issuer(APPLICATION_ID)
                    .build(), REQUIRED_CLAIMS);
            ((DefaultJWTClaimsVerifier<?>) claimsSetVerifier).setMaxClockSkew(5);
            claimsSetVerifier.verify(jwt.getJWTClaimsSet(), null);
            return jwt.getJWTClaimsSet();
        } catch (Exception e) {
            log.error("kyc token claims verification failed", e);
            throw new NotAuthenticatedException();
        }
    }

    @Override
    public ResponseWrapper<KycExchangeResult> doKycExchange(@NotNull @Valid KycExchangeRequest kycExchangeRequest) {
        ResponseWrapper responseWrapper = new ResponseWrapper<KycAuthResponse>();
        responseWrapper.setErrors(new ArrayList<>());

        try {
            JWTClaimsSet jwtClaimsSet = verifyAndGetClaims(kycExchangeRequest.getKycToken());
            String clientId = jwtClaimsSet.getStringClaim(CID_CLAIM);
            if(!kycExchangeRequest.getClientId().equals(clientId)) {
                responseWrapper.getErrors().add(new Error(INVALID_INPUT, INVALID_INPUT));
                return responseWrapper;
            }

            String relayingPartyId = jwtClaimsSet.getStringClaim(RID_CLAIM);
            Map<String,Object> kyc = getKycAttributesFromPolicy(relayingPartyId, jwtClaimsSet.getSubject(),
                    kycExchangeRequest.getAcceptedClaims());

            String plainKyc = objectMapper.writeValueAsString(kyc);
            //TODO - encrypt KYC
            KycExchangeResult kycExchangeResult = new KycExchangeResult();
            kycExchangeResult.setEncryptedKyc(CryptoUtil.encodeToURLSafeBase64(plainKyc.getBytes(StandardCharsets.UTF_8)));
            responseWrapper.setResponse(kycExchangeResult);
        } catch (Exception e) {
            log.error("Failed to create kyc", e);
            responseWrapper.getErrors().add(new Error(KYC_FAILED, KYC_FAILED));
        }
        return responseWrapper;
    }

    @Override
    public SendOtpResult sendOtp(String individualId, String channel) {
        SendOtpResult otpResult = new SendOtpResult();
        otpResult.setStatus(true);
        otpResult.setMessageCode("success");
        return otpResult;
    }

    private boolean authenticateUser(String individualId, AuthChallenge authChallenge, ResponseWrapper responseWrapper) {
        switch (authChallenge.getType()) {
            case "PIN" :
                return authenticateIndividualWithPin(individualId, authChallenge.getChallenge(), responseWrapper);
            case "OTP" :
                return authenticateIndividualWithOTP(individualId, authChallenge.getChallenge(), responseWrapper);
        }
        responseWrapper.getErrors().add(new Error("mock-ida-004", "Invalid auth challenge type"));
        return false;
    }

    private boolean authenticateIndividualWithPin(String individualId, String pin, ResponseWrapper responseWrapper) {
        String filename = String.format(INDIVIDUAL_FILE_NAME_FORMAT, individualId);
        try {
            DocumentContext context = JsonPath.parse(FileUtils.getFile(personaDir, filename));
            String savedPin = context.read("$.pin", String.class);
            if(!pin.equals(savedPin)) {
                responseWrapper.getErrors().add(new Error("mock-ida-001", "Incorrect PIN"));
                return false;
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to find {}", filename, e);
            responseWrapper.getErrors().add(new Error("mock-ida-002", "Invalid / No identity found"));
        }
        return false;
    }

    private boolean authenticateIndividualWithOTP(String individualId, String OTP, ResponseWrapper responseWrapper) {
        String filename = String.format(INDIVIDUAL_FILE_NAME_FORMAT, individualId);
        try {
            if(!FileUtils.directoryContains(personaDir, new File(filename))) {
                responseWrapper.getErrors().add(new Error("mock-ida-002", "Invalid / No identity found"));
                return false;
            }
            if(!OTP.equals("111111")) {
                responseWrapper.getErrors().add(new Error("mock-ida-003", "Incorrect OTP"));
                return false;
            }
            return true;
        } catch (IOException e) {
            log.error("Failed to find {}", filename, e);
            responseWrapper.getErrors().add(new Error("mock-ida-002", "Invalid / No identity found"));
        }
        return false;
    }

    private Map<String, Object> getKycAttributesFromPolicy(String relayingPartyId, String individualId,
                                                           List<String> claims) throws IdPException {
        String persona = String.format(INDIVIDUAL_FILE_NAME_FORMAT, individualId);
        try {
            DocumentContext personaContext = JsonPath.parse(new File(personaDir, persona));
            List<String> allowedAttributes = getPolicyKycAttributes(relayingPartyId);

            return claims.stream()
                    .distinct()
                    .collect(Collectors.toMap(claim -> claim, claim -> mappingDocumentContext.read("$.claims."+claim)))
                    .entrySet()
                    .stream()
                    .filter( e -> isValidAttributeName((String) e.getValue()) && allowedAttributes.contains((String)e.getValue()))
                    .collect(Collectors.toMap(e -> e.getKey(), e -> getKycValue(personaContext, (String) e.getValue())));

        } catch (Exception e) {
            log.error("Failed to load kyc for : {}", persona, e);
            throw new IdPException(KYC_FAILED);
        }
    }

    private String getKycValue(DocumentContext personaContext, String attributeName) {
        String path = mappingDocumentContext.read("$.attributes."+attributeName);
        //TODO handle address and the multi-lingual value
        return personaContext.read(path).toString();
    }

    private boolean isValidAttributeName(String attribute) {
        return attribute != null && !attribute.isBlank();
    }

    private List<String> getPolicyKycAttributes(String relayingPartyId) throws IOException {
        String filename = String.format(POLICY_FILE_NAME_FORMAT, relayingPartyId);
        if(!policyContextMap.containsKey(relayingPartyId)) {
            DocumentContext context = JsonPath.parse(new File(policyDir, filename));
            List<String> allowedAttributes = context.read("$.allowedKycAttributes.*.attributeName");
            policyContextMap.put(relayingPartyId, allowedAttributes);
        }

        return policyContextMap.get(relayingPartyId);
    }

    private List<String> resolveAuthMethods(String relayingPartyId) {
        //TODO - Need to check the policy to resolve supported auth methods
        return Arrays.asList("PIN");
    }
}
