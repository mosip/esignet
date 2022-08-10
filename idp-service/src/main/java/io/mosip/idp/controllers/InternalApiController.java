package io.mosip.idp.controllers;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/internal")
public class InternalApiController {

    private static final Logger logger = LoggerFactory.getLogger(InternalApiController.class);
}
