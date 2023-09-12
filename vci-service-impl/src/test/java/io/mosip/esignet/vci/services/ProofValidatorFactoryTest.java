/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.vci.services;

import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.vci.pop.JwtProofValidator;
import io.mosip.esignet.vci.pop.ProofValidatorFactory;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

@RunWith(MockitoJUnitRunner.class)
public class ProofValidatorFactoryTest {

    @InjectMocks
    ProofValidatorFactory proofValidatorFactory;

    @Test
    public void testGetValidator_thenPass() {
        ReflectionTestUtils.setField(proofValidatorFactory, "proofValidators", List.of(new JwtProofValidator()));
        Assert.assertNotNull(proofValidatorFactory.getProofValidator("jwt"));
    }

    @Test
    public void testGetValidator_withInvalidInput_thenFail() {
        ReflectionTestUtils.setField(proofValidatorFactory, "proofValidators", List.of(new JwtProofValidator()));
        try {
            proofValidatorFactory.getProofValidator("cwt");
            Assert.fail();
        }catch (EsignetException e) {
            Assert.assertEquals(ErrorConstants.UNSUPPORTED_PROOF_TYPE, e.getErrorCode());
        }
    }
}
