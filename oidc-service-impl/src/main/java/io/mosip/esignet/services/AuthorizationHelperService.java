/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.api.spi.CaptchaValidator;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.util.*;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.async.DeferredResult;

import javax.crypto.Cipher;
import javax.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.esignet.api.util.ErrorConstants.AUTH_FAILED;
import static io.mosip.esignet.api.util.ErrorConstants.SEND_OTP_FAILED;
import static io.mosip.esignet.core.spi.TokenService.ACR;
import static io.mosip.esignet.core.constants.Constants.*;
import static io.mosip.esignet.core.constants.ErrorConstants.*;
import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA3_256;

@Slf4j
@Component
public class AuthorizationHelperService {

    private static final Map<String, DeferredResult> LINK_STATUS_DEFERRED_RESULT_MAP = new HashMap<>();
    private static final Map<String, DeferredResult> LINK_AUTH_CODE_STATUS_DEFERRED_RESULT_MAP = new HashMap<>();

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    private Authenticator authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private KeyStore keyStore;

    @Autowired
    private KeymanagerDBHelper dbHelper;

    @Autowired
    private AuditPlugin auditWrapper;

    @Autowired(required = false)
    private CaptchaValidator captchaValidator;

    @Value("#{${mosip.esignet.supported.authorize.scopes}}")
    private List<String> authorizeScopes;

    @Value("${mosip.esignet.cache.security.secretkey.reference-id}")
    private String cacheSecretKeyRefId;

    @Value("${mosip.esignet.cache.security.algorithm-name}")
    private String aesECBTransformation;

    @Value("${mosip.esignet.cache.secure.individual-id}")
    private boolean secureIndividualId;

    @Value("${mosip.esignet.cache.store.individual-id}")
    private boolean storeIndividualId;

    @Value("${mosip.esignet.send-otp.captcha-required:false}")
    private boolean captchaRequired;

    public static ConsentAction validateConsent(OIDCTransaction transaction, UserConsent userConsent) {
        if(userConsent == null) {
            return ConsentAction.CAPTURE;
        }
        //validate requested claims
        Claims requestedClaims = transaction.getRequestedClaims();
        List<String> requestedScopes = transaction.getRequestedAuthorizeScopes();

        if(((requestedClaims == null ||
                (requestedClaims.getId_token().isEmpty() && requestedClaims.getUserinfo().isEmpty()))
                && requestedScopes.isEmpty())
        ) {
            return ConsentAction.NOCAPTURE;
        }

        //validate consented claims
        if(requestedClaims!= null && userConsent.getClaims() != null &&!requestedClaims.isEqualToIgnoringAccepted(userConsent.getClaims())){
            return ConsentAction.CAPTURE;
        }

        //validate consented scopes
        if(!requestedScopes.isEmpty()
                && ( !new HashSet<>(requestedScopes).containsAll(userConsent.getRequestedScopes()) ||
                !new HashSet<>(requestedScopes).containsAll(userConsent.getAuthorizedScopes())
        )){
            return ConsentAction.CAPTURE;
        }
        return ConsentAction.NOCAPTURE;
    }

    protected void validateCaptchaToken(String captchaToken) {
        if(!captchaRequired) {
            log.warn("captcha validation is disabled");
            return;
        }

        if(captchaValidator == null) {
            log.error("Captcha validator instance is NULL, Unable to validate captcha token");
            throw new EsignetException(ErrorConstants.FAILED_TO_VALIDATE_CAPTCHA);
        }

        if(!captchaValidator.validateCaptcha(captchaToken))
            throw new EsignetException(ErrorConstants.INVALID_CAPTCHA);
    }


    protected void addEntryInLinkStatusDeferredResultMap(String key, DeferredResult deferredResult) {
        LINK_STATUS_DEFERRED_RESULT_MAP.put(key, deferredResult);
    }

