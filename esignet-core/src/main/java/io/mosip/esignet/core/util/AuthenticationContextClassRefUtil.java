/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.util;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.AuthenticationFactor;
import io.mosip.esignet.core.exception.EsignetException;
import lombok.extern.slf4j.Slf4j;

@Component
@Slf4j
public class AuthenticationContextClassRefUtil {

    private static final String AMR_KEY = "amr";
    private static final String ACR_AMR = "acr_amr";

    @Value("${mosip.esignet.amr-acr-mapping-file-url:}")
    private String mappingFileUrl;

    @Autowired
    ObjectMapper objectMapper;

    @Autowired
    RestTemplate restTemplate;

    private String mappingJson;

    private String getMappingJson() {
        if(StringUtils.isEmpty(mappingJson)) {
            log.info("Fetching AMR-ACR mapping json from : {}", mappingFileUrl);
            mappingJson = restTemplate.getForObject(mappingFileUrl, String.class);
        }
        return mappingJson;
    }

    @Cacheable(value = "acr_amr", key = "amr", unless = "#result != null")
    private Map<String, List<AuthenticationFactor>> getAllAMRs()  throws EsignetException {
        try {
            ObjectNode objectNode = objectMapper.readValue(getMappingJson(), new TypeReference<ObjectNode>(){});
            return objectMapper.convertValue(objectNode.get(AMR_KEY),
                    new TypeReference<Map<String, List<AuthenticationFactor>>>(){});
        } catch (IOException e) {
            log.error("Failed to load / parse amr mappings", e);
            throw new EsignetException(ErrorConstants.ACR_AMR_MAPPING_NOT_FOUND);
        }
    }

    @Cacheable(value = "acr_amr", key = "acr_amr", unless = "#result != null")
    private Map<String, List<String>> getAllACR_AMR_Mapping()  throws EsignetException {
        try {
            ObjectNode objectNode = objectMapper.readValue(getMappingJson(), new TypeReference<ObjectNode>(){});
            return objectMapper.convertValue(objectNode.get(ACR_AMR),
                    new TypeReference<Map<String, List<String>>>(){});

        } catch (IOException e) {
            log.error("Failed to load / parse acr_amr mappings", e);
            throw new EsignetException(ErrorConstants.ACR_AMR_MAPPING_NOT_FOUND);
        }
    }

    public Set<String> getSupportedACRValues() throws EsignetException {
        return getAllACR_AMR_Mapping().keySet();
    }

    public List<List<AuthenticationFactor>> getAuthFactors(String[] authContextClassRefs) throws EsignetException {
        Map<String, List<AuthenticationFactor>> amr_mappings = getAllAMRs();
        Map<String, List<String>> acr_amr_mappings = getAllACR_AMR_Mapping();

        List<List<AuthenticationFactor>> result = new ArrayList<>();
        for(String acr : authContextClassRefs) {
            List<String> authFactorNames = acr_amr_mappings.getOrDefault(acr, Collections.emptyList());
            for(String authFactorName : authFactorNames) {
                if(amr_mappings.containsKey(authFactorName))
                    result.add(amr_mappings.get(authFactorName));
            }
        }
        return result;
    }

    public List<String> getACRs(Set<List<String>> authFactorTypesSet) {
        if(CollectionUtils.isEmpty(authFactorTypesSet))
            return Collections.emptyList();

        List<String> amrs = new ArrayList<>();
        for(Map.Entry<String, List<AuthenticationFactor>> entry : getAllAMRs().entrySet()) {
            if(authFactorTypesSet.stream().anyMatch(authFactorTypes -> authFactorTypes.containsAll(entry.getValue().stream()
                    .map(AuthenticationFactor::getType)
                    .collect(Collectors.toList())))) {
                amrs.add(entry.getKey());
            }
        }

        List<String> acrs = new ArrayList<>();
        for(Map.Entry<String, List<String>> entry : getAllACR_AMR_Mapping().entrySet()) {
            if(entry.getValue().stream().allMatch(amr -> amrs.contains(amr))) {
                acrs.add(entry.getKey());
            }
        }
        return acrs;
    }

}
