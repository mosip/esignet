package io.mosip.esignet.vci.services;

import com.apicatalog.jsonld.JsonLdError;
import com.apicatalog.jsonld.document.JsonDocument;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.EsignetException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.StringReader;

@Slf4j
@Service
public class VCIUtilService {

    @Autowired
    private RestTemplate restTemplate;

    protected JsonDocument readJsonLDDocument(String fileUrl) {
        try {
            log.debug("Downloading ContextJsonLd file : {}", fileUrl);
            String vcContextStr = restTemplate.getForObject(fileUrl, String.class);
            JsonDocument jsonDocument = JsonDocument.of(new StringReader(vcContextStr));
            return jsonDocument;
        } catch (JsonLdError e) {
            log.error("Failed to load VC context json LD document {}", fileUrl, e);
            throw new EsignetException(ErrorConstants.JSONLD_READ_FAILED);
        }
    }
}
