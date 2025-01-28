package io.mosip.esignet.core.validator;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import com.networknt.schema.ValidationMessage;
import io.mosip.esignet.core.exception.EsignetException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;

import javax.annotation.PostConstruct;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;
import java.util.Set;

@Slf4j
public class ClientAdditionalConfigValidator implements
        ConstraintValidator<ClientAdditionalConfig, Map<String, Object>> {

    @Value("${mosip.esignet.additional-config.schema.url}")
    private String schemaUrl;

    private JsonSchema schema;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ResourceLoader resourceLoader;

    @PostConstruct
    public void initSchema() {
        InputStream schemaResponse = getResource(schemaUrl);
        JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
        schema = jsonSchemaFactory.getSchema(schemaResponse);
    }

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
            errors = schema.validate(jsonNode);
            if (errors.isEmpty()) return true;
        } catch (Exception e) {
            log.error("Error validating additional_config schema: ", e);
        }
        log.error("Validation failed for additional_config ---> {}", errors);
        return false;
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
