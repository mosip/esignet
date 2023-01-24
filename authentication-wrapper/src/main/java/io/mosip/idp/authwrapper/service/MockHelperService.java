package io.mosip.idp.authwrapper.service;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.KycAuthException;
import io.mosip.esignet.core.spi.KeyBindingValidator;
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
        ValidateBindingRequest validateBindingRequest = new ValidateBindingRequest();
        validateBindingRequest.setIndividualId(individualId);
        validateBindingRequest.setTransactionId(transactionId);
        validateBindingRequest.setChallenges(challengeList);

        ValidateBindingResponse validateBindingResponse = keyBindingValidator.validateBinding(validateBindingRequest);
        if(validateBindingResponse == null || transactionId.equals(validateBindingResponse.getTransactionId())) {
            log.error("Failed validate binding");
            throw new KycAuthException(AUTH_FAILED);
        }
    }
}
