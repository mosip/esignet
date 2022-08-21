/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.core.dto.KycAuthRequest;
import io.mosip.idp.core.dto.KycAuthResponse;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.TokenGeneratorService;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.idp.domain.ClientDetail;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidClientException;
import io.mosip.idp.repositories.ClientDetailRepository;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AuthorizationServiceImpl implements io.mosip.idp.core.spi.AuthorizationService {

    private static final Logger logger = LoggerFactory.getLogger(AuthorizationServiceImpl.class);

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private TokenGeneratorService tokenGeneratorService;

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${mosip.idp.openid.scope.claims}")
    private Map<String, List<String>> scopeClaims;

    @Value("${mosip.idp.supported.authorize.scopes}")
    private List<String> authorizeScopes;

    @Value("${mosip.idp.ui.config}")
    private Map<String, List<String>> uiConfigs;

    @Override
    public OauthDetailResponse getOauthDetails(String nonce, OauthDetailRequest oauthDetailReqDto) throws IdPException {
        Optional<ClientDetail> result = clientDetailRepository.findByIdAndStatus(oauthDetailReqDto.getClientId(),
                Constants.CLIENT_ACTIVE_STATUS);

        if(!result.isPresent())
            throw new InvalidClientException(ErrorConstants.INVALID_CLIENT_ID);

        if(result.get().getRedirectUris().contains(oauthDetailReqDto.getRedirectUri()))
            throw new IdPException(ErrorConstants.INVALID_REDIRECT_URI);

        //Resolve the final set of claims based on registered and request parameter.
        Claims resolvedClaims = getRequestedClaims(oauthDetailReqDto, result.get());

        final String transactionId = UUID.randomUUID().toString();
        OauthDetailResponse oauthDetailResponse = new OauthDetailResponse();
        oauthDetailResponse.setTransactionId(transactionId);
        oauthDetailResponse.setAuthFactors(authenticationContextClassRefUtil.getAuthFactors(
               resolvedClaims.getId_token().get(Constants.ACR_CLAIM).getValues()
        ));
        setClaimNamesInResponse(resolvedClaims, oauthDetailResponse);
        setAuthorizeScopes(oauthDetailReqDto.getScope(), oauthDetailResponse);
        setUIConfigMap(oauthDetailResponse);

        //Cache the transaction
        IdPTransaction idPTransaction = new IdPTransaction();
        idPTransaction.setRedirectUri(oauthDetailReqDto.getRedirectUri());
        idPTransaction.setRequestedClaims(resolvedClaims);
        idPTransaction.setNonce(nonce);
        cacheUtilService.getSetTransaction(transactionId, idPTransaction);
        return oauthDetailResponse;
    }

    @Override
    public OtpResponse sendOtp(OtpRequest otpRequest) throws IdPException {
        validateTransaction(otpRequest.getTransactionId());
        SendOtpResult result = authenticationWrapper.sendOtp(otpRequest.getIndividualId(), otpRequest.getChannel());
        if(!result.isStatus())
            throw new IdPException(result.getMessage());

        OtpResponse otpResponse = new OtpResponse();
        otpResponse.setTransactionId(otpRequest.getTransactionId());
        otpResponse.setMessage(result.getMessage());
        return otpResponse;
    }

    @Override
    public AuthResponse authenticateUser(KycAuthRequest kycAuthRequest)  throws IdPException {
        IdPTransaction transaction = validateTransaction(kycAuthRequest.getTransactionId());

        //TODO Need to sign biometrics / OTP with MISP key - keymanager
        KycAuthResponse result = authenticationWrapper.doKycAuth(kycAuthRequest);
        if(result == null)
            throw new IdPException(ErrorConstants.AUTH_FAILED);

        //cache tokens on successful response
        transaction.setUserToken(result.getUserAuthToken());
        transaction.setKycToken(result.getKycToken());
        cacheUtilService.getSetTransaction(kycAuthRequest.getTransactionId(), transaction);

        AuthResponse authRespDto = new AuthResponse();
        authRespDto.setTransactionId(kycAuthRequest.getTransactionId());
        return authRespDto;
    }

    @Override
    public IdPTransaction getAuthCode(AuthCodeRequest authCodeRequest) {
        IdPTransaction transaction = cacheUtilService.getSetTransaction(authCodeRequest.getTransactionId(), null);
        if(transaction == null) {
            transaction = new IdPTransaction();
            transaction.setError(ErrorConstants.INVALID_TRANSACTION);
            return transaction;
        }

        String authCode = UUID.randomUUID().toString();
        // cache consent with auth-code as key
        transaction.setCode(authCode);
        transaction.setAcceptedClaims(authCodeRequest.getAcceptedClaims());
        return cacheUtilService.getSetAuthenticatedTransaction(authCode, authCodeRequest.getTransactionId(), transaction);
    }

    private IdPTransaction validateTransaction(String transactionId) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getSetTransaction(transactionId, null);
        if(transaction == null)
            throw new IdPException(ErrorConstants.INVALID_TRANSACTION);
        return transaction;
    }



    private Claims getRequestedClaims(OauthDetailRequest oauthDetailRequest, ClientDetail clientDetail) {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        resolvedClaims.setId_token(new HashMap<>());

        String requestedScope = oauthDetailRequest.getScope();
        Claims requestedClaims = oauthDetailRequest.getClaims();
        try {
            //Assumption is registered claims MUST have at least 1 user claim
            List<String> registeredUserClaims = objectMapper.readValue(clientDetail.getClaims(), new TypeReference<List<String>>(){});
            //get claims based on scope
            List<String> claimBasedOnScope = resolveScopeToClaims(requestedScope);

            boolean isRequestedUserInfoClaimsPresent = requestedClaims != null && requestedClaims.getUserinfo() != null;
            //claims considered only if part of registered claims
            for(String claimName : registeredUserClaims) {
                if(isRequestedUserInfoClaimsPresent && requestedClaims.getUserinfo().containsKey(claimName))
                    resolvedClaims.getUserinfo().put(claimName, requestedClaims.getUserinfo().get(claimName));
                else if(claimBasedOnScope.contains(claimName))
                    resolvedClaims.getUserinfo().put(claimName, null);
            }

            if(requestedClaims != null && requestedClaims.getId_token() != null ) {
                for(String claimName : tokenGeneratorService.getOptionalIdTokenClaims()) {
                    if(requestedClaims.getId_token().containsKey(claimName))
                        resolvedClaims.getId_token().put(claimName, requestedClaims.getId_token().get(claimName));
                }
            }
            resolveACRClaim(oauthDetailRequest.getAcrValues(), clientDetail.getAcrValues(), resolvedClaims);

        } catch (Exception e) {
            logger.error("Failed to parse claims", e);
        }
        return resolvedClaims;
    }

    private void resolveACRClaim(String requestedAcr, String registeredAcr, Claims claims) {
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setEssential(true);

        //acr_values request parameter takes highest priority
        claimDetail.setValues((requestedAcr != null) ?
                IdentityProviderUtil.splitAndTrimValue(requestedAcr, Constants.SPACE) :
                IdentityProviderUtil.splitAndTrimValue(registeredAcr, Constants.COMMA));

        claims.getId_token().put(Constants.ACR_CLAIM, claimDetail);
    }

    private List<String> resolveScopeToClaims(String scope) {
        List<String> claimNames = new ArrayList<>();
        String[] requestedScopes = IdentityProviderUtil.splitAndTrimValue(scope, Constants.SPACE);
        for(String scopeName : requestedScopes) {
            claimNames.addAll(scopeClaims.getOrDefault(scopeName, new ArrayList<>()));
        }
        return claimNames;
    }

    private void setClaimNamesInResponse(Claims resolvedClaims, OauthDetailResponse oauthDetailResponse) {
        oauthDetailResponse.setEssentialClaims(new ArrayList<>());
        oauthDetailResponse.setOptionalClaims(new ArrayList<>());
        for(Map.Entry<String, ClaimDetail> claim : resolvedClaims.getUserinfo().entrySet()) {
            if(claim.getValue() != null && claim.getValue().isEssential())
                oauthDetailResponse.getEssentialClaims().add(claim.getKey());
            else
                oauthDetailResponse.getOptionalClaims().add(claim.getKey());
        }
    }

    private void setUIConfigMap(OauthDetailResponse oauthDetailResponse) {
        oauthDetailResponse.setConfigs(null);
    }

    private void setAuthorizeScopes(String requestedScopes, OauthDetailResponse oauthDetailResponse) {
        List<String> scopes = Arrays.asList(IdentityProviderUtil.splitAndTrimValue(requestedScopes, Constants.SPACE));
        scopes.retainAll(authorizeScopes);
        oauthDetailResponse.setAuthorizeScopes(scopes);
    }
}
