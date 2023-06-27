/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.mapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.entity.ConsentHistory;
import io.mosip.esignet.mapper.converter.*;
import org.modelmapper.ModelMapper;



public class ConsentMapper {

    private ConsentMapper(){}

    private static final ModelMapper modelMapper = new ModelMapper();

    static {
        ObjectMapper objectMapper = new ObjectMapper();
        modelMapper.addConverter(new ClaimsToStringConverter(objectMapper));
        modelMapper.addConverter(new StringToClaimsConverter(objectMapper));
        modelMapper.addConverter(new MapToStringConverter(objectMapper));
        modelMapper.addConverter(new StringToMapConverter(objectMapper));
        modelMapper.addConverter(new ListToStringConverter());
        modelMapper.addConverter(new StringToListConverter());
        modelMapper.addMappings(new CustomConsentRequestMapping());
        modelMapper.addMappings(new CustomConsentHistoryMapping());
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

    public static ConsentHistory toConsentHistoryEntity(UserConsent userConsent){
        return modelMapper.map(userConsent, ConsentHistory.class);
    }
}
