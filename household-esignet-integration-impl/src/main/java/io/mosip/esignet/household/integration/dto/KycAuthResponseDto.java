package io.mosip.esignet.household.integration.dto;


import lombok.Data;

@Data
public class KycAuthResponseDto {

    private boolean authStatus;
    private String kycToken;
    private String partnerSpecificUserToken;

    public KycAuthResponseDto(String kycToken, String partnerSpecificUserToken) {
        this.kycToken = kycToken;
        this.partnerSpecificUserToken = partnerSpecificUserToken;
    }
}