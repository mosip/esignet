/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.advice.ExceptionHandlerAdvice;
import io.mosip.idp.core.spi.OpenIdConnectService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;


@RunWith(SpringRunner.class)
@WebMvcTest(value = OpenIdConnectController.class)
public class OpenIdConnectControllerTest {

    ObjectMapper objectMapper = new ObjectMapper();

    @Value("#{${mosip.idp.discovery.key-values}}")
    private Map<String, Object> discoveryMap;

    @Value("${mosip.idp.discovery.issuer-id}")
    private String issuerId;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    OpenIdConnectService openIdConnectServiceImpl;

    @Test
    public void getOpenIdConfiguration_pass() throws Exception {
        when(openIdConnectServiceImpl.getOpenIdConfiguration()).thenReturn(discoveryMap);

        mockMvc.perform(get("/oidc/.well-known/openid-configuration")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.issuer").value(issuerId))
                .andExpect(jsonPath("$.authorization_endpoint").value(discoveryMap.get("authorization_endpoint")))
                .andExpect(jsonPath("$.token_endpoint").value(discoveryMap.get("token_endpoint")))
                .andExpect(jsonPath("$.userinfo_endpoint").value(discoveryMap.get("userinfo_endpoint")));
    }
}
