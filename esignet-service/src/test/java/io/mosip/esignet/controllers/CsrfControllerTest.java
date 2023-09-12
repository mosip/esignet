package io.mosip.esignet.controllers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.mosip.esignet.core.dto.vci.ParsedAccessToken;
import io.mosip.esignet.vci.services.VCICacheService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.esignet.services.CacheUtilService;

@RunWith(SpringRunner.class)
@WebMvcTest(value = CsrfController.class)
public class CsrfControllerTest {

	@Autowired
	MockMvc mockMvc;	

	@MockBean
	CacheUtilService cacheUtilService;

	@MockBean
	ParsedAccessToken parsedAccessToken;

	@MockBean
	VCICacheService vciCacheService;

	ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void getCsrfToken_withValidToken_returnsuccessResponse() throws JsonProcessingException, Exception {
		CsrfToken csrfToken = new DefaultCsrfToken("headerName", "parameterName", "token");
		mockMvc.perform(get("/csrf/token").content(objectMapper.writeValueAsString(csrfToken))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());
	}

}
