package io.mosip.esignet.core.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.mosip.esignet.core.exception.EsignetException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ClientAdditionalConfigValidator implements
        ConstraintValidator<ClientAdditionalConfig, Map<String, Object>> {

    private String schemaUrl = "classpath:additional_config_request_schema.json";

    private volatile JsonSchema cachedSchema;

    private ObjectMapper objectMapper = new ObjectMapper();

    private ResourceLoader resourceLoader = new DefaultResourceLoader();

    @Override
    public void initialize(ClientAdditionalConfig constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(Map<String, Object> additionalConfig, ConstraintValidatorContext context) {
        if (additionalConfig == null) {
            return false;
        }
        Set<ValidationMessage> errors = null;
        try {
            JsonNode jsonNode = objectMapper.valueToTree(additionalConfig);
            errors = getCachedSchema().validate(jsonNode);
            if (errors.isEmpty()) return true;
        } catch (Exception e) {
            log.error("Error validating additional_config schema: ", e);
        }
        log.error("Validation failed for additional_config ---> {}", errors);
        return false;
    }

    private JsonSchema getCachedSchema() throws EsignetException {
        if(cachedSchema!=null ) return cachedSchema;
        synchronized (this) {
            if (cachedSchema == null) {
                InputStream schemaResponse = getResource(schemaUrl);
                JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                cachedSchema = jsonSchemaFactory.getSchema(schemaResponse);
            }
        }
        return cachedSchema;
    }

    private InputStream getResource(String url) {
        try {
            Resource resource = resourceLoader.getResource(url);
            return resource.getInputStream();
        } catch (IOException e) {
            log.error("Failed to parse data: {}", url, e);
        }
        throw new EsignetException("invalid_configuration");
    }
}
