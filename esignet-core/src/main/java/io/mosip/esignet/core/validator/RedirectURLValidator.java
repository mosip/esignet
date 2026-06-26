package io.mosip.esignet.core.validator;

import java.util.List;

import org.apache.commons.validator.routines.DomainValidator;
import org.apache.commons.validator.routines.UrlValidator;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * @class RedirectURLValidator uses a customised Apache {@link UrlValidator}
 * to check syntactical validity of redirect URLs, allowing any scheme and local URLs,
 * but restricting the authority part to valid IPv4/IPv6 addresses, localhost, or domain
 * names with an extendable list of non-public TLDs if necessary.
 */
@Component
public class RedirectURLValidator implements ConstraintValidator<RedirectURL, String> {

    private final UrlValidator urlValidator;

    public RedirectURLValidator(@Value("${mosip.esignet.allowed.non-public.tlds}") String[] allowedNonPublicTLDs) {
        this.urlValidator = new UrlValidator(null, null, UrlValidator.ALLOW_ALL_SCHEMES | UrlValidator.ALLOW_LOCAL_URLS,
                DomainValidator.getInstance(true, List.of(new DomainValidator.Item(DomainValidator.ArrayType.GENERIC_PLUS, allowedNonPublicTLDs))));
    }

    /**
     * Validates redirect URLs while allowing private/non-IANA TLDs.
     *
     * @param redirectUrl redirect URL to validate
     * @param constraintValidatorContext validation context, unused
     * @return true if the redirect URL is valid
     */
    @Override
    public boolean isValid(final String redirectUrl, final ConstraintValidatorContext constraintValidatorContext) {
        return this.urlValidator.isValid(redirectUrl);
    }

}