    protected void addEntryInLinkAuthCodeStatusDeferredResultMap(String key, DeferredResult deferredResult) {
        LINK_AUTH_CODE_STATUS_DEFERRED_RESULT_MAP.put(key, deferredResult);
    }

    @KafkaListener(id = "link-status-consumer", autoStartup = "true", topics = "${mosip.esignet.kafka.linked-session.topic}")
    public void consumeLinkStatus(String linkCodeHash) {
        DeferredResult deferredResult = LINK_STATUS_DEFERRED_RESULT_MAP.get(linkCodeHash);
        if(deferredResult != null) {
            if(!deferredResult.isSetOrExpired())
                deferredResult.setResult(getLinkStatusResponse(LINKED_STATUS));
            LINK_STATUS_DEFERRED_RESULT_MAP.remove(linkCodeHash);
        }
    }

    @KafkaListener(id = "link-auth-code-status-consumer", autoStartup = "true", topics = "${mosip.esignet.kafka.linked-auth-code.topic}")
    public void consumeLinkAuthCodeStatus(String linkTransactionId) {
        DeferredResult deferredResult = LINK_AUTH_CODE_STATUS_DEFERRED_RESULT_MAP.get(linkTransactionId);
        if(deferredResult != null) {
            try {
                if(!deferredResult.isSetOrExpired()) {
                    OIDCTransaction oidcTransaction = cacheUtilService.getConsentedTransaction(linkTransactionId);
                    if(oidcTransaction == null)
                        throw new InvalidTransactionException();

                    deferredResult.setResult(getLinkAuthStatusResponse(null, oidcTransaction));
                }
            } finally {
                LINK_AUTH_CODE_STATUS_DEFERRED_RESULT_MAP.remove(linkTransactionId);
            }
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
                                                        List<AuthChallenge> challengeList, OIDCTransaction transaction) {
        KycAuthResult kycAuthResult;
        try {
            kycAuthResult = authenticationWrapper.doKycAuth(transaction.getRelyingPartyId(), transaction.getClientId(),
                    new KycAuthDto(transaction.getAuthTransactionId(), individualId, challengeList));
        } catch (KycAuthException e) {
            log.error("KYC auth failed for transaction : {}", transactionId, e);
            throw new EsignetException(e.getErrorCode());
        }

        if(kycAuthResult == null || (StringUtils.isEmpty(kycAuthResult.getKycToken()) ||
                StringUtils.isEmpty(kycAuthResult.getPartnerSpecificUserToken()))) {
            log.error("** authenticationWrapper : {} returned empty tokens received **", authenticationWrapper);
            throw new EsignetException(AUTH_FAILED);
        }

        auditWrapper.logAudit(Action.DO_KYC_AUTH, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(transactionId, transaction), null);
        return kycAuthResult;
    }

    protected void validateAcceptedClaims(OIDCTransaction transaction, List<String> acceptedClaims) throws EsignetException {
        if(CollectionUtils.isEmpty(acceptedClaims))
            return;

        if(CollectionUtils.isEmpty(transaction.getRequestedClaims().getUserinfo()))
            throw new EsignetException(INVALID_ACCEPTED_CLAIM);

        if(acceptedClaims.stream()
                .allMatch( claim -> transaction.getRequestedClaims().getUserinfo().containsKey(claim) ))
            return;

        throw new EsignetException(INVALID_ACCEPTED_CLAIM);
    }

    protected void validateAuthorizeScopes(OIDCTransaction transaction, List<String> authorizeScopes) throws EsignetException {
        if(CollectionUtils.isEmpty(authorizeScopes))
            return;

        if(CollectionUtils.isEmpty(transaction.getRequestedAuthorizeScopes()))
            throw new EsignetException(INVALID_PERMITTED_SCOPE);

        if(!transaction.getRequestedAuthorizeScopes().containsAll(authorizeScopes))
            throw new EsignetException(INVALID_PERMITTED_SCOPE);
    }

    protected SendOtpResult delegateSendOtpRequest(OtpRequest otpRequest, OIDCTransaction transaction) {
        SendOtpResult sendOtpResult;
        try {
            SendOtpDto sendOtpDto = new SendOtpDto();
            sendOtpDto.setTransactionId(transaction.getAuthTransactionId());
            sendOtpDto.setIndividualId(otpRequest.getIndividualId());
            sendOtpDto.setOtpChannels(otpRequest.getOtpChannels());
            sendOtpResult = authenticationWrapper.sendOtp(transaction.getRelyingPartyId(), transaction.getClientId(),
                    sendOtpDto);
        } catch (SendOtpException e) {
            log.error("Failed to send otp for transaction : {}", otpRequest.getTransactionId(), e);
            throw new EsignetException(e.getErrorCode());
        }

        if(sendOtpResult == null || !transaction.getAuthTransactionId().equals(sendOtpResult.getTransactionId())) {
            log.error("Auth transactionId in request {} is not matching with send-otp response", transaction.getAuthTransactionId());
            throw new EsignetException(SEND_OTP_FAILED);
        }
        return sendOtpResult;
    }

    protected Set<List<AuthenticationFactor>> getProvidedAuthFactors(OIDCTransaction transaction, List<AuthChallenge> challengeList) throws EsignetException {
        List<List<AuthenticationFactor>> resolvedAuthFactors = authenticationContextClassRefUtil.getAuthFactors(
                transaction.getRequestedClaims().getId_token().get(ACR).getValues());
        List<String> providedAuthFactors = challengeList.stream()
                .map(AuthChallenge::getAuthFactorType)
                .collect(Collectors.toList());

        Set<List<AuthenticationFactor>> result = resolvedAuthFactors.stream()
                .filter( acrFactors ->
                        providedAuthFactors.containsAll(acrFactors.stream()
                                .map(AuthenticationFactor::getType)
                                .collect(Collectors.toList())))
                .collect(Collectors.toSet());

        if(CollectionUtils.isEmpty(result)) {
            log.error("Provided auth-factors {} do not match resolved auth-factor {}", providedAuthFactors, resolvedAuthFactors);
            throw new EsignetException(AUTH_FACTOR_MISMATCH);
        }
        return result;
    }

    protected ResponseWrapper<LinkStatusResponse> getLinkStatusResponse(String status){
        ResponseWrapper responseWrapper = new ResponseWrapper();
        LinkStatusResponse linkStatusResponse = new LinkStatusResponse();
        linkStatusResponse.setLinkStatus(status);
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(linkStatusResponse);
        return responseWrapper;
    }

    protected ResponseWrapper<LinkAuthCodeResponse> getLinkAuthStatusResponse(String transactionId, OIDCTransaction oidcTransaction) {
        String authCode = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, UUID.randomUUID().toString());
        if(oidcTransaction.getCodeHash() != null)
            cacheUtilService.removeAuthCodeGeneratedTransaction(oidcTransaction.getCodeHash());
        oidcTransaction.setCodeHash(getKeyHash(authCode));
        cacheUtilService.setAuthCodeGeneratedTransaction(transactionId, oidcTransaction);

        ResponseWrapper responseWrapper = new ResponseWrapper();
        LinkAuthCodeResponse linkAuthCodeResponse = new LinkAuthCodeResponse();
        linkAuthCodeResponse.setNonce(oidcTransaction.getNonce());
        linkAuthCodeResponse.setState(oidcTransaction.getState());
        linkAuthCodeResponse.setRedirectUri(oidcTransaction.getRedirectUri());
        linkAuthCodeResponse.setCode(authCode);
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        responseWrapper.setResponse(linkAuthCodeResponse);
        return responseWrapper;
    }

