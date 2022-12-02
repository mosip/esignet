package io.mosip.idp.services;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.async.DeferredResult;

import javax.validation.constraints.NotNull;
import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.idp.core.spi.TokenService.ACR;
import static io.mosip.idp.core.util.Constants.*;
import static io.mosip.idp.core.util.ErrorConstants.*;
import static io.mosip.idp.core.util.ErrorConstants.INVALID_PERMITTED_SCOPE;
import static io.mosip.idp.core.util.IdentityProviderUtil.ALGO_MD5;

@Slf4j
@Component
public class AuthorizationHelperService {

    private static final Map<String, DeferredResult> DEFERRED_RESULT_MAP = new HashMap<>();

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Value("#{${mosip.idp.supported.authorize.scopes}}")
    private List<String> authorizeScopes;


    protected void addEntryInDeferredResultMap(String key, DeferredResult deferredResult) {
        DEFERRED_RESULT_MAP.put(key, deferredResult);
    }

    @KafkaListener(id = "link-status-consumer", autoStartup = "true", topics = "${mosip.idp.kafka.linked-session.topic}")
    public void consumeLinkStatus(String linkCodeHash) {
        if(DEFERRED_RESULT_MAP.get(linkCodeHash) != null) {
            LinkTransactionMetadata linkTransactionMetadata = cacheUtilService.getLinkedTransactionMetadata(linkCodeHash);
            if(linkTransactionMetadata == null || linkTransactionMetadata.getLinkedTransactionId() == null) {
                log.warn("Received link-status kafka message, but key was not found in cache / was found in invalid state. Ignoring the message :{}",
                        linkCodeHash);
                return;
            }
            DEFERRED_RESULT_MAP.get(linkCodeHash).setResult(
                    createLinkStatusResponse(linkTransactionMetadata.getTransactionId(), LINKED_STATUS));
            DEFERRED_RESULT_MAP.remove(linkCodeHash);
        }
    }

    @KafkaListener(id = "link-auth-code-status-consumer", autoStartup = "true", topics = "${mosip.idp.kafka.linked-auth-code.topic}")
    public void consumeLinkAuthCodeStatus(String linkCodeHash) {
        if(DEFERRED_RESULT_MAP.get(linkCodeHash) != null) {
            LinkTransactionMetadata linkTransactionMetadata = cacheUtilService.getLinkedTransactionMetadata(linkCodeHash);
            if(linkTransactionMetadata == null || linkTransactionMetadata.getLinkedTransactionId() == null ||
                    linkTransactionMetadata.getAuthCode() == null) {
                log.warn("Received link-auth-code kafka message, but key was not found in cache / was found in invalid state. Ignoring the message :{}",
                        linkCodeHash);
                return;
            }
            IdPTransaction idPTransaction = cacheUtilService.getConsentedTransaction(getCacheKey(linkTransactionMetadata.getAuthCode()));
            if(idPTransaction == null || idPTransaction.getCodeHash() == null) {
                log.warn("Received link-auth-code kafka message, but key was not found in cache / was found in invalid state. Ignoring the message :{}",
                        linkCodeHash);
                return;
            }
            DEFERRED_RESULT_MAP.get(linkCodeHash).setResult(createLinkAuthCodeResponse(idPTransaction,
                            linkTransactionMetadata.getAuthCode()));
            DEFERRED_RESULT_MAP.remove(linkCodeHash);
        }
    }

    protected Map<String, List> getClaimNames(Claims resolvedClaims) {
        List<String> essentialClaims = new ArrayList<>();
        List<String> voluntaryClaims = new ArrayList<>();
        for(Map.Entry<String, ClaimDetail> claim : resolvedClaims.getUserinfo().entrySet()) {
            if(claim.getValue() != null && claim.getValue().isEssential())
                essentialClaims.add(claim.getKey());
            else
                voluntaryClaims.add(claim.getKey());
        }
        Map<String, List> result = new HashMap<>();
        result.put(ESSENTIAL, essentialClaims);
        result.put(VOLUNTARY, voluntaryClaims);
        return result;
    }

    protected List<String> getAuthorizeScopes(String requestedScopes) {
        String[] scopes = IdentityProviderUtil.splitAndTrimValue(requestedScopes, Constants.SPACE);
        return Arrays.stream(scopes)
                .filter( s -> authorizeScopes.contains(s) )
                .collect(Collectors.toList());
    }

    protected KycAuthResult delegateAuthenticateRequest(String transactionId, String individualId,
                                                        List<AuthChallenge> challengeList, IdPTransaction transaction) {
        KycAuthResult kycAuthResult;
        try {
            kycAuthResult = authenticationWrapper.doKycAuth(transaction.getRelyingPartyId(), transaction.getClientId(),
                    new KycAuthRequest(transaction.getAuthTransactionId(), individualId, challengeList));
        } catch (KycAuthException e) {
            log.error("KYC auth failed for transaction : {}", transactionId, e);
            throw new IdPException(e.getErrorCode());
        }

        if(kycAuthResult == null || (StringUtils.isEmpty(kycAuthResult.getKycToken()) ||
                StringUtils.isEmpty(kycAuthResult.getPartnerSpecificUserToken()))) {
            log.error("** authenticationWrapper : {} returned empty tokens received **", authenticationWrapper);
            throw new IdPException(AUTH_FAILED);
        }
        return kycAuthResult;
    }

