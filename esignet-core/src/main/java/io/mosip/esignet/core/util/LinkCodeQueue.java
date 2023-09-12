/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.util;

import java.util.concurrent.ArrayBlockingQueue;

public class LinkCodeQueue extends ArrayBlockingQueue<String> {

    private int queueSize;

    public LinkCodeQueue(int capacity) {
        super(capacity);
        this.queueSize = capacity;
    }


    synchronized public String addLinkCode(String linkCode) {
        String oldestLinkCode = null;
        if (super.size() == this.queueSize) {
            oldestLinkCode = super.remove();
        }
        super.add(linkCode);
        return oldestLinkCode;
    }
}
