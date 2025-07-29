/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.mosip.esignet.api.dto.claim.*;
import io.mosip.esignet.api.dto.KycAuthResult;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.spi.AuditPlugin;
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
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.core.util.LinkCodeQueue;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections.CollectionUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.UUID;

import static io.mosip.esignet.core.constants.Constants.*;
import static io.mosip.esignet.core.spi.TokenService.ACR;
import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA3_256;
import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA_256;

@Slf4j
@Service
public class AuthorizationServiceImpl implements AuthorizationService {

    private static final String KBI_FIELD_DETAILS_CONFIG_KEY = "auth.factor.kbi.field-details";

    public static final String REQUIRE_PAR= "require_pushed_authorization_requests";

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private CacheUtilService cacheUtilService;
    
    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    private AuthorizationHelperService authorizationHelperService;

    @Autowired
    private AuditPlugin auditWrapper;

    @Autowired
    private ObjectMapper objectMapper;

    private static ObjectMapper oAuthMapper;
    static {
        oAuthMapper = new ObjectMapper()
                .configure(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY, true)
                .configure(SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS, true);
    }

    @Autowired
    private ConsentHelperService consentHelperService;

    @Autowired
    private ClaimsHelperService claimsHelperService;

    @Value("#{${mosip.esignet.ui.config.key-values}}")
    private HashMap<String, Object> uiConfigMap;

    @Value("${mosip.esignet.auth-txn-id-length:10}")
    private int authTransactionIdLength;

    //Number of times generate-link-code could be invoked per transaction
    @Value("${mosip.esignet.generate-link-code.limit-per-transaction:10}")
    private int linkCodeLimitPerTransaction;

    @Value("${mosip.esignet.credential.scope.auto-permit:true}")
    private boolean autoPermitCredentialScopes;

    @Value("${mosip.esignet.credential.mandate-pkce:true}")
    private boolean mandatePKCEForVC;

    @Value("#{'${mosip.esignet.captcha.required}'.split(',')}")
    private List<String> captchaRequired;

    @Value("${mosip.esignet.signup-id-token-expire-seconds:60}")
    private int signupIDTokenValidity;

    @Value("${mosip.esignet.signup-id-token-audience}")
    private String signupIDTokenAudience;

    @Value("${mosip.esignet.authenticator.default.auth-factor.kbi.field-details-url}")
    private String KbiFormDetailsUrl;

    @Autowired
    private ResourceLoader resourceLoader;

    @PostConstruct
    public void init() {
        if(KbiFormDetailsUrl == null || KbiFormDetailsUrl.isEmpty()) {
            log.info("No kbi.field-details-url configured for KBI field details. Skipping url load.");
            return;
        }
        try {
            JsonNode fieldDetailsJson = fetchKBIFieldDetailsFromResource(KbiFormDetailsUrl);
            if (fieldDetailsJson != null) {
                uiConfigMap.put(KBI_FIELD_DETAILS_CONFIG_KEY, fieldDetailsJson);
                return;
            }
            log.error("*** Empty KBI details from URL: {} ***", KbiFormDetailsUrl);
        } catch (Exception e) {
            log.error("Error loading form details from URL: {}", KbiFormDetailsUrl, e);
        }
    }


    @Override
    public OAuthDetailResponseV1 getOauthDetails(OAuthDetailRequest oauthDetailReqDto) throws EsignetException {
        ClientDetail clientDetailDto = clientManagementService.getClientDetails(oauthDetailReqDto.getClientId());
        assertPARRequiredIsFalse(clientDetailDto);
        validateRedirectURIAndNonce(oauthDetailReqDto, clientDetailDto);
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
        assertPARRequiredIsFalse(clientDetailDto);
        validateRedirectURIAndNonce(oauthDetailReqDto, clientDetailDto);
        OAuthDetailResponseV2 oAuthDetailResponseV2 = new OAuthDetailResponseV2();
        return buildTransactionAndOAuthDetailResponse(oauthDetailReqDto, clientDetailDto, oAuthDetailResponseV2);
    }

    @Override
    public OAuthDetailResponseV2 getOauthDetailsV3(OAuthDetailRequestV3 oauthDetailReqDto, HttpServletRequest httpServletRequest) throws EsignetException {
        //id_token_hint is an optional parameter, if provided then it is expected to be a valid JWT
        handleIdTokenHint(oauthDetailReqDto, httpServletRequest);
        return getOauthDetailsV2(oauthDetailReqDto);
    }

