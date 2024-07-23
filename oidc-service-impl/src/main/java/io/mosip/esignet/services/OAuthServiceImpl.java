/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.jwk.JWK;
import io.mosip.esignet.api.dto.KycExchangeDto;
import io.mosip.esignet.api.dto.KycExchangeResult;
import io.mosip.esignet.api.dto.KycSigningCertificateData;
import io.mosip.esignet.api.dto.VerifiedKycExchangeDto;
import io.mosip.esignet.api.dto.claim.ClaimDetail;
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.exception.KycSigningCertificateException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.spi.*;
import io.mosip.esignet.core.util.*;
import io.mosip.kernel.keymanagerservice.dto.AllCertificatesDataResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import lombok.extern.slf4j.Slf4j;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static io.mosip.esignet.api.util.ErrorConstants.DATA_EXCHANGE_FAILED;
import static io.mosip.esignet.core.constants.Constants.*;

@Slf4j
@Service
public class OAuthServiceImpl implements OAuthService {


    @Autowired
    private ClientManagementService clientManagementService;

    @Autowired
    private AuthorizationHelperService authorizationHelperService;

    @Autowired
    private Authenticator authenticationWrapper;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private KeymanagerService keymanagerService;

    @Autowired
    private AuditPlugin auditWrapper;

    @Autowired
    private SecurityHelperService securityHelperService;

    @Value("${mosip.esignet.access-token-expire-seconds:60}")
    private int accessTokenExpireSeconds;

    @Value("${mosip.esignet.cnonce-expire-seconds:300}")
    private int cNonceExpireSeconds;

    @Value("#{${mosip.esignet.oauth.key-values}}")
    private Map<String, Object> oauthServerDiscoveryMap;

    @Value("${mosip.esignet.discovery.issuer-id}")
    private String discoveryIssuerId;


    @Override
    public TokenResponse getTokens(TokenRequest tokenRequest,boolean isV2) throws EsignetException {
        String codeHash = authorizationHelperService.getKeyHash(tokenRequest.getCode());
        OIDCTransaction transaction = cacheUtilService.getAuthCodeTransaction(codeHash);

        validateRequestParametersWithTransaction(tokenRequest, transaction);

        ClientDetail clientDetailDto = clientManagementService.getClientDetails(transaction.getClientId());
        IdentityProviderUtil.validateRedirectURI(clientDetailDto.getRedirectUris(), tokenRequest.getRedirect_uri());

        authenticateClient(tokenRequest, clientDetailDto,isV2);

        boolean isTransactionVCScoped = isTransactionVCScoped(transaction);
        if(!isTransactionVCScoped) { //if transaction is not VC scoped, only then do KYC exchange
            KycExchangeResult kycExchangeResult = doKycExchange(transaction);
            transaction.setEncryptedKyc(kycExchangeResult.getEncryptedKyc());
            auditWrapper.logAudit(Action.DO_KYC_EXCHANGE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(transaction.getTransactionId(), transaction), null);
        }

        TokenResponse tokenResponse = getTokenResponse(transaction, isTransactionVCScoped);
        // cache kyc with access-token as key
        cacheUtilService.setUserInfoTransaction(transaction.getAHash(), transaction);
        auditWrapper.logAudit(Action.GENERATE_TOKEN, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(transaction.getTransactionId(),
                transaction), null);
        return tokenResponse;
    }

