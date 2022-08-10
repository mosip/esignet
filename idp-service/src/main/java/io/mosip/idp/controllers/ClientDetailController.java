package io.mosip.idp.controllers;

import io.mosip.idp.dto.ClientReqDto;
import io.mosip.idp.dto.ClientRespDto;
import io.mosip.idp.dto.RequestWrapper;
import io.mosip.idp.dto.ResponseWrapper;
import io.mosip.idp.services.ClientDetailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;


/**
 * TODO - Add scope based pre-authorize annotations after integrating with auth-adapter
 */
@RestController
@RequestMapping("/oidc-client")
public class ClientDetailController {

    private static final Logger logger = LoggerFactory.getLogger(ClientDetailController.class);

    @Autowired
    ClientDetailService clientDetailService;

    @PostMapping
    public ResponseWrapper<ClientRespDto> createClient(
            @Valid @RequestBody RequestWrapper<ClientReqDto> requestWrapper) {
        //TODO
        return null;
    }

    @PutMapping("/{client_id}")
    public ResponseWrapper<ClientRespDto> updateClient(@Valid @PathVariable("client_id")  String clientId,
                                                       @Valid @RequestBody RequestWrapper<ClientReqDto> requestWrapper)
    {
        //TODO
        return null;
    }
}