    protected String getKeyHash(@NotNull String value) {
        return IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, value);
    }

    protected void setIndividualId(String individualId, OIDCTransaction transaction) {
        if(!storeIndividualId)
            return;
        transaction.setIndividualId(secureIndividualId ? encryptIndividualId(individualId) : individualId);
    }

    protected String getIndividualId(OIDCTransaction transaction) {
        if(!storeIndividualId)
            return null;
        return secureIndividualId ? decryptIndividualId(transaction.getIndividualId()) : transaction.getIndividualId();
    }

    private String encryptIndividualId(String individualId) {
        try {
            Cipher cipher = Cipher.getInstance(aesECBTransformation);
            byte[] secretDataBytes = individualId.getBytes(StandardCharsets.UTF_8);
            cipher.init(Cipher.ENCRYPT_MODE, getSecretKeyFromHSM());
            return IdentityProviderUtil.b64Encode(cipher.doFinal(secretDataBytes, 0, secretDataBytes.length));
        } catch(Exception e) {
            log.error("Error Cipher Operations of provided secret data.", e);
            throw new EsignetException(ErrorConstants.AES_CIPHER_FAILED);
        }
    }

    private String decryptIndividualId(String encryptedIndividualId) {
        try {
            Cipher cipher = Cipher.getInstance(aesECBTransformation);
            byte[] decodedBytes = IdentityProviderUtil.b64Decode(encryptedIndividualId);
            cipher.init(Cipher.DECRYPT_MODE, getSecretKeyFromHSM());
            return new String(cipher.doFinal(decodedBytes, 0, decodedBytes.length));
        } catch(Exception e) {
            log.error("Error Cipher Operations of provided secret data.", e);
            throw new EsignetException(ErrorConstants.AES_CIPHER_FAILED);
        }
    }

    private Key getSecretKeyFromHSM() {
        String keyAlias = getKeyAlias(OIDC_SERVICE_APP_ID, cacheSecretKeyRefId);
        if (Objects.nonNull(keyAlias)) {
            return keyStore.getSymmetricKey(keyAlias);
        }
        throw new EsignetException(ErrorConstants.NO_UNIQUE_ALIAS);
    }

    private String getKeyAlias(String keyAppId, String keyRefId) {
        Map<String, List<KeyAlias>> keyAliasMap = dbHelper.getKeyAliases(keyAppId, keyRefId, LocalDateTime.now(ZoneOffset.UTC));
        List<KeyAlias> currentKeyAliases = keyAliasMap.get(KeymanagerConstant.CURRENTKEYALIAS);
        if (!currentKeyAliases.isEmpty() && currentKeyAliases.size() == 1) {
            return currentKeyAliases.get(0).getAlias();
        }
        log.error("CurrentKeyAlias is not unique. KeyAlias count: {}", currentKeyAliases.size());
        throw new EsignetException(ErrorConstants.NO_UNIQUE_ALIAS);
    }

    public AuthResponseV2 authResponseV2Mapper(AuthResponse authResponse) throws EsignetException {
        AuthResponseV2 authResponseV2 = new AuthResponseV2();
        authResponseV2.setTransactionId(authResponse.getTransactionId());
//        log.debug("consent {}", );
        OIDCTransaction transaction = cacheUtilService.getAuthenticatedTransaction(authResponse.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();


        authResponseV2.setConsentAction(transaction.getConsentAction());
        return authResponseV2;
    }

    public LinkedKycAuthResponseV2 linkedKycAuthResponseV2Mapper(LinkedKycAuthResponse linkedKycAuthResponse) throws EsignetException {
        LinkedKycAuthResponseV2 linkedKycAuthResponseV2 = new LinkedKycAuthResponseV2();
        linkedKycAuthResponseV2.setLinkedTransactionId(linkedKycAuthResponse.getLinkedTransactionId());
        OIDCTransaction transaction = cacheUtilService.getAuthenticatedTransaction(linkedKycAuthResponse.getLinkedTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();
        linkedKycAuthResponseV2.setConsentAction(transaction.getConsentAction());
        return linkedKycAuthResponseV2;
    }
}
