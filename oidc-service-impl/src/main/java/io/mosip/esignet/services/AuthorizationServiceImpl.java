/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.claim.*;
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
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.AuthenticationContextClassRefUtil;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.core.util.LinkCodeQueue;
import lombok.extern.slf4j.Slf4j;

import org.apache.commons.collections.CollectionUtils;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.util.Pair;
import org.springframework.stereotype.Service;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

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

    private static final String VERIFIED_CLAIMS = "verified_claims";
    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd");

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private Authenticator authenticationWrapper;

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

    @Value("#{'${mosip.esignet.captcha.required}'.split(',')}")
    private List<String> captchaRequired;

    @Value("${mosip.esignet.signup-id-token-expire-seconds:60}")
    private int signupIDTokenValidity;

    @Value("${mosip.esignet.signup-id-token-audience}")
    private String signupIDTokenAudience;


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
    public OAuthDetailResponseV2 getOauthDetailsV3(OAuthDetailRequestV3 oauthDetailReqDto, HttpServletRequest httpServletRequest) throws EsignetException {
        //id_token_hint is an optional parameter, if provided then it is expected to be a valid JWT
        if (oauthDetailReqDto.getIdTokenHint() != null) {
            String subject = getSubject(oauthDetailReqDto.getIdTokenHint());
            Optional<Cookie> result = Arrays.stream(httpServletRequest.getCookies()).filter(x -> x.getName().equals(subject)).findFirst();
            if (result.isEmpty()) {
                throw new EsignetException(ErrorConstants.INVALID_ID_TOKEN_HINT);
            }
            String[] parts = result.get().getValue().split(SERVER_NONCE_SEPARATOR);
            oauthDetailReqDto.setState(parts.length == 2? parts[1] : result.get().getValue());
        }
        return getOauthDetailsV2(oauthDetailReqDto);
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

        for(Map.Entry<String, ClaimDetail> entry : transaction.getRequestedClaims().getUserinfo().entrySet()) {
            list.add(getClaimStatus(entry.getKey(), entry.getValue(), transaction.getClaimMetadata()));
        }

        //Profile update is mandated only if any essential verified claim is requested
        boolean isEssentialVerifiedClaimRequested = transaction.getRequestedClaims().getUserinfo()
                .entrySet()
                .stream()
                .anyMatch( entry -> entry.getValue() !=null && entry.getValue().isEssential() && entry.getValue().getVerification() != null);
        claimDetailResponse.setProfileUpdateRequired(isEssentialVerifiedClaimRequested);
        claimDetailResponse.setClaimStatus(list);
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
        signupRedirectResponse.setIdToken(tokenService.getIDToken(signupRedirectRequest.getTransactionId(), signupIDTokenAudience, signupIDTokenValidity, oidcTransaction));

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
        return signupRedirectResponse;
    }


    @Override
    public ResumeResponse resumeHaltedTransaction(ResumeRequest resumeRequest) {
        OIDCTransaction oidcTransaction = cacheUtilService.getHaltedTransaction(resumeRequest.getTransactionId());
        if(oidcTransaction == null) {
            throw new InvalidTransactionException();
        }

        ResumeResponse resumeResponse = new ResumeResponse();
        if(resumeRequest.isWithError()) {
            cacheUtilService.removeHaltedTransaction(resumeRequest.getTransactionId());
            resumeResponse.setStatus(Constants.RESUME_NOT_APPLICABLE);
            return resumeResponse;
        }

        //move the transaction to "authenticated" cache
        cacheUtilService.setAuthenticatedTransaction(resumeRequest.getTransactionId(), oidcTransaction);
        resumeResponse.setStatus(Constants.RESUMED);
        return resumeResponse;
    }

    //As pathFragment is included in the response header, we should sanitize the input to mitigate
    //response splitting vulnerability
    private String sanitizePathFragment(String pathFragment) {
        return pathFragment.replaceAll("[\r\n]", "");
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
            kycAuthResult = authorizationHelperService.handleInternalAuthenticateRequest(authRequest.getChallengeList().get(0), transaction,
                    httpServletRequest);
            transaction.setInternalAuthSuccess(true);
        }
        else {
            kycAuthResult = authorizationHelperService.delegateAuthenticateRequest(authRequest.getTransactionId(),
                    authRequest.getIndividualId(), authRequest.getChallengeList(), transaction);
        }

        //cache tokens on successful response
        transaction.setPartnerSpecificUserToken(kycAuthResult.getPartnerSpecificUserToken());
        transaction.setKycToken(kycAuthResult.getKycToken());
        transaction.setAuthTimeInSeconds(IdentityProviderUtil.getEpochSeconds());
        transaction.setClaimMetadata(kycAuthResult.getClaimsMetadata());
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
                oauthDetailReqDto.getAcrValues(),
                oauthDetailReqDto.getClaims()!=null ? oauthDetailReqDto.getClaims().getId_token():null ));
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
        oidcTransaction.setServerNonce(UUID.randomUUID().toString());
        oidcTransaction.setRequestedCredentialScopes(authorizationHelperService.getCredentialScopes(oauthDetailReqDto.getScope()));
        oidcTransaction.setInternalAuthSuccess(false);
        return Pair.of(oAuthDetailResponse, oidcTransaction);
    }

    private Claims getRequestedClaims(OAuthDetailRequest oauthDetailRequest, ClientDetail clientDetailDto)
            throws EsignetException {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        resolvedClaims.setId_token(new HashMap<>());

        String[] requestedScopes = IdentityProviderUtil.splitAndTrimValue(oauthDetailRequest.getScope(), Constants.SPACE);
        ClaimsV2 requestedClaims = oauthDetailRequest.getClaims();
        boolean isRequestedUserInfoClaimsPresent = requestedClaims != null && requestedClaims.getUserinfo() != null;
        log.info("isRequestedUserInfoClaimsPresent ? {}", isRequestedUserInfoClaimsPresent);
        //Claims request parameter is allowed, only if 'openid' is part of the scope request parameter
        if(isRequestedUserInfoClaimsPresent && !Arrays.stream(requestedScopes).anyMatch(s  -> SCOPE_OPENID.equals(s)))
            throw new EsignetException(ErrorConstants.INVALID_SCOPE);

        log.info("Started to resolve claims based on the request scope {} and claims {}", requestedScopes, requestedClaims);

        Map<String, ClaimDetail> verifiedClaimsMap = new HashMap<>();
        if(isRequestedUserInfoClaimsPresent && requestedClaims.getUserinfo().get(VERIFIED_CLAIMS) != null) {
            JsonNode verifiedClaims = requestedClaims.getUserinfo().get(VERIFIED_CLAIMS);
            if(verifiedClaims.isArray()) {
                Iterator itr = verifiedClaims.iterator();
                while(itr.hasNext()) {
                    resolveVerifiedClaims((JsonNode) itr.next(), verifiedClaimsMap);
                }
            }
            else {
                resolveVerifiedClaims(verifiedClaims, verifiedClaimsMap);
            }
        }

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
                            resolvedClaims.getUserinfo().put(claimName, convertJsonNodeToClaimDetail(requestedClaims.getUserinfo().get(claimName)));
                        else if(claimBasedOnScope.contains(claimName))
                            resolvedClaims.getUserinfo().put(claimName, null);

                        //Verified claim request takes priority
                        if(verifiedClaimsMap.containsKey(claimName))
                            resolvedClaims.getUserinfo().put(claimName, verifiedClaimsMap.get(claimName));
                    });
        }

        log.info("Final resolved user claims : {}", resolvedClaims);
        return resolvedClaims;
    }

    private void resolveVerifiedClaims(JsonNode verifiedClaims, Map<String, ClaimDetail> verifiedClaimsMap) {
        ClaimDetail verifiedClaim = convertJsonNodeToClaimDetail(verifiedClaims);
        validateVerifiedClaims(verifiedClaim);
        //iterate through all the claims in the verified_claims object
        for(Map.Entry<String, ClaimDetail> entry : verifiedClaim.getClaims().entrySet()) {
            ClaimDetail claimDetail = new ClaimDetail();
            claimDetail.setVerification(verifiedClaim.getVerification());
            claimDetail.setEssential(entry.getValue() != null && entry.getValue().isEssential());
            claimDetail.setPurpose(entry.getValue() != null? entry.getValue().getPurpose(): null);
            verifiedClaimsMap.put(entry.getKey(), claimDetail);
        }
    }

    private ClaimDetail convertJsonNodeToClaimDetail(JsonNode claimDetailJsonNode) {
        try {
            if(claimDetailJsonNode.isNull())
                return null;
            return objectMapper.treeToValue(claimDetailJsonNode, ClaimDetail.class);
        } catch (JsonProcessingException e) {
            log.error("Failed to parse the requested claim details", e);
        }
        throw new EsignetException(ErrorConstants.INVALID_CLAIM);
    }

    private void validateVerifiedClaims(ClaimDetail verifiedClaim) {
        if(verifiedClaim == null)
            throw new EsignetException(ErrorConstants.INVALID_VERIFIED_CLAIMS);

        if(verifiedClaim.getVerification() == null) //TODO add more validations
            throw new EsignetException(ErrorConstants.INVALID_VERIFICATION);

        if(verifiedClaim.getClaims() == null || verifiedClaim.getClaims().isEmpty())
            throw new EsignetException(ErrorConstants.INVALID_VERIFIED_CLAIMS);
    }

    private ClaimDetail resolveACRClaim(List<String> registeredACRs, String requestedAcr, Map<String, ClaimDetail> requestedIdToken) throws EsignetException {
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setEssential(true);

        log.info("Registered ACRS :{}", registeredACRs);
        if(registeredACRs == null || registeredACRs.isEmpty())
            throw new EsignetException(ErrorConstants.NO_ACR_REGISTERED);

        //First priority is given to claims request parameter
        if(requestedIdToken != null && requestedIdToken.get(ACR) != null) {
            String [] acrs = requestedIdToken.get(ACR).getValues();
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

    private String getSubject(String idTokenHint) {
        try {
            String[] jwtParts = idTokenHint.split("\\.");
            if (jwtParts.length == 3) {
                String payload = new String(Base64.getDecoder().decode(jwtParts[1]));
                JSONObject payloadJson = new JSONObject(payload);
                return payloadJson.getString(TokenService.SUB);
            }
        } catch (Exception e) {
           log.error("Failed to parse the given IDTokenHint as JWT", e);
        }
        throw new EsignetException(ErrorConstants.INVALID_ID_TOKEN_HINT);
    }

    private ClaimStatus getClaimStatus(String claim, ClaimDetail claimDetail, Map<String,
            List<VerificationDetail>> storedVerificationData) {
        if(storedVerificationData == null || storedVerificationData.isEmpty())
            return new ClaimStatus(claim, false, false);

        if(claimDetail == null || claimDetail.getVerification() == null || !CollectionUtils.isEmpty(storedVerificationData.get(claim)))
            return new ClaimStatus(claim, false, storedVerificationData.containsKey(claim));

        List<VerificationDetail> verificationDetails = storedVerificationData.get(claim);

        //check trust_framework
        Optional<VerificationDetail> result = verificationDetails.stream()
                .filter( vd -> doMatch(claimDetail.getVerification().getTrust_framework(), vd.getTrust_framework()))
                .findFirst();

        if(result.isEmpty())
            return new ClaimStatus(claim, false, true);

        //check verification datetime
        result = verificationDetails.stream()
                .filter( vd -> doMatch(claimDetail.getVerification().getTime(), vd.getTime(), dateTimeFormat))
                .findFirst();

        if(result.isEmpty())
            return new ClaimStatus(claim, false, true);

        //check verification_process
        result = verificationDetails.stream()
                .filter( vd -> doMatch(claimDetail.getVerification().getVerification_process(), vd.getVerification_process()))
                .findFirst();

        if(result.isEmpty())
            return new ClaimStatus(claim, false, true);

        //check assuranceLevel
        result = verificationDetails.stream()
                .filter( vd -> doMatch(claimDetail.getVerification().getAssurance_level(), vd.getAssurance_level()))
                .findFirst();

        if(result.isEmpty())
            return new ClaimStatus(claim, false, true);

        return new ClaimStatus(claim, true, true);
    }

    private boolean doMatch(FilterCriteria filterCriteria, String value) {
        if(filterCriteria == null)
            return true;
        if(filterCriteria.getValue() != null)
           return filterCriteria.getValue().equals(value);
        if(filterCriteria.getValues() != null)
            return filterCriteria.getValues().contains(value);
        return false;
    }

    private boolean doMatch(FilterDateTime filterDateTime, String value, SimpleDateFormat format) {
        if(filterDateTime == null)
            return true;

        if(value == null || value.isEmpty())
            return false;

        try {
            format.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = format.parse(value);
            return ((System.currentTimeMillis() - date.getTime())/1000) < filterDateTime.getMax_age();
        } catch (ParseException e) {
            log.error("Failed to parse the given date-time : {}", value, e);
        }
        return false;
    }

}
