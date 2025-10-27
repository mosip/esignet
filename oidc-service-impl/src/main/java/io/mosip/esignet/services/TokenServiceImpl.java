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

import static io.mosip.esignet.core.constants.Constants.SPACE;

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

    @Value("${mosip.esignet.dpop.nonce.expire.seconds:15}")
    private long dpopNonceExpirySeconds;

    @Value("#{${mosip.esignet.discovery.key-values}}")
    private Map<String, Object> discoveryMap;

    @Value("${mosip.esignet.client-assertion.unique.jti.required}")
    private boolean uniqueJtiRequired;

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
    public void verifyClientAssertionToken(String clientId, String jwk, String clientAssertion, String audience) throws EsignetException {
        if (clientAssertion == null) {
            throw new EsignetException(ErrorConstants.INVALID_CLIENT);
        }

        try {
            List<String> supportedAlgs = (List<String>) discoveryMap.get("token_endpoint_auth_signing_alg_values_supported");
            SignedJWT signedJWT = SignedJWT.parse(clientAssertion);
            String alg = signedJWT.getHeader().getAlgorithm().getName();

            if (alg == null || !supportedAlgs.contains(alg)) {
                log.error("Invalid or unsupported alg header: {}", alg);
                throw new InvalidRequestException(ErrorConstants.INVALID_CLIENT);
            }

            String issuer = (String) discoveryMap.get("issuer");

            NimbusJwtDecoder jwtDecoder = getNimbusJwtDecoderFromJwk(jwk, clientId, audience, issuer, maxClockSkew,alg);
            jwtDecoder.decode(clientAssertion);
            String jti = signedJWT.getJWTClaimsSet().getJWTID();
            if (uniqueJtiRequired && (jti == null || cacheUtilService.checkAndMarkJti(jti))) {
                log.error("invalid jti {}", jti);
                throw new EsignetException();
            }
        } catch (Exception e) {
            log.error("Failed to verify client assertion", e);
            throw new InvalidRequestException(ErrorConstants.INVALID_CLIENT);
        }
    }

    private NimbusJwtDecoder getNimbusJwtDecoderFromJwk(String jwkJson, String clientId, String audience, String issuer, int maxClockSkew, String alg) throws Exception {

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
                new JwtClaimValidator<Instant>(JwtClaimNames.IAT, iat -> iat != null),
                new JwtClaimValidator<Instant>(JwtClaimNames.EXP, exp -> exp != null),
                new JwtClaimValidator<List<String>>(JwtClaimNames.AUD, aud ->
                        aud != null && aud.stream().anyMatch(a -> a.equals(audience) || a.equals(issuer))
                ),
                new JwtClaimValidator<String>(JwtClaimNames.SUB, sub -> clientId.equals(sub)),
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
            net.minidev.json.JSONObject payload = dpopJwt.getPayload().toJSONObject();
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
        payload.put(NONCE, nonce == null ? transaction.getNonce() : nonce);
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
