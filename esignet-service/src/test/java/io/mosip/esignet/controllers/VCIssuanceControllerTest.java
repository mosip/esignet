package io.mosip.esignet.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import foundation.identity.jsonld.JsonLDObject;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.vci.*;
import io.mosip.esignet.core.spi.VCIssuanceService;
import io.mosip.esignet.services.CacheUtilService;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
