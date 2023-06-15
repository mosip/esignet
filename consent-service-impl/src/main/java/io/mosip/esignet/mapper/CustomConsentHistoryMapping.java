package io.mosip.esignet.mapper;

import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.entity.ConsentDetail;
import io.mosip.esignet.entity.ConsentHistory;
import org.modelmapper.PropertyMap;

public class CustomConsentHistoryMapping extends PropertyMap<UserConsent, ConsentHistory> {
    @Override
    protected void configure() {
         // Skip the 'id' field when mapping
        skip().setId(null);
    }
}


