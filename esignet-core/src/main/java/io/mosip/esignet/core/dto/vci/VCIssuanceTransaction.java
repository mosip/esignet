package io.mosip.esignet.core.dto.vci;


import lombok.Data;

@Data
public class VCIssuanceTransaction {

    private String cNonce;
    private long cNonceIssuedEpoch;
    private int cNonceExpireSeconds;


}
