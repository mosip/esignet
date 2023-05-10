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
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.spi.LinkedAuthorizationService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.core.util.KafkaHelperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.constants.ErrorConstants.OPERATION_UNIMPLEMENTED;

@Slf4j
@Service
@Qualifier("linkedAuthorizationServiceV2")
public class LinkedAuthorizationServiceV2Impl implements LinkedAuthorizationService {

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private AuthorizationHelperService authorizationHelperService;

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    private KafkaHelperService kafkaHelperService;
    
    @Autowired
    private AuditPlugin auditWrapper;

    @Value("${mosip.esignet.link-code-expire-in-secs}")
    private int linkCodeExpiryInSeconds;

    @Value("#{${mosip.esignet.ui.config.key-values}}")
    private Map<String, Object> uiConfigMap;

    @Value("${mosip.esignet.kafka.linked-session.topic}")
    private String linkedSessionTopicName;

    @Value("${mosip.esignet.kafka.linked-auth-code.topic}")
    private String linkedAuthCodeTopicName;

    @Value("${mosip.esignet.link-code-length:15}")
    private int linkCodeLength;

    @Override
    public LinkCodeResponse generateLinkCode(LinkCodeRequest linkCodeRequest) throws EsignetException {
        throw new UnsupportedOperationException("This method is not supported in this version");
    }

    @Override
    public LinkTransactionResponse linkTransaction(LinkTransactionRequest linkTransactionRequest) throws EsignetException {
        throw new UnsupportedOperationException("This method is not supported in this version");
    }

    @Override
    public OtpResponse sendOtp(OtpRequest otpRequest) throws EsignetException {
        throw new UnsupportedOperationException("This method is not supported in this version");
    }

    @Override
    public LinkedKycAuthResponse authenticateUser(LinkedKycAuthRequest linkedKycAuthRequest) throws EsignetException {
        OIDCTransaction transaction = cacheUtilService.getLinkedSessionTransaction(linkedKycAuthRequest.getLinkedTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        //Validate provided challenge list auth-factors with resolved auth-factors for the transaction.
        Set<List<AuthenticationFactor>> providedAuthFactors = authorizationHelperService.getProvidedAuthFactors(transaction, linkedKycAuthRequest.getChallengeList());
        KycAuthResult kycAuthResult = authorizationHelperService.delegateAuthenticateRequest(linkedKycAuthRequest.getLinkedTransactionId(),
                linkedKycAuthRequest.getIndividualId(), linkedKycAuthRequest.getChallengeList(), transaction);
        //cache tokens on successful response
        transaction.setPartnerSpecificUserToken(kycAuthResult.getPartnerSpecificUserToken());
        transaction.setKycToken(kycAuthResult.getKycToken());
        transaction.setAuthTimeInSeconds(IdentityProviderUtil.getEpochSeconds());
        authorizationHelperService.setIndividualId(linkedKycAuthRequest.getIndividualId(), transaction);
        transaction.setProvidedAuthFactors(providedAuthFactors.stream().map(acrFactors -> acrFactors.stream()
                .map(AuthenticationFactor::getType)
                .collect(Collectors.toList())).collect(Collectors.toSet()));
        String consentIdentifier = transaction.getClientId() + transaction.getPartnerSpecificUserToken();
        UserConsent userConsent = ConsentCache.getUserConsent(consentIdentifier);
        Consent consent = AuthorizationHelperService.validateConsent(transaction, userConsent);
        transaction.setConsent(consent);
        if(consent.equals(Consent.NOCAPTURE)){
            transaction.setAcceptedClaims(userConsent.getAcceptedClaims());
            transaction.setPermittedScopes(userConsent.getAuthorizedScopes());
            kafkaHelperService.publish(linkedAuthCodeTopicName, transaction.getLinkedTransactionId());
        }
        cacheUtilService.setLinkedAuthenticatedTransaction(linkedKycAuthRequest.getLinkedTransactionId(), transaction);

        LinkedKycAuthResponse authRespDto = new LinkedKycAuthResponse();
        authRespDto.setLinkedTransactionId(linkedKycAuthRequest.getLinkedTransactionId());
        auditWrapper.logAudit(Action.LINK_AUTHENTICATE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(null, transaction), null);
        return authRespDto;
    }

    @Override
    public LinkedConsentResponse saveConsent(LinkedConsentRequest linkedConsentRequest) throws EsignetException {
        throw new UnsupportedOperationException("This method is not supported in this version");
    }

    @Async
    @Override
    public void getLinkStatus(DeferredResult deferredResult, LinkStatusRequest linkStatusRequest) throws EsignetException {
        throw new UnsupportedOperationException(OPERATION_UNIMPLEMENTED);
    }

    @Async
    @Override
    public void getLinkAuthCode(DeferredResult deferredResult, LinkAuthCodeRequest linkAuthCodeRequest) throws EsignetException {
        throw new UnsupportedOperationException("This method is not supported in this version");
    }

}
