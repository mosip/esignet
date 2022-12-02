/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.DuplicateLinkCodeException;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidTransactionException;
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.spi.LinkedAuthorizationService;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
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
import java.util.UUID;

import static io.mosip.idp.core.spi.TokenService.ACR;
import static io.mosip.idp.core.util.Constants.*;
import static io.mosip.idp.core.util.IdentityProviderUtil.ALGO_MD5;

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

    @Value("${mosip.idp.link-code-expire-in-secs}")
    private int linkCodeExpiryInSeconds;

    @Value("#{${mosip.idp.ui.config.key-values}}")
    private Map<String, Object> uiConfigMap;

    @Value("${mosip.idp.kafka.linked-session.topic}")
    private String linkedSessionTopicName;

    @Value("${mosip.idp.kafka.linked-auth-code.topic}")
    private String linkedAuthConsentTopicName;

    @Value("${mosip.idp.link-code-length:15}")
    private int linkCodeLength;


    @Override
    public LinkCodeResponse generateLinkCode(LinkCodeRequest linkCodeRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(linkCodeRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        //Duplicate link code is handled only once, duplicate exception on the second try is thrown out.
        String linkCode = null;
        ZonedDateTime expireDateTime = null;
        try {
            linkCode = IdentityProviderUtil.generateRandomAlphaNumeric(linkCodeLength);
        } catch (DuplicateLinkCodeException e) {
            log.error("Generated duplicate link code");
            linkCode = null;
        }

        if(linkCode == null) {
            log.info("Found duplicate link-code, generating new link-code");
            linkCode = IdentityProviderUtil.generateRandomAlphaNumeric(linkCodeLength);
        }

        cacheUtilService.setLinkCode(authorizationHelperService.getCacheKey(linkCode),
                new LinkTransactionMetadata(linkCodeRequest.getTransactionId(),null,null));
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
        String linkCodeHash = authorizationHelperService.getCacheKey(linkTransactionRequest.getLinkCode());
        LinkTransactionMetadata linkTransactionMetadata = cacheUtilService.getLinkedTransactionMetadata(linkCodeHash);
        if(linkTransactionMetadata == null || linkTransactionMetadata.getTransactionId() == null)
            throw new IdPException(ErrorConstants.INVALID_LINK_CODE);

        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(linkTransactionMetadata.getTransactionId());
        if(transaction == null)
            throw new IdPException(ErrorConstants.TRANSACTION_NOT_FOUND);

        log.info("Valid link-code provided, proceeding to generate linkTransactionId");
        ClientDetail clientDetailDto = clientManagementService.getClientDetails(transaction.getClientId());

        //if valid, generate link-transaction-id and move transaction from preauth-sessions to linked-sessions cache
        String linkedTransactionId = IdentityProviderUtil.createTransactionId(transaction.getNonce());
        transaction.setLinkedTransactionId(linkedTransactionId);
        transaction.setLinkCodeHash(linkCodeHash);
        transaction = cacheUtilService.setLinkedTransaction(linkTransactionMetadata.getTransactionId(), transaction);
        linkTransactionMetadata.setLinkedTransactionId(linkedTransactionId);
        cacheUtilService.updateLinkCode(linkCodeHash, linkTransactionMetadata);

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
        IdPTransaction transaction = cacheUtilService.getLinkedSessionTransaction(otpRequest.getTransactionId());
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
        IdPTransaction transaction = cacheUtilService.getLinkedSessionTransaction(linkedKycAuthRequest.getLinkedTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        //Validate provided challenge list auth-factors with resolved auth-factors for the transaction.
        authorizationHelperService.validateProvidedAuthFactors(transaction, linkedKycAuthRequest.getChallengeList());
        KycAuthResult kycAuthResult = authorizationHelperService.delegateAuthenticateRequest(linkedKycAuthRequest.getLinkedTransactionId(),
                linkedKycAuthRequest.getIndividualId(), linkedKycAuthRequest.getChallengeList(), transaction);
        //cache tokens on successful response
        transaction.setPartnerSpecificUserToken(kycAuthResult.getPartnerSpecificUserToken());
        transaction.setKycToken(kycAuthResult.getKycToken());
        transaction.setAuthTimeInSeconds(IdentityProviderUtil.getEpochSeconds());
        cacheUtilService.setLinkedAuthenticatedTransaction(linkedKycAuthRequest.getLinkedTransactionId(), transaction);

        LinkedKycAuthResponse authRespDto = new LinkedKycAuthResponse();
        authRespDto.setLinkedTransactionId(linkedKycAuthRequest.getLinkedTransactionId());
        return authRespDto;
    }

    @Override
    public LinkedConsentResponse saveConsent(LinkedConsentRequest linkedConsentRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getLinkedAuthTransaction(linkedConsentRequest.getLinkedTransactionId());
        if(transaction == null) {
            throw new InvalidTransactionException();
        }

        authorizationHelperService.validateAcceptedClaims(transaction, linkedConsentRequest.getAcceptedClaims());
        authorizationHelperService.validateAuthorizeScopes(transaction, linkedConsentRequest.getPermittedAuthorizeScopes());

        String authCode = IdentityProviderUtil.generateB64EncodedHash(ALGO_MD5, UUID.randomUUID().toString());
        // cache consent with auth-code-hash as key
        transaction.setCodeHash(authorizationHelperService.getCacheKey(authCode));
        transaction.setAcceptedClaims(linkedConsentRequest.getAcceptedClaims());
        transaction.setPermittedScopes(linkedConsentRequest.getPermittedAuthorizeScopes());
        cacheUtilService.setLinkedConsentedTransaction(linkedConsentRequest.getLinkedTransactionId(), transaction);

        LinkTransactionMetadata linkTransactionMetadata = cacheUtilService.getLinkedTransactionMetadata(transaction.getLinkCodeHash());
        linkTransactionMetadata.setAuthCode(authCode);
        cacheUtilService.updateLinkCode(transaction.getLinkCodeHash(), linkTransactionMetadata);

        //Publish message after successfully saving the consent
        kafkaHelperService.publish(linkedAuthConsentTopicName, transaction.getLinkCodeHash());

        LinkedConsentResponse authRespDto = new LinkedConsentResponse();
        authRespDto.setLinkedTransactionId(linkedConsentRequest.getLinkedTransactionId());
        return authRespDto;
    }

    @Async
    @Override
    public void getLinkStatus(DeferredResult deferredResult, LinkStatusRequest linkStatusRequest) throws IdPException {
        String linkCodeHash = authorizationHelperService.getCacheKey(linkStatusRequest.getLinkCode());
        authorizationHelperService.addEntryInDeferredResultMap(linkCodeHash, deferredResult);
        LinkTransactionMetadata linkTransactionMetadata = cacheUtilService.getLinkedTransactionMetadata(linkCodeHash);
        if (linkTransactionMetadata == null || !linkStatusRequest.getTransactionId().equals(linkTransactionMetadata.getTransactionId()))
            throw new IdPException(ErrorConstants.INVALID_LINK_CODE);

        if (linkTransactionMetadata.getLinkedTransactionId() != null) {
            deferredResult.setResult(authorizationHelperService.createLinkStatusResponse(linkTransactionMetadata.getTransactionId(),
                    LINKED_STATUS));
        }
    }

    @Async
    @Override
    public void getLinkAuthCodeStatus(DeferredResult deferredResult, LinkAuthCodeRequest linkAuthCodeRequest) throws IdPException {
        String linkCodeHash = authorizationHelperService.getCacheKey(linkAuthCodeRequest.getLinkedCode());
        authorizationHelperService.addEntryInDeferredResultMap(linkCodeHash, deferredResult);
        LinkTransactionMetadata linkTransactionMetadata = cacheUtilService.getLinkedTransactionMetadata(linkCodeHash);
        if(linkTransactionMetadata == null || !linkAuthCodeRequest.getTransactionId().equals(linkTransactionMetadata.getTransactionId()) ||
                linkTransactionMetadata.getLinkedTransactionId() == null)
            throw new IdPException(ErrorConstants.INVALID_LINK_CODE);

        if(linkTransactionMetadata.getAuthCode() != null) {
            try {
                deferredResult.setResult(authorizationHelperService.getLinkAuthStatusResponse(linkTransactionMetadata.getAuthCode()));
            } catch (IdPException e) {
                log.error("Cache hit failed to get the auth-code status for linkCode : {}", linkAuthCodeRequest.getLinkedCode());
            }
        }
    }
}
