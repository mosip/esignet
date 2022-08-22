package io.mosip.idp.services;

import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.dto.KycExchangeRequest;
import io.mosip.idp.core.dto.TokenRequest;
import io.mosip.idp.core.dto.TokenResponse;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidClientException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.AuthorizationService;
import io.mosip.idp.core.spi.OAuthService;
import io.mosip.idp.core.spi.TokenGeneratorService;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.domain.ClientDetail;
import io.mosip.idp.repositories.ClientDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class OAuthServiceImpl implements OAuthService {

    private static final Logger logger = LoggerFactory.getLogger(OAuthServiceImpl.class);

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private TokenGeneratorService tokenGeneratorService;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Override
    public TokenResponse getTokens(TokenRequest tokenRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getSetAuthenticatedTransaction(tokenRequest.getCode(), null, null);
        if(transaction == null)
            throw new IdPException(ErrorConstants.INVALID_CODE);

        Optional<ClientDetail> result = clientDetailRepository.findByIdAndStatus(tokenRequest.getClient_id(),
                Constants.CLIENT_ACTIVE_STATUS);
        if(!result.isPresent())
            throw new InvalidClientException(ErrorConstants.INVALID_CLIENT_ID);

        switch (tokenRequest.getClient_assertion_type()) {
            case "urn:ietf:params:oauth:client-assertion-type:jwt-bearer" :
                validateJwtClientAssertion(result.get(), tokenRequest.getClient_assertion());
                break;
            default:
                throw new IdPException(ErrorConstants.INVALID_ASSERTION_TYPE);
        }

        // TODO - Sign KYC exchange request with MISP key
        KycExchangeRequest kycExchangeRequest = new KycExchangeRequest();
        kycExchangeRequest.setClientId(tokenRequest.getClient_id());
        kycExchangeRequest.setKycToken(transaction.getKycToken());
        kycExchangeRequest.setAcceptedClaims(transaction.getAcceptedClaims());
        String encryptedKyc = authenticationWrapper.doKycExchange(kycExchangeRequest);

        TokenResponse tokenResponse = generateTokens();
        // cache kyc with access-token as key
        String accessTokenHash = ""; //TODO - generate access token hash - keymanager
        transaction.setAccessToken(tokenResponse.getAccess_token());
        transaction.setIdToken(tokenResponse.getId_token());
        transaction.setEncryptedKyc(encryptedKyc);
        cacheUtilService.getSetKycTransaction(accessTokenHash, transaction);

        return tokenResponse;
    }



    private TokenResponse generateTokens() {
        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setId_token(tokenGeneratorService.getIDToken());
        tokenResponse.setAccess_token(tokenGeneratorService.getAccessToken());
        tokenResponse.setScope("openid"); //TODO ??
        tokenResponse.setExpires_in(10); //TODO why is this Required ?
        return tokenResponse;
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

        //TODO my work - Need key-manager integration
        //verify signature
        //on valid signature, verify each claims on JWT payload
        //throw exception on any claim check failure
    }
}
