package io.mosip.esignet.api.dto.Claim;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FilterCriteria {
    private String value;
    private String[] values;
}
