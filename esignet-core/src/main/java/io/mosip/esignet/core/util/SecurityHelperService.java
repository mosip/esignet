package io.mosip.esignet.core.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SecurityHelperService {

    public String generateSecureRandomString(int length) {
        //TODO
        return IdentityProviderUtil.generateRandomAlphaNumeric(length);
    }
}
