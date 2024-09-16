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
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Set;


@Slf4j
public class ClaimSchemaValidator implements ConstraintValidator<ClaimSchema, ClaimsV2> {


    @Value("${mosip.esignet.json.validation.schema.url}")
    private String schemaUrl;

    private volatile JsonSchema cachedSchema;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;


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
                    ResponseEntity<String> schemaResponse = restTemplate.getForEntity(schemaUrl, String.class);
                    if (!schemaResponse.getStatusCode().is2xxSuccessful() || schemaResponse.getBody() == null) {
                        throw new EsignetException("Failed to retrieve schema");
                    }
                    String schemaContent = schemaResponse.getBody();
                    JsonSchemaFactory jsonSchemaFactory = JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012);
                    cachedSchema = jsonSchemaFactory.getSchema(new ByteArrayInputStream(schemaContent.getBytes(StandardCharsets.UTF_8)));
                }
            }
        }
        return cachedSchema;
    }
}

