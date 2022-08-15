/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.domain.ClientDetail;
import io.mosip.idp.dto.*;
import io.mosip.idp.exception.IdPException;
import io.mosip.idp.exception.InvalidClientException;
import io.mosip.idp.repositories.ClientDetailRepository;
import io.mosip.idp.util.Constants;
import io.mosip.idp.util.ErrorConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class InternalApiService {

    private static final Logger logger = LoggerFactory.getLogger(InternalApiService.class);

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    public OauthRespDto getOauthDetails(OauthReqDto oauthReqDto) throws IdPException {
        Optional<ClientDetail> result = clientDetailRepository.findByIdAndStatus(oauthReqDto.getClientId(),
                Constants.CLIENT_ACTIVE_STATUS);

        if(!result.isPresent())
            throw new InvalidClientException(ErrorConstants.INVALID_CLIENT_ID);

        if(result.get().getRedirectUris().contains(oauthReqDto.getRedirectUri()))
            throw new IdPException(ErrorConstants.INVALID_REDIRECT_URI);

        final String transactionId = UUID.randomUUID().toString();
        OauthRespDto oauthRespDto = new OauthRespDto();
        oauthRespDto.setTransactionId(transactionId);
        oauthRespDto.setAuthFactors(getClientAuthFactors(result.get()));
        oauthRespDto.setEssentialClaims(getEssentialClaims(result.get()));
        oauthRespDto.setOptionalClaims(getOptionalClaims(result.get()));
        oauthRespDto.setConfigs(getUIConfigMap());

        IdPTransaction idPTransaction = new IdPTransaction();
        idPTransaction.setRedirectUri(oauthReqDto.getRedirectUri());
        getSetTransaction(transactionId, idPTransaction);
        return oauthRespDto;
    }

    public OtpRespDto sendOtp(OtpReqDto otpReqDto) throws IdPException {
        validateTransaction(otpReqDto.getTransactionId());
        String message = ""; // Need to call auth wrapper to send out OTP.
        OtpRespDto otpRespDto = new OtpRespDto();
        otpRespDto.setTransactionId(otpReqDto.getTransactionId());
        otpRespDto.setMessage(message);
        return otpRespDto;
    }

    public AuthRespDto authenticateEndUser(AuthReqDto authReqDto) throws IdPException {
        IdPTransaction transaction = validateTransaction(authReqDto.getTransactionId());

        // Need to call auth wrapper to authenticate end-user.
        KycAuthRespDto kycAuthRespDto = null; // kyc-auth api call

        //cache tokens on successful response
        transaction.setUserToken(kycAuthRespDto.getAuthToken());
        transaction.setKycToken(kycAuthRespDto.getKycToken());
        getSetTransaction(authReqDto.getTransactionId(), transaction);

        AuthRespDto authRespDto = new AuthRespDto();
        authRespDto.setTransactionId(authReqDto.getTransactionId());
        return authRespDto;
    }


    @CacheEvict(value = "transactions", key = "#authCodeReqDto.getTransactionId()")
    public IdPTransaction getAuthorizationCode(AuthCodeReqDto authCodeReqDto) {
        IdPTransaction transaction = getSetTransaction(authCodeReqDto.getTransactionId(), null);
        if(transaction == null) {
            transaction = new IdPTransaction();
            transaction.setError(ErrorConstants.INVALID_TRANSACTION);
            return transaction;
        }

        String authCode = UUID.randomUUID().toString();
        // cache consent with auth-code as key
        transaction.setCode(authCode);
        transaction.setAcceptedClaims(authCodeReqDto.getAcceptedClaims());
        return getSetAuthenticatedTransaction(authCode, transaction);
    }


    private IdPTransaction validateTransaction(String transactionId) throws IdPException {
        IdPTransaction transaction = getSetTransaction(transactionId, null);
        if(transaction == null)
            throw new IdPException(ErrorConstants.INVALID_TRANSACTION);
        return transaction;
    }

    @Cacheable(value = "transactions", key = "#transactionId", unless = "#result != null")
    private IdPTransaction getSetTransaction(String transactionId, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    @Cacheable(value = "authenticated", key = "#authCode", unless = "#result != null")
    public IdPTransaction getSetAuthenticatedTransaction(String authCode, IdPTransaction idPTransaction) {
        return idPTransaction;
    }

    private String getClientAuthFactors(ClientDetail clientDetail) {
        return "{}";
    }

    private List<String> getEssentialClaims(ClientDetail clientDetail) {
        return new ArrayList<String>();
    }

    private List<String> getOptionalClaims(ClientDetail clientDetail) {
        return new ArrayList<String>();
    }

    private Map<String, String> getUIConfigMap() {
        return null;
    }
}
