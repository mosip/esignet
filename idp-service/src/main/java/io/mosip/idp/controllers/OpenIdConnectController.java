/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import io.mosip.idp.dto.*;
import io.mosip.idp.exception.IdPException;
import io.mosip.idp.exception.InvalidClientException;
import io.mosip.idp.exception.NotAuthenticatedException;
import io.mosip.idp.services.OpenIdConnectService;
import io.mosip.idp.util.ErrorConstants;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import javax.validation.ConstraintViolation;
import javax.validation.ConstraintViolationException;
import javax.validation.Valid;
import java.util.ArrayList;
import java.util.Set;

import static io.mosip.idp.util.ErrorConstants.*;

@RestController
public class OpenIdConnectController {

    private static final Logger logger = LoggerFactory.getLogger(OpenIdConnectController.class);

    @Autowired
    private OpenIdConnectService openIdConnectService;

    @PostMapping("/token")
    public TokenRespDto getToken(@Valid @RequestBody TokenReqDto tokenReqDto) throws IdPException {
        return openIdConnectService.getTokens(tokenReqDto);
    }

    @GetMapping("/userinfo")
    public String getUserInfo(@RequestHeader("Authorization") String bearerToken) throws NotAuthenticatedException {
        return openIdConnectService.getCachedUserInfo(bearerToken);
    }
}
