package io.mosip.idp.core.dto;

import lombok.Data;

@Data
public class SendOtpRequest {

    private String transactionId;
    private String individualId;
    private String otpChannel;
}
