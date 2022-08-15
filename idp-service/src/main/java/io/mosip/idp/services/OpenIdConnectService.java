/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.domain.ClientDetail;
import io.mosip.idp.dto.IdPTransaction;
import io.mosip.idp.dto.TokenReqDto;
import io.mosip.idp.dto.TokenRespDto;
import io.mosip.idp.exception.IdPException;
import io.mosip.idp.exception.InvalidClientException;
import io.mosip.idp.exception.NotAuthenticatedException;
import io.mosip.idp.repositories.ClientDetailRepository;
import io.mosip.idp.util.Constants;
import io.mosip.idp.util.ErrorConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OpenIdConnectService {

    private static final Logger logger = LoggerFactory.getLogger(InternalApiService.class);

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Autowired
    private InternalApiService internalApiService;

    @CacheEvict(value = "authenticated", key = "#tokenReqDto.getCode()")
    public TokenRespDto getTokens(TokenReqDto tokenReqDto) throws IdPException {
        IdPTransaction transaction = internalApiService.getSetAuthenticatedTransaction(tokenReqDto.getCode(),
                null);
        if(transaction == null)
            throw new IdPException(ErrorConstants.INVALID_CODE);

        Optional<ClientDetail> result = clientDetailRepository.findByIdAndStatus(tokenReqDto.getClient_id(),
                Constants.CLIENT_ACTIVE_STATUS);
        if(!result.isPresent())
            throw new InvalidClientException(ErrorConstants.INVALID_CLIENT_ID);

        switch (tokenReqDto.getClient_assertion_type()) {
            case "urn:ietf:params:oauth:client-assertion-type:jwt-bearer" :
                validateJwtClientAssertion(result.get(), tokenReqDto.getClient_assertion());
                break;
            default:
                throw new IdPException(ErrorConstants.INVALID_ASSERTION_TYPE);
        }

        // TODO
        // invoke kyc-exchange API with (clientid, kyc-token, consent) signed with MISP key
        // successful response is encrypted kyc
        String encryptedKyc = "";

        TokenRespDto tokenRespDto = generateTokens();
        // cache kyc with access-token as key
        String accessTokenHash = ""; //TODO
        transaction.setAccessToken(tokenRespDto.getAccess_token());
        transaction.setIdToken(tokenRespDto.getId_token());
        transaction.setEncryptedKyc(encryptedKyc);
        getSetKycTransaction(accessTokenHash, transaction);

        return tokenRespDto;
    }

    public String getCachedUserInfo(String accessToken) throws NotAuthenticatedException {
        if(accessToken == null || accessToken.isBlank())
            throw new NotAuthenticatedException();

        //validate access token expiry

        String accessTokenHash = "";
        IdPTransaction transaction = getSetKycTransaction(accessTokenHash, null);
        if(transaction == null)
            throw new NotAuthenticatedException();

        return transaction.getEncryptedKyc();
    }


    @Cacheable(value = "kyc", key = "#accessToken", unless = "#result != null")
    private IdPTransaction getSetKycTransaction(String accessToken, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    private TokenRespDto generateTokens() {
        TokenRespDto tokenRespDto = new TokenRespDto();
        // TODO
        String accessToken = "";
        String idToken = "";
        int expiresIn = 10;
        return tokenRespDto;
    }

    /**
     * Client's authentication token when using token endpoint
     * This method validates the client's authentication token W.R.T private_key_jwt method.
     * The JWT MUST contain the following REQUIRED Claim Values:
     * iss : Issuer. This MUST contain the client_id of the OAuth Client.
     * sub : Subject. This MUST contain the client_id of the OAuth Client.
     * aud : Audience. Value that identifies the Authorization Server as an intended audience.
     * The Authorization Server MUST verify that it is an intended audience for the token.
     * The Audience SHOULD be the URL of the Authorization Server's Token Endpoint.
     * jti :  JWT ID. A unique identifier for the token, which can be used to prevent reuse of the token.
     * These tokens MUST only be used once, unless conditions for reuse were negotiated between the parties;
     * any such negotiation is beyond the scope of this specification.
     * exp : Expiration time on or after which the ID Token MUST NOT be accepted for processing.
     * iat : OPTIONAL. Time at which the JWT was issued.
     */
    private void validateJwtClientAssertion(ClientDetail clientDetail, String clientAssertion) throws IdPException {
        if(clientAssertion == null || clientAssertion.isBlank())
            throw new IdPException(ErrorConstants.INVALID_ASSERTION);

        //verify signature
        //on valid signature, verify each claims on JWT payload
        //throw exception on any claim check failure
    }

}
