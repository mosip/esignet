/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.vci.pop;

import io.mosip.esignet.core.dto.vci.CredentialProof;


public interface ProofValidator {

    String getProofType();

    boolean validate(String clientId, String cNonce, CredentialProof credentialProof);

    String getKeyMaterial(CredentialProof credentialProof);
}
