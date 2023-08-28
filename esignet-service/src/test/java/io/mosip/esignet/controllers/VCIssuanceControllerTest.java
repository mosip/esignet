package io.mosip.esignet.controllers;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import io.mosip.esignet.core.spi.VCIssuanceService;
import io.mosip.esignet.services.CacheUtilService;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@RunWith(SpringRunner.class)
@WebMvcTest(value = VCIssuanceController.class)
public class VCIssuanceControllerTest {

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

    @InjectMocks
    VCIssuanceController vcIssuanceController;

    @Test
    public void getVC_withValidDetails_thenPass() {
        //TODO WIP
        Assert.assertTrue(true);
    }
}
