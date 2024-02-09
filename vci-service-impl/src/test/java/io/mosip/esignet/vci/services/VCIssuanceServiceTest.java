/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.vci.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import foundation.identity.jsonld.JsonLDObject;
import io.mosip.esignet.api.dto.AuditDTO;
import io.mosip.esignet.api.dto.VCRequestDto;
import io.mosip.esignet.api.dto.VCResult;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.spi.VCIssuancePlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.vci.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.spi.VCIssuanceService;
import io.mosip.esignet.core.util.SecurityHelperService;
import io.mosip.esignet.vci.exception.InvalidNonceException;
import io.mosip.esignet.vci.pop.ProofValidator;
import io.mosip.esignet.vci.pop.ProofValidatorFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

public class VCIssuanceServiceTest {

    private VCIssuanceService vcIssuanceService;

    private VCICacheService vciCacheService;

    private VCResult<String> vcResult_jwt;

    private VCResult<JsonLDObject> vcResult;


    @Before
    public void setup() {
        vcIssuanceService = new VCIssuanceServiceImpl();
        vciCacheService = new VCICacheService();
        ConcurrentMapCacheManager concurrentMapCacheManager = new ConcurrentMapCacheManager("vcissuance");
        ReflectionTestUtils.setField(vciCacheService, "cacheManager", concurrentMapCacheManager);
        ReflectionTestUtils.setField(vcIssuanceService, "vciCacheService", vciCacheService);
        ReflectionTestUtils.setField(vcIssuanceService, "cNonceExpireSeconds", 300);

        ReflectionTestUtils.setField(vcIssuanceService, "objectMapper", new ObjectMapper());
        ProofValidatorFactory proofValidatorFactory = new ProofValidatorFactory();
        ProofValidator jwtProofValidator = new ProofValidator() {
            @Override
            public String getProofType() {
                return "jwt";
            }
            @Override
            public boolean validate(String clientId, String cNonce, CredentialProof credentialProof) {
                return credentialProof !=null && credentialProof.getJwt() != null ? true : false;
            }
            @Override
            public String getKeyMaterial(CredentialProof credentialProof) {
                return "holder-identifier";
            }
        };
        ReflectionTestUtils.setField(proofValidatorFactory, "proofValidators", List.of(jwtProofValidator));
        ReflectionTestUtils.setField(vcIssuanceService, "proofValidatorFactory", proofValidatorFactory);
        ReflectionTestUtils.setField(vcIssuanceService, "securityHelperService", new SecurityHelperService());

        ReflectionTestUtils.setField(vcIssuanceService, "auditWrapper", new AuditPlugin() {
            @Override
            public void logAudit(Action action, ActionStatus status, AuditDTO audit, Throwable t) {}
            @Override
            public void logAudit(String username, Action action, ActionStatus status, AuditDTO audit, Throwable t) {}
        });
        ReflectionTestUtils.setField(vcIssuanceService, "vcIssuancePlugin", new VCIssuancePlugin() {
            @Override
            public VCResult<JsonLDObject> getVerifiableCredentialWithLinkedDataProof(VCRequestDto vcRequestDto, String holderId, Map<String, Object> identityDetails) {
                vcResult = new VCResult<>();
                vcResult.setCredential(new JsonLDObject());
                vcResult.setFormat("ldp_vc");
                return vcResult;
            }

            @Override
            public VCResult<String> getVerifiableCredential(VCRequestDto vcRequestDto, String holderId, Map<String, Object> identityDetails) {
                vcResult_jwt = new VCResult<>();
                vcResult_jwt.setCredential("jwt");
                vcResult_jwt.setFormat("jwt_vc_json-ld");
                return vcResult_jwt;
            }
        });

        Map<String, Object> vciMetadata = new LinkedHashMap<>();
        Map<String, Object> latestIssuerMetadata = new LinkedHashMap<>();
        vciMetadata.put("latest", latestIssuerMetadata);

        Map<String, Object> sampleCredential = new LinkedHashMap<>();
        sampleCredential.put("format", "ldp_vc");
        sampleCredential.put("scope", "sample_vc_ldp");
        sampleCredential.put("proof_types_supported", Arrays.asList("jwt"));
        Map<String, Object> credentialDefinition = new LinkedHashMap<>();
        credentialDefinition.put("type", Arrays.asList("VerifiableCredential","SampleVerifiableCredential"));
        sampleCredential.put("credential_definition", credentialDefinition);
        Map<String, Object> supportedCredentials = new LinkedHashMap<>();
        supportedCredentials.put("SampleVerifiableCredential_ldp", sampleCredential);

        latestIssuerMetadata.put("credential_issuer", "https://localhost:9090");
        latestIssuerMetadata.put("credential_endpoint", "https://localhost:9090/v1/esignet/vci/credential");
        latestIssuerMetadata.put("credentials_supported", supportedCredentials);

        ReflectionTestUtils.setField(vcIssuanceService, "issuerMetadata", vciMetadata);
    }


