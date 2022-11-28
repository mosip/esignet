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
import io.mosip.idp.core.spi.LinkAuthorizationService;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.listener.MessageListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

import static io.mosip.idp.core.spi.TokenService.ACR;
import static io.mosip.idp.core.util.Constants.*;

@Slf4j
@Component
public class LinkAuthorizationServiceImpl implements LinkAuthorizationService {

    private static final int LINK_CODE_LENGTH = 15;

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

    @Value("${mosip.idp.kafka.linked-auth.topic}")
    private String linkedAuthTopicName;


    @Override
    public LinkCodeResponse generateLinkCode(LinkCodeRequest linkCodeRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(linkCodeRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        //Duplicate link code is handled only once, duplicate exception on the second try is thrown out.
        String linkCode = null;
        ZonedDateTime expireDateTime = null;
        try {
            linkCode = IdentityProviderUtil.generateRandomAlphaNumeric(LINK_CODE_LENGTH);
            cacheUtilService.setLinkCode(linkCode, linkCodeRequest.getTransactionId());
            expireDateTime = ZonedDateTime.now(ZoneOffset.UTC).plus(linkCodeExpiryInSeconds, ChronoUnit.SECONDS);
        } catch (DuplicateLinkCodeException e) {
            log.error("Generated duplicate link code");
            linkCode = null;
        }

        if(linkCode == null) {
            linkCode = IdentityProviderUtil.generateRandomAlphaNumeric(LINK_CODE_LENGTH);
            cacheUtilService.setLinkCode(linkCode, linkCodeRequest.getTransactionId());
            expireDateTime = ZonedDateTime.now(ZoneOffset.UTC).plus(linkCodeExpiryInSeconds, ChronoUnit.SECONDS);
        }

        LinkCodeResponse linkCodeResponse = new LinkCodeResponse();
        linkCodeResponse.setLinkCode(linkCode);
        linkCodeResponse.setTransactionId(linkCodeRequest.getTransactionId());
        linkCodeResponse.setExpireDateTime(expireDateTime == null ? null :
                expireDateTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        return linkCodeResponse;
    }

    @Override
    public LinkTransactionResponse linkTransaction(LinkTransactionRequest linkTransactionRequest) throws IdPException {
        String transactionId = cacheUtilService.getLinkCode(linkTransactionRequest.getLinkCode());
        if(transactionId == null)
            throw new IdPException(ErrorConstants.INVALID_LINK_CODE);

        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(transactionId);
        if(transaction == null)
            throw new IdPException(ErrorConstants.TRANSACTION_NOT_FOUND);

        transaction = cacheUtilService.getLinkedTransaction(transactionId);
        if(transaction != null)
            throw new IdPException(ErrorConstants.TRANSACTION_ALREADY_LINKED);

        ClientDetail clientDetailDto = clientManagementService.getClientDetails(transaction.getClientId());

        //if valid, generate link-transaction-id and move transaction from preauth-sessions to linked-sessions cache
        String linkedTransactionId = IdentityProviderUtil.createTransactionId(transaction.getNonce());
        transaction.setLinkTransactionId(linkedTransactionId);
        transaction = cacheUtilService.setLinkedSession(transactionId, transaction);

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
        kafkaHelperService.publish(linkedSessionTopicName, transactionId);
        return linkTransactionResponse;
    }

    @Async
    @Override
    public void getLinkStatus(DeferredResult deferredResult, LinkStatusRequest linkStatusRequest) throws IdPException  {
        String transactionId = cacheUtilService.getLinkCode(linkStatusRequest.getLinkCode());
        if(transactionId == null || !transactionId.equals(linkStatusRequest.getTransactionId()))
            throw new IdPException(ErrorConstants.INVALID_LINK_CODE);

        //It's a valid link-code, check the status
        ResponseWrapper<LinkStatusResponse> responseWrapper = getLinkStatusResponse(transactionId);
        if(responseWrapper != null) {
            deferredResult.setResult(responseWrapper);
            return;
        }

        //Start kafka consumer thread
        kafkaHelperService.subscribe(linkedSessionTopicName, transactionId, new MessageListener<String, String>() {
            @Override
            public void onMessage(ConsumerRecord<String, String> record) {
                if(record.value().equals(transactionId)) {
                    log.info("link-status message received for transactionId : {}", transactionId);
                    ResponseWrapper<LinkStatusResponse> responseWrapper = getLinkStatusResponse(transactionId);
                    if(responseWrapper == null)
                        throw new IdPException(ErrorConstants.INVALID_TRANSACTION);
                    //Set the responseWrapper into DeferredResult
                    deferredResult.setResult(responseWrapper);
                    //After setting the deferred result, stop the consumer
                    kafkaHelperService.unsubscribe(transactionId);
                }
            }
        });
    }


    @Async
    @Override
    public void getLinkAuthCodeStatus(DeferredResult deferredResult, LinkAuthCodeRequest linkAuthCodeRequest) throws IdPException {
        String transactionId = cacheUtilService.getLinkCode(linkAuthCodeRequest.getLinkedCode());
        if(transactionId == null || !transactionId.equals(linkAuthCodeRequest.getTransactionId()))
            throw new IdPException(ErrorConstants.INVALID_LINK_CODE);

        //It's a valid link-code, check the status
        ResponseWrapper<LinkAuthCodeResponse> responseWrapper = getLinkAuthStatusResponse(transactionId);
        if(responseWrapper != null) {
            deferredResult.setResult(responseWrapper);
            return;
        }

        //Start kafka consumer thread
        kafkaHelperService.subscribe(linkedAuthTopicName, transactionId, new MessageListener<String, String>() {
            @Override
            public void onMessage(ConsumerRecord<String, String> record) {
                if(record.value().equals(transactionId)) {
                    log.info("link-auth-code message received for transactionId : {}", transactionId);
                    ResponseWrapper<LinkAuthCodeResponse> responseWrapper = getLinkAuthStatusResponse(transactionId);
                    if(responseWrapper == null)
                        throw new IdPException(ErrorConstants.INVALID_TRANSACTION);
                    //Set the responseWrapper into DeferredResult
                    deferredResult.setResult(responseWrapper);
                    //After setting the deferred result, stop the consumer
                    kafkaHelperService.unsubscribe(transactionId);
                }
            }
        });
    }

    private ResponseWrapper<LinkStatusResponse> getLinkStatusResponse(String transactionId) {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        LinkStatusResponse linkStatusResponse = new LinkStatusResponse();
        linkStatusResponse.setTransactionId(transactionId);
        IdPTransaction idPTransaction = cacheUtilService.getLinkedTransaction(transactionId);
        if(idPTransaction != null) {
            linkStatusResponse.setLinkStatus("ACTIVE");
            linkStatusResponse.setLinkedDateTime(IdentityProviderUtil.getUTCDateTime());
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
            responseWrapper.setResponse(linkStatusResponse);
            return responseWrapper;
        }
        return null;
    }

    private ResponseWrapper<LinkAuthCodeResponse> getLinkAuthStatusResponse(String transactionId) {
        ResponseWrapper responseWrapper = new ResponseWrapper();
        LinkAuthCodeResponse linkAuthCodeResponse = new LinkAuthCodeResponse();
        IdPTransaction idPTransaction = cacheUtilService.getLinkedTransaction(transactionId);
        if(idPTransaction != null) {
            linkAuthCodeResponse.setNonce(idPTransaction.getNonce());
            linkAuthCodeResponse.setState(idPTransaction.getState());
            linkAuthCodeResponse.setRedirectUri(idPTransaction.getRedirectUri());
            linkAuthCodeResponse.setCode(idPTransaction.getCode());
            responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
            responseWrapper.setResponse(linkAuthCodeResponse);
            return responseWrapper;
        }
        return null;
    }
}
