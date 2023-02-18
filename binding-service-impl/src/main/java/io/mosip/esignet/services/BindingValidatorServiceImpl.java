package io.mosip.esignet.services;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;
import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.BindingAuthResult;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.spi.KeyBindingValidator;
import io.mosip.esignet.entity.PublicKeyRegistry;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.constants.ErrorConstants.*;

@ConditionalOnProperty(value = "mosip.esignet.integration.binding-validator", havingValue = "BindingValidatorServiceImpl")
@Component
@Slf4j
public class BindingValidatorServiceImpl implements KeyBindingValidator {

    @Autowired
    private KeyBindingHelperService keyBindingHelperService;

    @Autowired
    private PublicKeyRegistryRepository publicKeyRegistryRepository;

    @Autowired
    private KeymanagerUtil keymanagerUtil;

    @Value("${mosip.esignet.binding.audience-id}")
    private String bindingAudienceId;

    private static Set<String> REQUIRED_WLA_CLAIMS;

    static {
        REQUIRED_WLA_CLAIMS = new HashSet<>();
        REQUIRED_WLA_CLAIMS.add("sub");
        REQUIRED_WLA_CLAIMS.add("aud");
        REQUIRED_WLA_CLAIMS.add("exp");
        REQUIRED_WLA_CLAIMS.add("iss");
        REQUIRED_WLA_CLAIMS.add("iat");
    }

    @Override
    public BindingAuthResult validateBindingAuth(String transactionId, String individualId, List<AuthChallenge> challengeList) throws KycAuthException {
        String individualIdHash = keyBindingHelperService.getIndividualIdHash(individualId);

        Map<String,String> providedAuthFactorTypes = challengeList.stream()
                .collect(Collectors.toMap(AuthChallenge::getAuthFactorType, AuthChallenge::getFormat));

        List<PublicKeyRegistry> publicKeyRegistryEntries = publicKeyRegistryRepository.findByIdHashAndAuthFactorInAndExpiredtimesGreaterThan(individualIdHash,
                providedAuthFactorTypes.keySet(), LocalDateTime.now(ZoneOffset.UTC));
        if (CollectionUtils.isEmpty(publicKeyRegistryEntries))
            throw new KycAuthException(ErrorConstants.KEY_BINDING_NOT_FOUND);

        //check if provided challenge auth-factor is the bound auth-factor-type for the provided individualId
        if(publicKeyRegistryEntries.size() < providedAuthFactorTypes.size())
            throw new KycAuthException(ErrorConstants.UNBOUND_AUTH_FACTOR);

        boolean result = challengeList.stream()
                .allMatch(authChallenge -> validateChallenge(individualId, authChallenge,
                        publicKeyRegistryEntries.stream().filter( e -> e.getAuthFactor().equals(authChallenge.getAuthFactorType())).findFirst()));

        if(result) {
            return new BindingAuthResult(transactionId, individualId);
        }

        throw new KycAuthException(ErrorConstants.INVALID_CHALLENGE);
    }

    private boolean validateChallenge(String individualId, AuthChallenge authChallenge, Optional<PublicKeyRegistry> publicKeyRegistry) {
        if(!publicKeyRegistry.isPresent())
            return false;

        try {
            switch (authChallenge.getAuthFactorType()) {
                case "WLA" : return validateWLAToken(individualId, authChallenge.getChallenge(), authChallenge.getFormat(), publicKeyRegistry.get());
                default: return false;
            }
        } catch (Exception e) {
            log.error("Failed to validate challenge : {}", authChallenge.getAuthFactorType(), e);
        }
        return false;
    }

    private boolean validateWLAToken(String individualId, String wlaToken, String format, PublicKeyRegistry publicKeyRegistry) throws KycAuthException {
        switch (format) {
            case "jwt" :
                try {
                    X509Certificate x509Certificate = (X509Certificate) keymanagerUtil.convertToCertificate(publicKeyRegistry.getCertificate());
                    JWSKeySelector keySelector = new JWSVerificationKeySelector(JWSAlgorithm.RS256,
                            new ImmutableJWKSet(new JWKSet(RSAKey.parse(x509Certificate))));

                    JWT jwt = JWTParser.parse(wlaToken);
                    if(!jwt.getHeader().toJSONObject().containsKey("x5t#S256"))
                        throw new KycAuthException(SHA256_THUMBPRINT_HEADER_MISSING);

                    JWTClaimsSetVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                            .audience(bindingAudienceId)
                            .subject(individualId)
                            .build(), REQUIRED_WLA_CLAIMS);

                    ConfigurableJWTProcessor jwtProcessor = new DefaultJWTProcessor();
                    jwtProcessor.setJWSKeySelector(keySelector);
                    jwtProcessor.setJWTClaimsSetVerifier(claimsSetVerifier);
                    jwtProcessor.process(jwt, null); //If invalid throws exception
                    return true;
                } catch (KycAuthException e) {
                    throw e;
                } catch (Exception e) {
                    log.error("Failed to verify WLA token", e);
                }
                throw new KycAuthException(ErrorConstants.INVALID_WLA_TOKEN);
           default: throw new KycAuthException(UNKNOWN_WLA_FORMAT);
        }
    }
}
