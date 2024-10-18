package io.mosip.esignet.core.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.mosip.esignet.api.dto.claim.ClaimsV2;
import io.mosip.esignet.core.exception.EsignetException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.web.client.RestTemplate;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.stream.Collectors;


@Slf4j
public class ClaimSchemaValidator implements ConstraintValidator<ClaimSchema, ClaimsV2> {


    @Value("${mosip.esignet.json.validation.schema.url}")
    private String schemaUrl;

    private volatile JsonSchema cachedSchema;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceLoader resourceLoader;


    @Override
    public boolean isValid(ClaimsV2 claims, ConstraintValidatorContext context) {
        Set<ValidationMessage> errors = null;
        try {
            JsonNode jsonNode = objectMapper.valueToTree(claims);
            errors = getCachedSchema().validate(jsonNode);
            if(errors.isEmpty())return true;
        } catch (Exception e) {
            log.error("Error validating claims schema", e);
        }
        log.error("Validation failed for claims: {}", errors);
        return false;
    }

    private JsonSchema getCachedSchema() throws EsignetException {
        if (cachedSchema == null) {
            synchronized (this) {
                if (cachedSchema == null) {
                    String schemaResponse = getResource(schemaUrl);
                    JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                    cachedSchema = jsonSchemaFactory.getSchema(new ByteArrayInputStream(schemaResponse.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
        return cachedSchema;
    }

    private String getResource(String url) {
        Resource resource = resourceLoader.getResource(url);
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
            return reader.lines().collect(Collectors.joining("\n"));
        } catch (IOException e) {
            log.error("Failed to parse data: {}", url, e);
        }
        throw new EsignetException("invalid_configuration");
    }
}

