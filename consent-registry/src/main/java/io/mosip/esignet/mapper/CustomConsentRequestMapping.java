package io.mosip.esignet.mapper;

import io.mosip.esignet.core.dto.ConsentRequest;
import io.mosip.esignet.entity.Consent;
import org.modelmapper.PropertyMap;

public class CustomConsentRequestMapping extends PropertyMap<ConsentRequest, Consent> {
    @Override
    protected void configure() {
         // Skip the 'id' field when mapping
        skip().setId(null);
    }
}


