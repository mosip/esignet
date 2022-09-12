/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.core.dto.KycAuthRequest;
import io.mosip.idp.core.dto.KycAuthResponse;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.InvalidTransactionException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.TokenService;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.idp.entity.ClientDetail;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidClientException;
import io.mosip.idp.repository.ClientDetailRepository;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.idp.core.spi.TokenService.ACR;
import static io.mosip.idp.core.util.Constants.SCOPE_OPENID;

@Slf4j
@Service
public class AuthorizationServiceImpl implements io.mosip.idp.core.spi.AuthorizationService {

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("#{${mosip.idp.openid.scope.claims}}")
    private Map<String, List<String>> claims;

    @Value("${mosip.idp.supported.authorize.scopes}")
    private List<String> authorizeScopes;

    @Value("${mosip.idp.misp.license.key}")
    private String licenseKey;


    @Override
    public OAuthDetailResponse getOauthDetails(OAuthDetailRequest oauthDetailReqDto) throws IdPException {
        Optional<ClientDetail> result = clientDetailRepository.findByIdAndStatus(oauthDetailReqDto.getClientId(),
                Constants.CLIENT_ACTIVE_STATUS);
        if(!result.isPresent())
            throw new InvalidClientException();

        log.info("nonce : {} Valid client id found, proceeding to validate redirect URI", oauthDetailReqDto.getNonce());
        IdentityProviderUtil.validateRedirectURI(result.get().getRedirectUris(), oauthDetailReqDto.getRedirectUri());

        //Resolve the final set of claims based on registered and request parameter.
        Claims resolvedClaims = getRequestedClaims(oauthDetailReqDto, result.get());
        if(resolvedClaims.getId_token().get(ACR) == null)
            throw new IdPException(ErrorConstants.INVALID_ACR);

        final String transactionId = IdentityProviderUtil.createTransactionId(oauthDetailReqDto.getNonce());
        OAuthDetailResponse oauthDetailResponse = new OAuthDetailResponse();
        oauthDetailResponse.setTransactionId(transactionId);
        oauthDetailResponse.setAuthFactors(authenticationContextClassRefUtil.getAuthFactors(
               resolvedClaims.getId_token().get(ACR).getValues()
        ));
        setClaimNamesInResponse(resolvedClaims, oauthDetailResponse);
        setAuthorizeScopes(oauthDetailReqDto.getScope(), oauthDetailResponse);
        setUIConfigMap(oauthDetailResponse);
        oauthDetailResponse.setClientName(result.get().getName());
        oauthDetailResponse.setLogoUrl(result.get().getLogoUri());

        //Cache the transaction
        IdPTransaction idPTransaction = new IdPTransaction();
        idPTransaction.setRedirectUri(oauthDetailReqDto.getRedirectUri());
        idPTransaction.setRelyingPartyId(result.get().getRpId());
        idPTransaction.setClientId(result.get().getId());
        idPTransaction.setRequestedClaims(resolvedClaims);
        idPTransaction.setScopes(oauthDetailReqDto.getScope());
        idPTransaction.setNonce(oauthDetailReqDto.getNonce());
        idPTransaction.setClaimsLocales(oauthDetailReqDto.getClaimsLocales());
        cacheUtilService.setTransaction(transactionId, idPTransaction);
        return oauthDetailResponse;
    }

