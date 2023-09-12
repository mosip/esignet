/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;

import io.mosip.esignet.core.util.SecurityHelperService;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.util.Assert;

@RunWith(MockitoJUnitRunner.class)
public class SecurityHelperServiceTest {

    SecurityHelperService securityHelperService = new SecurityHelperService();


    @Test
    public void test_generateSecureRandomString_thenPass() {
        Assert.notNull(securityHelperService.generateSecureRandomString(20));
    }
}
