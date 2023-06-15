package io.mosip.esignet.mapper;

import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.entity.ConsentDetail;
import org.modelmapper.PropertyMap;

public class CustomConsentRequestMapping extends PropertyMap<UserConsent, ConsentDetail> {
    @Override
    protected void configure() {
         // Skip the 'id' field when mapping
        skip().setId(null);
    }
}


