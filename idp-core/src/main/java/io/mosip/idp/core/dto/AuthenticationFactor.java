package io.mosip.idp.core.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AuthenticationFactor {

    private String type;
    private int count;
    private List<String> subTypes;
}
