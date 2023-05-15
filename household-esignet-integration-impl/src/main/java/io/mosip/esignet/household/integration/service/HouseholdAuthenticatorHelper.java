package io.mosip.esignet.household.integration.service;
import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.household.integration.dto.KycTransactionDto;
import io.mosip.esignet.household.integration.entity.HouseholdView;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.util.*;

import static io.mosip.esignet.household.integration.util.ErrorConstants.*;

@Component
@Slf4j
public class HouseholdAuthenticatorHelper {

    private static final String   KYC_AUTH_CACHE = "kycauthcache";
    private static final Map<String, List<String>> supportedKycAuthFormats = new HashMap<>();

    private static final String PASSWORD = "PWD";

    private static  final String regexPattern="^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d]{8,}$";

    @Autowired
    CacheManager cacheManager;

    static {
        supportedKycAuthFormats.put(PASSWORD, List.of("alpha-numeric"));
    }

    public void validateAuthChallenge(HouseholdView householdView, AuthChallenge authChallenge)
            throws KycAuthException {

            if(authChallenge== null || authChallenge.getAuthFactorType()==null)
                throw new KycAuthException(INVALID_AUTH_CHALLENGE);

            if (!isKycAuthFormatSupported(authChallenge.getAuthFactorType(), authChallenge.getFormat())) {
                throw new KycAuthException(INVALID_CHALLENGE_FORMAT);
            }

            switch (authChallenge.getAuthFactorType()) {
                case PASSWORD:
                            validatePassword(householdView.getPassword(), authChallenge.getChallenge());
                          break;
                default:
                    throw new KycAuthException(INVALID_AUTH_CHALLENGE);
            }
    }

   // to check whether the give challenge is supported or not
    protected boolean isKycAuthFormatSupported(String authFactorType, String kycAuthFormat) {
        var supportedFormat = supportedKycAuthFormats.get(authFactorType);
        return supportedFormat != null && supportedFormat.contains(kycAuthFormat);
    }

    @Cacheable(value = KYC_AUTH_CACHE, key = "#kycToken")
    public KycTransactionDto setKycAuthTransaction(String kycToken, String psut, long houseHoldId) {

        KycTransactionDto kycTransactionDto =new KycTransactionDto();
        kycTransactionDto.setKycToken(kycToken);
        kycTransactionDto.setPsut(psut);
        kycTransactionDto.setHouseholdId(houseHoldId);
        return kycTransactionDto;
    }

    @CacheEvict(value = KYC_AUTH_CACHE, key = "#kycToken")
    public KycTransactionDto getKycAuthTransaction(String kycToken) {
        return cacheManager.getCache(KYC_AUTH_CACHE).get(kycToken, KycTransactionDto.class); //NOSONAR getCache() will not be returning null here.
    }

    public   void validatePassword(String challenge, String hash) throws KycAuthException
    {
        if(!challenge.matches(regexPattern)){
            throw new KycAuthException(INVALID_AUTH_CHALLENGE);
        }
        String[] hashAttrs = hash.split("\\$");
        if (hashAttrs.length < 4) {
            throw new KycAuthException(INVALID_PASSWORD_HASH);
        }
        String hmac = hashAttrs[1].split("-")[1].toUpperCase();
        int iterations = Integer.parseInt(hashAttrs[2]);
        byte[] salt= Base64.getDecoder().decode(hashAttrs[3].replace(".", "+"));
        int keyLength= 512;
        String computedHash= "";
        try {
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmac" + hmac);
            PBEKeySpec spec = new PBEKeySpec(
                    challenge.toCharArray(),
                    salt,
                    iterations,
                    keyLength
            );
            SecretKey secretKey = skf.generateSecret(spec);
            computedHash = Base64.getEncoder().encodeToString(secretKey.getEncoded());
            computedHash = computedHash.replace("+", ".");
            computedHash = computedHash.replaceAll("=+$", "");
        }catch (Exception e) {
            log.error("Error while validating password hash", e);
            throw new KycAuthException(UNKNOWN_ERROR);
        }
        if(!hashAttrs[4].equals(computedHash))
            throw new KycAuthException(INVALID_PASSWORD);
    }
}
