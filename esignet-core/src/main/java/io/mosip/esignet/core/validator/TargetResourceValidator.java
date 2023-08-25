package io.mosip.esignet.core.validator;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.List;

@Component
public class TargetResourceValidator implements ConstraintValidator<TargetResource, String> {

    @Value("#{${mosip.esignet.supported.resources}}")
    private List<String> supportedResources;

    @Override
    public boolean isValid(String resource, ConstraintValidatorContext constraintValidatorContext) {
        return StringUtils.isEmpty(resource) || supportedResources.contains(resource);
    }
}
