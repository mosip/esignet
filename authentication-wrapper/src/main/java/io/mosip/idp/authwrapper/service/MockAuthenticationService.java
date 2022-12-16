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
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import io.mosip.idp.authwrapper.dto.PathInfo;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.*;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.keymanagerservice.dto.AllCertificatesDataResponseDto;
import io.mosip.kernel.keymanagerservice.dto.CertificateDataResponseDto;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateRequestDto;
import io.mosip.kernel.keymanagerservice.exception.KeymanagerServiceException;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.json.simple.JSONObject;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.text.ParseException;
import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.idp.core.util.ErrorConstants.INVALID_INPUT;
import static io.mosip.idp.core.util.ErrorConstants.SEND_OTP_FAILED;
import static io.mosip.idp.core.util.IdentityProviderUtil.ALGO_SHA3_256;

@Slf4j
public class MockAuthenticationService implements AuthenticationWrapper {

    private static final String APPLICATION_ID = "MOCK_IDA_SERVICES";
    private static final String PSUT_FORMAT = "%s%s";
    private static final String CID_CLAIM = "cid";
    private static final String RID_CLAIM = "rid";
    private static final String PSUT_CLAIM = "psut";
    private static final String SUB_CLAIM = "sub";
    private static final String ISS_CLAIM = "iss";
    private static final String AUD_CLAIM = "aud";
    private static final String IAT_CLAIM = "iat";
    private static final String EXP_CLAIM = "exp";
    private static final String INDIVIDUAL_FILE_NAME_FORMAT = "%s.json";
    private static final String POLICY_FILE_NAME_FORMAT = "%s_policy.json";
    private static Map<String, List<String>> policyContextMap;
    private static Map<String, RSAKey> relyingPartyPublicKeys;
    private static Map<String, String> localesMapping;
    private static Set<String> REQUIRED_CLAIMS;
    private int tokenExpireInSeconds;
    private SignatureService signatureService;
    private ObjectMapper objectMapper;
    private KeymanagerService keymanagerService;
    private DocumentContext mappingDocumentContext;
    private File personaDir;
    private File policyDir;

