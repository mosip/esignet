/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.vci.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import foundation.identity.jsonld.JsonLDObject;
import io.mosip.esignet.api.dto.VCResult;
import io.mosip.esignet.api.spi.VCIssuancePlugin;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.vci.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.util.SecurityHelperService;
import io.mosip.esignet.vci.exception.InvalidNonceException;
import io.mosip.esignet.vci.pop.JwtProofValidator;
import io.mosip.esignet.vci.pop.ProofValidatorFactory;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.util.*;

@RunWith(MockitoJUnitRunner.class)
public class VCIssuanceServiceTest {

    @InjectMocks
    private VCIssuanceServiceImpl vcIssuanceService;

    @Mock
    private VCIssuancePlugin vcIssuancePlugin;

    @Mock
    private ProofValidatorFactory proofValidatorFactory;

    @Mock
    private VCICacheService vciCacheService;

    @Mock
    private ParsedAccessToken parsedAccessToken;

    @Mock
    private JwtProofValidator jwtProofValidator;

    @Mock
    private SecurityHelperService securityHelperService;


    @Before
    public void setup() {
        Map<String, Object> issuerMetadata = new HashMap<>();
        issuerMetadata.put("credential_issuer", "https://localhost:9090");
        issuerMetadata.put("credential_endpoint", "https://localhost:9090/v1/esignet/vci/credential");
        Map<String, Object> supportedCredential = new LinkedHashMap<>();
        supportedCredential.put("format", "ldp_vc");
        supportedCredential.put("id", "SampleVerifiableCredential_ldp");
        supportedCredential.put("scope", "sample_vc_ldp");
        supportedCredential.put("proof_types_supported", Arrays.asList("jwt"));
        supportedCredential.put("credential_definition", null);
        issuerMetadata.put("credentials_supported", Arrays.asList(supportedCredential));
        ReflectionTestUtils.setField(vcIssuanceService, "issuerMetadata", issuerMetadata);
        ReflectionTestUtils.setField(vcIssuanceService, "objectMapper", new ObjectMapper());
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
        credentialDefinition.setType(Arrays.asList("VerifiableCredential", "SampleVerifiableCredential_ldp"));
        credentialDefinition.setContext(Arrays.asList(""));
        credentialRequest.setCredential_definition(credentialDefinition);
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialProof.setJwt("header.payload.signature");
        credentialRequest.setProof(credentialProof);

        VCResult<JsonLDObject> vcResult = new VCResult<>();
        vcResult.setCredential(new JsonLDObject());
        vcResult.setFormat("ldp_vc");

        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        Mockito.when(proofValidatorFactory.getProofValidator("jwt")).thenReturn(jwtProofValidator);
        Mockito.when(jwtProofValidator.validate(Mockito.anyString(), Mockito.anyString(), Mockito.any(CredentialProof.class)))
                .thenReturn(true);
        Mockito.when(jwtProofValidator.getKeyMaterial(credentialProof)).thenReturn("holder-identifier");
        Mockito.when(vcIssuancePlugin.getVerifiableCredentialWithLinkedDataProof(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(vcResult);
        CredentialResponse credentialResponse = vcIssuanceService.getCredential(credentialRequest);
        Assert.assertNotNull(credentialResponse);
        Assert.assertEquals(vcResult.getFormat(), credentialResponse.getFormat());
        Assert.assertEquals(vcResult.getCredential(), credentialResponse.getCredential());

        credentialRequest.setFormat("jwt_vc_json-ld");
        VCResult<String> vcResult_jwt = new VCResult<>();
        vcResult_jwt.setCredential("jwt");
        vcResult_jwt.setFormat("jwt_vc_json-ld");
        Mockito.when(vcIssuancePlugin.getVerifiableCredential(Mockito.any(), Mockito.any(), Mockito.any()))
                .thenReturn(vcResult_jwt);
        CredentialResponse credentialResponse_jwt = vcIssuanceService.getCredential(credentialRequest);
        Assert.assertNotNull(credentialResponse_jwt);
        Assert.assertEquals(vcResult_jwt.getFormat(), credentialResponse_jwt.getFormat());
        Assert.assertEquals(vcResult_jwt.getCredential(), credentialResponse_jwt.getCredential());
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getCredential_withInactiveAccessToken_thenFail() {
        Mockito.when(parsedAccessToken.isActive()).thenReturn(false);
        vcIssuanceService.getCredential(new CredentialRequest());
    }

    @Test
    public void getCredential_withNoScopeInAccessToken_thenFail() {
        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(new HashMap<>());
        try {
            vcIssuanceService.getCredential(new CredentialRequest());
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_SCOPE, ex.getErrorCode());
        }
    }

    @Test
    public void getCredential_withInvalidScopeInAccessToken_thenFail() {
        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "mosip_identity_vc_ldp");
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        try {
            vcIssuanceService.getCredential(new CredentialRequest());
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_SCOPE, ex.getErrorCode());
        }
    }

