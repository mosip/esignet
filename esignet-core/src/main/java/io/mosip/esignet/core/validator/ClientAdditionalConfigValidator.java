package io.mosip.esignet.core.validator;

import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Map;
import java.util.Set;

@Component
public class ClientAdditionalConfigValidator implements
        ConstraintValidator<ClientAdditionalConfigConstraint, Map<String, Object>> {

    private static final Set<String> VALID_RESPONSE_TYPES = Set.of("JWS", "JWE");

    @Override
    public void initialize(ClientAdditionalConfigConstraint constraintAnnotation) {
        ConstraintValidator.super.initialize(constraintAnnotation);
    }

    @Override
    public boolean isValid(Map<String, Object> value, ConstraintValidatorContext context) {
        if (value == null) {
            return false;
        }

        Object responseType = value.get("userinfo_response_type");
        if (!(responseType instanceof String) ||
                !VALID_RESPONSE_TYPES.contains(responseType.toString())) {
            return false;
        }

        Object purpose = value.get("purpose");
        if (!(purpose instanceof Map) || !isPurposeValid((Map<?, ?>) purpose)) {
            return false;
        }

        if(!(value.get("signup_banner_required") instanceof Boolean)
        || !(value.get("forgot_pwd_link_required") instanceof Boolean)
        || !(value.get("consent_expire_in_days") instanceof Number)) {
            return false;
        }

        return true;
    }

    private boolean isPurposeValid(Map<?, ?> purpose) {
        return purpose.get("type") instanceof String
                && purpose.get("title") instanceof String
                && purpose.get("subTitle") instanceof String;
    }
}
