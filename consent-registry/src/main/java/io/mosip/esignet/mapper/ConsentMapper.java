package io.mosip.esignet.mapper;

import io.mosip.esignet.core.dto.Consent;
import io.mosip.esignet.core.dto.ConsentRequest;
import io.mosip.esignet.mapper.converter.ClaimsToStringConverter;
import io.mosip.esignet.mapper.converter.MapToStringConverter;
import io.mosip.esignet.mapper.converter.StringToClaimsConverter;
import io.mosip.esignet.mapper.converter.StringToMapConverter;
import org.modelmapper.ModelMapper;



public class ConsentMapper {
    private static final ModelMapper modelMapper = new ModelMapper();

    static {
        modelMapper.addConverter(new ClaimsToStringConverter());
        modelMapper.addConverter(new StringToClaimsConverter());
        modelMapper.addConverter(new MapToStringConverter());
        modelMapper.addConverter(new StringToMapConverter());
        modelMapper.addMappings(new CustomConsentRequestMapping());
        //modelMapper.getConfiguration().setSkipNullEnabled(true);
    }

    public static io.mosip.esignet.entity.Consent toEntity(Consent consentDTo) {
        return modelMapper.map(consentDTo, io.mosip.esignet.entity.Consent.class);
    }

    public static io.mosip.esignet.entity.Consent toEntity(ConsentRequest consentRequest) {
        return modelMapper.map(consentRequest, io.mosip.esignet.entity.Consent.class);
    }

    public static Consent toDto(io.mosip.esignet.entity.Consent consent) {
        return modelMapper.map(consent, Consent.class);
    }
}
