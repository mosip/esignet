/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.api.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.api.dto.claim.AssuranceProcess;
import io.mosip.esignet.api.dto.claim.Evidence;
import io.mosip.esignet.api.dto.claim.FilterCriteria;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

@Slf4j
@Component
public class FilterCriteriaMatcher {

    private final SimpleDateFormat dateTimeFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'");

    @Autowired
    private ObjectMapper objectMapper;


    public boolean doMatch(Map<String, Object> verificationFilter, String key, String value) {
        if(verificationFilter.get(key) == null)
            return true;

        FilterCriteria filterCriteria = objectMapper.convertValue(verificationFilter.get(key), FilterCriteria.class);
        if(filterCriteria.getValue() != null)
            return filterCriteria.getValue().equals(value);
        if(filterCriteria.getValues() != null)
            return filterCriteria.getValues().contains(value);
        if(filterCriteria.getMax_age() != null && filterCriteria.getMax_age() > 0) {
            return doAgeMatch(value, filterCriteria.getMax_age());
        }
        return false;
    }

    public boolean doMatch(Map<String, Object> verificationFilter, AssuranceProcess assuranceProcess) {
        if(verificationFilter.get("assurance_process") == null)
            return true;

        if(assuranceProcess == null)
            return false;

        Map<String, Object> assuranceProcessFilter = (Map<String, Object>) verificationFilter.get("assurance_process");

        //TODO Add matcher for others
        return doMatch(assuranceProcessFilter, "policy", assuranceProcess.getPolicy()) &&
                doMatch(assuranceProcessFilter, "procedure", assuranceProcess.getProcedure());
    }

    public boolean doMatch(Map<String, Object> verificationFilter, List<Evidence> evidences) {
        if(verificationFilter.get("evidence") == null)
            return true;

        if(evidences == null || evidences.isEmpty())
            return false;

        //TODO
        return false;
    }

    private boolean doAgeMatch(String value, Integer maxAge) {
        if(value == null || value.isEmpty())
            return false;
        try {
            dateTimeFormat.setTimeZone(TimeZone.getTimeZone("UTC"));
            Date date = dateTimeFormat.parse(value);
            return ((System.currentTimeMillis() - date.getTime())/1000) < maxAge;
        } catch (ParseException e) {
            log.error("Failed to parse the given date-time : {}", value, e);
        }
        return false;
    }
}
