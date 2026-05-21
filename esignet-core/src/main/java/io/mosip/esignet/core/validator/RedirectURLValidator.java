package io.mosip.esignet.core.validator;

import org.apache.commons.validator.routines.RegexValidator;
import org.apache.commons.validator.routines.UrlValidator;
import static org.apache.commons.validator.routines.UrlValidator.ALLOW_ALL_SCHEMES;
import static org.apache.commons.validator.routines.UrlValidator.ALLOW_LOCAL_URLS;
import org.springframework.stereotype.Component;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * @class RedirectURLValidator uses a customised Apache {@link UrlValidator}
 * to check syntactical validity of redirect URLs, allowing any scheme and local URLs,
 * but restricting the authority part to valid IPv4/IPv6 addresses, localhost, or domain
 * names with any TLD.
 */
@Component
public class RedirectURLValidator implements ConstraintValidator<RedirectURL, String> {

    // IPv6 address in brackets – strict RFC 4291 alternation covering all
    // compressed (::) and full (8-group) forms; rejects bare garbage like [::::]
    private static final String IPV6_REGEX =
        "\\[(?:" +
        "(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}" +              // full 8-group, no ::
        "|(?:[0-9a-fA-F]{1,4}:){1,7}:" +                          // trailing ::
        "|(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}" +          // 6+1 around ::
        "|(?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2}" + // 5+1-2
        "|(?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3}" + // 4+1-3
        "|(?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4}" + // 3+1-4
        "|(?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5}" + // 2+1-5
        "|[0-9a-fA-F]{1,4}:(?::[0-9a-fA-F]{1,4}){1,6}" +          // 1+1-6
        "|:(?::[0-9a-fA-F]{1,4}){1,7}" +                          // leading ::
        "|::" +                                                   // all-zeros
        ")\\]";
    // IPv4 address
    private static final String IPV4_REGEX = "((25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])\\.){3}(25[0-5]|2[0-4][0-9]|1[0-9]{2}|[1-9]?[0-9])";
    // localhost
    private static final String LOCALHOST_REGEX = "localhost";
    // Domain name with any TLD (at least two letters).
    // Each label must start and end with an alphanumeric character (RFC 1123);
    // hyphens are only permitted in the interior of a label.
    private static final String DOMAIN_REGEX = "([a-zA-Z0-9]([a-zA-Z0-9-]*[a-zA-Z0-9])?\\.)+[a-zA-Z]{2,}";
    // Optional port — restricted to the valid TCP/UDP range 0–65535
    private static final String PORT_REGEX = "(:(6553[0-5]|655[0-2]\\d|65[0-4]\\d{2}|6[0-4]\\d{3}|[1-5]\\d{4}|\\d{1,4}))?";

    // The resulting regular expression validates the authority part of the URL (host and optional port) while allowing any TLD in the domain name.
    private static final String AUTHORITY_PART_RX = "^(" + IPV6_REGEX + "|" + IPV4_REGEX + "|" + LOCALHOST_REGEX + "|" + DOMAIN_REGEX + ")" + PORT_REGEX + "$";

    private final UrlValidator urlValidator = new UrlValidator(new RegexValidator(AUTHORITY_PART_RX), ALLOW_ALL_SCHEMES+ALLOW_LOCAL_URLS);

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