    private boolean encryptKyc;


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
        relyingPartyPublicKeys = new HashMap<>();
    }

    public MockAuthenticationService(String personaDirPath, String policyDirPath, String claimsMappingFilePath,
                                     int kycTokenExpireSeconds, boolean encryptKyc, SignatureService signatureService,
                                     ObjectMapper objectMapper, KeymanagerService keymanagerService) throws IOException {
        this.signatureService = signatureService;
        this.objectMapper = objectMapper;
        this.keymanagerService = keymanagerService;
        this.encryptKyc = encryptKyc;

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
    public KycAuthResult doKycAuth(@NotBlank String relyingPartyId, @NotBlank String clientId,
                                                    @NotNull @Valid KycAuthRequest kycAuthRequest) throws KycAuthException {
        List<String> authMethods = resolveAuthMethods(relyingPartyId);
        boolean result = kycAuthRequest.getChallengeList()
                .stream()
                .allMatch(authChallenge -> authMethods.contains(authChallenge.getAuthFactorType()) &&
                        authenticateUser(kycAuthRequest.getIndividualId(), authChallenge));
        log.info("Auth methods as per partner policy : {}, KYC auth result : {}",authMethods, result);
        if(!result) {
            throw new KycAuthException(ErrorConstants.AUTH_FAILED);
        }

        String psut;
        try {
            psut = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256,
                    String.format(PSUT_FORMAT, kycAuthRequest.getIndividualId(), relyingPartyId));
        } catch (IdPException e) {
            log.error("Failed to generate PSUT",authMethods, e);
            throw new KycAuthException("mock-ida-006", "Failed to generate Partner specific user token");
        }
        String kycToken = getKycToken(kycAuthRequest.getIndividualId(), clientId, relyingPartyId, psut);
        KycAuthResult kycAuthResult = new KycAuthResult();
        kycAuthResult.setKycToken(kycToken);
        kycAuthResult.setPartnerSpecificUserToken(psut);
        return kycAuthResult;
    }



    @Override
    public KycExchangeResult doKycExchange(@NotBlank String relyingPartyId, @NotBlank String clientId,
                                                            @NotNull @Valid KycExchangeRequest kycExchangeRequest)
            throws KycExchangeException {
        log.info("Accepted claims : {} and locales : {}", kycExchangeRequest.getAcceptedClaims(), kycExchangeRequest.getClaimsLocales());
        try {
            JWTClaimsSet jwtClaimsSet = verifyAndGetClaims(kycExchangeRequest.getKycToken());
            log.info("KYC token claim set : {}", jwtClaimsSet);
            String clientIdClaim = jwtClaimsSet.getStringClaim(CID_CLAIM);
            if(!clientId.equals(clientIdClaim) || jwtClaimsSet.getStringClaim(PSUT_CLAIM) == null) {
                throw new KycExchangeException("mock-ida-008", "Provided invalid KYC token");
            }
            Map<String,Object> kyc = buildKycDataBasedOnPolicy(relyingPartyId, jwtClaimsSet.getSubject(),
                    kycExchangeRequest.getAcceptedClaims(), kycExchangeRequest.getClaimsLocales());
            kyc.put(SUB_CLAIM, jwtClaimsSet.getStringClaim(PSUT_CLAIM));
            KycExchangeResult kycExchangeResult = new KycExchangeResult();
            kycExchangeResult.setEncryptedKyc(this.encryptKyc ? getJWE(relyingPartyId, signKyc(kyc)) : signKyc(kyc));
            return kycExchangeResult;
        } catch (Exception e) {
            log.error("Failed to create kyc", e);
        }
        throw new KycExchangeException("mock-ida-005", "Failed to build kyc data");
    }

    private String getJWE(String relyingPartyId, String signedJwt) throws Exception {
        JsonWebEncryption jsonWebEncryption = new JsonWebEncryption();
        jsonWebEncryption.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.RSA_OAEP_256);
        jsonWebEncryption.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_256_GCM);
        jsonWebEncryption.setPayload(signedJwt);
        jsonWebEncryption.setContentTypeHeaderValue("JWT");
        RSAKey rsaKey = getRelyingPartyPublicKey(relyingPartyId);
        jsonWebEncryption.setKey(rsaKey.toPublicKey());
        jsonWebEncryption.setKeyIdHeaderValue(rsaKey.getKeyID());
        return jsonWebEncryption.getCompactSerialization();
    }

    private RSAKey getRelyingPartyPublicKey(String relyingPartyId) throws IOException, ParseException {
        if(!relyingPartyPublicKeys.containsKey(relyingPartyId)) {
            String filename = String.format(POLICY_FILE_NAME_FORMAT, relyingPartyId);
            DocumentContext context = JsonPath.parse(new File(policyDir, filename));
            Map<String, String> publicKey = context.read("$.publicKey");
            relyingPartyPublicKeys.put(relyingPartyId,
                    RSAKey.parse(new JSONObject(publicKey).toJSONString()));
        }
        return relyingPartyPublicKeys.get(relyingPartyId);
    }

    private String getKycToken(String individualId, String clientId, String relyingPartyId, @NotBlank String psut) {
        Map<String,Object> payload = new HashMap<>();
        payload.put(ISS_CLAIM, APPLICATION_ID);
        payload.put(SUB_CLAIM, individualId);
        payload.put(CID_CLAIM, clientId);
        payload.put(PSUT_CLAIM, psut);
        payload.put(RID_CLAIM, relyingPartyId);
        payload.put(AUD_CLAIM, Constants.IDP_SERVICE_APP_ID);
        long issueTime = IdentityProviderUtil.getEpochSeconds();
        payload.put(IAT_CLAIM, issueTime);
        payload.put(EXP_CLAIM, issueTime +tokenExpireInSeconds);
        try {
            return signKyc(payload);
        } catch (JsonProcessingException e) {
            log.error("Failed to generate kyc token", e);
        }
        return null;
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

    private String signKyc(Map<String,Object> kyc) throws JsonProcessingException {
        setupMockIDAKey();
        String payload = objectMapper.writeValueAsString(kyc);
        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(APPLICATION_ID);
        jwtSignatureRequestDto.setReferenceId("");
        jwtSignatureRequestDto.setIncludePayload(true);
        jwtSignatureRequestDto.setIncludeCertificate(false);
        jwtSignatureRequestDto.setDataToSign(IdentityProviderUtil.b64Encode(payload));
        jwtSignatureRequestDto.setIncludeCertHash(false);
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }

    private void setupMockIDAKey() {
        KeyPairGenerateRequestDto mockIDAMasterKeyRequest = new KeyPairGenerateRequestDto();
        mockIDAMasterKeyRequest.setApplicationId(APPLICATION_ID);
        keymanagerService.generateMasterKey("CSR", mockIDAMasterKeyRequest);
        log.info("===================== MOCK_IDA_SERVICES MASTER KEY SETUP COMPLETED ========================");
    }

    @Override
    public SendOtpResult sendOtp(String relyingPartyId, String clientId, SendOtpRequest sendOtpRequest)
            throws SendOtpException {
        String filename = String.format(INDIVIDUAL_FILE_NAME_FORMAT, sendOtpRequest.getIndividualId());
        try {
            if(FileUtils.directoryContains(personaDir, new File(personaDir.getAbsolutePath(), filename))) {
                DocumentContext context = JsonPath.parse(FileUtils.getFile(personaDir, filename));
                String maskedEmailId = context.read("$.maskedEmailId", String.class);
                String maskedMobile = context.read("$.maskedMobile", String.class);
                return new SendOtpResult(sendOtpRequest.getTransactionId(), maskedEmailId, maskedMobile);
            }

            log.error("Provided identity is not found {}", sendOtpRequest.getIndividualId());
            throw new SendOtpException("mock-ida-001");
        } catch (IOException e) {
            log.error("authenticateIndividualWithPin failed {}", filename, e);
        }
        throw new SendOtpException(SEND_OTP_FAILED);
    }

    @Override
    public boolean isSupportedOtpChannel(String channel) {
        return channel != null && ("email".equalsIgnoreCase(channel) || "mobile".equalsIgnoreCase(channel));
    }

    @Override
    public List<KycSigningCertificateData> getAllKycSigningCertificates() {
        List<KycSigningCertificateData> certs = new ArrayList<>();
        AllCertificatesDataResponseDto allCertificatesDataResponseDto = keymanagerService.getAllCertificates(APPLICATION_ID,
                Optional.empty());
        for(CertificateDataResponseDto dto : allCertificatesDataResponseDto.getAllCertificates()) {
            certs.add(new KycSigningCertificateData(dto.getKeyId(), dto.getCertificateData(),
                    dto.getExpiryAt(), dto.getIssuedAt()));
        }
        return certs;
    }

    private boolean authenticateUser(String individualId, AuthChallenge authChallenge) {
        switch (authChallenge.getAuthFactorType()) {
            case "PIN" :
                return authenticateIndividualWithPin(individualId, authChallenge.getChallenge());
            case "OTP" :
                return authenticateIndividualWithOTP(individualId, authChallenge.getChallenge());
            case "BIO" :
                return authenticateIndividualWithBio(individualId);
        }
        return false;
    }

    private boolean authenticateIndividualWithPin(String individualId, String pin) {
        String filename = String.format(INDIVIDUAL_FILE_NAME_FORMAT, individualId);
        try {
            DocumentContext context = JsonPath.parse(FileUtils.getFile(personaDir, filename));
            String savedPin = context.read("$.pin", String.class);
            return pin.equals(savedPin);
        } catch (IOException e) {
            log.error("authenticateIndividualWithPin failed {}", filename, e);
        }
        return false;
    }

    private boolean authenticateIndividualWithOTP(String individualId, String OTP) {
        String filename = String.format(INDIVIDUAL_FILE_NAME_FORMAT, individualId);
        try {
            return FileUtils.directoryContains(personaDir, new File(personaDir.getAbsolutePath(), filename))
                    && OTP.equals("111111");
        } catch (IOException e) {
            log.error("authenticateIndividualWithOTP failed {}", filename, e);
        }
        return false;
    }

    private boolean authenticateIndividualWithBio(String individualId) {
        String filename = String.format(INDIVIDUAL_FILE_NAME_FORMAT, individualId);
        try {
            return FileUtils.directoryContains(personaDir, new File(personaDir.getAbsolutePath(), filename));
        } catch (IOException e) {
            log.error("authenticateIndividualWithBio failed {}", filename, e);
        }
        return false;
    }

    private Map<String, Object> buildKycDataBasedOnPolicy(String relyingPartyId, String individualId,
                                                           List<String> claims, String[] locales) {
        Map<String, Object> kyc = new HashMap<>();
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
                Map<String, String> langResult = Arrays.stream( (locales == null || locales.length == 0) ? new String[]{"en"} : locales)
                         .filter( locale -> getKycValue(personaContext, entry.getValue(), locale) != null)
                        .collect(Collectors.toMap(locale -> locale,
                                locale -> getKycValue(personaContext, entry.getValue(), locale)));

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
        return kyc;
    }

    private String getKycValue(DocumentContext persona, PathInfo pathInfo, String locale) {
        try {
            String path =  pathInfo.getPath();
            String jsonPath = locale == null ? path : path.replace("_LOCALE_",
                    getLocalesMapping(locale, pathInfo.getDefaultLocale()));
            var value = persona.read(jsonPath);
            if(value instanceof List)
                return (String) ((List)value).get(0);
            return (String) value;
        } catch (Exception ex) {
            log.error("Failed to get kyc value with path {}", pathInfo, ex);
        }
        return null;
    }

    private String  getLocalesMapping(String locale, String defaultLocale) {
        if(localesMapping == null || localesMapping.isEmpty()) {
            localesMapping = mappingDocumentContext.read("$.locales");
        }
        return localesMapping.getOrDefault(locale, defaultLocale);
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
        return Arrays.asList("PIN", "OTP", "BIO");
    }
}
