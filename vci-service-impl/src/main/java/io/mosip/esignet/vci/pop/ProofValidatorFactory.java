package io.mosip.esignet.vci.pop;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.EsignetException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class ProofValidatorFactory {

    @Autowired
    private List<ProofValidator> proofValidators;

    public ProofValidator getProofValidator(String proofType) {
        Optional<ProofValidator> result = proofValidators.stream()
                .filter(v -> v.getProofType().equals(proofType))
                .findFirst();

        if(result.isPresent())
            return result.get();

        throw new EsignetException(ErrorConstants.UNSUPPORTED_PROOF_TYPE);
    }

}
