package io.mosip.esignet.core.validator;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Locale;
import java.util.Set;

public class ClientNameLangValidator implements ConstraintValidator<ClientNameLang, String> {

    @Override
    public void initialize(ClientNameLang constraintAnnotation) {
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        Locale[] availableLocales = Locale.getAvailableLocales();
        boolean isValid = false;

        for (Locale locale : availableLocales) {
            if (value.equals(locale.getISO3Language())) {
                isValid = true;
                break;
            }
        }
        return isValid;
    }
}