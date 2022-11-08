/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp;

import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.util.IdentityProviderUtil;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class IdentityProviderUtilTest {


    @Test
    public void validateRedirectURIPositiveTest() throws IdPException {
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/**"),
                "https://api.dev.mosip.net/home/test");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test"),
                "https://api.dev.mosip.net/home/test");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test?"),
                "https://api.dev.mosip.net/home/test1");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/*"),
                "https://api.dev.mosip.net/home/werrrwqfdsfg5fgs34sdffggdfgsdfg?state=reefdf");
        IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/t*"),
                "https://api.dev.mosip.net/home/testament?rr=rrr");
    }

    @Test
    public void validateRedirectURINegativeTest() {
        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test1"),
                    "https://api.dev.mosip.net/home/test");
            Assert.fail();
        } catch (IdPException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/test1"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (IdPException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home**"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (IdPException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/*"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (IdPException e) {}

        try {
            IdentityProviderUtil.validateRedirectURI(Arrays.asList("https://api.dev.mosip.net/home/t*"),
                    "https://api.dev.mosip.net/home/TEST1");
            Assert.fail();
        } catch (IdPException e) {}
    }
}
