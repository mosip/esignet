package utils;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import models.Vid;

public class VIDManager {

	private static final BlockingQueue<Vid> availableVIDs = new LinkedBlockingQueue<>();

	static {
		availableVIDs.add(new Vid(EsignetConfigManager.getproperty("vid")));
	}

	public static Vid acquireVID() throws InterruptedException {
		return availableVIDs.take();
	}

	public static void releaseVID(Vid vid) {
		availableVIDs.offer(vid);
	}

	public static Vid acquireVIDWithTimeout(long timeoutSeconds) throws InterruptedException {
		return availableVIDs.poll(timeoutSeconds, TimeUnit.SECONDS);
	}
}
