/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.authwrapper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
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
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.spi.TokenService;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateRequestDto;
import io.mosip.kernel.keymanagerservice.exception.KeymanagerServiceException;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.Data;
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

import static io.mosip.idp.core.spi.TokenService.SUB;
import static io.mosip.idp.core.util.ErrorConstants.INVALID_INPUT;
import static io.mosip.idp.core.util.IdentityProviderUtil.ALGO_SHA3_256;

@Slf4j
public class MockAuthenticationService implements AuthenticationWrapper {

    private static final String APPLICATION_ID = "MOCK_IDA_SERVICES";
    private static final String PSUT_FORMAT = "%s%s";
    private static final String CID_CLAIM = "cid";
    private static final String RID_CLAIM = "rid";
    private static final String PSUT_CLAIM = "psut";
    private static final String INDIVIDUAL_FILE_NAME_FORMAT = "%s.json";
    private static final String POLICY_FILE_NAME_FORMAT = "%s_policy.json";
    private static final String KEY_FILE_NAME_FORMAT = "%s_keys.json";
    private static Map<String, List<String>> policyContextMap;
    private static Map<String, String> localesMapping;
    private static Set<String> REQUIRED_CLAIMS;
    private int tokenExpireInSeconds;
    private SignatureService signatureService;
    private ClientManagementService clientManagementService;
    private TokenService tokenService;
    private ObjectMapper objectMapper;
    private KeymanagerService keymanagerService;
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
                                     TokenService tokenService, ObjectMapper objectMapper,
                                     ClientManagementService clientManagementService,
                                     KeymanagerService keymanagerService) throws IOException {
        this.signatureService = signatureService;
        this.tokenService = tokenService;
        this.objectMapper = objectMapper;
        this.clientManagementService = clientManagementService;
        this.keymanagerService = keymanagerService;

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
    public ResponseWrapper<KycAuthResponse> doKycAuth(@NotBlank String licenseKey, @NotBlank String relyingPartyId,
                                         @NotBlank String clientId,
                                         @NotNull @Valid KycAuthRequest kycAuthRequest) {

        ResponseWrapper responseWrapper = new ResponseWrapper<KycAuthResponse>();
        responseWrapper.setErrors(new ArrayList<>());

        List<String> authMethods = resolveAuthMethods(relyingPartyId);

        boolean result = kycAuthRequest.getChallengeList()
                .stream()
                .allMatch(authChallenge -> authMethods.contains(authChallenge.getAuthFactorType()) &&
                        authenticateUser(kycAuthRequest.getIndividualId(), authChallenge, responseWrapper));

        log.info("Auth methods as per partner policy : {}, KYC auth result : {}",authMethods, result);

        if(!result) {
            return responseWrapper;
        }

        String psut;
        try {
            psut = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256,
                    String.format(PSUT_FORMAT, kycAuthRequest.getIndividualId(), relyingPartyId));
        } catch (IdPException e) {
            responseWrapper.getErrors().add(new Error("mock-ida-006", "Failed to generate Partner specific user token"));
            return responseWrapper;
        }
        String kycToken = getKycToken(kycAuthRequest.getIndividualId(), clientId, relyingPartyId, psut);
        KycAuthResponse kycAuthResponse = new KycAuthResponse();
        kycAuthResponse.setKycToken(kycToken);
        kycAuthResponse.setPartnerSpecificUserToken(psut);
        responseWrapper.setResponse(kycAuthResponse);
        return responseWrapper;
    }



    @Override
    public ResponseWrapper<KycExchangeResult> doKycExchange(@NotNull @Valid KycExchangeRequest kycExchangeRequest) {
        ResponseWrapper responseWrapper = new ResponseWrapper<KycAuthResponse>();
        responseWrapper.setErrors(new ArrayList<>());

        log.info("Accepted claims : {} and locales : {}", kycExchangeRequest.getAcceptedClaims(), kycExchangeRequest.getClaimsLocales());

        try {
            JWTClaimsSet jwtClaimsSet = verifyAndGetClaims(kycExchangeRequest.getKycToken());
            log.info("KYC token claim set : {}", jwtClaimsSet);
            String clientId = jwtClaimsSet.getStringClaim(CID_CLAIM);
            if(!kycExchangeRequest.getClientId().equals(clientId) || jwtClaimsSet.getStringClaim(PSUT_CLAIM) == null) {
                responseWrapper.getErrors().add(new Error(INVALID_INPUT, INVALID_INPUT));
                return responseWrapper;
            }

            String relyingPartyId = jwtClaimsSet.getStringClaim(RID_CLAIM);
            Map<String,String> kyc = buildKycDataBasedOnPolicy(relyingPartyId, jwtClaimsSet.getSubject(),
                    kycExchangeRequest.getAcceptedClaims(), kycExchangeRequest.getClaimsLocales());

            kyc.put(SUB, jwtClaimsSet.getStringClaim(PSUT_CLAIM));

            KycExchangeResult kycExchangeResult = new KycExchangeResult();
            kycExchangeResult.setEncryptedKyc(signKyc(kyc)); //TODO encrypt with relying party public key
            responseWrapper.setResponse(kycExchangeResult);
        } catch (Exception e) {
            log.error("Failed to create kyc", e);
            responseWrapper.getErrors().add(new Error("mock-ida-005", "Failed to build kyc data"));
        }
        return responseWrapper;
    }

    private String getKycToken(String individualId, String clientId, String relyingPartyId, @NotBlank String psut) {
        JSONObject payload = new JSONObject();
        payload.put(TokenService.ISS, APPLICATION_ID);
        payload.put(SUB, individualId);
        payload.put(CID_CLAIM, clientId);
        payload.put(PSUT_CLAIM, psut);
        payload.put(RID_CLAIM, relyingPartyId);
        payload.put(TokenService.AUD, Constants.IDP_SERVICE_APP_ID);
        long issueTime = IdentityProviderUtil.getEpochSeconds();
        payload.put(TokenService.IAT, issueTime);
        payload.put(TokenService.EXP, issueTime +tokenExpireInSeconds);
        setupMockIDAKey();
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

    private String signKyc(Map<String,String> kyc) throws JsonProcessingException {
        setupMockIDAKey();
        String payload = objectMapper.writeValueAsString(kyc);
        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(APPLICATION_ID);
        jwtSignatureRequestDto.setReferenceId("");
        jwtSignatureRequestDto.setIncludePayload(true);
        jwtSignatureRequestDto.setIncludeCertificate(true);
        jwtSignatureRequestDto.setDataToSign(IdentityProviderUtil.B64Encode(payload));
        jwtSignatureRequestDto.setIncludeCertHash(true);
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }

    private void setupMockIDAKey() {
        try {
            keymanagerService.getCertificate(APPLICATION_ID, Optional.empty());
            //Nothing to do as key is already present.
            return;
        } catch (KeymanagerServiceException ex) {
            log.error("Failed while getting MOCK IDA signing certificate", ex);
        }
        KeyPairGenerateRequestDto mockIDAMasterKeyRequest = new KeyPairGenerateRequestDto();
        mockIDAMasterKeyRequest.setApplicationId(APPLICATION_ID);
        keymanagerService.generateMasterKey("CSR", mockIDAMasterKeyRequest);
        log.info("===================== MOCK_IDA_SERVICES MASTER KEY SETUP COMPLETED ========================");
    }

    @Override
    public SendOtpResult sendOtp(String individualId, String channel) {
        SendOtpResult otpResult = new SendOtpResult();
        otpResult.setStatus(true);
        otpResult.setMessageCode("success");
        return otpResult;
    }

    private boolean authenticateUser(String individualId, AuthChallenge authChallenge, ResponseWrapper responseWrapper) {
        switch (authChallenge.getAuthFactorType()) {
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

    private Map<String, String> buildKycDataBasedOnPolicy(String relyingPartyId, String individualId,
                                                           List<String> claims, String[] locales) {
        Map<String, String> kyc = new HashMap<>();
        String persona = String.format(INDIVIDUAL_FILE_NAME_FORMAT, individualId);
        try {
            DocumentContext personaContext = JsonPath.parse(new File(personaDir, persona));
            List<String> allowedAttributes = getPolicyKycAttributes(relyingPartyId);

            log.info("Allowed kyc attributes as per policy : {}", allowedAttributes);

            Map<String, PathInfo> kycAttributeMap = claims.stream()
                    .distinct()
                    .collect(Collectors.toMap(claim -> claim, claim -> mappingDocumentContext.read("$.claims."+claim)))
                    .entrySet()
                    .stream()
                    .filter( e -> isValidAttributeName((String) e.getValue()) && allowedAttributes.contains((String)e.getValue()))
                    .collect(Collectors.toMap(e -> e.getKey(), e -> mappingDocumentContext.read("$.attributes."+e.getValue(), PathInfo.class)))
                    .entrySet()
                    .stream()
                    .filter( e -> e.getValue() != null && e.getValue().getPath() != null && !e.getValue().getPath().isBlank() )
                    .collect(Collectors.toMap(e -> e.getKey(), e -> e.getValue()));

            log.info("Final kyc attribute map : {}", kycAttributeMap);

            for(Map.Entry<String, PathInfo> entry : kycAttributeMap.entrySet()) {
                String path = entry.getValue().getPath();

                Map<String, String> langResult = Arrays.stream(locales)
                        .filter( locale -> getKycValue(personaContext, path, locale) != null)
                        .collect(Collectors.toMap(locale -> locale, locale -> getKycValue(personaContext, path, locale)));

                if(langResult.isEmpty())
                    continue;

                if(langResult.size() == 1)
                    kyc.put(entry.getKey(), langResult.values().stream().findFirst().get());
                else {
                    //Handling the language tagging based on the requested claims_locales
                    kyc.putAll(langResult.entrySet()
                            .stream()
                            .collect(Collectors.toMap(e -> entry.getKey()+"#"+e.getKey(), e-> e.getValue())));
                }
            }
        } catch (Exception e) {
            log.error("Failed to load kyc for : {}", persona, e);
        }
        log.info("buildKycDataBasedOnPolicy :{}", kyc);
        return kyc;
    }

    private String getKycValue(DocumentContext persona, String path, String locale) {
        try {
            String jsonPath = locale == null ? path : path.replace("_LOCALE_", getLocalesMapping(locale));
            var value = persona.read(jsonPath);
            if(value instanceof List)
                return (String) ((List)value).get(0);
            return (String) value;
        } catch (Exception ex) {
            log.error("Failed to get kyc value with path {}", path, ex);
        }
        return null;
    }

    private String  getLocalesMapping(String locale) {
        if(localesMapping == null || localesMapping.isEmpty()) {
            localesMapping = mappingDocumentContext.read("$.locales");
        }
        return localesMapping.getOrDefault(locale, "");
    }

    private boolean isValidAttributeName(String attribute) {
        return attribute != null && !attribute.isBlank();
    }

    private List<String> getPolicyKycAttributes(String relyingPartyId) throws IOException {
        String filename = String.format(POLICY_FILE_NAME_FORMAT, relyingPartyId);
        if(!policyContextMap.containsKey(relyingPartyId)) {
            DocumentContext context = JsonPath.parse(new File(policyDir, filename));
            List<String> allowedAttributes = context.read("$.allowedKycAttributes.*.attributeName");
            policyContextMap.put(relyingPartyId, allowedAttributes);
        }

        return policyContextMap.get(relyingPartyId);
    }

    private List<String> resolveAuthMethods(String relyingPartyId) {
        //TODO - Need to check the policy to resolve supported auth methods
        return Arrays.asList("PIN");
    }
}

@Data
class PathInfo {
    String path;
    String defaultLocale;
}
