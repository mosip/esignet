/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import io.mosip.idp.core.exception.NotAuthenticatedException;
import io.mosip.idp.core.spi.OpenIdConnectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/oidc")
public class OpenIdConnectController {

    private static final Logger logger = LoggerFactory.getLogger(OpenIdConnectController.class);

    @Autowired
    private OpenIdConnectService openIdConnectServiceImpl;

    @GetMapping("/userinfo")
    public String getUserInfo(@RequestHeader("Authorization") String bearerToken)
            throws NotAuthenticatedException {
        return openIdConnectServiceImpl.getUserInfo(bearerToken);
    }

    //TODO discovery API
}
