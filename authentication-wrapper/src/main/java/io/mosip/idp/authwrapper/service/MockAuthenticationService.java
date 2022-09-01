package io.mosip.idp.authwrapper.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.kernel.core.util.CryptoUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.FileUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.annotation.PostConstruct;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Slf4j
@ConditionalOnProperty(value = "mosip.idp.authn.wrapper.impl", havingValue = "MockAuthenticationService")
@Component
public class MockAuthenticationService implements AuthenticationWrapper {

    @Value("${mosip.idp.authn.mock.impl.persona-repo}")
    private String personaRepoDirPath;

    @Value("${mosip.idp.authn.mock.impl.policy-repo}")
    private String policyRepoDirPath;

    @Value("${mosip.idp.authn.mock.impl.claims-mapping-file}")
    private String claimsMappingFilePath;

    private static final String INDIVIDUAL_FILE_NAME_FORMAT = "%s.json";
    private static final String POLICY_FILE_NAME_FORMAT = "%s_policy.json";
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private DocumentContext mappingDocumentContext;
    private File personaDir;
    private File policyDir;
    private Map<String, String> issuedKycTokens;
    private Map<String, String> clientIDRelayingPartyIdMapping;

    @PostConstruct
    public void setupMockIDA() {
        try {
            issuedKycTokens = new HashMap<>();
            clientIDRelayingPartyIdMapping = new HashMap<>();
            File mappingFile = new File(claimsMappingFilePath);
            mappingDocumentContext = JsonPath.parse((!mappingFile.exists()) ?
                    getClass().getResourceAsStream(claimsMappingFilePath): mappingFile);
            personaDir = new File(personaRepoDirPath);
            policyDir = new File(policyRepoDirPath);
        } catch (Exception e) {
            log.error("Failed to setup MOCK IDA", e);
        }
    }

    @Validated
    @Override
    public KycAuthResponse doKycAuth(@NotBlank String licenseKey, @NotBlank String relayingPartnerId,
                                         @NotBlank String clientId,
                                         @NotNull @Valid KycAuthRequest kycAuthRequest) {
        List<String> authMethods = resolveAuthMethods(relayingPartnerId);
        Map<String, Boolean> authResult = authenticateUser(kycAuthRequest);
        for(String authMethod : authMethods) {
            if(!authResult.getOrDefault(authMethod, false))
                return null;
        }
        String kycToken = UUID.randomUUID().toString();
        String psut = UUID.randomUUID().toString();
        issuedKycTokens.put(kycToken, kycAuthRequest.getIndividualId());
        clientIDRelayingPartyIdMapping.put(clientId, relayingPartnerId);
        KycAuthResponse kycAuthResponse = new KycAuthResponse();
        kycAuthResponse.setKycToken(kycToken);
        kycAuthResponse.setUserAuthToken(psut);
        return kycAuthResponse;
    }

    @Override
    public String doKycExchange(KycExchangeRequest kycExchangeRequest) {
        if(kycExchangeRequest.getKycToken() == null || kycExchangeRequest.getKycToken().isBlank())
            return null;

        String relayingPartyId = clientIDRelayingPartyIdMapping.getOrDefault(kycExchangeRequest.getClientId(), null);
        if(relayingPartyId == null || relayingPartyId.isBlank())
            return null;

        Map<String,Object> kyc = getKycAttributesFromPolicy(relayingPartyId,
                issuedKycTokens.get(kycExchangeRequest.getKycToken()), kycExchangeRequest.getAcceptedClaims());

        try {
            String plainKyc = objectMapper.writeValueAsString(kyc);
            //TODO - encrypt KYC
            return CryptoUtil.encodeToURLSafeBase64(plainKyc.getBytes(StandardCharsets.UTF_8));
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize kyc", e);
            throw new RuntimeException(e);
        }
    }

    @Override
    public SendOtpResult sendOtp(String individualId, String channel) {
        SendOtpResult otpResult = new SendOtpResult();
        otpResult.setStatus(true);
        otpResult.setMessageCode("success");
        return otpResult;
    }

    private Map<String, Boolean> authenticateUser(KycAuthRequest kycAuthRequest) {
        Map<String, Boolean> authResult = new HashMap<>();
        for(AuthChallenge authChallenge : kycAuthRequest.getChallengeList()) {
            switch (authChallenge.getType()) {
                case "PIN" :
                    authResult.put(authChallenge.getType(), authenticateIndividualWithPin(kycAuthRequest.getIndividualId(),
                            authChallenge.getChallenge()));
                    break;
                case "OTP" :
                    authResult.put(authChallenge.getType(), authenticateIndividualWithOTP(kycAuthRequest.getIndividualId(),
                            authChallenge.getChallenge()));
            }
        }
        return authResult;
    }

    private boolean authenticateIndividualWithPin(String individualId, String pin) {
        String filename = String.format(INDIVIDUAL_FILE_NAME_FORMAT, individualId);
        try {
            DocumentContext context = JsonPath.parse(FileUtils.getFile(personaDir, filename));
            String savedPin = context.read("$.pin", String.class);
            return pin.equals(savedPin);
        } catch (IOException e) {
            log.error("Failed to find {} under {}", filename, personaRepoDirPath, e);
        }
        return false;
    }

    private boolean authenticateIndividualWithOTP(String individualId, String OTP) {
        String filename = String.format(INDIVIDUAL_FILE_NAME_FORMAT, individualId);
        try {
            return FileUtils.directoryContains(personaDir, new File(filename)) && OTP.equals("111111");
        } catch (IOException e) {
            log.error("Failed to find {} under {}", filename, personaRepoDirPath, e);
        }
        return false;
    }

    private Map<String, Object> getKycAttributesFromPolicy(String relayingPartyId, String individualId, List<String> claims) {
        Map<String, Object> kyc = new HashMap<>();
        String persona = String.format(INDIVIDUAL_FILE_NAME_FORMAT, individualId);
        String filename = String.format(POLICY_FILE_NAME_FORMAT, relayingPartyId);
        try {
            DocumentContext personaContext = JsonPath.parse(new File(personaDir, persona));
            DocumentContext context = JsonPath.parse(new File(policyDir, filename));
            List<String> allowedAttributes = context.read("$.allowedKycAttributes.*.attributeName");
            for(String claim : claims) {
                String attributeName = mappingDocumentContext.read("$.claims."+claim);
                if(attributeName != null && allowedAttributes.contains(attributeName)) {
                    String path = mappingDocumentContext.read("$.attributes."+attributeName);
                    kyc.put(claim, personaContext.read(path));
                }
            }
        } catch (Exception e) {
            log.error("Failed to load relaying party policy", filename, policyDir, e);
        }
        return kyc;
    }


    private List<String> resolveAuthMethods(String relayingPartyId) {
        //TODO - Need to check the policy to resolve supported auth methods
        return Arrays.asList("PIN");
    }
}
