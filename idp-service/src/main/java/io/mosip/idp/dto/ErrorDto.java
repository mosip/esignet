package io.mosip.idp.dto;

import lombok.Data;

@Data
public class ErrorDto {

    private String errorCode;
    private String errorMessage;
}
