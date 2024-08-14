package io.mosip.esignet.core.dto.vci;


import lombok.Data;

import java.io.Serializable;

@Data
public class VCIssuanceTransaction implements Serializable {

    private static final long serialVersionUID = 1L;
    private String cNonce;
    private long cNonceIssuedEpoch;
    private int cNonceExpireSeconds;


}
