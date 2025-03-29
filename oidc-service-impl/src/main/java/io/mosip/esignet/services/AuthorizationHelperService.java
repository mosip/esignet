/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.nimbusds.jwt.JWT;
import com.nimbusds.jwt.JWTParser;
import io.mosip.esignet.api.dto.*;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.*;
import io.mosip.kernel.core.keymanager.spi.KeyStore;
import io.mosip.kernel.keymanagerservice.constant.KeymanagerConstant;
import io.mosip.kernel.keymanagerservice.entity.KeyAlias;
import io.mosip.kernel.keymanagerservice.helper.KeymanagerDBHelper;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.util.Pair;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.async.DeferredResult;

import javax.crypto.Cipher;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.NotNull;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.TimeUnit;
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

    private static final Cache<String, DeferredResult> LINK_STATUS_DEFERRED_RESULT_MAP = CacheBuilder.newBuilder()
            .expireAfterWrite(40, TimeUnit.SECONDS)
                .build();

    private static final Cache<String, DeferredResult> LINK_AUTH_CODE_STATUS_DEFERRED_RESULT_MAP = CacheBuilder.newBuilder()
            .expireAfterWrite(40, TimeUnit.SECONDS)
            .build();

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

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CaptchaHelper captchaHelper;

    @Autowired
    private ClaimsHelperService claimsHelperService;

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

    @Value("#{'${mosip.esignet.captcha.required}'.split(',')}")
    private List<String> captchaRequired;

    @Value("#{${mosip.esignet.supported.credential.scopes}}")
    private List<String> credentialScopes;

    @Value("${mosip.esignet.signup-id-token-audience}")
    private String signupIDTokenAudience;

    protected void validateSendOtpCaptchaToken(String captchaToken) {
        if(!captchaRequired.contains("send-otp")) {
            log.warn("captcha validation is disabled for send-otp request!");
            return;
        }
        if(!StringUtils.hasText(captchaToken)) {
        	log.error("Captcha token is Null or Empty");
        	throw new EsignetException(INVALID_CAPTCHA);
        }
        validateCaptchaToken(captchaToken);
    }

    protected void validateCaptchaToken(String captchaToken) {
        if (captchaHelper == null) {
            log.error("Captcha validator instance is NULL, Unable to validate captcha token");
            throw new EsignetException(FAILED_TO_VALIDATE_CAPTCHA);
        }

        if (!captchaHelper.validateCaptcha(captchaToken))
            throw new EsignetException(INVALID_CAPTCHA);
    }

    protected void addEntryInLinkStatusDeferredResultMap(String key, DeferredResult deferredResult) {
        LINK_STATUS_DEFERRED_RESULT_MAP.put(key, deferredResult);
    }

    protected void addEntryInLinkAuthCodeStatusDeferredResultMap(String key, DeferredResult deferredResult) {
        LINK_AUTH_CODE_STATUS_DEFERRED_RESULT_MAP.put(key, deferredResult);
    }

    @KafkaListener(id = "${spring.kafka.consumer.group-id}"+"-link-status", autoStartup = "${kafka.enabled:true}", topics = "${mosip.esignet.kafka.linked-session.topic}")
    public void consumeLinkStatus(String linkCodeHash) {
        DeferredResult deferredResult = LINK_STATUS_DEFERRED_RESULT_MAP.getIfPresent(linkCodeHash);
        if(deferredResult != null) {
            if(!deferredResult.isSetOrExpired())
                deferredResult.setResult(getLinkStatusResponse(LINKED_STATUS));
            LINK_STATUS_DEFERRED_RESULT_MAP.invalidate(linkCodeHash);
        }
    }

    @KafkaListener(id = "${spring.kafka.consumer.group-id}"+"-linked-auth-code", autoStartup = "${kafka.enabled:true}", topics = "${mosip.esignet.kafka.linked-auth-code.topic}")
    public void consumeLinkAuthCodeStatus(String linkTransactionId) {
        DeferredResult deferredResult = LINK_AUTH_CODE_STATUS_DEFERRED_RESULT_MAP.getIfPresent(linkTransactionId);
        if(deferredResult != null) {
            try {
                if(!deferredResult.isSetOrExpired()) {
                    OIDCTransaction oidcTransaction = cacheUtilService.getConsentedTransaction(linkTransactionId);
                    if(oidcTransaction == null)
                        throw new InvalidTransactionException();

                    deferredResult.setResult(getLinkAuthStatusResponse(null, oidcTransaction));
                }
            } finally {
                LINK_AUTH_CODE_STATUS_DEFERRED_RESULT_MAP.invalidate(linkTransactionId);
            }
        }
    }

    protected List<String> getAuthorizeScopes(String requestedScopes) {
        String[] scopes = IdentityProviderUtil.splitAndTrimValue(requestedScopes, SPACE);
        return Arrays.stream(scopes)
                .filter( s -> authorizeScopes.contains(s) )
                .collect(Collectors.toList());
    }

    protected List<String> getCredentialScopes(String requestedScopes) {
        String[] scopes = IdentityProviderUtil.splitAndTrimValue(requestedScopes, SPACE);
        return Arrays.stream(scopes)
                .filter( s -> credentialScopes.contains(s) )
                .collect(Collectors.toList());
    }

    protected KycAuthResult delegateAuthenticateRequest(String transactionId, String individualId,
                                                        List<AuthChallenge> challengeList, OIDCTransaction transaction) {

        KycAuthResult kycAuthResult;
        try {
            KycAuthDto kycAuthDto = new KycAuthDto(transaction.getAuthTransactionId(), individualId, challengeList);
            kycAuthResult = authenticationWrapper.doKycAuth(transaction.getRelyingPartyId(), transaction.getClientId(),
                    claimsHelperService.isVerifiedClaimRequested(transaction), kycAuthDto);
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

    /**
     * Method validates challenge with "IDT" auth factor
     *
     * @param authChallenge {@link AuthChallenge}
     * @param individualId individual id from {@link AuthRequestV2}
     * @param transaction {@link OIDCTransaction}
     * @param httpServletRequest {@link HttpServletRequest}
     * @return {@link KycAuthResult}
     */
    protected KycAuthResult handleInternalAuthenticateRequest(@NonNull AuthChallenge authChallenge,
                                                              @NotNull String individualId, @NonNull OIDCTransaction transaction, HttpServletRequest httpServletRequest) {
        try {
            JsonNode jsonNode = objectMapper.readTree(IdentityProviderUtil.b64Decode(authChallenge.getChallenge()));
            if(jsonNode.isNull() || jsonNode.get("token").isNull())
                throw new EsignetException(AUTH_FAILED);
            String token = jsonNode.get("token").textValue();
            tokenService.verifyIdToken(token, signupIDTokenAudience);
            JWT jwt = JWTParser.parse(token);
            String subject = jwt.getJWTClaimsSet().getSubject();

            //compares individual from auth request against subject from jwt token.
            if(!individualId.equals(subject)) {
                throw new EsignetException(INVALID_INDIVIDUAL_ID);
            }

            Optional<Cookie> result = Arrays.stream(httpServletRequest.getCookies())
                    .filter(x -> x.getName().equals(subject))
                    .findFirst();
            OIDCTransaction haltedTransaction = cacheUtilService.getHaltedTransaction(subject);

            //Checks to confirm that the ID token is not mis-used or re-used
            //Validate if cookie is present with token subject as name and halted transaction is present in cache
            //validate if the server nonce in the halted transaction is same as the nonce in the ID token
            //validate if the nonce in the ID token is same as the nonce in the current OIDC transaction
            if(result.isPresent() && haltedTransaction != null &&
                    haltedTransaction.getServerNonce().equals(result.get().getValue().split(SERVER_NONCE_SEPARATOR)[0]) &&
                    haltedTransaction.getServerNonce().equals(jwt.getJWTClaimsSet().getStringClaim(TokenService.NONCE)) &&
                    transaction.getNonce().equals(jwt.getJWTClaimsSet().getStringClaim(TokenService.NONCE))) {
                transaction.setIndividualId(haltedTransaction.getIndividualId());
                KycAuthResult kycAuthResult = new KycAuthResult();
                kycAuthResult.setKycToken(subject);
                kycAuthResult.setPartnerSpecificUserToken(subject);
                return kycAuthResult;
            }
            log.error("ID token in the challenge is not matching the required conditions. isCookiePresent: {}, isHaltedTransactionFound: {}",
                    result.isPresent(), haltedTransaction!=null);
        } catch (EsignetException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to parse ID token as challenge", e);
        }
        throw new EsignetException(AUTH_FAILED);
    }

    protected void validatePermittedScopes(OIDCTransaction transaction, List<String> permittedScopes) throws EsignetException {
        if(CollectionUtils.isEmpty(permittedScopes))
            return;

        if(CollectionUtils.isEmpty(transaction.getRequestedAuthorizeScopes()) &&
                CollectionUtils.isEmpty(transaction.getRequestedCredentialScopes()))
            throw new EsignetException(INVALID_PERMITTED_SCOPE);

        List<String> authorizeScopes = Objects.requireNonNullElse(transaction.getRequestedAuthorizeScopes(), Collections.emptyList());
        List<String> credentialScopes = Objects.requireNonNullElse(transaction.getRequestedCredentialScopes(), Collections.emptyList());
        if(!permittedScopes.stream().allMatch(scope -> authorizeScopes.contains(scope) || credentialScopes.contains(scope)))
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
                (String[]) transaction.getResolvedClaims().getId_token().get(ACR).get("values"));
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
        auditWrapper.logAudit(Action.LINK_AUTH_CODE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(transactionId, oidcTransaction), null);
        return responseWrapper;
    }

    public String getKeyHash(@NotNull String value) {
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
            throw new EsignetException(AES_CIPHER_FAILED);
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
            throw new EsignetException(AES_CIPHER_FAILED);
        }
    }

    private Key getSecretKeyFromHSM() {
        String keyAlias = getKeyAlias(OIDC_SERVICE_APP_ID, cacheSecretKeyRefId);
        if (Objects.nonNull(keyAlias)) {
            return keyStore.getSymmetricKey(keyAlias);
        }
        throw new EsignetException(NO_UNIQUE_ALIAS);
    }

    private String getKeyAlias(String keyAppId, String keyRefId) {
        Map<String, List<KeyAlias>> keyAliasMap = dbHelper.getKeyAliases(keyAppId, keyRefId, LocalDateTime.now(ZoneOffset.UTC));
        List<KeyAlias> currentKeyAliases = keyAliasMap.get(KeymanagerConstant.CURRENTKEYALIAS);
        if (!currentKeyAliases.isEmpty() && currentKeyAliases.size() == 1) {
            return currentKeyAliases.get(0).getAlias();
        }
        log.error("CurrentKeyAlias is not unique. KeyAlias count: {}", currentKeyAliases.size());
        throw new EsignetException(NO_UNIQUE_ALIAS);
    }

    protected Pair<String,String> validateAndGetSubjectAndNonce(String clientId, String idTokenHint) {
        try {
            String[] jwtParts = idTokenHint.split("\\.");
            if (jwtParts.length == 3) {
                String payload = new String(Base64.getDecoder().decode(jwtParts[1]));
                JSONObject payloadJson = new JSONObject(payload);
                String audience = payloadJson.getString(TokenService.AUD);
                if(!signupIDTokenAudience.equals(audience) || !signupIDTokenAudience.equals(clientId))
                    throw new EsignetException(ErrorConstants.INVALID_ID_TOKEN_HINT);
                return Pair.of(payloadJson.getString(TokenService.SUB), payloadJson.getString(TokenService.NONCE));
            }
        } catch (Exception e) {
            log.error("Failed to parse the given IDTokenHint as JWT", e);
        }
        throw new EsignetException(ErrorConstants.INVALID_ID_TOKEN_HINT);
    }
}
