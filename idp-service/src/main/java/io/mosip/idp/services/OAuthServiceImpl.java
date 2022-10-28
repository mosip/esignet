/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.*;
import io.mosip.idp.core.spi.*;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.NotImplementedException;
import org.jose4j.jwk.JsonWebKeySet;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.validation.Valid;

@Slf4j
@Service
public class OAuthServiceImpl implements OAuthService {


    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Value("${mosip.idp.access-token.expire.seconds:60}")
    private int accessTokenExpireSeconds;


    @Override
    public TokenResponse getTokens(@Valid TokenRequest tokenRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getAuthenticatedTransaction(tokenRequest.getCode());
        if(transaction == null)
            throw new InvalidTransactionException();

        if(transaction.getKycToken() == null)
            throw new IdPException(ErrorConstants.INVALID_TRANSACTION);

        if(!transaction.getClientId().equals(tokenRequest.getClient_id()))
            throw new InvalidClientException();

        if(!transaction.getRedirectUri().equals(tokenRequest.getRedirect_uri()))
            throw new IdPException(ErrorConstants.INVALID_REDIRECT_URI);

        io.mosip.idp.core.dto.ClientDetail clientDetailDto = clientManagementService.getClientDetails(tokenRequest.getClient_id());

        authenticateClient(tokenRequest, clientDetailDto);

        IdentityProviderUtil.validateRedirectURI(clientDetailDto.getRedirectUris(), tokenRequest.getRedirect_uri());

        KycExchangeResult kycExchangeResult;
        try {
            KycExchangeRequest kycExchangeRequest = new KycExchangeRequest();
            kycExchangeRequest.setKycToken(transaction.getKycToken());
            kycExchangeRequest.setAcceptedClaims(transaction.getAcceptedClaims());
            kycExchangeRequest.setClaimsLocales(transaction.getClaimsLocales());
            kycExchangeResult = authenticationWrapper.doKycExchange(transaction.getRelyingPartyId(),
                    transaction.getClientId(), kycExchangeRequest);
        } catch (KycExchangeException e) {
            log.error("KYC exchange failed", e);
            throw new IdPException(e.getErrorCode());
        }

        if(kycExchangeResult == null || kycExchangeResult.getEncryptedKyc() == null)
            throw new IdPException(ErrorConstants.DATA_EXCHANGE_FAILED);

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccess_token(tokenService.getAccessToken(transaction));
        String accessTokenHash = IdentityProviderUtil.generateOIDCAtHash(tokenResponse.getAccess_token());
        transaction.setAHash(accessTokenHash);
        tokenResponse.setId_token(tokenService.getIDToken(transaction));
        tokenResponse.setExpires_in(accessTokenExpireSeconds);
        tokenResponse.setToken_type(Constants.BEARER);

        // cache kyc with access-token as key
        transaction.setEncryptedKyc(kycExchangeResult.getEncryptedKyc());
        cacheUtilService.setKycTransaction(accessTokenHash, transaction);

        return tokenResponse;
    }

    @Override
    public JsonWebKeySet getJwks() {
        throw new NotImplementedException("Under implementation...");
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
