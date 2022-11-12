package io.mosip.idp.core.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class KycSigningCertificateData {

    private String keyId;
    private String certificateData; //X509 certificate
    private LocalDateTime expiryAt;
    private LocalDateTime issuedAt;

}