    @Override
    public OAuthDetailResponseV2 getPAROAuthDetails(PushedOAuthDetailRequest pushedOAuthDetailRequest, HttpServletRequest httpServletRequest) throws EsignetException {
        String requestUriUniqueId = pushedOAuthDetailRequest.getRequestUri().substring(PAR_REQUEST_URI_PREFIX.length());
        PushedAuthorizationRequest pushedAuthorizationRequest = cacheUtilService.getAndEvictPAR(requestUriUniqueId);
        if(pushedAuthorizationRequest == null) {
            log.error("There is no par request with this requestUri: {}", pushedOAuthDetailRequest.getRequestUri());
            throw new EsignetException(ErrorConstants.INVALID_REQUEST);
        }
        if(!pushedAuthorizationRequest.getClient_id().equals(pushedOAuthDetailRequest.getClientId())) {
            log.error("clientId does not match with the clientId in par cache");
            throw new EsignetException(ErrorConstants.INVALID_REQUEST);
        }
        OAuthDetailRequestV3 oAuthDetailRequestV3 = mapPushedAuthorizationRequestToOAuthDetailsRequest(pushedAuthorizationRequest);
        handleIdTokenHint(oAuthDetailRequestV3, httpServletRequest);
        ClientDetail clientDetailDto = clientManagementService.getClientDetails(oAuthDetailRequestV3.getClientId());
        OAuthDetailResponseV2 oAuthDetailResponseV2 = new OAuthDetailResponseV2();
        return buildTransactionAndOAuthDetailResponse(oAuthDetailRequestV3, clientDetailDto, oAuthDetailResponseV2);
    }