    @Override
    public OtpResponse sendOtp(OtpRequest otpRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(otpRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        SendOtpResult result = authenticationWrapper.sendOtp(otpRequest.getIndividualId(), otpRequest.getChannel());
        if(!result.isStatus())
            throw new IdPException(result.getMessageCode());

        OtpResponse otpResponse = new OtpResponse();
        otpResponse.setTransactionId(otpRequest.getTransactionId());
        otpResponse.setMessage(result.getMessageCode());
        return otpResponse;
    }

    @Override
    public AuthResponse authenticateUser(KycAuthRequest kycAuthRequest)  throws IdPException {
        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(kycAuthRequest.getTransactionId());
        if(transaction == null)
            throw new InvalidTransactionException();

        ResponseWrapper<KycAuthResponse> result = null;
        try {
            result = authenticationWrapper.doKycAuth(licenseKey, transaction.getRelyingPartyId(),
                    transaction.getClientId(), kycAuthRequest);
        } catch (Throwable t) {
            log.error("KYC auth failed for transaction : {}", kycAuthRequest.getTransactionId(), t);
            throw new IdPException(ErrorConstants.AUTH_FAILED);
        }

        if(result.getErrors() != null && !result.getErrors().isEmpty())
            throw new IdPException(result.getErrors().get(0).getErrorCode());

        //cache tokens on successful response
        transaction.setUserToken(result.getResponse().getUserAuthToken());
        transaction.setKycToken(result.getResponse().getKycToken());
        transaction.setAuthTimeInSeconds(IdentityProviderUtil.getEpochSeconds());
        cacheUtilService.setTransaction(kycAuthRequest.getTransactionId(), transaction);

        AuthResponse authRespDto = new AuthResponse();
        authRespDto.setTransactionId(kycAuthRequest.getTransactionId());
        authRespDto.setMessage(ErrorConstants.AUTH_PASSED);
        return authRespDto;
    }

    @Override
    public IdPTransaction getAuthCode(AuthCodeRequest authCodeRequest) throws IdPException {
        IdPTransaction transaction = cacheUtilService.getPreAuthTransaction(authCodeRequest.getTransactionId());
        if(transaction == null) {
            log.error("getAuthCode -> Transaction verification failure : {}", authCodeRequest.getTransactionId());
            throw new InvalidTransactionException();
        }

        String authCode = IdentityProviderUtil.generateB64EncodedHash("MD5", UUID.randomUUID().toString());
        // cache consent with auth-code as key
        transaction.setCode(authCode);
        transaction.setAcceptedClaims(authCodeRequest.getAcceptedClaims());
        transaction.setPermittedScopes(authCodeRequest.getPermittedAuthorizeScopes());
        return cacheUtilService.setAuthenticatedTransaction(authCode, authCodeRequest.getTransactionId(), transaction);
    }

    private Claims getRequestedClaims(OAuthDetailRequest oauthDetailRequest, ClientDetail clientDetail) throws IdPException {
        Claims resolvedClaims = new Claims();
        resolvedClaims.setUserinfo(new HashMap<>());
        resolvedClaims.setId_token(new HashMap<>());

        String requestedScope = oauthDetailRequest.getScope();
        Claims requestedClaims = oauthDetailRequest.getClaims();
        boolean isRequestedUserInfoClaimsPresent = requestedClaims != null && requestedClaims.getUserinfo() != null;
        log.info("isRequestedUserInfoClaimsPresent ? {}", isRequestedUserInfoClaimsPresent);

        //Claims request parameter is allowed, only if 'openid' is part of the scope request parameter
        if(isRequestedUserInfoClaimsPresent && !Arrays.stream(IdentityProviderUtil.splitAndTrimValue(requestedScope, Constants.SPACE))
                .anyMatch( s  -> SCOPE_OPENID.equals(s)))
            throw new IdPException(ErrorConstants.INVALID_SCOPE);

        log.info("Started to resolve claims based on the request scope {} and claims {}", requestedScope, requestedClaims);
        List<String> registeredUserClaims = Arrays.asList(IdentityProviderUtil.splitAndTrimValue(clientDetail.getClaims(), Constants.COMMA));
        //get claims based on scope
        List<String> claimBasedOnScope = resolveScopeToClaims(requestedScope);
        //claims considered only if part of registered claims
        for(String claimName : registeredUserClaims) {
            if(isRequestedUserInfoClaimsPresent && requestedClaims.getUserinfo().containsKey(claimName))
                resolvedClaims.getUserinfo().put(claimName, requestedClaims.getUserinfo().get(claimName));
            else if(claimBasedOnScope.contains(claimName))
                resolvedClaims.getUserinfo().put(claimName, null);
        }

        //Resolve ans set ACR claim
        resolvedClaims.getId_token().put(ACR, resolveACRClaim(clientDetail.getAcrValues(), oauthDetailRequest.getAcrValues(),
                requestedClaims));

        log.info("Final resolved claims : {}", resolvedClaims);
        return resolvedClaims;
    }

    private ClaimDetail resolveACRClaim(String registeredAcr, String requestedAcr, Claims requestedClaims) throws IdPException {
        ClaimDetail claimDetail = new ClaimDetail();
        claimDetail.setEssential(true);

        List<String> registeredACRs = Arrays.asList(IdentityProviderUtil.splitAndTrimValue(registeredAcr, Constants.COMMA));
        if(registeredACRs == null || registeredACRs.isEmpty())
            throw new IdPException(ErrorConstants.NO_ACR_REGISTERED);

        log.info("Registered ACRS :{}", registeredACRs);
        //First priority is given to claims request parameter
        if(requestedClaims != null && requestedClaims.getId_token() != null && requestedClaims.getId_token().get(ACR) != null) {
            String [] acrs = requestedClaims.getId_token().get(ACR).getValues();
            String[] filteredAcrs = Arrays.stream(acrs).filter(acr -> registeredAcr.contains(acr)).toArray(String[]::new);
            if(filteredAcrs.length > 0) {
                claimDetail.setValues(filteredAcrs);
                return claimDetail;
            }
            log.info("No ACRS found / filtered in claims request parameter : {}", acrs);
        }
        //Next priority is given to claims request parameter
        String[] acrs = IdentityProviderUtil.splitAndTrimValue(requestedAcr, Constants.SPACE);
        String[] filteredAcrs = Arrays.stream(acrs).filter(acr -> registeredAcr.contains(acr)).toArray(String[]::new);
        if(filteredAcrs.length > 0) {
            claimDetail.setValues(filteredAcrs);
            return claimDetail;
        }
        log.info("Considering registered acrs as no valid acrs found in acr_values request param: {}", requestedAcr);
        claimDetail.setValues(registeredACRs.toArray(new String[0]));
        return claimDetail;
    }

    private List<String> resolveScopeToClaims(String scope) {
        List<String> claimNames = new ArrayList<>();
        String[] requestedScopes = IdentityProviderUtil.splitAndTrimValue(scope, Constants.SPACE);
        for(String scopeName : requestedScopes) {
            claimNames.addAll(claims.getOrDefault(scopeName, new ArrayList<>()));
        }
        log.info("Resolved claims: {} based on request scope : {}", claimNames, scope);
        return claimNames;
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
        oauthDetailResponse.setConfigs(null);
    }

    private void setAuthorizeScopes(String requestedScopes, OAuthDetailResponse oauthDetailResponse) {
        String[] scopes = IdentityProviderUtil.splitAndTrimValue(requestedScopes, Constants.SPACE);
        oauthDetailResponse.setAuthorizeScopes(Arrays.stream(scopes)
                .filter( s -> authorizeScopes.contains(s) )
                .collect(Collectors.toList()));
    }
}