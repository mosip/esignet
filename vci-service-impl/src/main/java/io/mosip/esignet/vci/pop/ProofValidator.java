package io.mosip.esignet.vci.pop;

import io.mosip.esignet.core.dto.vci.CredentialProof;

public interface ProofValidator {

    String getProofType();

    boolean validate(String clientId, String cNonce, CredentialProof credentialProof);

    String getKeyMaterial(CredentialProof credentialProof);
}
