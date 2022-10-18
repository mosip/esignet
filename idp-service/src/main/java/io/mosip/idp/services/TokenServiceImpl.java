/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

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
import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.NotAuthenticatedException;
import io.mosip.idp.core.spi.TokenService;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
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

import java.util.*;

import static io.mosip.idp.core.util.Constants.SPACE;

@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mosip.idp.transaction-token.expire.seconds:30}")
    private int preAuthTransactionTokenExpireSeconds;

    @Value("${mosip.idp.id-token.expire.seconds:60}")
    private int idTokenExpireSeconds;

    @Value("${mosip.idp.access-token.expire.seconds:60}")
    private int accessTokenExpireSeconds;

    @Value("${mosip.idp.client-assertion.expire.seconds:60}")
    private int clientAssertionExpireSeconds;

    @Value("${mosip.idp.discovery.issuer-id}")
    private String issuerId;

    @Value("#{${mosip.idp.openid.scope.claims}}")
    private Map<String, List<String>> claims;

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
    public String getIDToken(@NonNull IdPTransaction transaction) {
        JSONObject payload = new JSONObject();
        payload.put(ISS, issuerId);
        payload.put(SUB, transaction.getPartnerSpecificUserToken());
        payload.put(AUD, transaction.getClientId());
        long issueTime = IdentityProviderUtil.getEpochSeconds();
        payload.put(IAT, issueTime);
        payload.put(EXP, issueTime + (idTokenExpireSeconds<=0 ? 3600 : idTokenExpireSeconds));
        payload.put(AUTH_TIME, transaction.getAuthTimeInSeconds());
        payload.put(NONCE, transaction.getNonce());
        String[] acrs = transaction.getRequestedClaims().getId_token().get(ACR).getValues();
        payload.put(ACR, String.join(SPACE, acrs));
        payload.put(ACCESS_TOKEN_HASH, transaction.getAHash());
        return getSignedJWT(Constants.IDP_SERVICE_APP_ID, payload);
    }

    @Override
    public String getAccessToken(IdPTransaction transaction) {
        JSONObject payload = new JSONObject();
        payload.put(ISS, issuerId);
        payload.put(SUB, transaction.getPartnerSpecificUserToken());
        payload.put(AUD, transaction.getClientId());
        long issueTime = IdentityProviderUtil.getEpochSeconds();
        payload.put(IAT, issueTime);
        //TODO Need to discuss -> jsonObject.put(JTI, transaction.getUserToken());
        if(transaction.getPermittedScopes() != null)
            payload.put(SCOPE, String.join(SPACE, transaction.getPermittedScopes()));
        payload.put(EXP, issueTime + (accessTokenExpireSeconds<=0 ? 3600 : accessTokenExpireSeconds));
        return getSignedJWT(Constants.IDP_SERVICE_APP_ID, payload);
    }

    @Override
    public void verifyClientAssertionToken(String clientId, String jwk, String clientAssertion) throws IdPException {
        if(clientAssertion == null)
            throw new IdPException(ErrorConstants.INVALID_ASSERTION);

        try {
            JWSKeySelector keySelector = new JWSVerificationKeySelector(JWSAlgorithm.RS256,
                    new ImmutableJWKSet(new JWKSet(RSAKey.parse(jwk))));
            JWTClaimsSetVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    .audience(issuerId)
                    .issuer(clientId)
                    .subject(clientId)
                    .build(), REQUIRED_CLIENT_ASSERTION_CLAIMS);

            ConfigurableJWTProcessor jwtProcessor = new DefaultJWTProcessor();
            jwtProcessor.setJWSKeySelector(keySelector);
            jwtProcessor.setJWTClaimsSetVerifier(claimsSetVerifier);
            jwtProcessor.process(clientAssertion, null); //If invalid throws exception
        } catch (Exception e) {
            log.error("Failed to verify client assertion", e);
            throw new IdPException(ErrorConstants.INVALID_ASSERTION);
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
    public String getSignedJWT(String applicationId, JSONObject payload) {
        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(applicationId);
        jwtSignatureRequestDto.setReferenceId("");
        jwtSignatureRequestDto.setIncludePayload(true);
        jwtSignatureRequestDto.setIncludeCertificate(true);
        jwtSignatureRequestDto.setDataToSign(IdentityProviderUtil.b64Encode(payload.toJSONString()));
        jwtSignatureRequestDto.setIncludeCertHash(true);
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }

    private boolean isSignatureValid(String jwt) {
        JWTSignatureVerifyRequestDto signatureVerifyRequestDto = new JWTSignatureVerifyRequestDto();
        signatureVerifyRequestDto.setApplicationId(Constants.IDP_SERVICE_APP_ID);
        signatureVerifyRequestDto.setReferenceId("");
        signatureVerifyRequestDto.setJwtSignatureData(jwt);
        JWTSignatureVerifyResponseDto responseDto = signatureService.jwtVerify(signatureVerifyRequestDto);
        return responseDto.isSignatureValid();
    }
}
