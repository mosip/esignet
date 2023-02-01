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
