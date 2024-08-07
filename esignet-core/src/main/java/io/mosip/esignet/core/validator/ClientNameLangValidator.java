package io.mosip.esignet.core.validator;

import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import java.util.Arrays;
import java.util.Locale;

public class ClientNameLangValidator implements ConstraintValidator<ClientNameLang, String> {

    private static final Locale[] availableLocales = Locale.getAvailableLocales();

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        boolean isValid = Arrays.stream(availableLocales)
                .anyMatch(locale -> value.equals(locale.getISO3Language()));
        return isValid;
    }
}