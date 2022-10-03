/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.idp.core.dto.AuthenticationFactor;
import io.mosip.idp.core.exception.IdPException;
import lombok.extern.slf4j.Slf4j;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.util.*;

@Component
@Slf4j
public class AuthenticationContextClassRefUtil {

    private static final String AMR_KEY = "amr";
    private static final String ACR_AMR = "acr_amr";

    @Value("${mosip.idp.amr-acr-mapping-file-url:}")
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
    private Map<String, List<AuthenticationFactor>> getAllAMRs()  throws IdPException {
        try {
            ObjectNode objectNode = objectMapper.readValue(getMappingJson(), new TypeReference<ObjectNode>(){});
            return objectMapper.convertValue(objectNode.get(AMR_KEY),
                    new TypeReference<Map<String, List<AuthenticationFactor>>>(){});
        } catch (IOException e) {
            log.error("Failed to load / parse amr mappings", e);
            throw new IdPException(ErrorConstants.ACR_AMR_MAPPING_NOT_FOUND);
        }
    }

    @Cacheable(value = "acr_amr", key = "acr_amr", unless = "#result != null")
    private Map<String, List<String>> getAllACR_AMR_Mapping()  throws IdPException {
        try {
            ObjectNode objectNode = objectMapper.readValue(getMappingJson(), new TypeReference<ObjectNode>(){});
            return objectMapper.convertValue(objectNode.get(ACR_AMR),
                    new TypeReference<Map<String, List<String>>>(){});

        } catch (IOException e) {
            log.error("Failed to load / parse acr_amr mappings", e);
            throw new IdPException(ErrorConstants.ACR_AMR_MAPPING_NOT_FOUND);
        }
    }

    public Set<String> getSupportedACRValues() throws IdPException {
        return getAllACR_AMR_Mapping().keySet();
    }

    public List<List<AuthenticationFactor>> getAuthFactors(String[] authContextClassRefs) throws IdPException {
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

}
