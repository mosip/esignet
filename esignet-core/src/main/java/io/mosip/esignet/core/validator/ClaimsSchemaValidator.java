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

import javax.annotation.PostConstruct;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import javax.validation.constraints.NotNull;
import java.io.*;
import java.util.Set;


@Slf4j
public class ClaimsSchemaValidator implements ConstraintValidator<ClaimsSchema, ClaimsV2> {


    @Value("${mosip.esignet.claims.schema.url}")
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
    public boolean isValid(ClaimsV2 claims, ConstraintValidatorContext context) {
        Set<ValidationMessage> errors = null;
        try {
            JsonNode jsonNode = objectMapper.valueToTree(claims);
            errors = schema.validate(jsonNode);
            if(errors.isEmpty())return true;
        } catch (Exception e) {
            log.error("Error validating claims schema", e);
        }
        log.error("Validation failed for claims: {}", errors);
        return false;
    }

    private InputStream getResource(String url) {
        try{
            Resource resource = resourceLoader.getResource(url);
            return resource.getInputStream();
        }catch (IOException e){
            log.error("Failed to parse data: {}", url, e);
        }
        throw new EsignetException("invalid_configuration");
    }
}

