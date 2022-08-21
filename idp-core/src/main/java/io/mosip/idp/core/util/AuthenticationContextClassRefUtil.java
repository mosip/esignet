package io.mosip.idp.core.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.mosip.idp.core.dto.AuthenticationFactor;
import io.mosip.idp.core.exception.IdPException;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.*;

@Component
public class AuthenticationContextClassRefUtil {

    private static final Logger logger = LoggerFactory.getLogger(AuthenticationContextClassRefUtil.class);
    private static final String ACR_KEY = "acr_values";
    private static final String AMR_KEY = "amr_values";
    private static final String AMR_ACR = "acr_amr";

    @Value("classpath:amr_acr_mapping.json")
    Resource mappingFile;

    @Autowired
    ObjectMapper objectMapper;

    @Cacheable(value = "acr_amr", key = "acr", unless = "#result != null")
    private Map<String, String> getAllACRs() throws IdPException {
        try {
            ObjectNode objectNode = objectMapper.readValue(mappingFile.getFile(), new TypeReference<ObjectNode>(){});
            return objectMapper.convertValue(objectNode.get(ACR_KEY),
                    new TypeReference<Map<String, String>>(){});
        } catch (IOException e) {
            logger.error("Failed to load / parse acr mappings", e);
            throw new IdPException(ErrorConstants.ACR_AMR_MAPPING_NOT_FOUND);
        }
    }

    @Cacheable(value = "acr_amr", key = "amr", unless = "#result != null")
    private Map<String, List<AuthenticationFactor>> getAllAMRs()  throws IdPException {
        try {
            ObjectNode objectNode = objectMapper.readValue(mappingFile.getFile(), new TypeReference<ObjectNode>(){});
            return objectMapper.convertValue(objectNode.get(AMR_KEY),
                    new TypeReference<Map<String, List<AuthenticationFactor>>>(){});
        } catch (IOException e) {
            logger.error("Failed to load / parse amr mappings", e);
            throw new IdPException(ErrorConstants.ACR_AMR_MAPPING_NOT_FOUND);
        }
    }

    @Cacheable(value = "acr_amr", key = "acr_amr", unless = "#result != null")
    private Map<String, List<String>> getAllACR_AMR_Mapping()  throws IdPException {
        try {
            ObjectNode objectNode = objectMapper.readValue(mappingFile.getFile(), new TypeReference<ObjectNode>(){});
            return objectMapper.convertValue(objectNode.get(AMR_KEY),
                    new TypeReference<Map<String, List<String>>>(){});

        } catch (IOException e) {
            logger.error("Failed to load / parse acr_amr mappings", e);
            throw new IdPException(ErrorConstants.ACR_AMR_MAPPING_NOT_FOUND);
        }
    }

    public Set<String> getSupportedACRValues() throws IdPException {
        return getAllACRs().keySet();
    }

    public List<List<AuthenticationFactor>> getAuthFactors(String[] authContextClassRefs) throws IdPException {
        Map<String, String> acr_mappings = getAllACRs();
        Map<String, List<AuthenticationFactor>> amr_mappings = getAllAMRs();
        Map<String, List<String>> acr_amr_mappings = getAllACR_AMR_Mapping();

        List<List<AuthenticationFactor>> result = new ArrayList<>();
        for(String acr : authContextClassRefs) {
            String acr_key = acr_mappings.getOrDefault(acr, Strings.EMPTY);
            List<String> authFactorNames = acr_amr_mappings.getOrDefault(acr_key, Collections.emptyList());
            for(String authFactorName : authFactorNames) {
                if(amr_mappings.containsKey(authFactorName))
                    result.add(amr_mappings.get(authFactorName));
            }
        }
        return result;
    }

}