    @Test
    public void getCredential_withValidDetails_thenPass() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()).minusSeconds(10));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        claims.put("accessTokenHash", "access-token-hash");
        claims.put("client_id", "test-client-id");
        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setType(Arrays.asList("VerifiableCredential", "SampleVerifiableCredential"));
        credentialDefinition.setContext(Arrays.asList(""));
        credentialRequest.setCredential_definition(credentialDefinition);
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialProof.setJwt("header.payload.signature");
        credentialRequest.setProof(credentialProof);

        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        parsedAccessToken.setClaims(claims);
        parsedAccessToken.setAccessTokenHash("access-token-hash");
        ReflectionTestUtils.setField(vcIssuanceService, "parsedAccessToken", parsedAccessToken);

        CredentialResponse credentialResponse = vcIssuanceService.getCredential(credentialRequest);
        Assert.assertNotNull(credentialResponse);
        Assert.assertEquals(vcResult.getFormat(), credentialResponse.getFormat());
        Assert.assertEquals(vcResult.getCredential(), credentialResponse.getCredential());

        credentialRequest.setFormat("jwt_vc_json-ld");
        CredentialResponse credentialResponse_jwt = vcIssuanceService.getCredential(credentialRequest);
        Assert.assertNotNull(credentialResponse_jwt);
        Assert.assertEquals(vcResult_jwt.getFormat(), credentialResponse_jwt.getFormat());
        Assert.assertEquals(vcResult_jwt.getCredential(), credentialResponse_jwt.getCredential());
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getCredential_withInactiveAccessToken_thenFail() {
        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        ReflectionTestUtils.setField(vcIssuanceService, "parsedAccessToken", parsedAccessToken);
        vcIssuanceService.getCredential(new CredentialRequest());
    }

    @Test
    public void getCredential_withNoScopeInAccessToken_thenFail() {
        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        parsedAccessToken.setClaims(new HashMap<>());
        parsedAccessToken.setAccessTokenHash("access-token-hash");
        ReflectionTestUtils.setField(vcIssuanceService, "parsedAccessToken", parsedAccessToken);
        try {
            vcIssuanceService.getCredential(new CredentialRequest());
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_SCOPE, ex.getErrorCode());
        }
    }

    @Test
    public void getCredential_withInvalidScopeInAccessToken_thenFail() {
        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "mosip_identity_vc_ldp");
        parsedAccessToken.setClaims(claims);
        parsedAccessToken.setAccessTokenHash("access-token-hash");
        ReflectionTestUtils.setField(vcIssuanceService, "parsedAccessToken", parsedAccessToken);

        try {
            vcIssuanceService.getCredential(new CredentialRequest());
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_SCOPE, ex.getErrorCode());
        }
    }

    @Test
    public void getCredential_withInvalidProof_thenFail() {
        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()).minusSeconds(10));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        parsedAccessToken.setClaims(claims);
        parsedAccessToken.setAccessTokenHash("access-token-hash");
        ReflectionTestUtils.setField(vcIssuanceService, "parsedAccessToken", parsedAccessToken);

        CredentialRequest credentialRequest = new CredentialRequest();
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialRequest.setProof(credentialProof);
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_PROOF, ex.getErrorCode());
        }
    }

    @Test
    public void getCredential_withInvalidVCFormat_thenFail() {
        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()).minusSeconds(10));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        parsedAccessToken.setClaims(claims);
        parsedAccessToken.setAccessTokenHash("access-token-hash");
        ReflectionTestUtils.setField(vcIssuanceService, "parsedAccessToken", parsedAccessToken);

        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_VC");
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialProof.setJwt("test-jwt");
        credentialRequest.setProof(credentialProof);
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setContext(List.of());
        credentialDefinition.setType(List.of());
        credentialRequest.setCredential_definition(credentialDefinition);
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.UNSUPPORTED_VC_FORMAT, ex.getErrorCode());
        }
    }

    @Test
    public void getCredential_withInvalidCredentialDefinition_thenFail() {
        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()).minusSeconds(10));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        parsedAccessToken.setClaims(claims);
        parsedAccessToken.setAccessTokenHash("access-token-hash");
        ReflectionTestUtils.setField(vcIssuanceService, "parsedAccessToken", parsedAccessToken);

        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialProof.setJwt("test-jwt");
        credentialRequest.setProof(credentialProof);
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setContext(List.of());
        credentialDefinition.setType(List.of());
        credentialRequest.setCredential_definition(credentialDefinition);
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.UNSUPPORTED_VC_TYPE, ex.getErrorCode());
        }

        credentialDefinition.setType(List.of("VerifiableCredential"));
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.UNSUPPORTED_VC_TYPE, ex.getErrorCode());
        }

        credentialDefinition.setType(List.of("SampleVerifiableCredential_ldp"));
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.UNSUPPORTED_VC_TYPE, ex.getErrorCode());
        }

        credentialDefinition.setType(List.of("verifiableCredential", "SampleVerifiableCredential_ldp"));
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.UNSUPPORTED_VC_TYPE, ex.getErrorCode());
        }
    }

    @Test
    public void getCredential_withExpiredCNonce_thenFail() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 0);
        claims.put("accessTokenHash", "access-token-hash");
        claims.put("client_id", "test-client-id");
        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setType(Arrays.asList("VerifiableCredential", "SampleVerifiableCredential_ldp"));
        credentialDefinition.setContext(Arrays.asList(""));
        credentialRequest.setCredential_definition(credentialDefinition);
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialProof.setJwt("header.payload.signature");
        credentialRequest.setProof(credentialProof);

        VCIssuanceTransaction issuanceTransaction = new VCIssuanceTransaction();
        issuanceTransaction.setCNonce("new_c_nonce");
        issuanceTransaction.setCNonceExpireSeconds(400);

        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        parsedAccessToken.setClaims(claims);
        parsedAccessToken.setAccessTokenHash("access-token-hash");
        ReflectionTestUtils.setField(vcIssuanceService, "parsedAccessToken", parsedAccessToken);
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (InvalidNonceException ex) {
            Assert.assertNotNull(ex.getClientNonce());
            Assert.assertTrue(ex.getClientNonceExpireSeconds() > 0);
        }
    }

    @Test
    public void getCredential_withExpiredCNonceInVCITransaction_thenFail() {
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()));
        claims.put("accessTokenHash", "access-token-hash");
        claims.put("client_id", "test-client-id");
        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setType(Arrays.asList("VerifiableCredential", "SampleVerifiableCredential_ldp"));
        credentialDefinition.setContext(Arrays.asList(""));
        credentialRequest.setCredential_definition(credentialDefinition);
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialProof.setJwt("header.payload.signature");
        credentialRequest.setProof(credentialProof);

        ParsedAccessToken parsedAccessToken = new ParsedAccessToken();
        parsedAccessToken.setActive(true);
        parsedAccessToken.setClaims(claims);
        parsedAccessToken.setAccessTokenHash("access-token-hash");
        ReflectionTestUtils.setField(vcIssuanceService, "parsedAccessToken", parsedAccessToken);

        VCIssuanceTransaction expiredTransaction = new VCIssuanceTransaction();
        expiredTransaction.setCNonce("test-nonce");
        expiredTransaction.setCNonceExpireSeconds(2);
        expiredTransaction.setCNonceIssuedEpoch(Instant.now(Clock.systemUTC()).minusSeconds(10).getEpochSecond());
        vciCacheService.setVCITransaction("access-token-hash", expiredTransaction);

        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (InvalidNonceException ex) {
            Assert.assertNotNull(ex.getClientNonce());
            Assert.assertTrue(ex.getClientNonceExpireSeconds() > 0);
        }
    }
}