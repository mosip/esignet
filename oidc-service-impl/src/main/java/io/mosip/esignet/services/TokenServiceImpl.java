/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureVerifyResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;

import static io.mosip.esignet.core.constants.Constants.SPACE;

@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

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
    
    private static Set<String> REQUIRED_CLIENT_ASSERTION_CLAIMS;

    static {
        REQUIRED_CLIENT_ASSERTION_CLAIMS = new HashSet<>();
        REQUIRED_CLIENT_ASSERTION_CLAIMS.add("sub");
        REQUIRED_CLIENT_ASSERTION_CLAIMS.add("aud");
        REQUIRED_CLIENT_ASSERTION_CLAIMS.add("exp");
        REQUIRED_CLIENT_ASSERTION_CLAIMS.add("iss");
        REQUIRED_CLIENT_ASSERTION_CLAIMS.add("iat");
    }


    @Override
    public String getIDToken(@NonNull OIDCTransaction transaction) {
        JSONObject payload = buildIDToken(transaction.getPartnerSpecificUserToken(),
                transaction.getClientId(), idTokenExpireSeconds, transaction);
        payload.put(ACCESS_TOKEN_HASH, transaction.getAHash());
        return getSignedJWT(Constants.OIDC_SERVICE_APP_ID, payload);
    }

    @Override
    public String getIDToken(@NonNull String subject, @NonNull String audience, int validitySeconds,
                             @NonNull OIDCTransaction transaction) {
        JSONObject payload = buildIDToken(subject, audience, validitySeconds, transaction);
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
        payload.put(EXP, issueTime + (accessTokenExpireSeconds<=0 ? 3600 : accessTokenExpireSeconds));
        payload.put(CLIENT_ID, transaction.getClientId());

        if(cNonce != null) {
            payload.put(C_NONCE, cNonce);
            payload.put(C_NONCE_EXPIRES_IN, cNonceExpireSeconds);
        }
        return getSignedJWT(Constants.OIDC_SERVICE_APP_ID, payload);
    }

    @Override
    public void verifyClientAssertionToken(String clientId, String jwk, String clientAssertion,String audience) throws EsignetException {
        if(clientAssertion == null)
            throw new EsignetException(ErrorConstants.INVALID_ASSERTION);

        try {
      
            JWSKeySelector keySelector = new JWSVerificationKeySelector(JWSAlgorithm.RS256,
                    new ImmutableJWKSet(new JWKSet(RSAKey.parse(jwk))));
            DefaultJWTClaimsVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    .audience(Collections.singletonList(audience))
                    .issuer(clientId)
                    .subject(clientId)
                    .build(), REQUIRED_CLIENT_ASSERTION_CLAIMS);
            claimsSetVerifier.setMaxClockSkew(0);

            ConfigurableJWTProcessor jwtProcessor = new DefaultJWTProcessor();
            jwtProcessor.setJWSKeySelector(keySelector);
            jwtProcessor.setJWTClaimsSetVerifier(claimsSetVerifier);
            jwtProcessor.process(clientAssertion, null); //If invalid throws exception
        } catch (Exception e) {
            log.error("Failed to verify client assertion", e);
            throw new InvalidRequestException(ErrorConstants.INVALID_ASSERTION);
        }
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
                    .build(), REQUIRED_CLIENT_ASSERTION_CLAIMS);
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
                    .build(), REQUIRED_CLIENT_ASSERTION_CLAIMS);
            claimsSetVerifier.verify(jwt.getJWTClaimsSet(), null);
        } catch (Exception e) {
            log.error("ID token claims verification failed", e);
            throw new NotAuthenticatedException();
        }
    }

    @Override
    public String getSignedJWT(String applicationId, JSONObject payload) {
        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(applicationId);
        jwtSignatureRequestDto.setReferenceId("");
        jwtSignatureRequestDto.setIncludePayload(true);
        jwtSignatureRequestDto.setIncludeCertificate(false);
        jwtSignatureRequestDto.setDataToSign(IdentityProviderUtil.b64Encode(payload.toJSONString()));
        jwtSignatureRequestDto.setIncludeCertHash(false);
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }

    private JSONObject buildIDToken(String subject, String audience, int validitySeconds,
                                    OIDCTransaction transaction) {
        JSONObject payload = new JSONObject();
        payload.put(ISS, issuerId);
        payload.put(SUB, subject);
        payload.put(AUD, audience);
        long issueTime = IdentityProviderUtil.getEpochSeconds();
        payload.put(IAT, issueTime);
        payload.put(EXP, issueTime + (validitySeconds<=0 ? 3600 : validitySeconds));
        payload.put(AUTH_TIME, transaction.getAuthTimeInSeconds());
        payload.put(NONCE, transaction.getNonce());
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
}
