/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.InvalidTransactionException;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.idp.core.spi.TokenService.ACR;
import static io.mosip.idp.core.util.Constants.SCOPE_OPENID;
import static io.mosip.idp.core.util.Constants.SPACE;
import static io.mosip.idp.core.util.ErrorConstants.*;
import static io.mosip.idp.core.util.IdentityProviderUtil.ALGO_MD5;
import static io.mosip.idp.core.util.IdentityProviderUtil.ALGO_SHA3_256;

@Slf4j
@Service
public class AuthorizationServiceImpl implements io.mosip.idp.core.spi.AuthorizationService {

    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Value("#{${mosip.idp.openid.scope.claims}}")
    private Map<String, List<String>> claims;

    @Value("${mosip.idp.supported.authorize.scopes}")
    private List<String> authorizeScopes;

    @Value("#{${mosip.idp.ui.config.key-values}}")
    private Map<String, Object> uiConfigMap;


    @Override
    public OAuthDetailResponse getOauthDetails(OAuthDetailRequest oauthDetailReqDto) throws IdPException {
        ClientDetail clientDetailDto = clientManagementService.getClientDetails(oauthDetailReqDto.getClientId());

        log.info("nonce : {} Valid client id found, proceeding to validate redirect URI", oauthDetailReqDto.getNonce());
        IdentityProviderUtil.validateRedirectURI(clientDetailDto.getRedirectUris(), oauthDetailReqDto.getRedirectUri());

        //Resolve the final set of claims based on registered and request parameter.
        Claims resolvedClaims = getRequestedClaims(oauthDetailReqDto, clientDetailDto);
        //Resolve and set ACR claim
        resolvedClaims.getId_token().put(ACR, resolveACRClaim(clientDetailDto.getAcrValues(), oauthDetailReqDto.getAcrValues(),
                oauthDetailReqDto.getClaims()));
        log.info("Final resolved claims : {}", resolvedClaims);

        final String transactionId = IdentityProviderUtil.createTransactionId(oauthDetailReqDto.getNonce());
        OAuthDetailResponse oauthDetailResponse = new OAuthDetailResponse();
        oauthDetailResponse.setTransactionId(transactionId);
        oauthDetailResponse.setAuthFactors(authenticationContextClassRefUtil.getAuthFactors(
               resolvedClaims.getId_token().get(ACR).getValues()
        ));
        setClaimNamesInResponse(resolvedClaims, oauthDetailResponse);
        setAuthorizeScopes(oauthDetailReqDto.getScope(), oauthDetailResponse);
        setUIConfigMap(oauthDetailResponse);
        oauthDetailResponse.setClientName(clientDetailDto.getName());
        oauthDetailResponse.setLogoUrl(clientDetailDto.getLogoUri());

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
        idPTransaction.setAuthTransactionId(IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, transactionId));
        cacheUtilService.setTransaction(transactionId, idPTransaction);
        return oauthDetailResponse;
    }

    @Override
    public OtpResponse sendOtp(OtpRequest otpRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(otpRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

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

        if(!transaction.getAuthTransactionId().equals(sendOtpResult.getTransactionId())) {
            log.error("Auth transactionId in request {} is not matching with send-otp response : {}", transaction.getAuthTransactionId(),
                    sendOtpResult.getTransactionId());
            throw new IdPException(SEND_OTP_FAILED);
        }

        OtpResponse otpResponse = new OtpResponse();
        otpResponse.setTransactionId(otpRequest.getTransactionId());
        otpResponse.setMaskedEmail(sendOtpResult.getMaskedEmail());
        otpResponse.setMaskedMobile(sendOtpResult.getMaskedMobile());
        return otpResponse;
    }

    @Override
    public AuthResponse authenticateUser(KycAuthRequest kycAuthRequest)  throws IdPException {
        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(kycAuthRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        KycAuthResult kycAuthResult;
        try {
            kycAuthResult = authenticationWrapper.doKycAuth(transaction.getRelyingPartyId(), transaction.getClientId(),
                    new KycAuthRequest(transaction.getAuthTransactionId(), kycAuthRequest.getIndividualId(),
                            kycAuthRequest.getChallengeList()));
        } catch (KycAuthException e) {
            log.error("KYC auth failed for transaction : {}", kycAuthRequest.getTransactionId(), e);
            throw new IdPException(e.getErrorCode());
        }

        if(kycAuthResult == null || (StringUtils.isEmpty(kycAuthResult.getKycToken()) ||
                StringUtils.isEmpty(kycAuthResult.getPartnerSpecificUserToken()))) {
            log.error("** authenticationWrapper : {} returned empty tokens received **", authenticationWrapper);
            throw new IdPException(AUTH_FAILED);
        }

        //cache tokens on successful response
        transaction.setPartnerSpecificUserToken(kycAuthResult.getPartnerSpecificUserToken());
        transaction.setKycToken(kycAuthResult.getKycToken());
        transaction.setAuthTimeInSeconds(IdentityProviderUtil.getEpochSeconds());
        cacheUtilService.setTransaction(kycAuthRequest.getTransactionId(), transaction);

        AuthResponse authRespDto = new AuthResponse();
        authRespDto.setTransactionId(kycAuthRequest.getTransactionId());
        return authRespDto;
    }

    @Override
    public IdPTransaction getAuthCode(AuthCodeRequest authCodeRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(authCodeRequest.getTransactionId());
        if(transaction == null) {
            throw new InvalidTransactionException();
        }
        validateAcceptedClaims(transaction, authCodeRequest.getAcceptedClaims());
        validateAuthorizeScopes(transaction, authCodeRequest.getPermittedAuthorizeScopes());

        String authCode = IdentityProviderUtil.generateB64EncodedHash(ALGO_MD5, UUID.randomUUID().toString());
        // cache consent with auth-code as key
        transaction.setCode(authCode);
        transaction.setAcceptedClaims(authCodeRequest.getAcceptedClaims());
        transaction.setPermittedScopes(authCodeRequest.getPermittedAuthorizeScopes());
        return cacheUtilService.setAuthenticatedTransaction(authCode, authCodeRequest.getTransactionId(), transaction);
    }

    private void validateAcceptedClaims(IdPTransaction transaction, List<String> acceptedClaims) throws IdPException {
        if(CollectionUtils.isEmpty(acceptedClaims))
            return;

        if(CollectionUtils.isEmpty(transaction.getRequestedClaims().getUserinfo()))
            throw new IdPException(INVALID_CLAIM);

        if(acceptedClaims.stream()
                .allMatch( claim -> transaction.getRequestedClaims().getUserinfo().containsKey(claim) ))
            return;

        throw new IdPException(INVALID_CLAIM);
    }

    private void validateAuthorizeScopes(IdPTransaction transaction, List<String> authorizeScopes) throws IdPException {
        if(CollectionUtils.isEmpty(authorizeScopes))
            return;

        if(CollectionUtils.isEmpty(transaction.getRequestedAuthorizeScopes()))
            throw new IdPException(INVALID_SCOPE);

        if(!transaction.getRequestedAuthorizeScopes().containsAll(authorizeScopes))
            throw new IdPException(INVALID_SCOPE);
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
        if(isRequestedUserInfoClaimsPresent && !Arrays.stream(requestedScopes).anyMatch( s  -> SCOPE_OPENID.equals(s)))
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

    private void setClaimNamesInResponse(Claims resolvedClaims, OAuthDetailResponse oauthDetailResponse) {
        oauthDetailResponse.setEssentialClaims(new ArrayList<>());
        oauthDetailResponse.setVoluntaryClaims(new ArrayList<>());
        for(Map.Entry<String, ClaimDetail> claim : resolvedClaims.getUserinfo().entrySet()) {
            if(claim.getValue() != null && claim.getValue().isEssential())
                oauthDetailResponse.getEssentialClaims().add(claim.getKey());
            else
                oauthDetailResponse.getVoluntaryClaims().add(claim.getKey());
        }
    }

    private void setUIConfigMap(OAuthDetailResponse oauthDetailResponse) {
        oauthDetailResponse.setConfigs(uiConfigMap);
    }

    private void setAuthorizeScopes(String requestedScopes, OAuthDetailResponse oauthDetailResponse) {
        String[] scopes = IdentityProviderUtil.splitAndTrimValue(requestedScopes, Constants.SPACE);
        oauthDetailResponse.setAuthorizeScopes(Arrays.stream(scopes)
                .filter( s -> authorizeScopes.contains(s) )
                .collect(Collectors.toList()));
    }
}