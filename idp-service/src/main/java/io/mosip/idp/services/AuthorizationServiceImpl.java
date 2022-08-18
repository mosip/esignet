/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.core.dto.KycAuthRequest;
import io.mosip.idp.core.dto.KycAuthResponse;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.spi.AuthenticationWrapper;
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
    private ObjectMapper objectMapper;

    @Value("${mosip.idp.scope.claims}")
    private Map<String, List<String>> scopeClaims;

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
        Claims finalizedClaims = getRequestedClaims(oauthDetailReqDto.getScope(), oauthDetailReqDto.getClaims(), result.get());

        final String transactionId = UUID.randomUUID().toString();
        OauthDetailResponse oauthDetailResponse = new OauthDetailResponse();
        oauthDetailResponse.setTransactionId(transactionId);
        //TODO - Need to set authFactors in response based on the ACR claim (registered & request param)
        setClaimNamesInResponse(finalizedClaims, oauthDetailResponse);
        setUIConfigMap(oauthDetailResponse);

        //Cache the transaction
        IdPTransaction idPTransaction = new IdPTransaction();
        idPTransaction.setRedirectUri(oauthDetailReqDto.getRedirectUri());
        idPTransaction.setRequestedClaims(finalizedClaims);
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



    private Claims getRequestedClaims(String requestedScope, Claims requestedClaims, ClientDetail clientDetail) {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        try {
            //Assumption is registered claims MUST have at least 1 claim under user_info and id_token
            Claims registeredClaims = objectMapper.readValue(clientDetail.getClaims(), Claims.class);
            resolvedClaims.setId_token(registeredClaims.getId_token());

            Set<String> registeredClaimNames = registeredClaims.getUserinfo().keySet();
            //get claims based on scope
            List<String> claimNames = resolveScopeToClaims(requestedScope);
            //Remove unregistered user claims and add retained claims into resolvedClaims
            claimNames.retainAll(registeredClaimNames);
            claimNames.forEach( claimName -> { resolvedClaims.getUserinfo().put(claimName, null); });

            //override user claims from authorize - claims request parameter
            if(requestedClaims != null && requestedClaims.getUserinfo() != null ) {
                for(String claimName : requestedClaims.getUserinfo().keySet()) {
                    if(registeredClaimNames.contains(claimName)) //Add it only if its registered claim
                        resolvedClaims.getUserinfo().put(claimName,requestedClaims.getUserinfo().get(claimName));
                }
            }

            //set id token claim as per the registered id token claim
            resolvedClaims.setId_token(registeredClaims.getId_token());
            registeredClaimNames = registeredClaims.getUserinfo().keySet();
            //override id token claim from authorize - claims request parameter
            if(requestedClaims != null && requestedClaims.getId_token() != null ) {
                for(String claimName : requestedClaims.getId_token().keySet()) {
                    if(registeredClaimNames.contains(claimName)) //Add it only if its registered claim
                        resolvedClaims.getId_token().put(claimName, requestedClaims.getId_token().get(claimName));
                }
            }
            logger.info("Resolved claims for this session", objectMapper.writeValueAsString(resolvedClaims));
        } catch (Exception e) {
            logger.error("Failed to parse claims", e);
        }
        return resolvedClaims;
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
}