    @Override
    public OtpResponse sendOtp(OtpRequest otpRequest) throws EsignetException {
        authorizationHelperService.validateSendOtpCaptchaToken(otpRequest.getCaptchaToken());

        OIDCTransaction transaction = cacheUtilService.getPreAuthTransaction(otpRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        transaction = cacheUtilService.updateIndividualIdHashInPreAuthCache(otpRequest.getTransactionId(),
                otpRequest.getIndividualId());

        if(cacheUtilService.isIndividualIdBlocked(transaction.getIndividualIdHash()))
            throw new EsignetException(ErrorConstants.INDIVIDUAL_ID_BLOCKED);

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
        authenticate(authRequest, false, null);
        AuthResponse authRespDto = new AuthResponse();
        authRespDto.setTransactionId(authRequest.getTransactionId());
        return authRespDto;
    }

    @Override
    public AuthResponseV2 authenticateUserV2(AuthRequest authRequest) throws EsignetException {
        OIDCTransaction transaction = authenticate(authRequest, true, null);
        AuthResponseV2 authRespDto = new AuthResponseV2();
        authRespDto.setTransactionId(authRequest.getTransactionId());
        authRespDto.setConsentAction(transaction.getConsentAction());
        return authRespDto;
    }

    @Override
    public AuthResponseV2 authenticateUserV3(AuthRequestV2 authRequest, HttpServletRequest httpServletRequest) throws EsignetException {
        if(!CollectionUtils.isEmpty(captchaRequired) &&
                authRequest.getChallengeList().stream().anyMatch(authChallenge ->
                        captchaRequired.contains(authChallenge.getAuthFactorType().toLowerCase()))) {
            authorizationHelperService.validateCaptchaToken(authRequest.getCaptchaToken());
        }
        OIDCTransaction transaction = authenticate(authRequest, true, httpServletRequest);
        AuthResponseV2 authRespDto = new AuthResponseV2();
        authRespDto.setTransactionId(authRequest.getTransactionId());
        authRespDto.setConsentAction(transaction.getConsentAction());
        return authRespDto;
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

        claimsHelperService.validateAcceptedClaims(transaction, acceptedClaims);
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

    @Override
    public ClaimDetailResponse getClaimDetails(String transactionId) {
        OIDCTransaction transaction = cacheUtilService.getAuthenticatedTransaction(transactionId);
        if(transaction == null) {
            throw new InvalidTransactionException();
        }
        ClaimDetailResponse claimDetailResponse = new ClaimDetailResponse();
        claimDetailResponse.setConsentAction(transaction.getConsentAction());
        claimDetailResponse.setTransactionId(transactionId);
        List<ClaimStatus> list = new ArrayList<>();

        log.debug("Get claims status based on stored claim metadata : {}", transaction.getClaimMetadata());
        for(Map.Entry<String, List<Map<String, Object>>> entry : transaction.getResolvedClaims().getUserinfo().entrySet()) {
            list.add(claimsHelperService.getClaimStatus(entry.getKey(), entry.getValue(), transaction.getClaimMetadata()));
        }

        //Profile update is mandated only if any essential verified claim is requested
        boolean unverifiedEssentialClaimsExists = transaction.getResolvedClaims().getUserinfo()
                .entrySet()
                .stream()
                .anyMatch( entry -> entry.getValue().stream()
                        .anyMatch(m ->
                                (boolean) m.getOrDefault("essential", false) && m.get("verification") != null &&
                                ((transaction.getClaimMetadata().get(entry.getKey()) == null || transaction.getClaimMetadata().get(entry.getKey()).isEmpty()))));
        claimDetailResponse.setProfileUpdateRequired(unverifiedEssentialClaimsExists);
        claimDetailResponse.setClaimStatus(list);

        auditWrapper.logAudit(Action.CLAIM_DETAILS, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(transactionId, transaction), null);
        return claimDetailResponse;
    }


    @Override
    public SignupRedirectResponse prepareSignupRedirect(SignupRedirectRequest signupRedirectRequest, HttpServletResponse response) {
        OIDCTransaction oidcTransaction = cacheUtilService.getAuthenticatedTransaction(signupRedirectRequest.getTransactionId());
        if(oidcTransaction == null) {
            throw new InvalidTransactionException();
        }

        SignupRedirectResponse signupRedirectResponse = new SignupRedirectResponse();
        signupRedirectResponse.setTransactionId(signupRedirectRequest.getTransactionId());
        signupRedirectResponse.setIdToken(tokenService.getIDToken(signupRedirectRequest.getTransactionId(), signupIDTokenAudience, signupIDTokenValidity,
                oidcTransaction, oidcTransaction.getServerNonce()));

        //Move the transaction to halted transaction
        cacheUtilService.setHaltedTransaction(signupRedirectRequest.getTransactionId(), oidcTransaction);

        Cookie cookie = new Cookie(signupRedirectRequest.getTransactionId(), oidcTransaction.getServerNonce()
                .concat(SERVER_NONCE_SEPARATOR)
                .concat(sanitizePathFragment(signupRedirectRequest.getPathFragment())));
        cookie.setMaxAge(signupIDTokenValidity);
        cookie.setSecure(true);
        cookie.setHttpOnly(true);
        cookie.setPath("/");
        response.addCookie(cookie);
        auditWrapper.logAudit(Action.PREPARE_SIGNUP_REDIRECT, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(signupRedirectRequest.getTransactionId(),
                oidcTransaction), null);
        return signupRedirectResponse;
    }


    @Override
    public CompleteSignupRedirectResponse completeSignupRedirect(CompleteSignupRedirectRequest completeSignupRedirectRequest) {
        OIDCTransaction oidcTransaction = cacheUtilService.getHaltedTransaction(completeSignupRedirectRequest.getTransactionId());
        if(oidcTransaction == null) {
            throw new InvalidTransactionException();
        }

        CompleteSignupRedirectResponse completeSignupRedirectResponse = new CompleteSignupRedirectResponse();
        if(Constants.VERIFICATION_COMPLETE.equals(oidcTransaction.getVerificationStatus())) {
            //move the transaction to "authenticated" cache
            cacheUtilService.setAuthenticatedTransaction(completeSignupRedirectRequest.getTransactionId(), oidcTransaction);
            completeSignupRedirectResponse.setStatus(Constants.VERIFICATION_COMPLETE);
            auditWrapper.logAudit(Action.COMPLETE_SIGNUP_REDIRECT, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(completeSignupRedirectRequest.getTransactionId(),
                    oidcTransaction), null);
            return completeSignupRedirectResponse;
        }
        cacheUtilService.removeHaltedTransaction(completeSignupRedirectRequest.getTransactionId());
        throw new EsignetException(oidcTransaction.getVerificationErrorCode() == null ? ErrorConstants.VERIFICATION_INCOMPLETE :
                oidcTransaction.getVerificationErrorCode());
    }

    //As pathFragment is included in the response header, we should sanitize the input to mitigate
    //response splitting vulnerability. Removed all whitespace characters
    private String sanitizePathFragment(String pathFragment) {
        return pathFragment.replaceAll("\\s", "");
    }

    private OIDCTransaction authenticate(AuthRequest authRequest, boolean checkConsentAction, HttpServletRequest httpServletRequest) {
        OIDCTransaction transaction = cacheUtilService.getPreAuthTransaction(authRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        transaction = cacheUtilService.updateIndividualIdHashInPreAuthCache(authRequest.getTransactionId(),
                authRequest.getIndividualId());
        if(cacheUtilService.isIndividualIdBlocked(transaction.getIndividualIdHash()))
            throw new EsignetException(ErrorConstants.INDIVIDUAL_ID_BLOCKED);

        //Validate provided challenge list auth-factors with resolved auth-factors for the transaction.
        Set<List<AuthenticationFactor>> providedAuthFactors = authorizationHelperService.getProvidedAuthFactors(transaction,
                authRequest.getChallengeList());

        KycAuthResult kycAuthResult;
        if(authRequest.getChallengeList().size() == 1 && authRequest.getChallengeList().get(0).getAuthFactorType().equals("IDT")) {
            kycAuthResult = authorizationHelperService.handleInternalAuthenticateRequest(authRequest.getChallengeList().get(0),authRequest.getIndividualId(), transaction,
                    httpServletRequest);
            transaction.setInternalAuthSuccess(true);
        }
        else {
            kycAuthResult = authorizationHelperService.delegateAuthenticateRequest(authRequest.getTransactionId(),
                    authRequest.getIndividualId(), authRequest.getChallengeList(), transaction);
            authorizationHelperService.setIndividualId(authRequest.getIndividualId(), transaction);
        }

        //cache tokens on successful response
        transaction.setPartnerSpecificUserToken(kycAuthResult.getPartnerSpecificUserToken());
        transaction.setKycToken(kycAuthResult.getKycToken());
        transaction.setAuthTimeInSeconds(IdentityProviderUtil.getEpochSeconds());
        transaction.setClaimMetadata(kycAuthResult.getClaimsMetadata());
        transaction.setProvidedAuthFactors(providedAuthFactors.stream().map(acrFactors -> acrFactors.stream()
                .map(AuthenticationFactor::getType)
                .collect(Collectors.toList())).collect(Collectors.toSet()));

        if(checkConsentAction) {
            consentHelperService.processConsent(transaction, false);
        }

        cacheUtilService.setAuthenticatedTransaction(authRequest.getTransactionId(), transaction);
        auditWrapper.logAudit(Action.AUTHENTICATE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(authRequest.getTransactionId(), transaction), null);
        return transaction;
    }

    private void validateRedirectURIAndNonce(OAuthDetailRequest oAuthDetailRequest, ClientDetail clientDetail) {
        log.info("nonce : {} Valid client id found, proceeding to validate redirect URI", oAuthDetailRequest.getNonce());
        IdentityProviderUtil.validateRedirectURI(clientDetail.getRedirectUris(), oAuthDetailRequest.getRedirectUri());
        authorizationHelperService.validateNonce(oAuthDetailRequest.getNonce());
    }

    private void assertPARRequiredIsFalse(ClientDetail clientDetail) throws EsignetException {
        boolean isParRequired = clientDetail.getAdditionalConfig(REQUIRE_PAR, false);
        if (isParRequired) {
            log.error("Pushed Authorization Request (PAR) flow is mandated for clientId: {}", clientDetail.getId());
            throw new EsignetException(ErrorConstants.INVALID_REQUEST);
        }
    }

    private Pair<OAuthDetailResponse, OIDCTransaction> checkAndBuildOIDCTransaction(OAuthDetailRequest oauthDetailReqDto,
                                                                                    ClientDetail clientDetailDto, OAuthDetailResponse oAuthDetailResponse) {
        //Resolve the final set of claims based on registered and request parameter.
        Claims resolvedClaims = claimsHelperService.resolveRequestedClaims(oauthDetailReqDto, clientDetailDto);
        //Resolve and set ACR claim
        resolvedClaims.getId_token().put(ACR, resolveACRClaim(clientDetailDto.getAcrValues(),
                oauthDetailReqDto.getAcrValues(),
                oauthDetailReqDto.getClaims()!=null ? oauthDetailReqDto.getClaims().getId_token():null ));
        log.info("Final resolved claims : {}", resolvedClaims);

        final String transactionId = IdentityProviderUtil.createTransactionId(oauthDetailReqDto.getNonce());
        oAuthDetailResponse.setTransactionId(transactionId);
        oAuthDetailResponse.setAuthFactors(authenticationContextClassRefUtil.getAuthFactors(
                (String[]) resolvedClaims.getId_token().get(ACR).get("values")
        ));

        Map<String, List<String>> claimsMap = claimsHelperService.getClaimNames(resolvedClaims);
        oAuthDetailResponse.setEssentialClaims(claimsMap.get(ESSENTIAL));
        oAuthDetailResponse.setVoluntaryClaims(claimsMap.get(VOLUNTARY));
        oAuthDetailResponse.setAuthorizeScopes(authorizationHelperService.getAuthorizeScopes(oauthDetailReqDto.getScope()));
        Map<String, Object> config = new HashMap<>(uiConfigMap);
        config.put("clientAdditionalConfig", clientDetailDto.getAdditionalConfig());
        oAuthDetailResponse.setConfigs(config);
        oAuthDetailResponse.setLogoUrl(clientDetailDto.getLogoUri());
        oAuthDetailResponse.setRedirectUri(oauthDetailReqDto.getRedirectUri());

        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setTransactionId(oAuthDetailResponse.getTransactionId());
        oidcTransaction.setEssentialClaims(oAuthDetailResponse.getEssentialClaims());
        oidcTransaction.setVoluntaryClaims(oAuthDetailResponse.getVoluntaryClaims());
        oidcTransaction.setRedirectUri(oauthDetailReqDto.getRedirectUri());
        oidcTransaction.setRelyingPartyId(clientDetailDto.getRpId());
        oidcTransaction.setClientId(clientDetailDto.getId());
        oidcTransaction.setResolvedClaims(resolvedClaims);
        oidcTransaction.setRequestedAuthorizeScopes(oAuthDetailResponse.getAuthorizeScopes());
        oidcTransaction.setNonce(oauthDetailReqDto.getNonce());
        oidcTransaction.setState(oauthDetailReqDto.getState());
        oidcTransaction.setClaimsLocales(IdentityProviderUtil.splitAndTrimValue(oauthDetailReqDto.getClaimsLocales(), SPACE));
        oidcTransaction.setAuthTransactionId(getAuthTransactionId(oAuthDetailResponse.getTransactionId()));
        oidcTransaction.setLinkCodeQueue(new LinkCodeQueue(2));
        oidcTransaction.setCurrentLinkCodeLimit(linkCodeLimitPerTransaction);
        oidcTransaction.setServerNonce(IdentityProviderUtil.createTransactionId(null));
        oidcTransaction.setRequestedCredentialScopes(authorizationHelperService.getCredentialScopes(oauthDetailReqDto.getScope()));
        oidcTransaction.setInternalAuthSuccess(false);
        oidcTransaction.setRequestedClaimDetails(oauthDetailReqDto.getClaims()!=null? oauthDetailReqDto.getClaims().getUserinfo() : null);
        oidcTransaction.setUserInfoResponseType(clientDetailDto.getAdditionalConfig(USERINFO_RESPONSE_TYPE,"JWS"));
        oidcTransaction.setPrompt(IdentityProviderUtil.splitAndTrimValue(oauthDetailReqDto.getPrompt(), Constants.SPACE));
        oidcTransaction.setConsentExpireMinutes(clientDetailDto.getAdditionalConfig(CONSENT_EXPIRE_IN_MINS, 0));
        return Pair.of(oAuthDetailResponse, oidcTransaction);
    }

    private OAuthDetailResponseV2 buildTransactionAndOAuthDetailResponse(OAuthDetailRequestV2 oauthDetailReqDto,
                                                                         ClientDetail clientDetailDto, OAuthDetailResponseV2 oAuthDetailResponseV2) {

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


    private Map<String, Object> resolveACRClaim(List<String> registeredACRs, String requestedAcr, Map<String, ClaimDetail> requestedIdToken) throws EsignetException {
        Map<String, Object> map = new HashMap<>();
        map.put("essential", true);

        log.info("Registered ACRS :{}", registeredACRs);
        if(registeredACRs == null || registeredACRs.isEmpty())
            throw new EsignetException(ErrorConstants.NO_ACR_REGISTERED);

        //First priority is given to claims request parameter
        if(requestedIdToken != null && requestedIdToken.get(ACR) != null) {
            String [] acrs = requestedIdToken.get(ACR).getValues();
            String[] filteredAcrs = Arrays.stream(acrs).filter(acr -> registeredACRs.contains(acr)).toArray(String[]::new);
            if(filteredAcrs.length > 0) {
                map.put("values", filteredAcrs);
                return map;
            }
            log.info("No ACRS found / filtered in claims request parameter : {}", acrs);
        }
        //Next priority is given to acr_values request parameter
        String[] acrs = IdentityProviderUtil.splitAndTrimValue(requestedAcr, Constants.SPACE);
        String[] filteredAcrs = Arrays.stream(acrs).filter(acr -> registeredACRs.contains(acr)).toArray(String[]::new);
        if(filteredAcrs.length > 0) {
            map.put("values", filteredAcrs);
            return map;
        }
        log.info("Considering registered acrs as no valid acrs found in acr_values request param: {}", requestedAcr);
        map.put("values", registeredACRs.toArray(new String[0]));
        return map;
    }

    private String getOauthDetailsResponseHash(OAuthDetailResponse oauthDetailResponse) {
        try {
            String json = oAuthMapper.writeValueAsString(objectMapper.convertValue(oauthDetailResponse, Object.class));
            log.info("Oauth details json: {}", json);
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



    private JsonNode fetchKBIFieldDetailsFromResource(String url) {
        try (InputStream resp = getResource(url)) {
            ObjectMapper objectMapper = new ObjectMapper();
            return objectMapper.readTree(resp);
        } catch (IOException e) {
            log.error("Error parsing the KBI form details: {}", e.getMessage(), e);
        }
        throw new EsignetException(ErrorConstants.KBI_SPEC_NOT_FOUND);
    }

    private InputStream getResource(String url) {
        try {
            Resource resource = resourceLoader.getResource(url);
            return resource.getInputStream();
        } catch (IOException e) {
            log.error("Failed to read resource from : {}", url, e);
            throw new EsignetException(ErrorConstants.KBI_SPEC_NOT_FOUND);
        }
    }

    private OAuthDetailRequestV3 mapPushedAuthorizationRequestToOAuthDetailsRequest(PushedAuthorizationRequest pushedAuthorizationRequest) {
        OAuthDetailRequestV3 oAuthDetailRequestV3 = new OAuthDetailRequestV3();
        oAuthDetailRequestV3.setClientId(pushedAuthorizationRequest.getClient_id());
        oAuthDetailRequestV3.setScope(pushedAuthorizationRequest.getScope());
        oAuthDetailRequestV3.setResponseType(pushedAuthorizationRequest.getResponse_type());
        oAuthDetailRequestV3.setRedirectUri(pushedAuthorizationRequest.getRedirect_uri());
        oAuthDetailRequestV3.setDisplay(pushedAuthorizationRequest.getDisplay());
        oAuthDetailRequestV3.setPrompt(pushedAuthorizationRequest.getPrompt());
        oAuthDetailRequestV3.setNonce(pushedAuthorizationRequest.getNonce());
        oAuthDetailRequestV3.setScope(pushedAuthorizationRequest.getScope());
        oAuthDetailRequestV3.setAcrValues(pushedAuthorizationRequest.getAcr_values());
        oAuthDetailRequestV3.setMaxAge(pushedAuthorizationRequest.getMax_age());
        oAuthDetailRequestV3.setClaims(pushedAuthorizationRequest.getClaims());
        oAuthDetailRequestV3.setClaimsLocales(pushedAuthorizationRequest.getClaims_locales());
        oAuthDetailRequestV3.setUiLocales(pushedAuthorizationRequest.getUi_locales());
        oAuthDetailRequestV3.setCodeChallenge(pushedAuthorizationRequest.getCode_challenge());
        oAuthDetailRequestV3.setCodeChallengeMethod(pushedAuthorizationRequest.getCode_challenge_method());
        return oAuthDetailRequestV3;
    }

    private void handleIdTokenHint(OAuthDetailRequestV3 oauthDetailReqDto, HttpServletRequest httpServletRequest) {
        if (oauthDetailReqDto.getIdTokenHint() != null) {
            Pair<String, String> pair = authorizationHelperService.validateAndGetSubjectAndNonce(oauthDetailReqDto.getClientId(), oauthDetailReqDto.getIdTokenHint());
            if(httpServletRequest.getCookies() == null)
                throw new EsignetException(ErrorConstants.INVALID_ID_TOKEN_HINT);
            Optional<Cookie> result = Arrays.stream(httpServletRequest.getCookies()).filter(x -> x.getName().equals(pair.getFirst())).findFirst();
            if (result.isEmpty()) {
                throw new EsignetException(ErrorConstants.INVALID_ID_TOKEN_HINT);
            }
            String[] parts = result.get().getValue().split(SERVER_NONCE_SEPARATOR);
            oauthDetailReqDto.setNonce(pair.getSecond());
            oauthDetailReqDto.setState(parts.length == 2? parts[1] : result.get().getValue());
        }
    }

}
