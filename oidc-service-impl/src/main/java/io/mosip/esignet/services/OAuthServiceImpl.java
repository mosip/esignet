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
import io.mosip.esignet.api.exception.KycExchangeException;
import io.mosip.esignet.api.exception.KycSigningCertificateException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.Authenticator;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.spi.*;
import io.mosip.esignet.core.util.*;
import io.mosip.kernel.keymanagerservice.dto.AllCertificatesDataResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;

import javax.validation.Valid;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static io.mosip.esignet.core.constants.Constants.*;

@Slf4j
@Service
@Validated
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

    @Value("${mosip.esignet.access-token-expire-seconds:60}")
    private int accessTokenExpireSeconds;


    @Override
    public TokenResponse getTokens(@Valid TokenRequest tokenRequest) throws IdPException {
        String codeHash = authorizationHelperService.getKeyHash(tokenRequest.getCode());
        IdPTransaction transaction = cacheUtilService.getAuthCodeTransaction(codeHash);
        if(transaction == null || transaction.getKycToken() == null)
            throw new InvalidRequestException(ErrorConstants.INVALID_TRANSACTION);

        if(!StringUtils.isEmpty(tokenRequest.getClient_id()) && !transaction.getClientId().equals(tokenRequest.getClient_id()))
            throw new InvalidRequestException(ErrorConstants.INVALID_CLIENT_ID);

        if(!transaction.getRedirectUri().equals(tokenRequest.getRedirect_uri()))
            throw new InvalidRequestException(ErrorConstants.INVALID_REDIRECT_URI);

        ClientDetail clientDetailDto = clientManagementService.getClientDetails(transaction.getClientId());
        IdentityProviderUtil.validateRedirectURI(clientDetailDto.getRedirectUris(), tokenRequest.getRedirect_uri());

        authenticateClient(tokenRequest, clientDetailDto);

        KycExchangeResult kycExchangeResult;
        try {
            KycExchangeDto kycExchangeDto = new KycExchangeDto();
            kycExchangeDto.setTransactionId(transaction.getAuthTransactionId());
            kycExchangeDto.setKycToken(transaction.getKycToken());
            kycExchangeDto.setAcceptedClaims(transaction.getAcceptedClaims());
            kycExchangeDto.setClaimsLocales(transaction.getClaimsLocales());
            kycExchangeDto.setIndividualId(authorizationHelperService.getIndividualId(transaction));
            kycExchangeResult = authenticationWrapper.doKycExchange(transaction.getRelyingPartyId(),
                    transaction.getClientId(), kycExchangeDto);
        } catch (KycExchangeException e) {
            log.error("KYC exchange failed", e);
            auditWrapper.logAudit(Action.DO_KYC_EXCHANGE, ActionStatus.ERROR, AuditHelper.buildAuditDto(codeHash, transaction), e);
            throw new IdPException(e.getErrorCode());
        }

        if(kycExchangeResult == null || kycExchangeResult.getEncryptedKyc() == null)
            throw new IdPException(ErrorConstants.DATA_EXCHANGE_FAILED);

        auditWrapper.logAudit(Action.DO_KYC_EXCHANGE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(codeHash, transaction), null);

        TokenResponse tokenResponse = new TokenResponse();
        tokenResponse.setAccess_token(tokenService.getAccessToken(transaction));
        String accessTokenHash = IdentityProviderUtil.generateOIDCAtHash(tokenResponse.getAccess_token());
        transaction.setAHash(accessTokenHash);
        tokenResponse.setId_token(tokenService.getIDToken(transaction));
        tokenResponse.setExpires_in(accessTokenExpireSeconds);
        tokenResponse.setToken_type(Constants.BEARER);

        // cache kyc with access-token as key
        transaction.setEncryptedKyc(kycExchangeResult.getEncryptedKyc());
        cacheUtilService.setUserInfoTransaction(accessTokenHash, transaction);

        auditWrapper.logAudit(Action.GENERATE_TOKEN, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(codeHash,
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

    private Map<String, Object> getJwk(String keyId, String certificate, LocalDateTime expireAt)
            throws JOSEException {
        JWK jwk = JWK.parseFromPEMEncodedX509Cert(certificate);
        Map<String, Object> map = new HashMap<>();
        map.put(JWK_KEY_ID, keyId);
        if(jwk.getAlgorithm() != null) { map.put(JWK_KEY_ALG, jwk.getAlgorithm().getName()); }
        map.put(JWK_KEY_TYPE, jwk.getKeyType().getValue());
        map.put(JWK_KEY_USE, jwk.getKeyUse().getValue());
        map.put(JWK_KEY_EXPIRE, expireAt.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
        List<String> certs = new ArrayList<>();
        jwk.getX509CertChain().forEach(c -> { certs.add(c.toString()); });
        map.put(JWK_KEY_CERT_CHAIN, certs);
        map.put(JWK_KEY_CERT_SHA256_THUMBPRINT, jwk.getX509CertSHA256Thumbprint().toString());
        map.put(JWK_EXPONENT, jwk.toPublicJWK().getRequiredParams().get(JWK_EXPONENT));
        map.put(JWK_MODULUS, jwk.toPublicJWK().getRequiredParams().get(JWK_MODULUS));
        return map;
    }

    private void authenticateClient(TokenRequest tokenRequest, ClientDetail clientDetail) throws IdPException {
        switch (tokenRequest.getClient_assertion_type()) {
            case JWT_BEARER_TYPE:
                validateJwtClientAssertion(clientDetail.getId(), clientDetail.getPublicKey(), tokenRequest.getClient_assertion());
                break;
            default:
                throw new InvalidRequestException(ErrorConstants.INVALID_ASSERTION_TYPE);
        }
    }


    private void validateJwtClientAssertion(String ClientId, String jwk, String clientAssertion) throws IdPException {
        if(clientAssertion == null || clientAssertion.isBlank())
            throw new InvalidRequestException(ErrorConstants.INVALID_ASSERTION);

        //verify signature
        //on valid signature, verify each claims on JWT payload
        tokenService.verifyClientAssertionToken(ClientId, jwk, clientAssertion);
    }
}
