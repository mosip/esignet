/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.ClaimDetail;
import io.mosip.esignet.api.dto.Claims;
import io.mosip.esignet.api.dto.KycAuthResult;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.api.util.ConsentAction;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.AuthorizationService;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.core.util.LinkCodeQueue;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.constants.Constants.*;
import static io.mosip.esignet.core.spi.TokenService.ACR;
import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA3_256;
import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA_256;

@Slf4j
@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private Authenticator authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    private AuthorizationHelperService authorizationHelperService;

    @Autowired
    private AuditPlugin auditWrapper;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    ConsentHelperService consentHelperService;

    @Value("#{${mosip.esignet.ui.config.key-values}}")
    private Map<String, Object> uiConfigMap;

    @Value("#{${mosip.esignet.openid.scope.claims}}")
    private Map<String, List<String>> claims;

    @Value("${mosip.esignet.auth-txn-id-length:10}")
    private int authTransactionIdLength;

    //Number of times generate-link-code could be invoked per transaction
    @Value("${mosip.esignet.generate-link-code.limit-per-transaction:10}")
    private int linkCodeLimitPerTransaction;

    @Value("${mosip.esignet.credential.scope.auto-permit:true}")
    private boolean autoPermitCredentialScopes;

    @Value("${mosip.esignet.credential.mandate-pkce:true}")
    private boolean mandatePKCEForVC;

    @Value("#{${mosip.esignet.captcha.required.auth-factors}}")
    private List<String> authFactorsRequireCaptchaValidation;


    @Override
    public OAuthDetailResponseV1 getOauthDetails(OAuthDetailRequest oauthDetailReqDto) throws EsignetException {
        ClientDetail clientDetailDto = clientManagementService.getClientDetails(oauthDetailReqDto.getClientId());
        OAuthDetailResponseV1 oAuthDetailResponseV1 = new OAuthDetailResponseV1();
        Pair<OAuthDetailResponse, OIDCTransaction> pair = checkAndBuildOIDCTransaction(oauthDetailReqDto, clientDetailDto, oAuthDetailResponseV1);
        oAuthDetailResponseV1 = (OAuthDetailResponseV1) pair.getFirst();
        oAuthDetailResponseV1.setClientName(clientDetailDto.getName().get(Constants.NONE_LANG_KEY));
        pair.getSecond().setOauthDetailsHash(getOauthDetailsResponseHash(oAuthDetailResponseV1));
        cacheUtilService.setTransaction(oAuthDetailResponseV1.getTransactionId(), pair.getSecond());
        auditWrapper.logAudit(Action.TRANSACTION_STARTED, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(oAuthDetailResponseV1.getTransactionId(),
                pair.getSecond()), null);
        return oAuthDetailResponseV1;
    }

    @Override
    public OAuthDetailResponseV2 getOauthDetailsV2(OAuthDetailRequestV2 oauthDetailReqDto) throws EsignetException {
        ClientDetail clientDetailDto = clientManagementService.getClientDetails(oauthDetailReqDto.getClientId());
        OAuthDetailResponseV2 oAuthDetailResponseV2 = new OAuthDetailResponseV2();
        Pair<OAuthDetailResponse, OIDCTransaction> pair = checkAndBuildOIDCTransaction(oauthDetailReqDto, clientDetailDto, oAuthDetailResponseV2);
        oAuthDetailResponseV2 = (OAuthDetailResponseV2) pair.getFirst();
        oAuthDetailResponseV2.setClientName(clientDetailDto.getName());

        OIDCTransaction oidcTransaction = pair.getSecond();

        //TODO - Need to check to persist credential scopes in consent registry
        oAuthDetailResponseV2.setCredentialScopes(oidcTransaction.getRequestedCredentialScopes());

        oidcTransaction.setOauthDetailsHash(getOauthDetailsResponseHash(oAuthDetailResponseV2));

        //PKCE support
        oidcTransaction.setProofKeyCodeExchange(ProofKeyCodeExchange.getInstance(oauthDetailReqDto.getCodeChallenge(),
                oauthDetailReqDto.getCodeChallengeMethod()));

        if(mandatePKCEForVC && CollectionUtils.isNotEmpty(oidcTransaction.getRequestedCredentialScopes()) &&
                oidcTransaction.getProofKeyCodeExchange() == null) {
            log.error("PKCE is mandated for VC scoped transactions");
            throw new EsignetException(ErrorConstants.INVALID_PKCE_CHALLENGE);
        }

        cacheUtilService.setTransaction(oAuthDetailResponseV2.getTransactionId(), pair.getSecond());
        auditWrapper.logAudit(Action.TRANSACTION_STARTED, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(oAuthDetailResponseV2.getTransactionId(),
                pair.getSecond()), null);
        return oAuthDetailResponseV2;
    }

    @Override
    public OtpResponse sendOtp(OtpRequest otpRequest) throws EsignetException {
        authorizationHelperService.validateSendOtpCaptchaToken(otpRequest.getCaptchaToken());

        OIDCTransaction transaction = cacheUtilService.getPreAuthTransaction(otpRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        SendOtpResult sendOtpResult = authorizationHelperService.delegateSendOtpRequest(otpRequest, transaction);
        OtpResponse otpResponse = new OtpResponse();
        otpResponse.setTransactionId(otpRequest.getTransactionId());
        otpResponse.setMaskedEmail(sendOtpResult.getMaskedEmail());
        otpResponse.setMaskedMobile(sendOtpResult.getMaskedMobile());
        auditWrapper.logAudit(Action.SEND_OTP, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(otpRequest.getTransactionId(), transaction), null);
        return otpResponse;
    }

    @Override
    public AuthResponse authenticateUser(AuthRequest authRequest)  throws EsignetException {
        authenticate(authRequest, false);
        AuthResponse authRespDto = new AuthResponse();
        authRespDto.setTransactionId(authRequest.getTransactionId());
        return authRespDto;
    }

    @Override
    public AuthResponseV2 authenticateUserV2(AuthRequest authRequest) throws EsignetException {
        OIDCTransaction transaction = authenticate(authRequest, true);
        AuthResponseV2 authRespDto = new AuthResponseV2();
        authRespDto.setTransactionId(authRequest.getTransactionId());
        authRespDto.setConsentAction(transaction.getConsentAction());
        return authRespDto;
    }

    @Override
    public AuthResponseV2 authenticateUserV3(AuthRequestV2 authRequest) throws EsignetException {
        if(!CollectionUtils.isEmpty(authFactorsRequireCaptchaValidation) &&
                authRequest.getChallengeList().stream().anyMatch(authChallenge ->
                        authFactorsRequireCaptchaValidation.contains(authChallenge.getAuthFactorType()))) {
            authorizationHelperService.validateCaptchaToken(authRequest.getCaptchaToken());
        }
        return authenticateUserV2(authRequest);
    }

    @Override
    public AuthCodeResponse getAuthCode(AuthCodeRequest authCodeRequest) throws EsignetException {
        OIDCTransaction transaction = cacheUtilService.getAuthenticatedTransaction(authCodeRequest.getTransactionId());
        if(transaction == null) {
            throw new InvalidTransactionException();
        }

        List<String> acceptedClaims = authCodeRequest.getAcceptedClaims();
        List<String> acceptedScopes = authCodeRequest.getPermittedAuthorizeScopes();
        if(ConsentAction.NOCAPTURE.equals(transaction.getConsentAction())) {
            acceptedClaims = transaction.getAcceptedClaims();
            acceptedScopes = transaction.getPermittedScopes();
        }

        //Combination of OIDC and credential scopes are not allowed in single OIDC transaction
        if(CollectionUtils.isNotEmpty(transaction.getRequestedCredentialScopes()) && autoPermitCredentialScopes) {
            log.info("Permitting the requested credential scopes automatically");
            acceptedScopes = transaction.getRequestedCredentialScopes();
        }

        authorizationHelperService.validateAcceptedClaims(transaction, acceptedClaims);
        authorizationHelperService.validatePermittedScopes(transaction, acceptedScopes);

        String authCode = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, UUID.randomUUID().toString());
        // cache consent with auth-code-hash as key
        transaction.setCodeHash(authorizationHelperService.getKeyHash(authCode));
        transaction.setAcceptedClaims(acceptedClaims);
        transaction.setPermittedScopes(acceptedScopes);
        consentHelperService.updateUserConsent(transaction, null);
        transaction = cacheUtilService.setAuthCodeGeneratedTransaction(authCodeRequest.getTransactionId(), transaction);
        auditWrapper.logAudit(Action.GET_AUTH_CODE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(authCodeRequest.getTransactionId(), transaction), null);

        AuthCodeResponse authCodeResponse = new AuthCodeResponse();
        authCodeResponse.setCode(authCode);
        authCodeResponse.setRedirectUri(transaction.getRedirectUri());
        authCodeResponse.setNonce(transaction.getNonce());
        authCodeResponse.setState(transaction.getState());
        return authCodeResponse;
    }

    private OIDCTransaction authenticate(AuthRequest authRequest, boolean checkConsentAction) {
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
        authorizationHelperService.setIndividualId(authRequest.getIndividualId(), transaction);

        if(checkConsentAction) {
            consentHelperService.processConsent(transaction, false);
        }

        cacheUtilService.setAuthenticatedTransaction(authRequest.getTransactionId(), transaction);
        auditWrapper.logAudit(Action.AUTHENTICATE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(authRequest.getTransactionId(), transaction), null);
        return transaction;
    }

    private Pair<OAuthDetailResponse, OIDCTransaction> checkAndBuildOIDCTransaction(OAuthDetailRequest oauthDetailReqDto,
                                                                                    ClientDetail clientDetailDto,
                                                                                    OAuthDetailResponse oAuthDetailResponse) {
        log.info("nonce : {} Valid client id found, proceeding to validate redirect URI", oauthDetailReqDto.getNonce());
        IdentityProviderUtil.validateRedirectURI(clientDetailDto.getRedirectUris(), oauthDetailReqDto.getRedirectUri());

        //Resolve the final set of claims based on registered and request parameter.
        Claims resolvedClaims = getRequestedClaims(oauthDetailReqDto, clientDetailDto);
        //Resolve and set ACR claim
        resolvedClaims.getId_token().put(ACR, resolveACRClaim(clientDetailDto.getAcrValues(),
                oauthDetailReqDto.getAcrValues(), oauthDetailReqDto.getClaims()));
        log.info("Final resolved claims : {}", resolvedClaims);

        final String transactionId = IdentityProviderUtil.createTransactionId(oauthDetailReqDto.getNonce());
        oAuthDetailResponse.setTransactionId(transactionId);
        oAuthDetailResponse.setAuthFactors(authenticationContextClassRefUtil.getAuthFactors(
                resolvedClaims.getId_token().get(ACR).getValues()
        ));

        Map<String, List> claimsMap = authorizationHelperService.getClaimNames(resolvedClaims);
        oAuthDetailResponse.setEssentialClaims(claimsMap.get(ESSENTIAL));
        oAuthDetailResponse.setVoluntaryClaims(claimsMap.get(VOLUNTARY));
        oAuthDetailResponse.setAuthorizeScopes(authorizationHelperService.getAuthorizeScopes(oauthDetailReqDto.getScope()));
        oAuthDetailResponse.setConfigs(uiConfigMap);
        oAuthDetailResponse.setLogoUrl(clientDetailDto.getLogoUri());
        oAuthDetailResponse.setRedirectUri(oauthDetailReqDto.getRedirectUri());

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setTransactionId(oAuthDetailResponse.getTransactionId());
        oidcTransaction.setEssentialClaims(oAuthDetailResponse.getEssentialClaims());
        oidcTransaction.setVoluntaryClaims(oAuthDetailResponse.getVoluntaryClaims());
        oidcTransaction.setRedirectUri(oauthDetailReqDto.getRedirectUri());
        oidcTransaction.setRelyingPartyId(clientDetailDto.getRpId());
        oidcTransaction.setClientId(clientDetailDto.getId());
        oidcTransaction.setRequestedClaims(resolvedClaims);
        oidcTransaction.setRequestedAuthorizeScopes(oAuthDetailResponse.getAuthorizeScopes());
        oidcTransaction.setNonce(oauthDetailReqDto.getNonce());
        oidcTransaction.setState(oauthDetailReqDto.getState());
        oidcTransaction.setClaimsLocales(IdentityProviderUtil.splitAndTrimValue(oauthDetailReqDto.getClaimsLocales(), SPACE));
        oidcTransaction.setAuthTransactionId(getAuthTransactionId(oAuthDetailResponse.getTransactionId()));
        oidcTransaction.setLinkCodeQueue(new LinkCodeQueue(2));
        oidcTransaction.setCurrentLinkCodeLimit(linkCodeLimitPerTransaction);
        oidcTransaction.setRequestedCredentialScopes(authorizationHelperService.getCredentialScopes(oauthDetailReqDto.getScope()));
        return Pair.of(oAuthDetailResponse, oidcTransaction);
    }

    private Claims getRequestedClaims(OAuthDetailRequest oauthDetailRequest, ClientDetail clientDetailDto)
            throws EsignetException {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        resolvedClaims.setId_token(new HashMap<>());

        String[] requestedScopes = IdentityProviderUtil.splitAndTrimValue(oauthDetailRequest.getScope(), Constants.SPACE);
        Claims requestedClaims = oauthDetailRequest.getClaims();
        boolean isRequestedUserInfoClaimsPresent = requestedClaims != null && requestedClaims.getUserinfo() != null;
        log.info("isRequestedUserInfoClaimsPresent ? {}", isRequestedUserInfoClaimsPresent);

        //Claims request parameter is allowed, only if 'openid' is part of the scope request parameter
        if(isRequestedUserInfoClaimsPresent && !Arrays.stream(requestedScopes).anyMatch(s  -> SCOPE_OPENID.equals(s)))
            throw new EsignetException(ErrorConstants.INVALID_SCOPE);

        log.info("Started to resolve claims based on the request scope {} and claims {}", requestedScopes, requestedClaims);
        //get claims based on scope
        List<String> claimBasedOnScope = new ArrayList<>();
        Arrays.stream(requestedScopes)
                .forEach(scope -> { claimBasedOnScope.addAll(claims.getOrDefault(scope, new ArrayList<>())); });

        log.info("Resolved claims: {} based on request scope : {}", claimBasedOnScope, requestedScopes);

        //claims considered only if part of registered claims
        if(clientDetailDto.getClaims() != null) {
            clientDetailDto.getClaims()
                    .stream()
                    .forEach( claimName -> {
                        if(isRequestedUserInfoClaimsPresent && requestedClaims.getUserinfo().containsKey(claimName))
                            resolvedClaims.getUserinfo().put(claimName, requestedClaims.getUserinfo().get(claimName));
                        else if(claimBasedOnScope.contains(claimName))
                            resolvedClaims.getUserinfo().put(claimName, null);
                    });
        }

        log.info("Final resolved user claims : {}", resolvedClaims);
        return resolvedClaims;
    }

    private ClaimDetail resolveACRClaim(List<String> registeredACRs, String requestedAcr, Claims requestedClaims) throws EsignetException {
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setEssential(true);

        log.info("Registered ACRS :{}", registeredACRs);
        if(registeredACRs == null || registeredACRs.isEmpty())
            throw new EsignetException(ErrorConstants.NO_ACR_REGISTERED);

        //First priority is given to claims request parameter
        if(requestedClaims != null && requestedClaims.getId_token() != null && requestedClaims.getId_token().get(ACR) != null) {
            String [] acrs = requestedClaims.getId_token().get(ACR).getValues();
            String[] filteredAcrs = Arrays.stream(acrs).filter(acr -> registeredACRs.contains(acr)).toArray(String[]::new);
            if(filteredAcrs.length > 0) {
                claimDetail.setValues(filteredAcrs);
                return claimDetail;
            }
            log.info("No ACRS found / filtered in claims request parameter : {}", acrs);
        }
        //Next priority is given to acr_values request parameter
        String[] acrs = IdentityProviderUtil.splitAndTrimValue(requestedAcr, Constants.SPACE);
        String[] filteredAcrs = Arrays.stream(acrs).filter(acr -> registeredACRs.contains(acr)).toArray(String[]::new);
        if(filteredAcrs.length > 0) {
            claimDetail.setValues(filteredAcrs);
            return claimDetail;
        }
        log.info("Considering registered acrs as no valid acrs found in acr_values request param: {}", requestedAcr);
        claimDetail.setValues(registeredACRs.toArray(new String[0]));
        return claimDetail;
    }

    private String getOauthDetailsResponseHash(OAuthDetailResponse oauthDetailResponse) {
        try {
            String json = objectMapper.writeValueAsString(oauthDetailResponse);
            return IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA_256, json);
        } catch (Exception e) {
            log.error("Failed to generate oauth-details-response hash", e);
        }
        throw new EsignetException(ErrorConstants.FAILED_TO_GENERATE_HEADER_HASH);
    }

    private String getOauthDetailsResponseHash(OAuthDetailResponseV2 oauthDetailResponseV2) {
        try {
            String json = objectMapper.writeValueAsString(oauthDetailResponseV2);
            return IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA_256, json);
        } catch (Exception e) {
            log.error("Failed to generate oauth-details-response hash", e);
        }
        throw new EsignetException(ErrorConstants.FAILED_TO_GENERATE_HEADER_HASH);
    }

    private String getAuthTransactionId(String oidcTransactionId) {
        final String transactionId = oidcTransactionId.replaceAll("_|-", "");
        final byte[] oidcTransactionIdBytes = transactionId.getBytes();
        final byte[] authTransactionIdBytes = new byte[authTransactionIdLength];
        int i = oidcTransactionIdBytes.length - 1;
        int j = 0;
        while(j < authTransactionIdLength) {
            authTransactionIdBytes[j++] = oidcTransactionIdBytes[i--];
            if(i < 0) { i = oidcTransactionIdBytes.length - 1; }
        }
        return new String(authTransactionIdBytes);
    }
}
