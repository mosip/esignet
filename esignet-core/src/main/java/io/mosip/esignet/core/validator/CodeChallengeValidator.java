package io.mosip.esignet.core.validator;

import io.mosip.esignet.core.dto.OAuthDetailRequestV2;
import org.springframework.util.StringUtils;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;

public class CodeChallengeValidator implements ConstraintValidator<CodeChallenge, OAuthDetailRequestV2> {

    @Override
    public boolean isValid(OAuthDetailRequestV2 value, ConstraintValidatorContext context) {
        String codeChallenge = value.getCodeChallenge();
        String codeChallengeMethod = value.getCodeChallengeMethod();
        if((StringUtils.isEmpty(codeChallenge) && StringUtils.isEmpty(codeChallengeMethod))
        || (StringUtils.hasText(codeChallenge) && StringUtils.hasText(codeChallengeMethod))) {
            return true;
        }
        return false;
    }
}
