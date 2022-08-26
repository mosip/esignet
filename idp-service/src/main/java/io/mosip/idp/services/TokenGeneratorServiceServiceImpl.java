/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.signature.dto.JWSSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.NonNull;
import org.json.simple.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Service
public class TokenGeneratorServiceServiceImpl implements io.mosip.idp.core.spi.TokenGeneratorService {

    private static final Logger logger = LoggerFactory.getLogger(TokenGeneratorServiceServiceImpl.class);

    @Autowired
    private SignatureService signatureService;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Value("${mosip.idp.id-token.expire.seconds:60}")
    private long idTokenExpireSeconds;

    @Value("${mosip.idp.access-token.expire.seconds:60}")
    private long accessTokenExpireSeconds;

    @Value("${mosip.idp.discovery.issuer-id}")
    private String issuerId;

    @Value("#{${mosip.idp.openid.scope.claims}}")
    private Map<String, List<String>> claims;


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

        JWSSignatureRequestDto jwsSignatureRequestDto = new JWSSignatureRequestDto();
        jwsSignatureRequestDto.setIncludePayload(true);
        jwsSignatureRequestDto.setB64JWSHeaderParam(true);
        jwsSignatureRequestDto.setApplicationId(Constants.IDP_SERVICE_APP_ID);
        jwsSignatureRequestDto.setReferenceId(Constants.SIGN_REFERENCE_ID);
        jwsSignatureRequestDto.setDataToSign(jsonObject.toJSONString());
        JWTSignatureResponseDto responseDto = signatureService.jwsSign(jwsSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }

    @Override
    public List<String> getOptionalIdTokenClaims() {
        return Arrays.asList(NONCE, ACR, ACCESS_TOKEN_HASH, AUTH_TIME);
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

        JWSSignatureRequestDto jwsSignatureRequestDto = new JWSSignatureRequestDto();
        jwsSignatureRequestDto.setIncludePayload(true);
        jwsSignatureRequestDto.setB64JWSHeaderParam(true);
        jwsSignatureRequestDto.setApplicationId(Constants.IDP_SERVICE_APP_ID);
        jwsSignatureRequestDto.setReferenceId(Constants.SIGN_REFERENCE_ID);
        jwsSignatureRequestDto.setDataToSign(jsonObject.toJSONString());
        JWTSignatureResponseDto responseDto = signatureService.jwsSign(jwsSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }
}
