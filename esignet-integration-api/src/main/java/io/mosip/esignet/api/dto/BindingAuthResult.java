package io.mosip.esignet.api.dto;


import lombok.AllArgsConstructor;
import lombok.Data;

@AllArgsConstructor
@Data
public class BindingAuthResult {

    private String transactionId;
    private String individualId;
}
