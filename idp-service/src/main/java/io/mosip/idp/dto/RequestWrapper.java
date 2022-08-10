package io.mosip.idp.dto;

import lombok.Data;

@Data
public class RequestWrapper<T> {

    private String id;
    private String version;
    private String requestTime;
    private T request;
}
