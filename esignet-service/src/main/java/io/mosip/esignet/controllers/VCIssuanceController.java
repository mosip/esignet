package io.mosip.esignet.controllers;

import io.mosip.esignet.core.dto.vci.CredentialRequest;
import io.mosip.esignet.core.dto.vci.CredentialResponse;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.VCIssuanceService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/vci")
public class VCIssuanceController {

    @Autowired
    private VCIssuanceService vcIssuanceService;

    @Value("#{${mosip.esignet.vci.key-values}}")
    private Map<String, Object> metadata;

    /**
     * 1. The credential Endpoint MUST accept Access Tokens
     * @param bearerToken
     * @return
     * @throws EsignetException
     */
    @PostMapping(value = "/credential",produces = "application/json")
    public CredentialResponse getCredential(@RequestHeader("Authorization") String bearerToken,
                                            @RequestBody CredentialRequest credentialRequest) throws EsignetException {
        return vcIssuanceService.getCredential(bearerToken, credentialRequest);
    }

    @GetMapping(value = "/.well-known/openid-credential-issuer",produces = "application/json")
    public Map<String, Object> getMetadata() {
        return metadata;
    }
}
