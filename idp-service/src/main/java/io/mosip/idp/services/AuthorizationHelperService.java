package io.mosip.idp.services;

import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.*;
import java.util.stream.Collectors;

import static io.mosip.idp.core.spi.TokenService.ACR;
import static io.mosip.idp.core.util.Constants.ESSENTIAL;
import static io.mosip.idp.core.util.Constants.VOLUNTARY;
import static io.mosip.idp.core.util.ErrorConstants.*;
import static io.mosip.idp.core.util.ErrorConstants.INVALID_PERMITTED_SCOPE;

@Slf4j
@Component
public class AuthorizationHelperService {

    @Autowired
    private AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Value("#{${mosip.idp.openid.scope.claims}}")
    private Map<String, List<String>> claims;

    @Value("#{${mosip.idp.supported.authorize.scopes}}")
    private List<String> authorizeScopes;

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
}