    protected void validateAcceptedClaims(IdPTransaction transaction, List<String> acceptedClaims) throws IdPException {
        if(CollectionUtils.isEmpty(acceptedClaims))
            return;

        if(CollectionUtils.isEmpty(transaction.getRequestedClaims().getUserinfo()))
            throw new IdPException(INVALID_ACCEPTED_CLAIM);

        if(acceptedClaims.stream()
                .allMatch( claim -> transaction.getRequestedClaims().getUserinfo().containsKey(claim) ))
            return;

        throw new IdPException(INVALID_ACCEPTED_CLAIM);
    }

    protected void validateAuthorizeScopes(IdPTransaction transaction, List<String> authorizeScopes) throws IdPException {
        if(CollectionUtils.isEmpty(authorizeScopes))
            return;

        if(CollectionUtils.isEmpty(transaction.getRequestedAuthorizeScopes()))
            throw new IdPException(INVALID_PERMITTED_SCOPE);

        if(!transaction.getRequestedAuthorizeScopes().containsAll(authorizeScopes))
            throw new IdPException(INVALID_PERMITTED_SCOPE);
    }

    protected SendOtpResult delegateSendOtpRequest(OtpRequest otpRequest, IdPTransaction transaction) {
        SendOtpResult sendOtpResult;
        try {
            SendOtpRequest sendOtpRequest = new SendOtpRequest();
            sendOtpRequest.setTransactionId(transaction.getAuthTransactionId());
            sendOtpRequest.setIndividualId(otpRequest.getIndividualId());
            sendOtpRequest.setOtpChannels(otpRequest.getOtpChannels());
            sendOtpResult = authenticationWrapper.sendOtp(transaction.getRelyingPartyId(), transaction.getClientId(),
                    sendOtpRequest);
        } catch (SendOtpException e) {
            log.error("Failed to send otp for transaction : {}", otpRequest.getTransactionId(), e);
            throw new IdPException(e.getErrorCode());
        }

        if(sendOtpResult == null || !transaction.getAuthTransactionId().equals(sendOtpResult.getTransactionId())) {
            log.error("Auth transactionId in request {} is not matching with send-otp response : {}", transaction.getAuthTransactionId(),
                    sendOtpResult.getTransactionId());
            throw new IdPException(SEND_OTP_FAILED);
        }
        return sendOtpResult;
    }

    protected void validateProvidedAuthFactors(IdPTransaction transaction, List<AuthChallenge> challengeList) throws IdPException {
        List<List<AuthenticationFactor>> resolvedAuthFactors = authenticationContextClassRefUtil.getAuthFactors(
                transaction.getRequestedClaims().getId_token().get(ACR).getValues());
        List<String> providedAuthFactors = challengeList.stream()
                .map(AuthChallenge::getAuthFactorType)
                .collect(Collectors.toList());

        boolean result = resolvedAuthFactors.stream().anyMatch( acrFactors ->
                providedAuthFactors.containsAll(acrFactors.stream().map(AuthenticationFactor::getType).collect(Collectors.toList())));

        if(!result) {
            log.error("Provided auth-factors {} do not match resolved auth-factor {}", providedAuthFactors, resolvedAuthFactors);
            throw new IdPException(AUTH_FACTOR_MISMATCH);
        }
    }

    protected ResponseWrapper<LinkStatusResponse> createLinkStatusResponse(String transactionId, String status){
        ResponseWrapper responseWrapper = new ResponseWrapper();
        LinkStatusResponse linkStatusResponse = new LinkStatusResponse();
        linkStatusResponse.setTransactionId(transactionId);
        linkStatusResponse.setLinkStatus(status);
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(linkStatusResponse);
        return responseWrapper;
    }

    protected ResponseWrapper<LinkAuthCodeResponse> getLinkAuthStatusResponse(String authCode) {
        IdPTransaction idPTransaction = cacheUtilService.getConsentedTransaction(IdentityProviderUtil.generateB64EncodedHash(ALGO_MD5, authCode));
        if(idPTransaction == null || idPTransaction.getCodeHash() == null || idPTransaction.getRedirectUri() == null)
            throw new IdPException(ErrorConstants.INVALID_STATE);

        return createLinkAuthCodeResponse(idPTransaction, authCode);
    }

    protected ResponseWrapper<LinkAuthCodeResponse> createLinkAuthCodeResponse(IdPTransaction idPTransaction, String authCode){
        ResponseWrapper responseWrapper = new ResponseWrapper();
        LinkAuthCodeResponse linkAuthCodeResponse = new LinkAuthCodeResponse();
        linkAuthCodeResponse.setNonce(idPTransaction.getNonce());
        linkAuthCodeResponse.setState(idPTransaction.getState());
        linkAuthCodeResponse.setRedirectUri(idPTransaction.getRedirectUri());
        linkAuthCodeResponse.setCode(authCode);
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(linkAuthCodeResponse);
        return responseWrapper;
    }

    protected String getCacheKey(@NotNull String value) {
        return IdentityProviderUtil.generateB64EncodedHash(ALGO_MD5, value);
    }
}
