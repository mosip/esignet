package io.mosip.esignet.api.spi;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.BindingAuthResult;
import io.mosip.esignet.api.exception.KycAuthException;

import java.util.List;

public interface KeyBindingValidator {

    BindingAuthResult validateBindingAuth(String transactionId, String individualId,
                                          List<AuthChallenge> challengeList) throws KycAuthException;
}
