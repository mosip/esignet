/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.controllers;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.RequestWrapper;
import io.mosip.esignet.core.dto.ResponseWrapper;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateResponseDto;
import io.mosip.kernel.keymanagerservice.dto.UploadCertificateRequestDto;
import io.mosip.kernel.keymanagerservice.dto.UploadCertificateResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;
import java.util.Optional;

/**
 * Controller GET Idp service certificates
 */
@Slf4j
@RestController
@RequestMapping("/system-info")
public class SystemInfoController {

    @Autowired
    private KeymanagerService keymanagerService;
    
    @Autowired
    AuditPlugin auditWrapper;
    
    @Value("${mosip.esignet.audit.claim-name:preferred_username}")
    private String claimName;

    @GetMapping(value = "/certificate")
    public ResponseWrapper<KeyPairGenerateResponseDto> getCertificate(
            @Valid @NotBlank(message = ErrorConstants.INVALID_REQUEST) @RequestParam("applicationId") String applicationId,
            @RequestParam("referenceId") Optional<String> referenceId) {
        ResponseWrapper<KeyPairGenerateResponseDto> responseWrapper = new ResponseWrapper<>();
        try {
        	responseWrapper.setResponse(keymanagerService.getCertificate(applicationId, referenceId));
        } catch (EsignetException ex) {
        	auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
					Action.GET_CERTIFICATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(null), ex);
        	throw ex;
        }
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
                Action.GET_CERTIFICATE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(null), null);
        return responseWrapper;
    }

    @PostMapping(value = "/uploadCertificate")
    public ResponseWrapper<UploadCertificateResponseDto> uploadSignedCertificate(
            @Valid @RequestBody RequestWrapper<UploadCertificateRequestDto> requestWrapper) {
        ResponseWrapper<UploadCertificateResponseDto> responseWrapper = new ResponseWrapper<>();
        UploadCertificateRequestDto uploadCertificateRequestDto = requestWrapper.getRequest();
        try {
        	responseWrapper.setResponse(keymanagerService.uploadCertificate(uploadCertificateRequestDto));
        } catch (EsignetException ex) {
        	auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
					Action.UPLOAD_CERTIFICATE, ActionStatus.ERROR, AuditHelper.buildAuditDto(null), ex);
            throw ex;
        }
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        auditWrapper.logAudit(AuditHelper.getClaimValue(SecurityContextHolder.getContext(), claimName),
                Action.UPLOAD_CERTIFICATE, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(null), null);
        return responseWrapper;
    }

}
