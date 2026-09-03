package utils;

import java.util.HashMap;
import java.util.Map;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chromium.ChromiumDriver;

public class NetworkUtil {

	private NetworkUtil() {
	}

	public static void setNetworkOffline(WebDriver driver, boolean offline) {
		if (!(driver instanceof ChromiumDriver)) {
			throw new UnsupportedOperationException(
					"Network condition emulation is only supported on Chromium-based drivers");
		}
		ChromiumDriver chromiumDriver = (ChromiumDriver) driver;

		chromiumDriver.executeCdpCommand("Network.enable", new HashMap<>());

		Map<String, Object> params = new HashMap<>();
		params.put("offline", offline);
		params.put("latency", 0);
		params.put("downloadThroughput", offline ? 0 : -1);
		params.put("uploadThroughput", offline ? 0 : -1);
		chromiumDriver.executeCdpCommand("Network.emulateNetworkConditions", params);
	}

	public static void setUserAgentOverride(WebDriver driver, String userAgent) {
		if (!(driver instanceof ChromiumDriver)) {
			throw new UnsupportedOperationException(
					"User-Agent override is only supported on Chromium-based drivers");
		}
		ChromiumDriver chromiumDriver = (ChromiumDriver) driver;

		Map<String, Object> params = new HashMap<>();
		params.put("userAgent", userAgent);
		chromiumDriver.executeCdpCommand("Network.setUserAgentOverride", params);
	}
}
