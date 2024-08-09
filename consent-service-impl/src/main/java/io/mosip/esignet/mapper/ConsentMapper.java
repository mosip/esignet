package io.mosip.esignet.mapper;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.claim.Claims;
import io.mosip.esignet.core.dto.ConsentDetail;
import io.mosip.esignet.core.dto.UserConsent;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.entity.ConsentHistory;
import org.apache.commons.lang3.StringUtils;
import org.mapstruct.Mapper;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_CLAIM;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_PERMITTED_SCOPE;

@Mapper(componentModel = "spring")
public abstract class ConsentMapper {

    @Autowired
    protected ObjectMapper objectMapper;

    public abstract io.mosip.esignet.entity.ConsentDetail toEntity(UserConsent userConsent);

    public abstract ConsentDetail toDto(io.mosip.esignet.entity.ConsentDetail consentDetail);

    public abstract ConsentHistory toConsentHistoryEntity(UserConsent userConsent);

    public String convertClaimsToString(Claims claims) {
        try {
            return claims != null ? objectMapper.writeValueAsString(claims) : "";
        } catch (JsonProcessingException e) {
            throw new EsignetException(INVALID_CLAIM);
        }
    }

    public Claims convertStringToClaims(String claims) {
        try {
            return StringUtils.isNotBlank(claims) ? objectMapper.readValue(claims, Claims.class) : null;
        } catch (JsonProcessingException e) {
            throw new EsignetException(INVALID_CLAIM);
        }
    }

    public String convertListToString(List<String> list) {
        return list == null ? "" : String.join(",", list);
    }

    public List<String> convertStringToList(String value) {
        return StringUtils.isEmpty(value) ? List.of(): Arrays.asList(value.split(","));
    }

    public String convertMapToString(Map<String, Boolean> map) {
        try{
            return map!=null?objectMapper.writeValueAsString(map):"";
        }catch (JsonProcessingException e) {
            throw new EsignetException(INVALID_PERMITTED_SCOPE);
        }
    }

    public Map<String, Boolean> convertStringToMap(String value) {
        try{
            return StringUtils.isNotBlank(value) ? objectMapper.readValue(value,Map.class): Collections.emptyMap();
        } catch (JsonProcessingException e) {
            throw new EsignetException(INVALID_PERMITTED_SCOPE);
        }
    }
}