    @Override
    public Map<String, Object> getJwks() {
        AllCertificatesDataResponseDto allCertificatesDataResponseDto = keymanagerService.getAllCertificates(
                Constants.OIDC_SERVICE_APP_ID, Optional.empty());
        List<Map<String, Object>> jwkList = new ArrayList<>();
        Arrays.stream(allCertificatesDataResponseDto.getAllCertificates()).forEach( dto -> {
            try {
                jwkList.add(getJwk(dto.getKeyId(), dto.getCertificateData(), dto.getExpiryAt()));
            } catch (JOSEException e) {
                log.error("Failed to parse the certificate data", e);
            }
        });

        try {
            List<KycSigningCertificateData> allAuthCerts = authenticationWrapper.getAllKycSigningCertificates();
            if(allAuthCerts != null) {
                allAuthCerts.stream().forEach( authCert -> {
                    try {
                        jwkList.add(getJwk(authCert.getKeyId(), authCert.getCertificateData(), authCert.getExpiryAt()));
                    } catch (JOSEException e) {
                        log.error("Failed to parse the auth certificate data", e);
                    }
                });
            }
        } catch (KycSigningCertificateException e) {
            log.error("Failed to fetch authenticator certificate data", e);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("keys", jwkList);
        return response;
    }

    @Override
    public Map<String, Object> getOAuthServerDiscoveryInfo(){
        return oauthServerDiscoveryMap;
    }

    private Map<String, Object> getJwk(String keyId, String certificate, LocalDateTime expireAt)
            throws JOSEException {
        JWK jwk = JWK.parseFromPEMEncodedX509Cert(certificate);
        Map<String, Object> map = new HashMap<>();
        map.put(JWK_KEY_ID, keyId);
        if(jwk.getAlgorithm() != null) { map.put(JWK_KEY_ALG, jwk.getAlgorithm().getName()); }
        map.put(JWK_KEY_TYPE, jwk.getKeyType().getValue());
        if(jwk.getKeyUse() != null) { map.put(JWK_KEY_USE, jwk.getKeyUse().getValue()); }
        map.put(JWK_KEY_EXPIRE, expireAt.toEpochSecond(ZoneOffset.UTC));
        List<String> certs = new ArrayList<>();
        jwk.getX509CertChain().forEach(c -> { certs.add(c.toString()); });
        map.put(JWK_KEY_CERT_CHAIN, certs);
        map.put(JWK_KEY_CERT_SHA256_THUMBPRINT, jwk.getX509CertSHA256Thumbprint().toString());
        map.put(JWK_EXPONENT, jwk.toPublicJWK().getRequiredParams().get(JWK_EXPONENT));
        map.put(JWK_MODULUS, jwk.toPublicJWK().getRequiredParams().get(JWK_MODULUS));
        return map;
    }

    private void validateRequestParametersWithTransaction(TokenRequest tokenRequest, OIDCTransaction transaction) {
        if(transaction == null || transaction.getKycToken() == null)
            throw new InvalidRequestException(ErrorConstants.INVALID_TRANSACTION);

        if(StringUtils.hasText(tokenRequest.getClient_id()) && !transaction.getClientId().equals(tokenRequest.getClient_id()))
            throw new InvalidRequestException(ErrorConstants.INVALID_CLIENT_ID);

        if(!transaction.getRedirectUri().equals(tokenRequest.getRedirect_uri()))
            throw new InvalidRequestException(ErrorConstants.INVALID_REDIRECT_URI);

        validatePKCE(transaction.getProofKeyCodeExchange(), tokenRequest.getCode_verifier());
    }

    private void validatePKCE(ProofKeyCodeExchange proofKeyCodeExchange, String codeVerifier) {
        if(proofKeyCodeExchange == null) {
            log.info("Proof Key Code Exchange is not applicable, Do nothing");
            return;
        }

        if(StringUtils.isEmpty(codeVerifier)) {
            log.error("Null or empty code_verifier found in the request");
            throw new EsignetException(ErrorConstants.INVALID_PKCE_CODE_VERFIER);
        }

        String computedChallenge;
        switch (proofKeyCodeExchange.getCodeChallengeMethod()) {
            case S256 :
                byte[] verifierBytes = codeVerifier.getBytes(StandardCharsets.US_ASCII);
                computedChallenge = IdentityProviderUtil.generateB64EncodedHash(IdentityProviderUtil.ALGO_SHA_256, verifierBytes);
                break;
            default:
                throw new EsignetException(ErrorConstants.UNSUPPORTED_PKCE_CHALLENGE_METHOD);
        }

        if(StringUtils.isEmpty(computedChallenge) || !computedChallenge.equals(proofKeyCodeExchange.getCodeChallenge()))
            throw new EsignetException(ErrorConstants.PKCE_FAILED);
    }

    private void authenticateClient(TokenRequest tokenRequest, ClientDetail clientDetail,boolean isV2) throws EsignetException {
        switch (tokenRequest.getClient_assertion_type()) {
            case JWT_BEARER_TYPE:
                validateJwtClientAssertion(clientDetail.getId(), clientDetail.getPublicKey(), tokenRequest.getClient_assertion(),
                        isV2? (String) oauthServerDiscoveryMap.get("token_endpoint") :discoveryIssuerId+"/oauth/token");
                break;
            default:
                throw new InvalidRequestException(ErrorConstants.INVALID_ASSERTION_TYPE);
        }
    }

    private void validateJwtClientAssertion(String clientId, String jwk, String clientAssertion,String audience) throws EsignetException {
        if(clientAssertion == null || clientAssertion.isBlank())
            throw new InvalidRequestException(ErrorConstants.INVALID_ASSERTION);

        //verify signature
        //on valid signature, verify each claims on JWT payload
        tokenService.verifyClientAssertionToken(clientId, jwk, clientAssertion,audience);
    }

    private TokenResponse getTokenResponse(OIDCTransaction transaction, boolean isTransactionVCScoped) {
        TokenResponse tokenResponse = new TokenResponse();
        String cNonce = isTransactionVCScoped ? securityHelperService.generateSecureRandomString(20) : null;
        tokenResponse.setAccess_token(tokenService.getAccessToken(transaction, cNonce));
        tokenResponse.setExpires_in(accessTokenExpireSeconds);
        tokenResponse.setToken_type(Constants.BEARER);
        String accessTokenHash = IdentityProviderUtil.generateOIDCAtHash(tokenResponse.getAccess_token());
        transaction.setAHash(accessTokenHash);
        if(isTransactionVCScoped) {
            tokenResponse.setC_nonce(cNonce);
            tokenResponse.setC_nonce_expires_in(cNonceExpireSeconds);
        }
        else {
            tokenResponse.setId_token(tokenService.getIDToken(transaction));
        }
        return tokenResponse;
    }

    private KycExchangeResult doKycExchange(OIDCTransaction transaction) {
        KycExchangeResult kycExchangeResult;
        try {
            VerifiedKycExchangeDto kycExchangeDto = new VerifiedKycExchangeDto();
            kycExchangeDto.setTransactionId(transaction.getAuthTransactionId());
            kycExchangeDto.setKycToken(transaction.getKycToken());
            kycExchangeDto.setAcceptedClaims(transaction.getAcceptedClaims());
            kycExchangeDto.setClaimsLocales(transaction.getClaimsLocales());
            kycExchangeDto.setIndividualId(authorizationHelperService.getIndividualId(transaction));
            kycExchangeDto.setAcceptedVerifiedClaims(new HashMap<>());

            if(!CollectionUtils.isEmpty(transaction.getAcceptedClaims()) && transaction.getRequestedClaims().getUserinfo() != null) {
                for(String claim : transaction.getAcceptedClaims()) {
                    ClaimDetail claimDetail = transaction.getRequestedClaims().getUserinfo().get(claim);
                    if(claimDetail != null && claimDetail.getVerification()!=null) {
                        kycExchangeDto.getAcceptedVerifiedClaims().put(claim, claimDetail.getVerification());
                    }
                }
            }

            if(transaction.isInternalAuthSuccess()) {
                log.info("Internal kyc exchange is invoked as the transaction is marked as internal auth success");
                kycExchangeResult = doInternalKycExchange(kycExchangeDto);
            } else {
                kycExchangeResult = kycExchangeDto.getAcceptedVerifiedClaims().isEmpty() ?
                        authenticationWrapper.doKycExchange(transaction.getRelyingPartyId(),
                        transaction.getClientId(), kycExchangeDto) :
                        authenticationWrapper.doVerifiedKycExchange(transaction.getRelyingPartyId(),
                        transaction.getClientId(), kycExchangeDto);
            }

        } catch (KycExchangeException e) {
            log.error("KYC exchange failed", e);
            auditWrapper.logAudit(Action.DO_KYC_EXCHANGE, ActionStatus.ERROR, AuditHelper.buildAuditDto(transaction.getTransactionId(), transaction), e);
            throw new EsignetException(e.getErrorCode());
        }

        if(kycExchangeResult != null && kycExchangeResult.getEncryptedKyc() != null)
            return kycExchangeResult;

        throw new EsignetException(DATA_EXCHANGE_FAILED);
    }

    private KycExchangeResult doInternalKycExchange(KycExchangeDto kycExchangeDto) {
        KycExchangeResult kycExchangeResult = new KycExchangeResult();
        JSONObject payload = new JSONObject();
        payload.put(TokenService.SUB, kycExchangeDto.getIndividualId());
        kycExchangeResult.setEncryptedKyc(tokenService.getSignedJWT(Constants.OIDC_SERVICE_APP_ID, payload));
        return kycExchangeResult;
    }

    private boolean isTransactionVCScoped(OIDCTransaction transaction) {
        return (transaction.getRequestedCredentialScopes() != null &&
                transaction.getPermittedScopes() != null &&
                transaction.getPermittedScopes().stream()
                        .anyMatch(scope -> transaction.getRequestedCredentialScopes().contains(scope)));
    }
}
