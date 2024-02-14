package io.mosip.esignet.core.dto.vci;


import lombok.Data;

import java.io.Serializable;

@Data
public class VCIssuanceTransaction implements Serializable {

    private String cNonce;
    private long cNonceIssuedEpoch;
    private int cNonceExpireSeconds;


}
