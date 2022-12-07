/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import io.mosip.idp.core.dto.ResponseWrapper;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateResponseDto;
import io.mosip.kernel.keymanagerservice.dto.UploadCertificateRequestDto;
import io.mosip.kernel.keymanagerservice.dto.UploadCertificateResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;
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

    @GetMapping(value = "/certificate")
    public ResponseWrapper<KeyPairGenerateResponseDto> getCertificate(
            @RequestParam("applicationId") String applicationId,
            @RequestParam("referenceId") Optional<String> referenceId) {
        ResponseWrapper<KeyPairGenerateResponseDto> responseWrapper = new ResponseWrapper<>();
        responseWrapper.setResponse(keymanagerService.getCertificate(applicationId, referenceId));
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        return responseWrapper;
    }

    @PostMapping(value = "/uploadCertificate")
    public ResponseWrapper<UploadCertificateResponseDto> uploadSignedCertificate(
            @Valid UploadCertificateRequestDto uploadCertificateRequestDto) {
        ResponseWrapper<UploadCertificateResponseDto> responseWrapper = new ResponseWrapper<>();
        responseWrapper.setResponse(keymanagerService.uploadCertificate(uploadCertificateRequestDto));
        responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());
        return responseWrapper;
    }

}
