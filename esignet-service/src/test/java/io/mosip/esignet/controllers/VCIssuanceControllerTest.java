package io.mosip.esignet.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import foundation.identity.jsonld.JsonLDObject;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.vci.*;
import io.mosip.esignet.core.spi.VCIssuanceService;
import io.mosip.esignet.services.CacheUtilService;
import io.mosip.esignet.vci.exception.InvalidNonceException;
import io.mosip.esignet.vci.services.VCICacheService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@RunWith(SpringRunner.class)
@WebMvcTest(value = VCIssuanceController.class)
public class VCIssuanceControllerTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @MockBean
    AuditPlugin auditWrapper;

    @MockBean
    CacheUtilService cacheUtilService;

    @MockBean
    ParsedAccessToken parsedAccessToken;

    @MockBean
    VCIssuanceService vcIssuanceService;

    @MockBean
    VCICacheService vciCacheService;

    @Test
    public void test_getIssuerMetadata_thenPass() throws Exception {
        Map<String, Object> issuerMetadata = new HashMap<>();
        issuerMetadata.put("credential_issuer", "https://localhost:9090");
        issuerMetadata.put("credential_endpoint", "https://localhost:9090/v1/esignet/vci/credential");
        issuerMetadata.put("credentials_supported", Arrays.asList());

        Mockito.when(vcIssuanceService.getCredentialIssuerMetadata(Mockito.anyString())).thenReturn(issuerMetadata);

        mockMvc.perform(get("/vci/.well-known/openid-credential-issuer"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credential_issuer").exists())
                .andExpect(jsonPath("$.credential_issuer").exists())
                .andExpect(jsonPath("$.credentials_supported").exists())
                .andExpect(header().string("Content-Type", "application/json"));

        Mockito.verify(vcIssuanceService).getCredentialIssuerMetadata("latest");
    }

    @Test
    public void test_getIssuerMetadataWithQueryParam_thenPass() throws Exception {
        Map<String, Object> issuerMetadata = new HashMap<>();
        issuerMetadata.put("credential_issuer", "https://localhost:9090");
        issuerMetadata.put("credential_endpoint", "https://localhost:9090/v1/esignet/vci/credential");
        issuerMetadata.put("credentials_supported", Arrays.asList());

        Mockito.when(vcIssuanceService.getCredentialIssuerMetadata(Mockito.anyString())).thenReturn(issuerMetadata);

        mockMvc.perform(get("/vci/.well-known/openid-credential-issuer?version=v11"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.credential_issuer").exists())
                .andExpect(jsonPath("$.credential_issuer").exists())
                .andExpect(jsonPath("$.credentials_supported").exists())
                .andExpect(header().string("Content-Type", "application/json"));

        Mockito.verify(vcIssuanceService).getCredentialIssuerMetadata("v11");
    }

    @Test
    public void getVC_withValidDetails_thenPass() throws Exception {
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setType(Arrays.asList("VerifiableCredential", "SampleVerifiableCredential_ldp"));
        credentialDefinition.setContext(Arrays.asList("https://www.w3.org/2018/credentials/v1"));
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialProof.setJwt("dummy_jwt_proof");
        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        credentialRequest.setProof(credentialProof);
        credentialRequest.setCredential_definition(credentialDefinition);

        CredentialResponse credentialResponse = new CredentialResponse<JsonLDObject>();
        credentialResponse.setFormat("ldp_vc");
        credentialResponse.setCredential(new JsonLDObject());
        Mockito.when(vcIssuanceService.getCredential(credentialRequest)).thenReturn(credentialResponse);

        mockMvc.perform(post("/vci/credential")
                        .content(objectMapper.writeValueAsBytes(credentialRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.format").exists())
                .andExpect(jsonPath("$.credential").exists());
    }

    @Test
    public void getVC_withInvalidFormat_thenFail() throws Exception {
        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat(null);
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialRequest.setProof(credentialProof);
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setType(Arrays.asList("VerifiableCredential", "SampleVerifiableCredential_ldp"));
        credentialRequest.setCredential_definition(credentialDefinition);

        mockMvc.perform(post("/vci/credential")
                        .content(objectMapper.writeValueAsBytes(credentialRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(ErrorConstants.INVALID_VC_FORMAT));

        credentialRequest.setFormat("  ");
        mockMvc.perform(post("/vci/credential")
                        .content(objectMapper.writeValueAsBytes(credentialRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(ErrorConstants.INVALID_VC_FORMAT));
    }

    @Test
    public void getVC_withInvalidProof_thenFail() throws Exception {
        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("jwt_vc_json");
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setType(Arrays.asList("VerifiableCredential", "SampleVerifiableCredential_ldp"));
        credentialRequest.setCredential_definition(credentialDefinition);

        credentialRequest.setProof(null);
        mockMvc.perform(post("/vci/credential")
                        .content(objectMapper.writeValueAsBytes(credentialRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(ErrorConstants.INVALID_PROOF));

        CredentialProof credentialProof = new CredentialProof();
        credentialRequest.setProof(credentialProof);
        mockMvc.perform(post("/vci/credential")
                        .content(objectMapper.writeValueAsBytes(credentialRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(ErrorConstants.UNSUPPORTED_PROOF_TYPE));


        credentialProof.setProof_type("  ");
        credentialRequest.setProof(credentialProof);
        mockMvc.perform(post("/vci/credential")
                        .content(objectMapper.writeValueAsBytes(credentialRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(ErrorConstants.UNSUPPORTED_PROOF_TYPE));
    }

    @Test
    public void test_getVC_withInvalidNonceException_thenFail() throws Exception {
        CredentialDefinition credentialDefinition = new CredentialDefinition();
        credentialDefinition.setType(Arrays.asList("VerifiableCredential", "SampleVerifiableCredential_ldp"));
        credentialDefinition.setContext(Arrays.asList("https://www.w3.org/2018/credentials/v1"));
        CredentialProof credentialProof = new CredentialProof();
        credentialProof.setProof_type("jwt");
        credentialProof.setJwt("dummy_jwt_proof");
        CredentialRequest credentialRequest = new CredentialRequest();
        credentialRequest.setFormat("ldp_vc");
        credentialRequest.setProof(credentialProof);
        credentialRequest.setCredential_definition(credentialDefinition);

        InvalidNonceException exception = new InvalidNonceException("test-new-nonce", 400);
        Mockito.when(vcIssuanceService.getCredential(credentialRequest)).thenThrow(exception);

        mockMvc.perform(post("/vci/credential")
                        .content(objectMapper.writeValueAsBytes(credentialRequest))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(exception.getErrorCode()))
                .andExpect(jsonPath("$.c_nonce_expires_in").value(exception.getClientNonceExpireSeconds()))
                .andExpect(jsonPath("$.c_nonce").value(exception.getClientNonce()));
    }
}
