/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.api.dto.KycAuthResult;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.spi.CaptchaValidator;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.DuplicateLinkCodeException;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.spi.LinkedAuthorizationService;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.core.util.KafkaHelperService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.spi.TokenService.ACR;
import static io.mosip.esignet.core.constants.Constants.*;

@Slf4j
@Service
public class LinkedAuthorizationServiceImpl implements LinkedAuthorizationService {

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
    public LinkCodeResponse generateLinkCode(LinkCodeRequest linkCodeRequest) throws IdPException {
        OIDCTransaction transaction = cacheUtilService.getPreAuthTransaction(linkCodeRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        if(transaction.getCurrentLinkCodeLimit() <= 0)
            throw new IdPException(ErrorConstants.LINK_CODE_LIMIT_REACHED);

        //Duplicate link code is handled only once, duplicate exception on the second try is thrown out.
        String linkCode = null;
        ZonedDateTime expireDateTime = null;
        try {
            linkCode = IdentityProviderUtil.generateRandomAlphaNumeric(linkCodeLength);
            cacheUtilService.setLinkCodeGenerated(authorizationHelperService.getKeyHash(linkCode),
                    new LinkTransactionMetadata(linkCodeRequest.getTransactionId(),null));
        } catch (DuplicateLinkCodeException e) {
            log.error("Generated duplicate link code");
            linkCode = null;
        }

        if(linkCode == null) {
            log.info("Found duplicate link-code, generating new link-code");
            linkCode = IdentityProviderUtil.generateRandomAlphaNumeric(linkCodeLength);
            cacheUtilService.setLinkCodeGenerated(authorizationHelperService.getKeyHash(linkCode),
                    new LinkTransactionMetadata(linkCodeRequest.getTransactionId(),null));
        }

        //add the new link-code to queue and pop/evict the oldest link-code from the queue & cache
        String poppedLinkCode = transaction.getLinkCodeQueue().addLinkCode(linkCode);
        transaction.setCurrentLinkCodeLimit(transaction.getCurrentLinkCodeLimit()-1);
        cacheUtilService.updateTransactionAndEvictLinkCode(linkCodeRequest.getTransactionId(),
                poppedLinkCode == null ? null : authorizationHelperService.getKeyHash(poppedLinkCode), transaction);

        expireDateTime = ZonedDateTime.now(ZoneOffset.UTC).plus(linkCodeExpiryInSeconds, ChronoUnit.SECONDS);
        LinkCodeResponse linkCodeResponse = new LinkCodeResponse();
        linkCodeResponse.setLinkCode(linkCode);
        linkCodeResponse.setTransactionId(linkCodeRequest.getTransactionId());
        linkCodeResponse.setExpireDateTime(expireDateTime == null ? null :
                expireDateTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        return linkCodeResponse;
    }

    @Override
    public LinkTransactionResponse linkTransaction(LinkTransactionRequest linkTransactionRequest) throws IdPException {
        String linkCodeHash = authorizationHelperService.getKeyHash(linkTransactionRequest.getLinkCode());
        LinkTransactionMetadata linkTransactionMetadata = cacheUtilService.getLinkCodeGenerated(linkCodeHash);
        if(linkTransactionMetadata == null || linkTransactionMetadata.getTransactionId() == null)
            throw new IdPException(ErrorConstants.INVALID_LINK_CODE);

        OIDCTransaction transaction = cacheUtilService.getPreAuthTransaction(linkTransactionMetadata.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        log.info("Valid link-code provided, proceeding to generate linkTransactionId");
        ClientDetail clientDetailDto = clientManagementService.getClientDetails(transaction.getClientId());

        //if valid, generate link-transaction-id and move transaction from preauth to linked cache
        String linkedTransactionId = IdentityProviderUtil.createTransactionId(transaction.getNonce());
        transaction.setLinkedTransactionId(linkedTransactionId);
        transaction.setLinkedCodeHash(linkCodeHash);
        transaction = cacheUtilService.setLinkedTransaction(linkTransactionMetadata.getTransactionId(), transaction);
        linkTransactionMetadata.setLinkedTransactionId(linkedTransactionId);
        cacheUtilService.setLinkedCode(linkCodeHash, linkTransactionMetadata);

        LinkTransactionResponse linkTransactionResponse = new LinkTransactionResponse();
        linkTransactionResponse.setLinkTransactionId(linkedTransactionId);
        linkTransactionResponse.setAuthFactors(authenticationContextClassRefUtil.getAuthFactors(
                transaction.getRequestedClaims().getId_token().get(ACR).getValues()));
        Map<String, List> claimsMap = authorizationHelperService.getClaimNames(transaction.getRequestedClaims());
        linkTransactionResponse.setEssentialClaims(claimsMap.get(ESSENTIAL));
        linkTransactionResponse.setVoluntaryClaims(claimsMap.get(VOLUNTARY));
        linkTransactionResponse.setAuthorizeScopes(transaction.getRequestedAuthorizeScopes());
        linkTransactionResponse.setClientName(clientDetailDto.getName());
        linkTransactionResponse.setLogoUrl(clientDetailDto.getLogoUri());
        linkTransactionResponse.setConfigs(uiConfigMap);

        //Publish message after successfully linking the transaction
        kafkaHelperService.publish(linkedSessionTopicName, linkCodeHash);
        return linkTransactionResponse;
    }

    @Override
    public OtpResponse sendOtp(OtpRequest otpRequest) throws IdPException {
        OIDCTransaction transaction = cacheUtilService.getLinkedSessionTransaction(otpRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        SendOtpResult sendOtpResult = authorizationHelperService.delegateSendOtpRequest(otpRequest, transaction);
        OtpResponse otpResponse = new OtpResponse();
        otpResponse.setTransactionId(otpRequest.getTransactionId());
        otpResponse.setMaskedEmail(sendOtpResult.getMaskedEmail());
        otpResponse.setMaskedMobile(sendOtpResult.getMaskedMobile());
        return otpResponse;
    }

    @Override
    public LinkedKycAuthResponse authenticateUser(LinkedKycAuthRequest linkedKycAuthRequest) throws IdPException {
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
        cacheUtilService.setLinkedAuthenticatedTransaction(linkedKycAuthRequest.getLinkedTransactionId(), transaction);

        LinkedKycAuthResponse authRespDto = new LinkedKycAuthResponse();
        authRespDto.setLinkedTransactionId(linkedKycAuthRequest.getLinkedTransactionId());
        return authRespDto;
    }

    @Override
    public LinkedConsentResponse saveConsent(LinkedConsentRequest linkedConsentRequest) throws IdPException {
        OIDCTransaction transaction = cacheUtilService.getLinkedAuthTransaction(linkedConsentRequest.getLinkedTransactionId());
        if(transaction == null) {
            throw new InvalidTransactionException();
        }

        authorizationHelperService.validateAcceptedClaims(transaction, linkedConsentRequest.getAcceptedClaims());
        authorizationHelperService.validateAuthorizeScopes(transaction, linkedConsentRequest.getPermittedAuthorizeScopes());
        // cache consent only, auth-code will be generated on link-auth-code-status API call
        transaction.setAcceptedClaims(linkedConsentRequest.getAcceptedClaims());
        transaction.setPermittedScopes(linkedConsentRequest.getPermittedAuthorizeScopes());
        cacheUtilService.setLinkedConsentedTransaction(linkedConsentRequest.getLinkedTransactionId(), transaction);

        //Publish message after successfully saving the consent
        kafkaHelperService.publish(linkedAuthCodeTopicName, linkedConsentRequest.getLinkedTransactionId());

        LinkedConsentResponse authRespDto = new LinkedConsentResponse();
        authRespDto.setLinkedTransactionId(linkedConsentRequest.getLinkedTransactionId());
        return authRespDto;
    }

    @Async
    @Override
    public void getLinkStatus(DeferredResult deferredResult, LinkStatusRequest linkStatusRequest) throws IdPException {
        String linkCodeHash = authorizationHelperService.getKeyHash(linkStatusRequest.getLinkCode());
        LinkTransactionMetadata linkTransactionMetadata = cacheUtilService.getLinkCodeGenerated(linkCodeHash);
        if(linkTransactionMetadata == null) {
            //if its already linked
            linkTransactionMetadata =  cacheUtilService.getLinkedTransactionMetadata(linkCodeHash);
        }

        if (linkTransactionMetadata == null || !linkStatusRequest.getTransactionId().equals(linkTransactionMetadata.getTransactionId()))
            throw new IdPException(ErrorConstants.INVALID_LINK_CODE);

        if (linkTransactionMetadata.getLinkedTransactionId() != null) {
            deferredResult.setResult(authorizationHelperService.getLinkStatusResponse(LINKED_STATUS));
        } else {
            authorizationHelperService.addEntryInLinkStatusDeferredResultMap(linkCodeHash, deferredResult);
        }
    }

    @Async
    @Override
    public void getLinkAuthCode(DeferredResult deferredResult, LinkAuthCodeRequest linkAuthCodeRequest) throws IdPException {
        String linkCodeHash = authorizationHelperService.getKeyHash(linkAuthCodeRequest.getLinkedCode());
        LinkTransactionMetadata linkTransactionMetadata = cacheUtilService.getLinkedTransactionMetadata(linkCodeHash);
        if(linkTransactionMetadata == null || !linkAuthCodeRequest.getTransactionId().equals(linkTransactionMetadata.getTransactionId()) ||
                linkTransactionMetadata.getLinkedTransactionId() == null)
            throw new IdPException(ErrorConstants.INVALID_LINK_CODE);

        OIDCTransaction oidcTransaction = cacheUtilService.getConsentedTransaction(linkTransactionMetadata.getLinkedTransactionId());
        if(oidcTransaction != null) {
            deferredResult.setResult(authorizationHelperService.getLinkAuthStatusResponse(linkTransactionMetadata.getTransactionId(), oidcTransaction));
        } else {
            authorizationHelperService.addEntryInLinkAuthCodeStatusDeferredResultMap(linkTransactionMetadata.getLinkedTransactionId(), deferredResult);
        }
    }
}
