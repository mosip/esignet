package io.mosip.esignet.household.integration.service;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KycAuthDto;
import io.mosip.esignet.api.dto.KycAuthResult;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.household.integration.dto.KycAuthResponseDto;
import io.mosip.esignet.household.integration.entity.HouseholdView;
import io.mosip.esignet.household.integration.exception.HouseholdentityException;
import io.mosip.esignet.household.integration.repository.HouseholdRepository;
import io.mosip.esignet.household.integration.util.HelperUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.*;

@Component
@Slf4j
public class HouseholdAuthenticatorHelper {

    private static final String PSUT_FORMAT = "%s%s";

    private static final String FORMAT_PASSWORD="PASSWORD";
    @PostConstruct
    public void initialize() {
        log.info("Started to setup HouseHold IDA");
    }

    @Bean
    PasswordEncoder passwordEncoder()
    {
        return new BCryptPasswordEncoder();
    }

    private static final Map<String, List<String>> supportedKycAuthFormats = new HashMap<>();

    static {
       // supportedKycAuthFormats.put("OTP", List.of("alpha-numeric"));
       // supportedKycAuthFormats.put("PIN", List.of("number"));
       // supportedKycAuthFormats.put("BIO", List.of("encoded-json"));
       // supportedKycAuthFormats.put("WLA", List.of("jwt"));
        supportedKycAuthFormats.put(FORMAT_PASSWORD, List.of("alpha-numeric"));
    }
    @Autowired
    private HouseholdRepository householdRepository;

    public KycAuthResult doKycAuthHousehold(String relyingPartyId, String clientId, KycAuthDto kycAuthDto)
            throws KycAuthException {

        KycAuthResponseDto kycAuthResponseDto;
        KycAuthResult kycAuthResult = new KycAuthResult();
        String password = null;
        for (AuthChallenge authChallenge : kycAuthDto.getChallengeList()) {
            switch (authChallenge.getAuthFactorType()) {
                case FORMAT_PASSWORD:
                    password = authChallenge.getChallenge();
                    if(!validatePassword(password,kycAuthDto.getIndividualId(),authChallenge))
                        throw  new KycAuthException("password does not match");
                    break;
                default:
                    throw new KycAuthException("invalid_auth_challenge");
            }
        }
        return saveKycAuthTransaction(clientId, kycAuthDto.getIndividualId());
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
            throw new KycAuthException("Not able to generate psut");
        }
        KycAuthResult kycAuthResult = new KycAuthResult(kycToken, psut);
        return kycAuthResult;
    }
    private  boolean validatePassword(String password,String individualId,AuthChallenge authChallenge) throws KycAuthException
    {
        if(!password.matches("^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$")){
            throw new KycAuthException("password musht be 8 character long and must contain one digit and one alphabet");
        }
        if (!isKycAuthFormatSupported(authChallenge.getAuthFactorType(), authChallenge.getFormat())) {
            throw new KycAuthException("invalid_challenge_format");
        }
        HouseholdView householdView=getHosueHoldView(individualId);
        if(passwordEncoder().encode(password).equals(householdView.getPassword())) return true;
        return false;
    }

    private HouseholdView getHosueHoldView(String individualId) throws HouseholdentityException{
       HouseholdView householdView= householdRepository.findById(Long.parseLong(individualId)).get();
       if(householdView==null)throw new HouseholdentityException("no user found");
       return householdView;
    }
}
