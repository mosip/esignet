package io.mosip.esignet.controllers;

import io.mosip.esignet.core.dto.vci.CredentialRequest;
import io.mosip.esignet.core.dto.vci.CredentialResponse;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.VCIssuanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/vci")
public class VCIssuanceController {

    @Autowired
    private VCIssuanceService vcIssuanceService;

    /**
     * 1. The credential Endpoint MUST accept Access Tokens
     * @param credentialRequest VC credential request
     * @return Credential Response w.r.t requested format
     * @throws EsignetException
     */
    @PostMapping(value = "/credential",produces = "application/json")
    public CredentialResponse getCredential(@Valid @RequestBody CredentialRequest credentialRequest) throws EsignetException {
        return vcIssuanceService.getCredential(credentialRequest);
    }

    /**
     * Open endpoint to provide VC issuer's metadata
     * @return
     */
    @GetMapping(value = "/.well-known/openid-credential-issuer",produces = "application/json")
    public Map<String, Object> getMetadata() {
        return vcIssuanceService.getCredentialIssuerMetadata();
    }
}
