/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.vci.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import foundation.identity.jsonld.JsonLDObject;
import io.mosip.esignet.api.dto.VCRequestDto;
import io.mosip.esignet.api.dto.VCResult;
import io.mosip.esignet.api.exception.VCIExchangeException;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.VCIssuancePlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.vci.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.InvalidRequestException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.spi.VCIssuanceService;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.SecurityHelperService;
import io.mosip.esignet.vci.exception.InvalidNonceException;
import io.mosip.esignet.vci.pop.ProofValidator;
import io.mosip.esignet.vci.pop.ProofValidatorFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.*;

import static io.mosip.esignet.core.spi.TokenService.*;

@Slf4j
@Service
public class VCIssuanceServiceImpl implements VCIssuanceService {

    private static final String TYPE_VERIFIABLE_CREDENTIAL = "VerifiableCredential";

    @Value("#{${mosip.esignet.vci.key-values}}")
    private LinkedHashMap<String, LinkedHashMap<String, Object>> issuerMetadata;

    @Value("${mosip.esignet.cnonce-expire-seconds:300}")
    private int cNonceExpireSeconds;

    @Autowired
    private ParsedAccessToken parsedAccessToken;

    @Autowired
    private VCIssuancePlugin vcIssuancePlugin;

    @Autowired
    private ProofValidatorFactory proofValidatorFactory;

    @Autowired
    private VCICacheService vciCacheService;

    @Autowired
    private SecurityHelperService securityHelperService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AuditPlugin auditWrapper;

    private LinkedHashMap<String, Object> supportedCredentials;


    @Override
    public CredentialResponse getCredential(CredentialRequest credentialRequest) {
        if(!parsedAccessToken.isActive())
            throw new NotAuthenticatedException();

        String scopeClaim = (String) parsedAccessToken.getClaims().getOrDefault("scope", "");
        CredentialMetadata credentialMetadata = null;
        for(String scope : scopeClaim.split(Constants.SPACE)) {
            Optional<CredentialMetadata> result = getScopeCredentialMapping(scope);
            if(result.isPresent()) {
                credentialMetadata = result.get(); //considering only first credential scope
                break;
            }
        }

        if(credentialMetadata == null) {
            log.error("No credential mapping found for the provided scope {}", scopeClaim);
            throw new EsignetException(ErrorConstants.INVALID_SCOPE);
        }

        ProofValidator proofValidator = proofValidatorFactory.getProofValidator(credentialRequest.getProof().getProof_type());
        if(!proofValidator.validate((String)parsedAccessToken.getClaims().get(CLIENT_ID), getValidClientNonce(),
                credentialRequest.getProof())) {
            throw new EsignetException(ErrorConstants.INVALID_PROOF);
        }

        //Get VC from configured plugin implementation
        VCResult<?> vcResult = getVerifiableCredential(credentialRequest, credentialMetadata,
                proofValidator.getKeyMaterial(credentialRequest.getProof()));

        auditWrapper.logAudit(Action.VC_ISSUANCE, ActionStatus.SUCCESS,
                AuditHelper.buildAuditDto(parsedAccessToken.getAccessTokenHash(), "accessTokenHash", null), null);
        return getCredentialResponse(credentialRequest.getFormat(), vcResult);
    }

    @Override
    public Map<String, Object> getCredentialIssuerMetadata(String version) {
       if(issuerMetadata.containsKey(version))
           return issuerMetadata.get(version);
       return issuerMetadata.get("latest");
    }

    private VCResult<?> getVerifiableCredential(CredentialRequest credentialRequest, CredentialMetadata credentialMetadata,
                                                String holderId) {
        parsedAccessToken.getClaims().put("accessTokenHash", parsedAccessToken.getAccessTokenHash());
        VCRequestDto vcRequestDto = new VCRequestDto();
        vcRequestDto.setFormat(credentialRequest.getFormat());
        vcRequestDto.setContext(credentialRequest.getCredential_definition().getContext());
        vcRequestDto.setType(credentialRequest.getCredential_definition().getType());
        vcRequestDto.setCredentialSubject(credentialRequest.getCredential_definition().getCredentialSubject());

        VCResult<?> vcResult = null;
        try {
            switch (credentialRequest.getFormat()) {
                case "ldp_vc" :
                    validateLdpVcFormatRequest(credentialRequest, credentialMetadata);
                    vcResult = vcIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(vcRequestDto, holderId,
                            parsedAccessToken.getClaims());
                    break;

                // jwt_vc_json & jwt_vc_json-ld cases are merged
                case "jwt_vc_json-ld" :
                case "jwt_vc_json" :
                    vcResult = vcIssuancePlugin.getVerifiableCredential(vcRequestDto, holderId,
                            parsedAccessToken.getClaims());
                    break;
                default:
                    throw new EsignetException(ErrorConstants.UNSUPPORTED_VC_FORMAT);
            }
        } catch (VCIExchangeException e) {
            throw new EsignetException(e.getErrorCode());
        }

        if(vcResult != null && vcResult.getCredential() != null)
            return vcResult;

        log.error("Failed to generate VC : {}", vcResult);
        auditWrapper.logAudit(Action.VC_ISSUANCE, ActionStatus.ERROR,
                AuditHelper.buildAuditDto(parsedAccessToken.getAccessTokenHash(), "accessTokenHash", null), null);
        throw new EsignetException(ErrorConstants.VC_ISSUANCE_FAILED);
    }

