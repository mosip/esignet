package io.mosip.esignet.vci.pop;

import io.mosip.esignet.core.dto.vci.CredentialProof;

public interface ProofValidator {

    String getProofType();

    boolean validate(CredentialProof credentialProof);

    String getKeyMaterial(CredentialProof credentialProof);
}
