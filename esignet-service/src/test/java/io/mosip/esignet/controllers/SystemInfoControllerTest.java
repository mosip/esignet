package io.mosip.esignet.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
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

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.RequestWrapper;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.esignet.services.CacheUtilService;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateResponseDto;
import io.mosip.kernel.keymanagerservice.dto.UploadCertificateRequestDto;
import io.mosip.kernel.keymanagerservice.dto.UploadCertificateResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;

@RunWith(SpringRunner.class)
@WebMvcTest(value = SystemInfoController.class)
public class SystemInfoControllerTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @Autowired
    MockMvc mockMvc;

    @MockBean
    private KeymanagerService keymanagerService;

    @MockBean
    CacheUtilService cacheUtilService;
    
    @MockBean
    AuditPlugin auditWrapper;

    @MockBean
    ParsedAccessToken parsedAccessToken;

    @MockBean
    VCICacheService vciCacheService;

    @Test
    public void getCertificate_withValidRequest_thenPass() throws Exception {
        String applicationId = "test";
        String referenceId = "test-ref";
        Mockito.when(keymanagerService.getCertificate(applicationId, Optional.of(referenceId)))
                .thenReturn(new KeyPairGenerateResponseDto());

        mockMvc.perform(get("/system-info/certificate")
                        .param("applicationId", applicationId)
                        .param("referenceId", referenceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.errors").isEmpty());
    }

    @Test
    public void getCertificate_withInvalidRequest_thenThrowException() throws Exception {
        String applicationId = "test";
        String referenceId = "test-ref";
        Mockito.when(keymanagerService.getCertificate(applicationId, Optional.of(referenceId))).thenThrow(EsignetException.class);

        mockMvc.perform(get("/system-info/certificate")
                        .param("applicationId", applicationId)
                        .param("referenceId", referenceId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

    @Test
    public void uploadCertificate_withValidRequest_thenPass() throws Exception {
        RequestWrapper<UploadCertificateRequestDto> requestWrapper = new RequestWrapper<>();
        UploadCertificateRequestDto uploadCertificateRequestDto = new UploadCertificateRequestDto();
        uploadCertificateRequestDto.setApplicationId("appId");
        uploadCertificateRequestDto.setCertificateData("cert");
        uploadCertificateRequestDto.setReferenceId("refId");
        requestWrapper.setRequest(uploadCertificateRequestDto);
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());

        Mockito.when(keymanagerService.uploadCertificate(Mockito.any(UploadCertificateRequestDto.class)))
                .thenReturn(new UploadCertificateResponseDto());

        mockMvc.perform(post("/system-info/uploadCertificate")
                        .content(objectMapper.writeValueAsBytes(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.response").exists())
                .andExpect(jsonPath("$.errors").isEmpty());
    }
    
    @Test
    public void uploadCertificate_withInvalidRequest_thenThrowException() throws Exception {
        RequestWrapper<UploadCertificateRequestDto> requestWrapper = new RequestWrapper<>();
        UploadCertificateRequestDto uploadCertificateRequestDto = new UploadCertificateRequestDto();
        uploadCertificateRequestDto.setApplicationId("appId");
        uploadCertificateRequestDto.setCertificateData("cert");
        uploadCertificateRequestDto.setReferenceId("refId");
        requestWrapper.setRequest(uploadCertificateRequestDto);
        requestWrapper.setRequestTime(IdentityProviderUtil.getUTCDateTime());

        Mockito.when(keymanagerService.uploadCertificate(Mockito.any(UploadCertificateRequestDto.class)))
                .thenThrow(EsignetException.class);

        mockMvc.perform(post("/system-info/uploadCertificate")
                        .content(objectMapper.writeValueAsBytes(requestWrapper))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk());
    }

}
