package io.mosip.idp.dto;

import lombok.Data;

import java.util.List;

@Data
public class ResponseWrapper<T> {

    private String id;
    private String version;
    private String responseTime;
    private T response;
    private List<ErrorDto> errorDtos;
}
