/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

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

import java.text.ParseException;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
public class TokenServiceImpl implements TokenService {

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private CacheUtilService cacheUtilService;

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
        JSONObject jsonObject=new JSONObject();
        jsonObject.put(ISS, issuerId);
        jsonObject.put(SUB, transaction.getUserToken());
        jsonObject.put(AUD, transaction.getClientId());
        long issueTime = IdentityProviderUtil.getEpochSeconds();
        jsonObject.put(IAT, issueTime);
        jsonObject.put(EXP, issueTime + (idTokenExpireSeconds<=0 ? 3600 : idTokenExpireSeconds));
        jsonObject.put(AUTH_TIME, transaction.getAuthTimeInSeconds());
        jsonObject.put(NONCE, transaction.getNonce());
        jsonObject.put(ACR, transaction.getRequestedClaims().getId_token().get(ACR));
        jsonObject.put(ACCESS_TOKEN_HASH, transaction.getAHash());
        String payload = IdentityProviderUtil.B64Encode(jsonObject.toJSONString());

        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(Constants.IDP_SERVICE_APP_ID);
        jwtSignatureRequestDto.setReferenceId(Constants.SIGN_REFERENCE_ID);
        jwtSignatureRequestDto.setDataToSign(payload);
        jwtSignatureRequestDto.setIncludePayload(true);
        jwtSignatureRequestDto.setIncludeCertificate(true);
        jwtSignatureRequestDto.setIncludeCertHash(true);
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }

    @Override
    public List<String> getOptionalIdTokenClaims() {
        //return Arrays.asList(NONCE, ACR, ACCESS_TOKEN_HASH, AUTH_TIME);
        return Arrays.asList();
    }

    @Override
    public String getAccessToken(IdPTransaction transaction) {
        JSONObject jsonObject=new JSONObject();
        jsonObject.put(ISS, issuerId);
        jsonObject.put(SUB, transaction.getUserToken());
        jsonObject.put(AUD, transaction.getClientId());
        long issueTime = IdentityProviderUtil.getEpochSeconds();
        jsonObject.put(IAT, issueTime);
        //TODO Need to discuss -> jsonObject.put(JTI, transaction.getUserToken());
        jsonObject.put(SCOPE, transaction.getScopes()); //scopes as received in authorize request
        jsonObject.put(EXP, issueTime + (accessTokenExpireSeconds<=0 ? 3600 : accessTokenExpireSeconds));
        String payload = IdentityProviderUtil.B64Encode(jsonObject.toJSONString());

        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(Constants.IDP_SERVICE_APP_ID);
        jwtSignatureRequestDto.setReferenceId(Constants.SIGN_REFERENCE_ID);
        jwtSignatureRequestDto.setIncludePayload(true);
        jwtSignatureRequestDto.setIncludeCertificate(true);
        jwtSignatureRequestDto.setDataToSign(payload);
        jwtSignatureRequestDto.setIncludeCertHash(true);
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        return responseDto.getJwtSignedData();
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
                    .expirationTime(Date.from(Instant.now().plusSeconds(clientAssertionExpireSeconds)))
                    .issueTime(Date.from(Instant.now().minusSeconds(clientAssertionExpireSeconds)))
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
        JWTSignatureVerifyRequestDto signatureVerifyRequestDto = new JWTSignatureVerifyRequestDto();
        JWTSignatureVerifyResponseDto responseDto = signatureService.jwtVerify(signatureVerifyRequestDto);
        if(!responseDto.isSignatureValid()) {
            log.error("Access token signature verification failed");
            throw new NotAuthenticatedException();
        }
        try {
            JWT jwt = JWTParser.parse(accessToken);
            JWTClaimsSetVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    .audience(clientId)
                    .issuer(issuerId)
                    .subject(subject)
                    .expirationTime(Date.from(Instant.now().plusSeconds(accessTokenExpireSeconds)))
                    .issueTime(Date.from(Instant.now().minusSeconds(accessTokenExpireSeconds)))
                    .build(), REQUIRED_CLIENT_ASSERTION_CLAIMS);
            claimsSetVerifier.verify(jwt.getJWTClaimsSet(), null);

        } catch (Exception e) {
            log.error("Access token claims verification failed", e);
            throw new NotAuthenticatedException();
        }
    }
}
