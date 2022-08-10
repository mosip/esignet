package io.mosip.idp.controllers;


import io.mosip.idp.dto.TokenReqDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

@RestController
public class OpenIdConnectController {

    private static final Logger logger = LoggerFactory.getLogger(OpenIdConnectController.class);

    @PostMapping("/token")
    public void getToken(@Valid @RequestBody TokenReqDto tokenReqDto) {

    }

    @GetMapping("/userinfo")
    public void getUserInfo(@RequestHeader("Authorization") String bearerToken) {

    }
}
