package io.mosip.esignet.core.dto;

import lombok.Data;
import org.apache.kafka.common.protocol.types.Field;

@Data
public class ClaimStatus {

    String claim;
    Boolean Available;
    Boolean verified;
}
