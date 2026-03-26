package utils;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;

import com.browserstack.local.Local;
import org.apache.log4j.Logger;

public class BrowserStackLocalManager {

	private static final Logger LOGGER = Logger.getLogger(BrowserStackLocalManager.class);
	private static Local bsLocal;

	public synchronized static void start() throws Exception {

		if (bsLocal != null && bsLocal.isRunning()) {
			return;
		}

		bsLocal = new Local();
		String accessKey = StringUtils.isBlank(EsignetConfigManager.getproperty("browserstack_access_key"))
				? BaseTestUtil.getKeyValueFromYaml("/browserstack.yml", "accessKey")
				: EsignetConfigManager.getproperty("browserstack_access_key");

		Map<String, String> args = new HashMap<>();
		args.put("key", accessKey);
		args.put("forceLocal", "true");
		args.put("verbose", "true");
		args.put("logFile", "bs_local.log");

		bsLocal.start(args);

		// Wait till tunnel is fully running
		int retries = 0;
		while (!bsLocal.isRunning() && retries < 15) {
			Thread.sleep(1000);
			retries++;
		}

		if (!bsLocal.isRunning()) {
			throw new RuntimeException("BrowserStack Local failed to start");
		}

		LOGGER.info("BrowserStack Local started successfully");
	}

	public synchronized static void stop() throws Exception {
		if (bsLocal != null && bsLocal.isRunning()) {
			bsLocal.stop();
			LOGGER.info("BrowserStack Local stopped");
		}
	}
}
