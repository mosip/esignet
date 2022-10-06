package io.mosip.idp.authwrapper.dto;

import lombok.Data;

import java.util.List;

@Data
public class IdaKycExchangeRequest {

    private String kycToken;
    private List<String> consentObtained;
    private List<String> locales;
}
