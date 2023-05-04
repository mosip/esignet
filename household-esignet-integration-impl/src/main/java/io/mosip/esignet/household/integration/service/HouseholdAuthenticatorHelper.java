package io.mosip.esignet.household.integration.service;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KycAuthDto;
import io.mosip.esignet.api.dto.KycAuthResult;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.household.integration.entity.HouseholdView;
import io.mosip.esignet.household.integration.repository.HouseholdRepository;
import io.mosip.esignet.household.integration.util.HelperUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.*;

import static io.mosip.esignet.household.integration.util.ErrorConstants.*;

@Component
@Slf4j
public class HouseholdAuthenticatorHelper {

    private static final String PSUT_FORMAT = "%s%s";

    @Value("${mosip.esignet.household.pattern.regex}")
    private String regexPattern;

    private static final String PASSWORD="PWD";
    @PostConstruct
    public void initialize() {
        log.info("Started to setup HouseHold IDA");
    }

    private static final Map<String, List<String>> supportedKycAuthFormats = new HashMap<>();

    static {
       // supportedKycAuthFormats.put("OTP", List.of("alpha-numeric"));
       // supportedKycAuthFormats.put("PIN", List.of("number"));
       // supportedKycAuthFormats.put("BIO", List.of("encoded-json"));
       // supportedKycAuthFormats.put("WLA", List.of("jwt"));
        supportedKycAuthFormats.put(PASSWORD, List.of("alpha-numeric"));
    }
    @Autowired
    private HouseholdRepository householdRepository;

    public KycAuthResult doKycAuthHousehold(String relyingPartyId, String clientId, KycAuthDto kycAuthDto)
            throws KycAuthException {
        for (AuthChallenge authChallenge : kycAuthDto.getChallengeList()) {
            if(authChallenge== null)
                throw new KycAuthException(INVALID_AUTH_CHALLENGE);
            switch (authChallenge.getAuthFactorType()) {
                case PASSWORD:
                            validatePassword(authChallenge.getChallenge(),
                            kycAuthDto.getIndividualId(), authChallenge);
                          break;
                default:
                    throw new KycAuthException("invalid_auth_challenge");
            }
        }
        return saveKycAuthTransaction(relyingPartyId, kycAuthDto.getIndividualId());
    }
   // to check whether the give challenge is supported or not
    private boolean isKycAuthFormatSupported(String authFactorType, String kycAuthFormat) {
        var supportedFormat = supportedKycAuthFormats.get(authFactorType);
        return supportedFormat != null && supportedFormat.contains(kycAuthFormat);
    }

    private KycAuthResult saveKycAuthTransaction(String relyingPartyId, String individualId) throws KycAuthException {
        String kycToken = HelperUtil.generateB64EncodedHash(HelperUtil.ALGO_SHA3_256, UUID.randomUUID().toString());
        String psut;
        try {
            psut = HelperUtil.generateB64EncodedHash(HelperUtil.ALGO_SHA3_256,
                    String.format(PSUT_FORMAT, individualId, relyingPartyId));
        } catch (Exception e) {
            log.error("Failed to generate PSUT", e);
            throw new KycAuthException(INVALID_PSUT);
        }
        return new KycAuthResult(kycToken, psut);
    }
    private  void validatePassword(String password,String individualId,AuthChallenge authChallenge) throws KycAuthException
    {
        if(!password.matches(regexPattern)){
            throw new KycAuthException(INVALID_PASSWORD);
        }
        if (!isKycAuthFormatSupported(authChallenge.getAuthFactorType(), authChallenge.getFormat())) {
            throw new KycAuthException(INVALID_CHALLENGE_FORMAT);
        }
        try{
            HouseholdView householdView= getHouseHoldView(individualId);
            passwordMatcher(password, householdView.getPassword()); //checking for the passwor match
        }catch (Exception e){
            log.error("Failed to validate password", e);
            throw new KycAuthException(INVALID_INDIVIDUAL_ID);
        }
    }

    private HouseholdView getHouseHoldView(String individualId) throws Exception {
       Optional<HouseholdView> householdViewOptional= householdRepository.findById(Long.parseLong(individualId));
       if(householdViewOptional.isPresent())
           return householdViewOptional.get();

       throw new Exception("Household not found");
    }

    private void passwordMatcher(String password, String hash) throws Exception {
        String[] hashAttrs= hash.split("\\$");
        if (hashAttrs.length < 4) {
            throw new Exception("Invalid password hash");
        }
        String hmac= hashAttrs[1].split("-")[1].toUpperCase();
        int iterations= Integer.parseInt(hashAttrs[2]);
        byte[] salt= Base64.getDecoder().decode(hashAttrs[3].replace(".", "+"));
        int keyLength= 512;
        String computedHash= "";
        SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmac" + hmac);
        PBEKeySpec spec = new PBEKeySpec(
                password.toCharArray(),
                salt,
                iterations,
                keyLength
        );
        SecretKey secretKey = skf.generateSecret(spec);
        computedHash = Base64.getEncoder().encodeToString(secretKey.getEncoded());
        computedHash = computedHash.replace("+", ".");
        computedHash = computedHash.replaceAll("=+$", "");
        if(!hashAttrs[4].equals(computedHash))
            throw new Exception(INVALID_PASSWORD);
    }
}
