package io.mosip.esignet.mapper;

import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.UserConsent;
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

    public static io.mosip.esignet.entity.ConsentDetail toEntity(ConsentDetail consentDetailDTo) {
        return modelMapper.map(consentDetailDTo, io.mosip.esignet.entity.ConsentDetail.class);
    }

    public static io.mosip.esignet.entity.ConsentDetail toEntity(UserConsent userConsent) {
        return modelMapper.map(userConsent, io.mosip.esignet.entity.ConsentDetail.class);
    }

    public static ConsentDetail toDto(io.mosip.esignet.entity.ConsentDetail consentDetail) {
        return modelMapper.map(consentDetail, ConsentDetail.class);
    }
}
