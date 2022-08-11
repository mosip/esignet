/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;


import io.mosip.idp.dto.OauthReqDto;
import io.mosip.idp.dto.OauthRespDto;
import io.mosip.idp.dto.RequestWrapper;
import io.mosip.idp.dto.ResponseWrapper;
import io.mosip.idp.services.InternalApiService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalApiController {

    private static final Logger logger = LoggerFactory.getLogger(InternalApiController.class);

    @Autowired
    InternalApiService internalApiService;

    @PostMapping("/oauth-details")
    public ResponseWrapper<OauthRespDto> getOauthDetails(@RequestBody RequestWrapper<OauthReqDto> requestWrapper) {
        return null;
    }

    @PostMapping("/send-otp")
    public void sendOtp() {

    }

    @PostMapping("/authenticate")
    public void authenticateEndUser() {

    }

    @PostMapping("/auth-code")
    public void getAuthorizationCode() {

    }
}
