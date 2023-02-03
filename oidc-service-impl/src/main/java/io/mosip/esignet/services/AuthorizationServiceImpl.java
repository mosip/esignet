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
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.exception.InvalidTransactionException;
import io.mosip.esignet.core.spi.AuthorizationService;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.util.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.esignet.core.spi.TokenService.ACR;
import static io.mosip.esignet.core.constants.Constants.*;
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

    @Value("#{${mosip.esignet.ui.config.key-values}}")
    private Map<String, Object> uiConfigMap;

    @Value("#{${mosip.esignet.openid.scope.claims}}")
    private Map<String, List<String>> claims;

    @Value("${mosip.esignet.auth-txn-id-length:10}")
    private int authTransactionIdLength;

    //Number of times generate-link-code could be invoked per transaction
    @Value("${mosip.esignet.generate-link-code.limit-per-transaction:10}")
    private int linkCodeLimitPerTransaction;


    @Override
    public OAuthDetailResponse getOauthDetails(OAuthDetailRequest oauthDetailReqDto) throws IdPException {
        ClientDetail clientDetailDto = clientManagementService.getClientDetails(oauthDetailReqDto.getClientId());

        log.info("nonce : {} Valid client id found, proceeding to validate redirect URI", oauthDetailReqDto.getNonce());
        IdentityProviderUtil.validateRedirectURI(clientDetailDto.getRedirectUris(), oauthDetailReqDto.getRedirectUri());

        //Resolve the final set of claims based on registered and request parameter.
        Claims resolvedClaims = getRequestedClaims(oauthDetailReqDto, clientDetailDto);
        //Resolve and set ACR claim
        resolvedClaims.getId_token().put(ACR, resolveACRClaim(clientDetailDto.getAcrValues(),
                oauthDetailReqDto.getAcrValues(), oauthDetailReqDto.getClaims()));
        log.info("Final resolved claims : {}", resolvedClaims);

        final String transactionId = IdentityProviderUtil.createTransactionId(oauthDetailReqDto.getNonce());
        OAuthDetailResponse oauthDetailResponse = new OAuthDetailResponse();
        oauthDetailResponse.setTransactionId(transactionId);
        oauthDetailResponse.setAuthFactors(authenticationContextClassRefUtil.getAuthFactors(
               resolvedClaims.getId_token().get(ACR).getValues()
        ));

        Map<String, List> claimsMap = authorizationHelperService.getClaimNames(resolvedClaims);
        oauthDetailResponse.setEssentialClaims(claimsMap.get(ESSENTIAL));
        oauthDetailResponse.setVoluntaryClaims(claimsMap.get(VOLUNTARY));
        oauthDetailResponse.setAuthorizeScopes(authorizationHelperService.getAuthorizeScopes(oauthDetailReqDto.getScope()));
        oauthDetailResponse.setConfigs(uiConfigMap);
        oauthDetailResponse.setClientName(clientDetailDto.getName());
        oauthDetailResponse.setLogoUrl(clientDetailDto.getLogoUri());
        oauthDetailResponse.setRedirectUri(oauthDetailReqDto.getRedirectUri());

        //Cache the transaction
        IdPTransaction idPTransaction = new IdPTransaction();
        idPTransaction.setRedirectUri(oauthDetailReqDto.getRedirectUri());
        idPTransaction.setRelyingPartyId(clientDetailDto.getRpId());
        idPTransaction.setClientId(clientDetailDto.getId());
        idPTransaction.setRequestedClaims(resolvedClaims);
        idPTransaction.setRequestedAuthorizeScopes(oauthDetailResponse.getAuthorizeScopes());
        idPTransaction.setNonce(oauthDetailReqDto.getNonce());
        idPTransaction.setState(oauthDetailReqDto.getState());
        idPTransaction.setClaimsLocales(IdentityProviderUtil.splitAndTrimValue(oauthDetailReqDto.getClaimsLocales(), SPACE));
        idPTransaction.setAuthTransactionId(IdentityProviderUtil.generateRandomAlphaNumeric(authTransactionIdLength));
        idPTransaction.setLinkCodeQueue(new LinkCodeQueue(2));
        idPTransaction.setCurrentLinkCodeLimit(linkCodeLimitPerTransaction);
        idPTransaction.setOauthDetailsHash(getOauthDetailsResponseHash(oauthDetailResponse));
        cacheUtilService.setTransaction(transactionId, idPTransaction);
        auditWrapper.logAudit(Action.TRANSACTION_STARTED, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(transactionId, idPTransaction), null);
        return oauthDetailResponse;
    }

    @Override
    public OtpResponse sendOtp(OtpRequest otpRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(otpRequest.getTransactionId());
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
    public AuthResponse authenticateUser(AuthRequest authRequest)  throws IdPException {
        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(authRequest.getTransactionId());
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
        cacheUtilService.setAuthenticatedTransaction(authRequest.getTransactionId(), transaction);

        auditWrapper.logAudit(Action.AUTHENTICATE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(authRequest.getTransactionId(), transaction), null);

        AuthResponse authRespDto = new AuthResponse();
        authRespDto.setTransactionId(authRequest.getTransactionId());
        return authRespDto;
    }

    @Override
    public AuthCodeResponse getAuthCode(AuthCodeRequest authCodeRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getAuthenticatedTransaction(authCodeRequest.getTransactionId());
        if(transaction == null) {
            throw new InvalidTransactionException();
        }

        authorizationHelperService.validateAcceptedClaims(transaction, authCodeRequest.getAcceptedClaims());
        authorizationHelperService.validateAuthorizeScopes(transaction, authCodeRequest.getPermittedAuthorizeScopes());

        String authCode = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, UUID.randomUUID().toString());
        // cache consent with auth-code-hash as key
        transaction.setCodeHash(authorizationHelperService.getKeyHash(authCode));
        transaction.setAcceptedClaims(authCodeRequest.getAcceptedClaims());
        transaction.setPermittedScopes(authCodeRequest.getPermittedAuthorizeScopes());
        transaction = cacheUtilService.setAuthCodeGeneratedTransaction(authCodeRequest.getTransactionId(), transaction);

        auditWrapper.logAudit(Action.GET_AUTH_CODE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(authCodeRequest.getTransactionId(), transaction), null);

        AuthCodeResponse authCodeResponse = new AuthCodeResponse();
        authCodeResponse.setCode(authCode);
        authCodeResponse.setRedirectUri(transaction.getRedirectUri());
        authCodeResponse.setNonce(transaction.getNonce());
        authCodeResponse.setState(transaction.getState());
        return authCodeResponse;
    }

    private Claims getRequestedClaims(OAuthDetailRequest oauthDetailRequest, ClientDetail clientDetailDto)
            throws IdPException {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        resolvedClaims.setId_token(new HashMap<>());

        String[] requestedScopes = IdentityProviderUtil.splitAndTrimValue(oauthDetailRequest.getScope(), Constants.SPACE);
        Claims requestedClaims = oauthDetailRequest.getClaims();
        boolean isRequestedUserInfoClaimsPresent = requestedClaims != null && requestedClaims.getUserinfo() != null;
        log.info("isRequestedUserInfoClaimsPresent ? {}", isRequestedUserInfoClaimsPresent);

        //Claims request parameter is allowed, only if 'openid' is part of the scope request parameter
        if(isRequestedUserInfoClaimsPresent && !Arrays.stream(requestedScopes).anyMatch(s  -> SCOPE_OPENID.equals(s)))
            throw new IdPException(ErrorConstants.INVALID_SCOPE);

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

    private ClaimDetail resolveACRClaim(List<String> registeredACRs, String requestedAcr, Claims requestedClaims) throws IdPException {
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setEssential(true);

        log.info("Registered ACRS :{}", registeredACRs);
        if(registeredACRs == null || registeredACRs.isEmpty())
            throw new IdPException(ErrorConstants.NO_ACR_REGISTERED);

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
        throw new IdPException(ErrorConstants.FAILED_TO_GENERATE_HEADER_HASH);
    }
}
