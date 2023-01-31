package io.mosip.idp.authwrapper.service;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.BindingAuthResult;
import io.mosip.esignet.api.exception.KycAuthException;
import io.mosip.esignet.api.spi.KeyBindingValidator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

import static io.mosip.esignet.core.constants.ErrorConstants.AUTH_FAILED;

@Component
@Slf4j
public class MockHelperService {

    @Autowired
    private KeyBindingValidator keyBindingValidator;


    public void validateKeyBoundAuth(String transactionId, String individualId, List<AuthChallenge> challengeList)
            throws KycAuthException {

        BindingAuthResult bindingAuthResult = keyBindingValidator.validateBindingAuth(transactionId,individualId, challengeList);
        if(bindingAuthResult == null || transactionId.equals(bindingAuthResult.getTransactionId())) {
            log.error("Failed validate binding");
            throw new KycAuthException(AUTH_FAILED);
        }
    }
}
