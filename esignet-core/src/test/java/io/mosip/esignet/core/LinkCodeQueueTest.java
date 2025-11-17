/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import io.mosip.esignet.core.util.LinkCodeQueue;

@ExtendWith(MockitoExtension.class)
public class LinkCodeQueueTest {

    LinkCodeQueue linkCodeQueue = new LinkCodeQueue(1);

    @Test
    public void test_addLinkCode_firstEntry() {
        String linkCode = linkCodeQueue.addLinkCode("10");
        Assertions.assertNull(linkCode);
    }

    @Test
    public void test_addLinkCode_withTwoEntries() {
        linkCodeQueue.addLinkCode("10");
        String linkCode = linkCodeQueue.addLinkCode("20");
        Assertions.assertEquals(linkCode, "10");
    }

}
