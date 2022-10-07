package io.mosip.idp.authwrapper.dto;

import lombok.Data;

import java.util.List;

@Data
public class IdaSendOtpRequest {

    private String id;
    private String version;
    private String individualId;
    private String individualIdType;
    private String transactionID;
    private String requestTime;
    private List<String> otpChannel;

}
