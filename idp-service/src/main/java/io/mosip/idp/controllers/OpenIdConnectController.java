/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import io.mosip.idp.core.dto.DiscoveryResponse;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.OpenIdConnectService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/oidc")
public class OpenIdConnectController {

    @Autowired
    private OpenIdConnectService openIdConnectServiceImpl;

    @GetMapping("/userinfo")
    public String getUserInfo(@RequestHeader("Authorization") String bearerToken) throws IdPException {
        return openIdConnectServiceImpl.getUserInfo(bearerToken);
    }

    @GetMapping("/.well-known/openid-configuration")
    public DiscoveryResponse getDiscoveryEndpoints() {
        return openIdConnectServiceImpl.getOpenIdConfiguration();
    }
}
