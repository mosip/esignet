/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.api.dto.KycAuthResult;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.AuthorizationService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.constants.ErrorConstants.OPERATION_UNIMPLEMENTED;

@Slf4j
@Service
@Qualifier("authorizationServiceV2")
public class AuthorizationServiceV2Impl implements AuthorizationService {

    private final CacheUtilService cacheUtilService;


    private final AuthorizationHelperService authorizationHelperService;


    private final AuditPlugin auditWrapper;

    public AuthorizationServiceV2Impl(CacheUtilService cacheUtilService, AuthorizationHelperService authorizationHelperService, AuditPlugin auditWrapper) {
        this.cacheUtilService = cacheUtilService;
        this.authorizationHelperService = authorizationHelperService;
        this.auditWrapper = auditWrapper;
    }

    @Override
    public OAuthDetailResponse getOauthDetails(OAuthDetailRequest oauthDetailReqDto) throws EsignetException {
        throw new UnsupportedOperationException("Method Not Supported in AuthorizationService V2");
    }

    @Override
    public OtpResponse sendOtp(OtpRequest otpRequest) throws EsignetException {
        throw new UnsupportedOperationException("Method Not Supported in AuthorizationService V2");
    }

    @Override
    public AuthResponse authenticateUser(AuthRequest authRequest)  throws EsignetException {
        OIDCTransaction transaction = cacheUtilService.getPreAuthTransaction(authRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();
        //Validate provided challenge list auth-factors with resolved auth-factors for the transaction.
        Set<List<AuthenticationFactor>> providedAuthFactors = authorizationHelperService.getProvidedAuthFactors(transaction,
                authRequest.getChallengeList());
        KycAuthResult kycAuthResult = authorizationHelperService.delegateAuthenticateRequest(authRequest.getTransactionId(),
                authRequest.getIndividualId(), authRequest.getChallengeList(), transaction);
        //cache tokens on successful response
        transaction.setPartnerSpecificUserToken(kycAuthResult.getPartnerSpecificUserToken());
        transaction.setKycToken(kycAuthResult.getKycToken());
        transaction.setAuthTimeInSeconds(IdentityProviderUtil.getEpochSeconds());
        transaction.setProvidedAuthFactors(providedAuthFactors.stream().map(acrFactors -> acrFactors.stream()
                        .map(AuthenticationFactor::getType)
                        .collect(Collectors.toList())).collect(Collectors.toSet()));
        //create consent identifier
        String consentIdentifier = transaction.getClientId() + transaction.getPartnerSpecificUserToken();
        UserConsent userConsent = ConsentCache.getUserConsent(consentIdentifier);
        Consent consent = AuthorizationHelperService.validateConsent(transaction, userConsent);
        transaction.setConsent(consent);
        if(consent.equals(Consent.NOCAPTURE)){
            transaction.setAcceptedClaims(userConsent.getAcceptedClaims());
            transaction.setPermittedScopes(userConsent.getAuthorizedScopes());
        }
        authorizationHelperService.setIndividualId(authRequest.getIndividualId(), transaction);

        cacheUtilService.setAuthenticatedTransaction(authRequest.getTransactionId(), transaction);

        auditWrapper.logAudit(Action.AUTHENTICATE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(authRequest.getTransactionId(), transaction), null);

        AuthResponse authRespDto = new AuthResponse();
        authRespDto.setTransactionId(authRequest.getTransactionId());
        return authRespDto;
    }



    @Override
    public AuthCodeResponse getAuthCode(AuthCodeRequest authCodeRequest) throws EsignetException {
        throw new UnsupportedOperationException(OPERATION_UNIMPLEMENTED);
    }
}