    private CredentialResponse<?> getCredentialResponse(String format, VCResult<?> vcResult) {
        switch (format) {
            case "ldp_vc":
                CredentialResponse<JsonLDObject> ldpVcResponse = new CredentialResponse<>();
                ldpVcResponse.setCredential((JsonLDObject)vcResult.getCredential());
                ldpVcResponse.setFormat(vcResult.getFormat());
                return ldpVcResponse;

            case "jwt_vc_json-ld":
            case "jwt_vc_json":
                CredentialResponse<String> jsonResponse = new CredentialResponse<>();
                jsonResponse.setCredential((String)vcResult.getCredential());
                jsonResponse.setFormat(vcResult.getFormat());
                return jsonResponse;
        }
        throw new EsignetException(ErrorConstants.UNSUPPORTED_VC_FORMAT);
    }

    private Optional<CredentialMetadata>  getScopeCredentialMapping(String scope) {
        LinkedHashMap<String, Object> vciMetadata = issuerMetadata.get("latest");
        if(supportedCredentials == null) {
            supportedCredentials = (LinkedHashMap<String, Object>) vciMetadata.get("credentials_supported");
        }

        Optional<Map.Entry<String, Object>> result = supportedCredentials.entrySet().stream()
                .filter(cm -> ((LinkedHashMap<String, Object>)cm.getValue()).get("scope").equals(scope)).findFirst();

        if(result.isPresent()) {
            LinkedHashMap<String, Object> metadata = (LinkedHashMap<String, Object>)result.get().getValue();
            CredentialMetadata credentialMetadata = new CredentialMetadata();
            credentialMetadata.setFormat((String) metadata.get("format"));
            credentialMetadata.setProof_types_supported((List<String>) metadata.get("proof_types_supported"));
            credentialMetadata.setScope((String) metadata.get("scope"));
            credentialMetadata.setId(result.get().getKey());

            LinkedHashMap<String, Object> credentialDefinition = (LinkedHashMap<String, Object>) metadata.get("credential_definition");
            credentialMetadata.setTypes((List<String>) credentialDefinition.get("type"));
            return Optional.of(credentialMetadata);
        }
        return Optional.empty();
    }

    private void validateLdpVcFormatRequest(CredentialRequest credentialRequest,
                                               CredentialMetadata credentialMetadata) {
        if(!credentialRequest.getCredential_definition().getType().containsAll(credentialMetadata.getTypes()))
             throw new InvalidRequestException(ErrorConstants.UNSUPPORTED_VC_TYPE);

        //TODO need to validate Credential_definition as JsonLD document, if invalid throw exception
    }

    private String getValidClientNonce() {
        VCIssuanceTransaction transaction = vciCacheService.getVCITransaction(parsedAccessToken.getAccessTokenHash());
        //If the transaction is null, it means that VCI service never created cNonce, its authorization server issued cNonce
        String cNonce = (transaction == null) ?
                (String) parsedAccessToken.getClaims().get(C_NONCE) :
                transaction.getCNonce();
        Object nonceExpireSeconds = parsedAccessToken.getClaims().getOrDefault(C_NONCE_EXPIRES_IN, 0);
        int cNonceExpire = (transaction == null) ?
                nonceExpireSeconds instanceof Long ? (int)(long)nonceExpireSeconds : (int)nonceExpireSeconds :
                transaction.getCNonceExpireSeconds();
        long issuedEpoch = (transaction == null) ?
                ((Instant) parsedAccessToken.getClaims().getOrDefault(JwtClaimNames.IAT, Instant.MIN)).getEpochSecond():
                transaction.getCNonceIssuedEpoch();

        if( cNonce == null ||
                cNonceExpire <= 0 ||
                (issuedEpoch+cNonceExpire) < LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC) ) {
            log.error("Client Nonce not found / expired in the access token, generate new cNonce");
            transaction = createVCITransaction();
            throw new InvalidNonceException(transaction.getCNonce(), transaction.getCNonceExpireSeconds());
        }
        return cNonce;
    }

    private VCIssuanceTransaction createVCITransaction() {
        VCIssuanceTransaction transaction = new VCIssuanceTransaction();
        transaction.setCNonce(securityHelperService.generateSecureRandomString(20));
        transaction.setCNonceIssuedEpoch(LocalDateTime.now(ZoneOffset.UTC).toEpochSecond(ZoneOffset.UTC));
        transaction.setCNonceExpireSeconds(cNonceExpireSeconds);
        return vciCacheService.setVCITransaction(parsedAccessToken.getAccessTokenHash(), transaction);
    }
}