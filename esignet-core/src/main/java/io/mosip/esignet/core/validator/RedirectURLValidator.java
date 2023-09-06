package io.mosip.esignet.core.validator;

import org.apache.commons.validator.routines.UrlValidator;
import org.hibernate.validator.constraints.URL;
import org.springframework.stereotype.Component;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

import static org.apache.commons.validator.routines.UrlValidator.ALLOW_ALL_SCHEMES;
import static org.apache.commons.validator.routines.UrlValidator.ALLOW_LOCAL_URLS;

@Component
public class RedirectURLValidator implements ConstraintValidator<RedirectURL, String> {

    private final UrlValidator urlValidator = new UrlValidator(ALLOW_ALL_SCHEMES+ALLOW_LOCAL_URLS);

    @Override
    public boolean isValid(String redirectUrl, ConstraintValidatorContext constraintValidatorContext) {
        return urlValidator.isValid(redirectUrl);
    }


}
