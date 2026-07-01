/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jose.proc.SecurityContext;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.JWTParser;
import com.nimbusds.jwt.SignedJWT;
import com.nimbusds.jwt.proc.*;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.dto.ServerProfile;
import io.mosip.esignet.core.exception.*;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.kernel.signature.dto.*;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import java.text.ParseException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

import static io.mosip.esignet.core.constants.Constants.*;

@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    private final int DEFAULT_VALIDITY = 60;

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Value("${mosip.esignet.id-token-expire-seconds:60}")
    private int idTokenExpireSeconds;
    
    @Value("${mosip.esignet.access-token-expire-seconds:60}")
    private int accessTokenExpireSeconds;

    @Value("${mosip.esignet.discovery.issuer-id}")
    private String issuerId;

    @Value("#{${mosip.esignet.openid.scope.claims}}")
    private Map<String, List<String>> claims;

    @Value("${mosip.esignet.cnonce-expire-seconds:60}")
    private int cNonceExpireSeconds;

    @Value("#{${mosip.esignet.credential.scope-resource-mapping}}")
    private Map<String, String> scopesResourceMapping;

    @Value("${mosip.esignet.client-assertion-jwt.leeway-seconds:5}")
    private int maxClockSkew;

    @Value("${mosip.esignet.client-assertion.jti.cache.max-ttl-seconds:3600}")
    private long maxJtiCacheTtlSeconds;

    @Value("${mosip.esignet.client-assertion.jti.cache.skew-buffer-seconds:30}")
    private long jtiCacheSkewBufferSeconds;

    @Value("${mosip.esignet.dpop.nonce.expire.seconds:15}")
    private long dpopNonceExpirySeconds;

    @Value("#{${mosip.esignet.discovery.key-values}}")
    private Map<String, Object> discoveryMap;

    @Value("${mosip.esignet.client-assertion.unique.jti.required}")
    private boolean uniqueJtiRequired;

    @Autowired
    private ServerProfile serverProfile;

    private final String CNF = "cnf";
    private final String JKT = "jkt";
    
    private static Set<String> REQUIRED_TOKEN_CLAIMS;
    private static Set<String> REQUIRED_CLIENT_ASSERTION_CLAIMS;

    static {
        REQUIRED_TOKEN_CLAIMS = new HashSet<>();
        REQUIRED_TOKEN_CLAIMS.add("sub");
        REQUIRED_TOKEN_CLAIMS.add("aud");
        REQUIRED_TOKEN_CLAIMS.add("exp");
        REQUIRED_TOKEN_CLAIMS.add("iss");
        REQUIRED_TOKEN_CLAIMS.add("iat");

        REQUIRED_CLIENT_ASSERTION_CLAIMS = new HashSet<>(REQUIRED_TOKEN_CLAIMS);
        REQUIRED_CLIENT_ASSERTION_CLAIMS.add("jti");
    }


    @Override
    public String getIDToken(@NonNull OIDCTransaction transaction) {
        JSONObject payload = buildIDToken(transaction.getPartnerSpecificUserToken(),
                transaction.getClientId(), idTokenExpireSeconds, transaction, null);
        payload.put(ACCESS_TOKEN_HASH, transaction.getAHash());
        return getSignedJWT(Constants.OIDC_SERVICE_APP_ID, payload);
    }

    @Override
    public String getIDToken(@NonNull String subject, @NonNull String audience, int validitySeconds,
                             @NonNull OIDCTransaction transaction, String nonce) {
        JSONObject payload = buildIDToken(subject, audience, validitySeconds, transaction, nonce);
        return getSignedJWT(Constants.OIDC_SERVICE_APP_ID, payload);
    }

    @Override
    public String getAccessToken(OIDCTransaction transaction, String cNonce) {
        JSONObject payload = new JSONObject();
        payload.put(ISS, issuerId);
        payload.put(SUB, transaction.getPartnerSpecificUserToken());
        payload.put(AUD, transaction.getClientId());
        long issueTime = IdentityProviderUtil.getEpochSeconds();
        payload.put(IAT, issueTime);
        payload.put(AZP, transaction.getClientId());
        //TODO Need to discuss -> jsonObject.put(JTI, transaction.getUserToken());
        if(!CollectionUtils.isEmpty(transaction.getPermittedScopes())) {
            payload.put(SCOPE, String.join(SPACE, transaction.getPermittedScopes()));
            //AS of now taking only first matched credential scope, need to work on multiple resource support
            Optional<String> result = Objects.requireNonNullElse(transaction.getRequestedCredentialScopes(), new ArrayList<String>())
                    .stream()
                    .filter( scope -> transaction.getPermittedScopes().contains(scope) )
                    .findFirst();
            if(result.isPresent()) {
                payload.put(AUD, scopesResourceMapping.getOrDefault(result.get(), ""));
            }
        }
        payload.put(EXP, issueTime + getTokenExpireSeconds(transaction, accessTokenExpireSeconds));
        payload.put(CLIENT_ID, transaction.getClientId());

        if(cNonce != null) {
            payload.put(C_NONCE, cNonce);
            payload.put(C_NONCE_EXPIRES_IN, cNonceExpireSeconds);
        }
        if(transaction.isDpopBoundAccessToken()) {
            payload.put(CNF, Map.of(JKT, transaction.getDpopJkt()));
        }
        return getSignedJWT(Constants.OIDC_SERVICE_APP_ID, payload);
    }

    @Override
    public void verifyClientAssertionToken(String clientId, String jwk, String clientAssertion, List<String> audience) throws EsignetException {
        if (clientAssertion == null) {
            throw new EsignetException(ErrorConstants.INVALID_CLIENT);
        }

        try {
            List<String> supportedAlgs = (List<String>) discoveryMap.get("token_endpoint_auth_signing_alg_values_supported");
            SignedJWT signedJWT = SignedJWT.parse(clientAssertion);
            String alg = signedJWT.getHeader().getAlgorithm().getName();

            if (alg == null || !supportedAlgs.contains(alg)) {
                log.error("Invalid or unsupported alg header: {}", alg);
                throw new EsignetException(ErrorConstants.INVALID_CLIENT);
            }

            String sub = signedJWT.getJWTClaimsSet().getSubject();
            if (sub == null) {
                log.error("Client assertion sub is null for clientId '{}'", clientId);
                throw new EsignetException(ErrorConstants.INVALID_CLIENT);
            }

            if (!sub.equals(clientId)) {
                log.error("Client assertion sub '{}' does not match clientId '{}'", sub, clientId);
                throw new EsignetException(ErrorConstants.INVALID_GRANT);
            }
            List<String> validAudience = getValidAudienceForClientAssertion(audience);
            NimbusJwtDecoder jwtDecoder = getNimbusJwtDecoderFromJwk(jwk, clientId, validAudience, maxClockSkew, alg);
            jwtDecoder.decode(clientAssertion);
            if (uniqueJtiRequired) {
                enforceJtiReplayProtection(signedJWT.getJWTClaimsSet(), clientId);
            }
        } catch (EsignetException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to verify client assertion", e);
            throw new EsignetException(ErrorConstants.INVALID_CLIENT);
        }
    }

    private void enforceJtiReplayProtection(JWTClaimsSet claims, String clientId) {
        String jti = claims.getJWTID();
        Date   expDate   = claims.getExpirationTime();
        Date   iatDate   = claims.getIssueTime();

        if (jti == null) {
            log.error("Missing jti in client assertion for clientId {}", clientId);
            throw new EsignetException(ErrorConstants.INVALID_CLIENT);
        }

        long now = Instant.now().getEpochSecond();
        long exp = expDate.toInstant().getEpochSecond();
        long iat = iatDate.toInstant().getEpochSecond();

        // Guarantee cache always outlives validity → close the (MAX_CAP, exp) replay window.
        long declaredLifetime = exp - iat;
        if (declaredLifetime <= 0 || declaredLifetime > maxJtiCacheTtlSeconds) {
            log.error("Client assertion lifetime {}s outside (0,{}] for clientId {}",
                    declaredLifetime, maxJtiCacheTtlSeconds, clientId);
            throw new EsignetException(ErrorConstants.INVALID_CLIENT);
        }

        long jtiTtlSeconds = Math.min(
                (exp - now) + Math.max(jtiCacheSkewBufferSeconds,maxClockSkew),
                maxJtiCacheTtlSeconds
        );

        if (jtiTtlSeconds <= 0) {
            log.error("Client assertion already expired for clientId {}", clientId);
            throw new EsignetException(ErrorConstants.INVALID_CLIENT);
        }

        if (cacheUtilService.checkAndMarkJti(jti, jtiTtlSeconds)) {
            log.error("Replay detected for jti {} (clientId {})", jti, clientId);
            throw new EsignetException(ErrorConstants.INVALID_CLIENT);
        }
    }

    /**
     * Gets the valid audience list for client assertion verification.
     * If the server profile has 'client_auth_assertion_audience' configured for the 'strict_audience_check' feature,
     * it overrides the default audience list with the issuer URL only.
     * @param audience the default audience list
     * @return the valid audience list to use for verification
     */
    private List<String> getValidAudienceForClientAssertion(List<String> audience) {
        if (serverProfile != null && serverProfile.getFeatureMap() != null) {
            String configuredFeature = serverProfile.getFeatureMap().get(CLIENT_AUTH_ASSERTION_AUDIENCE);
            if (STRICT_AUDIENCE_CHECK.equalsIgnoreCase(configuredFeature)) {
                String issuer = (String) discoveryMap.get("issuer");
                if (issuer == null || issuer.isBlank()) {
                    log.error("Strict audience check is enabled but discovery issuer is missing");
                    return List.of(); // fail-closed
                }
                log.debug("Using strict audience check with issuer: {}", issuer);
                return List.of(issuer);
            }
        }
        return audience == null ? List.of() : audience;
    }

    private NimbusJwtDecoder getNimbusJwtDecoderFromJwk(String jwkJson, String clientId, List<String> audience, int maxClockSkew, String alg) throws Exception {

        JWK parsedJwk = JWK.parse(jwkJson);
        JWKSet jwkSet = new JWKSet(parsedJwk);
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(jwkSet);

        JWSAlgorithm jwsAlg = JWSAlgorithm.parse(alg);
        DefaultJWTProcessor<SecurityContext> jwtProcessor = new DefaultJWTProcessor<>();
        jwtProcessor.setJWSKeySelector(new JWSVerificationKeySelector<>(jwsAlg, jwkSource));

        NimbusJwtDecoder decoder = new NimbusJwtDecoder(jwtProcessor);

        OAuth2TokenValidator<Jwt> validator = new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(Duration.ofSeconds(maxClockSkew)),
                new JwtIssuerValidator(clientId),
                new JwtClaimValidator<Instant>(JwtClaimNames.IAT, Objects::nonNull),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, Objects::nonNull),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud ->
                        aud != null && aud.stream().anyMatch(audience::contains)
                ),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, clientId::equals),
                new JwtClaimValidator<String>(JwtClaimNames.JTI, jti ->
                        jti != null && !jti.trim().isEmpty()
                )
        );
        decoder.setJwtValidator(validator);
        return decoder;
    }

    @Override
    public void verifyAccessToken(String clientId, String subject, String accessToken) throws NotAuthenticatedException {
        if(!isSignatureValid(accessToken)) {
            log.error("Access token signature verification failed");
            throw new NotAuthenticatedException();
        }
        try {
            JWT jwt = JWTParser.parse(accessToken);
            JWTClaimsSetVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    .audience(clientId)
                    .issuer(issuerId)
                    .subject(subject)
                    .build(), REQUIRED_TOKEN_CLAIMS);
            claimsSetVerifier.verify(jwt.getJWTClaimsSet(), null);
        } catch (Exception e) {
            log.error("Access token claims verification failed", e);
            throw new NotAuthenticatedException();
        }
    }

    @Override
    public void verifyIdToken(String idToken, String clientId) throws NotAuthenticatedException {
        if(!isSignatureValid(idToken)) {
            log.error("ID token signature verification failed");
            throw new NotAuthenticatedException();
        }
        try {
            JWT jwt = JWTParser.parse(idToken);
            JWTClaimsSetVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    .audience(clientId)
                    .issuer(issuerId)
                    .build(), REQUIRED_TOKEN_CLAIMS);
            claimsSetVerifier.verify(jwt.getJWTClaimsSet(), null);
        } catch (Exception e) {
            log.error("ID token claims verification failed", e);
            throw new NotAuthenticatedException();
        }
    }

    @Override
    public String getSignedJWT(String applicationId, JSONObject payload) {
        JWSSignatureRequestDto jwsSignatureRequestDto = new JWSSignatureRequestDto();
        jwsSignatureRequestDto.setApplicationId(applicationId);
        jwsSignatureRequestDto.setReferenceId("");
        jwsSignatureRequestDto.setB64JWSHeaderParam(true);//required for payload encoding
        jwsSignatureRequestDto.setIncludePayload(true);
        jwsSignatureRequestDto.setIncludeCertificate(false);
        jwsSignatureRequestDto.setDataToSign(IdentityProviderUtil.b64Encode(payload.toJSONString()));
        jwsSignatureRequestDto.setIncludeCertHash(false);
        jwsSignatureRequestDto.setValidateJson(true);
        JWTSignatureResponseDto responseDto = signatureService.jwsSign(jwsSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }

    @Override
    public boolean isValidDpopServerNonce(String dpopHeader, OIDCTransaction transaction) {
        try {
            SignedJWT dpopJwt = SignedJWT.parse(dpopHeader);
            Map<String, Object> payload = dpopJwt.getPayload().toJSONObject();
            String dpopProofNonce = (String) payload.get("nonce");

            long currentTime = System.currentTimeMillis();

            String serverNonce = transaction.getDpopServerNonce();
            Long serverNonceTTL = transaction.getDpopServerNonceTTL();

            boolean nonceMatches = dpopProofNonce != null && dpopProofNonce.equals(serverNonce);
            boolean nonceExpired = serverNonceTTL == null || currentTime > serverNonceTTL;

            return nonceMatches && !nonceExpired;
        } catch (ParseException e) {
            log.error("dpopHeader parsing failed - Should never happen", e);
            throw new InvalidDpopHeaderException();
        }
    }

    @Override
    public void generateAndStoreNewNonce(String cacheKey, String cacheName) {
        String newNonce = IdentityProviderUtil.createTransactionId(null);
        cacheUtilService.updateNonceInCachedTransaction(cacheKey, newNonce, System.currentTimeMillis() + dpopNonceExpirySeconds * 1000L, cacheName);
        throw new DpopNonceMissingException(newNonce);
    }

    private JSONObject buildIDToken(String subject, String audience, int validitySeconds,
                                    OIDCTransaction transaction, String nonce) {
        JSONObject payload = new JSONObject();
        payload.put(ISS, issuerId);
        payload.put(SUB, subject);
        payload.put(AUD, audience);
        long issueTime = IdentityProviderUtil.getEpochSeconds();
        payload.put(IAT, issueTime);
        payload.put(EXP, issueTime + getTokenExpireSeconds(transaction, validitySeconds));
        payload.put(AUTH_TIME, transaction.getAuthTimeInSeconds());
        if (nonce == null) nonce = transaction.getNonce();
        if (nonce != null) payload.put(NONCE, nonce);
        List<String> acrs = authenticationContextClassRefUtil.getACRs(transaction.getProvidedAuthFactors());
        payload.put(ACR, String.join(SPACE, acrs));
        return payload;
    }

    private boolean isSignatureValid(String jwt) {
        JWTSignatureVerifyRequestDto signatureVerifyRequestDto = new JWTSignatureVerifyRequestDto();
        signatureVerifyRequestDto.setApplicationId(Constants.OIDC_SERVICE_APP_ID);
        signatureVerifyRequestDto.setReferenceId("");
        signatureVerifyRequestDto.setJwtSignatureData(jwt);
        JWTSignatureVerifyResponseDto responseDto = signatureService.jwtVerify(signatureVerifyRequestDto);
        return responseDto.isSignatureValid();
    }

    private int getTokenExpireSeconds(OIDCTransaction transaction, int configuredTokenLifetime) {
        configuredTokenLifetime = configuredTokenLifetime <=0 ? DEFAULT_VALIDITY : configuredTokenLifetime;

        if(transaction.getConsentExpireMinutes() <= 0)
            return configuredTokenLifetime;

        int consentExpireSeconds = transaction.getConsentExpireMinutes() * 60;
        if(consentExpireSeconds < configuredTokenLifetime) {
            log.info("Consent expire time is less than the configured token expire time!!");
            return consentExpireSeconds;
        }
        return configuredTokenLifetime;
    }
}