    @Test
    public void getCredential_withInvalidProof_thenFail() {
        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()).minusSeconds(10));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        CredentialRequest credentialRequest = new CredentialRequest();
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialRequest.setProof(credentialProof);
        Mockito.when(proofValidatorFactory.getProofValidator("jwt")).thenReturn(new JwtProofValidator());
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.INVALID_PROOF, ex.getErrorCode());
        }
    }

    @Test
    public void getCredential_withInvalidVCFormat_thenFail() {
        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()).minusSeconds(10));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_VC");
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialRequest.setProof(credentialProof);
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setContext(List.of());
        credentialDefinition.setType(List.of());
        credentialRequest.setCredential_definition(credentialDefinition);
        Mockito.when(proofValidatorFactory.getProofValidator("jwt")).thenReturn(jwtProofValidator);
        Mockito.when(jwtProofValidator.validate(Mockito.any(),Mockito.any(),Mockito.any())).thenReturn(true);
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.UNSUPPORTED_VC_FORMAT, ex.getErrorCode());
        }
    }

    @Test
    public void getCredential_withEmptyCredentialDefinition_thenFail() {
        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()).minusSeconds(10));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        Mockito.when(proofValidatorFactory.getProofValidator("jwt")).thenReturn(jwtProofValidator);
        Mockito.when(jwtProofValidator.validate(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);

        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
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
    }

    @Test
    public void getCredential_withOnlySuperTypeCredentialDefinition_thenFail() {
        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()).minusSeconds(10));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        Mockito.when(proofValidatorFactory.getProofValidator("jwt")).thenReturn(jwtProofValidator);
        Mockito.when(jwtProofValidator.validate(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);

        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialRequest.setProof(credentialProof);
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setContext(List.of());
        credentialDefinition.setType(List.of("VerifiableCredential"));
        credentialRequest.setCredential_definition(credentialDefinition);
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
    }

    @Test
    public void getCredential_withOnlyChildTypeCredentialDefinition_thenFail() {
        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()).minusSeconds(10));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        Mockito.when(proofValidatorFactory.getProofValidator("jwt")).thenReturn(jwtProofValidator);
        Mockito.when(jwtProofValidator.validate(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);

        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialRequest.setProof(credentialProof);
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setContext(List.of());
        credentialDefinition.setType(List.of("SampleVerifiableCredential_ldp"));
        credentialRequest.setCredential_definition(credentialDefinition);
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (EsignetException ex) {
            Assert.assertEquals(ErrorConstants.UNSUPPORTED_VC_TYPE, ex.getErrorCode());
        }
    }

    @Test
    public void getCredential_withImproperCaseInTypeCredentialDefinition_thenFail() {
        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Map<String, Object> claims = new HashMap<>();
        claims.put("scope", "sample_vc_ldp");
        claims.put("iat", Instant.now(Clock.systemUTC()).minusSeconds(10));
        claims.put("c_nonce", "test-nonce");
        claims.put("c_nonce_expires_in", 60);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        Mockito.when(proofValidatorFactory.getProofValidator("jwt")).thenReturn(jwtProofValidator);
        Mockito.when(jwtProofValidator.validate(Mockito.any(), Mockito.any(), Mockito.any())).thenReturn(true);

        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialRequest.setProof(credentialProof);
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setContext(List.of());
        credentialDefinition.setType(List.of("verifiableCredential", "SampleVerifiableCredential_ldp"));
        credentialRequest.setCredential_definition(credentialDefinition);
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

        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        Mockito.when(proofValidatorFactory.getProofValidator("jwt")).thenReturn(jwtProofValidator);
        Mockito.when(vciCacheService.setVCITransaction(Mockito.any(), Mockito.any())).thenReturn(issuanceTransaction);
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (InvalidNonceException ex) {
            Assert.assertEquals(issuanceTransaction.getCNonce(), ex.getClientNonce());
            Assert.assertEquals(issuanceTransaction.getCNonceExpireSeconds(), ex.getClientNonceExpireSeconds());
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

        Mockito.when(parsedAccessToken.isActive()).thenReturn(true);
        Mockito.when(parsedAccessToken.getClaims()).thenReturn(claims);
        Mockito.when(proofValidatorFactory.getProofValidator("jwt")).thenReturn(jwtProofValidator);

        VCIssuanceTransaction expiredTransaction = new VCIssuanceTransaction();
        expiredTransaction.setCNonce("test-nonce");
        expiredTransaction.setCNonceExpireSeconds(2);
        expiredTransaction.setCNonceIssuedEpoch(Instant.now(Clock.systemUTC()).minusSeconds(10).getEpochSecond());
        Mockito.when(vciCacheService.getVCITransaction(Mockito.any())).thenReturn(expiredTransaction);

        VCIssuanceTransaction issuanceTransaction = new VCIssuanceTransaction();
        issuanceTransaction.setCNonce("new_c_nonce");
        issuanceTransaction.setCNonceExpireSeconds(400);
        Mockito.when(vciCacheService.setVCITransaction(Mockito.any(), Mockito.any())).thenReturn(issuanceTransaction);
        try {
            vcIssuanceService.getCredential(credentialRequest);
            Assert.fail();
        } catch (InvalidNonceException ex) {
            Assert.assertEquals(issuanceTransaction.getCNonce(), ex.getClientNonce());
            Assert.assertEquals(issuanceTransaction.getCNonceExpireSeconds(), ex.getClientNonceExpireSeconds());
        }
    }
}
