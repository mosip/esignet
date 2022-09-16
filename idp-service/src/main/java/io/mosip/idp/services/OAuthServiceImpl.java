/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidClientException;
import io.mosip.idp.core.exception.NotAuthenticatedException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.AuthorizationService;
import io.mosip.idp.core.spi.OAuthService;
import io.mosip.idp.core.spi.TokenService;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.idp.entity.ClientDetail;
import io.mosip.idp.repository.ClientDetailRepository;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jwk.JsonWebKeySet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.validation.Valid;
import java.util.Optional;

@Slf4j
@Service
public class OAuthServiceImpl implements OAuthService {


    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private SignatureService signatureService;

    @Value("${mosip.idp.cache.key.hash.algorithm}")
    private String hashingAlgorithm;

    @Value("${mosip.idp.access-token.expire.seconds:60}")
    private int accessTokenExpireSeconds;


    @Override
    public TokenResponse getTokens(@Valid TokenRequest tokenRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getAuthenticatedTransaction(tokenRequest.getCode());
        if(transaction == null)
            throw new NotAuthenticatedException();

        if(!transaction.getClientId().equals(tokenRequest.getClient_id()))
            throw new InvalidClientException();

        if(!transaction.getRedirectUri().equals(tokenRequest.getRedirect_uri()))
            throw new IdPException(ErrorConstants.INVALID_REDIRECT_URI);

        Optional<ClientDetail> result = clientDetailRepository.findByIdAndStatus(tokenRequest.getClient_id(),
                Constants.CLIENT_ACTIVE_STATUS);
        if(!result.isPresent())
            throw new InvalidClientException();

        authenticateClient(tokenRequest, result.get());

        IdentityProviderUtil.validateRedirectURI(result.get().getRedirectUris(), tokenRequest.getRedirect_uri());

        KycExchangeRequest kycExchangeRequest = new KycExchangeRequest();
        kycExchangeRequest.setClientId(tokenRequest.getClient_id());
        kycExchangeRequest.setKycToken(transaction.getKycToken());
        kycExchangeRequest.setAcceptedClaims(transaction.getAcceptedClaims());
        kycExchangeRequest.setClaimsLocales(IdentityProviderUtil.splitAndTrimValue(transaction.getClaimsLocales(), Constants.SPACE));
        ResponseWrapper<KycExchangeResult> exchangeResult = authenticationWrapper.doKycExchange(kycExchangeRequest);

        if(exchangeResult.getErrors() != null && !exchangeResult.getErrors().isEmpty()) {
            throw new IdPException(exchangeResult.getErrors().get(0).getErrorCode());
        }

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccess_token(tokenService.getAccessToken(transaction));
        String accessTokenHash = IdentityProviderUtil.generateAccessTokenHash(tokenResponse.getAccess_token());
        transaction.setAHash(accessTokenHash);
        tokenResponse.setId_token(tokenService.getIDToken(transaction));
        tokenResponse.setExpires_in(accessTokenExpireSeconds);
        tokenResponse.setToken_type(Constants.BEARER);

        // cache kyc with access-token as key
        transaction.setIdHash(IdentityProviderUtil.generateAccessTokenHash(tokenResponse.getId_token()));
        transaction.setEncryptedKyc(exchangeResult.getResponse().getEncryptedKyc());
        cacheUtilService.setKycTransaction(accessTokenHash, transaction);

        return tokenResponse;
    }

    @Override
    public JsonWebKeySet getJwks() {
        return null;
    }

    private void authenticateClient(TokenRequest tokenRequest, ClientDetail clientDetail) throws IdPException {
        switch (tokenRequest.getClient_assertion_type()) {
            case JWT_BEARER_TYPE:
                validateJwtClientAssertion(clientDetail.getId(), clientDetail.getPublicKey(), tokenRequest.getClient_assertion());
                break;
            default:
                throw new IdPException(ErrorConstants.INVALID_ASSERTION_TYPE);
        }
    }


    private void validateJwtClientAssertion(String ClientId, String jwk, String clientAssertion) throws IdPException {
        if(clientAssertion == null || clientAssertion.isBlank())
            throw new IdPException(ErrorConstants.INVALID_ASSERTION);

        //verify signature
        //on valid signature, verify each claims on JWT payload
        tokenService.verifyClientAssertionToken(ClientId, jwk, clientAssertion);
    }
}
