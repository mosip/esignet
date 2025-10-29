package io.mosip.esignet.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.mosip.esignet.core.config.LocalAuthenticationEntryPoint;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.esignet.services.CacheUtilService;
import org.springframework.test.web.servlet.MvcResult;

@RunWith(SpringRunner.class)
@WebMvcTest(value = CsrfController.class)
public class CsrfControllerTest {

	@Autowired
	MockMvc mockMvc;	

	@MockBean
	CacheUtilService cacheUtilService;

	@MockBean
	LocalAuthenticationEntryPoint localAuthenticationEntryPoint;

	ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void getCsrfToken_withValidToken_returnsuccessResponse() throws JsonProcessingException, Exception {
		CsrfToken csrfToken = new DefaultCsrfToken("headerName", "parameterName", "token");
		mockMvc.perform(get("/csrf/token").content(objectMapper.writeValueAsString(csrfToken))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
	}

    @Test
    public void getCsrfToken_AfterSessionExpiry_thenFail() throws Exception {
        MockHttpSession session = new MockHttpSession();
        CsrfToken oldToken = new DefaultCsrfToken("X-XSRF-TOKEN", "_csrf", "old-token");
        session.setAttribute(CsrfToken.class.getName(), oldToken);
        MvcResult result1 = mockMvc.perform(get("/csrf/token").session(session))
                .andExpect(status().isOk())
                .andReturn();

        String responseBody1 = result1.getResponse().getContentAsString();
        session.invalidate();
        MvcResult result2 = mockMvc.perform(get("/csrf/token"))
                .andExpect(status().isOk())
                .andReturn();
        String responseBody2 = result2.getResponse().getContentAsString();
        Assert.assertNotEquals(responseBody1, responseBody2, "CSRF token should be regenerated after session expiry");
    }

}
