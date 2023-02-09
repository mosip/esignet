package io.mosip.esignet.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.JWK;
import io.mosip.esignet.TestUtil;
import io.mosip.esignet.core.spi.LinkedAuthorizationService;
import io.mosip.esignet.services.CacheUtilService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@WebMvcTest(value = LinkedAuthorizationController.class)
public class LinkedAuthorizationControllerTest {

    ObjectMapper objectMapper = new ObjectMapper();

    private JWK clientJWK = TestUtil.generateJWK_RSA();

    @Autowired
    MockMvc mockMvc;

    @MockBean
    private LinkedAuthorizationService linkedAuthorizationService;

    @InjectMocks
    LinkedAuthorizationController linkedAuthorizationController;

    @MockBean
    CacheUtilService cacheUtilService;

    @Test
    public void generateLinkCode_withValidRequest_thenPass() {

    }

    @Test
    public void linkTransaction_withValidRequest_thenPass() {

    }

    @Test
    public void authenticate_withValidRequest_thenPass() {

    }

    @Test
    public void saveConsent_withValidRequest_thenPass() {

    }

    @Test
    public void getLinkStatus_withValidRequest_thenPass() {

    }

    @Test
    public void getLinkAuthCode_withValidRequest_thenPass() {

    }

    @Test
    public void sendOtp_withValidRequest_thenPass() {

    }
}
