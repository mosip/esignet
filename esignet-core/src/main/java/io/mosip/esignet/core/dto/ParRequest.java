package io.mosip.esignet.core.dto;
import lombok.Data;

import java.io.Serializable;

@Data
public class ParRequest extends OAuthDetailRequestV3 implements Serializable {

    private static final long serialVersionUID = 1L;

    private String clientAssertionType;
    private String clientAssertion;

}