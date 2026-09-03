package utils;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.devtools.DevTools;
import org.openqa.selenium.devtools.HasDevTools;
import org.openqa.selenium.devtools.v134.network.Network;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.logging.LogType;
import org.openqa.selenium.logging.LoggingPreferences;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.yaml.snakeyaml.Yaml;

import io.cucumber.java.Scenario;
import io.github.bonigarcia.wdm.WebDriverManager;

public class BaseTestUtil {
	private static final Logger LOGGER = Logger.getLogger(BaseTestUtil.class.getName());
	private static final ThreadLocal<String> scenarioBrowserThreadLocal = new ThreadLocal<>();
	private static final ThreadLocal<String> threadLocalLanguage = new ThreadLocal<>();

	public static URI getBrowserStackUrl() {
		String accessKey = StringUtils.isBlank(EsignetConfigManager.getproperty("browserstack_access_key"))
				? getKeyValueFromYaml("/browserstack.yml", "accessKey")
				: EsignetConfigManager.getproperty("browserstack_access_key");
		String userName = StringUtils.isBlank(EsignetConfigManager.getproperty("browserstack_username"))
				? getKeyValueFromYaml("/browserstack.yml", "userName")
				: EsignetConfigManager.getproperty("browserstack_username");
		try {
			return new URI("https://" + userName + ":" + accessKey + "@hub-cloud.browserstack.com/wd/hub");
		} catch (URISyntaxException e) {
			throw new RuntimeException("Invalid BrowserStack URI", e);
		}
	}

	public static String getKeyValueFromYaml(String filePath, String key) {
		try (FileReader reader = new FileReader(System.getProperty("user.dir") + filePath)) {
			Yaml yaml = new Yaml();
			Object data = yaml.load(reader);
			if (data instanceof Map) {
				@SuppressWarnings("unchecked")
				Map<String, String> map = (Map<String, String>) data;
				return map.get(key);
			} else {
				throw new RuntimeException("Invalid YAML format, expected a map");
			}
		} catch (FileNotFoundException e) {
			throw new RuntimeException("YAML file not found: " + filePath, e);
		} catch (IOException e) {
			throw new RuntimeException("Error closing FileReader for: " + filePath, e);
		}
	}

	public static List<DesiredCapabilities> getAllCapabilities() {
		List<DesiredCapabilities> capsList = new ArrayList<>();
		String browsers = EsignetConfigManager.getProperty("browsers", EsignetConfigManager.getproperty("browserName"));

		for (String browser : browsers.split(",")) {
			DesiredCapabilities caps = new DesiredCapabilities();
			caps.setCapability("browserName", browser.trim());
			caps.setCapability("browserVersion", EsignetConfigManager.getproperty("browserVersion"));

			HashMap<String, Object> bsOptions = new HashMap<>();
			bsOptions.put("os", EsignetConfigManager.getproperty("browserStackOs"));
			bsOptions.put("osVersion", EsignetConfigManager.getproperty("osVersion"));
			bsOptions.put("projectName", "MOSIP ESignet UI Test");
			bsOptions.put("local", true);
			bsOptions.put("sessionName", "ESignet-" + Thread.currentThread().getId());
			caps.setCapability("bstack:options", bsOptions);

			if (browser.equalsIgnoreCase("chrome")) {
				ChromeOptions chromeOptions = new ChromeOptions();
				chromeOptions.addArguments("--use-fake-ui-for-media-stream");
				chromeOptions.addArguments("--use-fake-device-for-media-stream");
				applyBrowserLocale(chromeOptions, null, null);

				caps.setCapability(ChromeOptions.CAPABILITY, chromeOptions);
			}

			else if (browser.equalsIgnoreCase("firefox")) {
				FirefoxOptions firefoxOptions = new FirefoxOptions();
				firefoxOptions.addPreference("media.navigator.streams.fake", true);
				firefoxOptions.addPreference("media.navigator.permission.disabled", true);
				applyBrowserLocale(null, firefoxOptions, null);
				caps.setCapability(FirefoxOptions.FIREFOX_OPTIONS, firefoxOptions);
			}

			else if (browser.equalsIgnoreCase("edge")) {
				EdgeOptions edgeOptions = new EdgeOptions();
				edgeOptions.addArguments("--use-fake-ui-for-media-stream");
				edgeOptions.addArguments("--use-fake-device-for-media-stream");
				applyBrowserLocale(null, null, edgeOptions);
				caps.setCapability(EdgeOptions.CAPABILITY, edgeOptions);
			}

			else if (browser.equalsIgnoreCase("safari")) {
				LOGGER.info("Note: Safari does not support auto-allow camera via options.");
			}
			capsList.add(caps);
		}
		return capsList;
	}

	public static WebDriver getWebDriverInstance(String browserName) throws MalformedURLException {
		URL remoteUrl = getBrowserStackUrl().toURL();

		List<DesiredCapabilities> allCaps = getAllCapabilities();
		DesiredCapabilities caps = allCaps.stream()
				.filter(c -> c.getCapability("browserName").toString().equalsIgnoreCase(browserName)).findFirst()
				.orElse(allCaps.get(0));

		LOGGER.info("Running on BrowserStack with browser: " + browserName);
		LOGGER.info("Running with capabilities: " + caps.toString());
		return new RemoteWebDriver(remoteUrl, caps);
	}

	private static final Map<String, Object[]> MOBILE_DEVICE_PROFILES = new HashMap<>();
	static {
		MOBILE_DEVICE_PROFILES.put("pixel 5", new Object[] { 393, 851, 2.75,
				"Mozilla/5.0 (Linux; Android 11; Pixel 5) AppleWebKit/537.36 (KHTML, like Gecko) "
						+ "Chrome/126.0.0.0 Mobile Safari/537.36" });
	}

	private static Map<String, Object> buildMobileEmulationSettings(String deviceName) {
		Object[] profile = MOBILE_DEVICE_PROFILES.get(deviceName == null ? "" : deviceName.trim().toLowerCase());
		if (profile == null) {
			LOGGER.warning("No deviceMetrics profile for mobileDevice='" + deviceName
					+ "' - falling back to the Pixel 5 profile. Add an entry to MOBILE_DEVICE_PROFILES for it.");
			profile = MOBILE_DEVICE_PROFILES.get("pixel 5");
		}

		Map<String, Object> deviceMetrics = new HashMap<>();
		deviceMetrics.put("width", profile[0]);
		deviceMetrics.put("height", profile[1]);
		deviceMetrics.put("pixelRatio", profile[2]);

		Map<String, Object> mobileEmulation = new HashMap<>();
		mobileEmulation.put("deviceMetrics", deviceMetrics);
		mobileEmulation.put("userAgent", profile[3]);
		return mobileEmulation;
	}

	public static WebDriver getLocalWebDriverInstance(String browser, boolean isMobile, String deviceName)
			throws IOException {
		return getLocalWebDriverInstance(browser, isMobile, deviceName, false);
	}

	public static WebDriver getLocalWebDriverInstance(String browser, boolean isMobile, String deviceName,
			boolean ignoreUnhandledPrompts) throws IOException {
		browser = browser.toLowerCase();
		boolean isHeadless = Boolean.parseBoolean(EsignetConfigManager.getproperty("headless"));
		WebDriver driver;

		switch (browser) {
		case "chrome":
			if (System.getProperty("os.name").equalsIgnoreCase("Linux")
					&& "yes".equalsIgnoreCase(EsignetConfigManager.getDocker())) {
				String chromedriverPath = EsignetConfigManager.getProperty("chromeDriverPath", "/usr/bin/chromedriver");

				File driverFile = new File(chromedriverPath);

				if (!driverFile.exists() || !driverFile.canExecute()) {
					throw new RuntimeException("Invalid ChromeDriver path configured: " + chromedriverPath
							+ ". Ensure ChromeDriver exists and is executable.");
				}

				System.setProperty("webdriver.chrome.driver", chromedriverPath);

			} else {
				WebDriverManager.chromedriver().setup();
			}

			ChromeOptions chromeOptions = new ChromeOptions();
			LoggingPreferences logPrefs = new LoggingPreferences();
			logPrefs.enable(LogType.PERFORMANCE, Level.ALL);
			chromeOptions.setCapability("goog:loggingPrefs", logPrefs);

			String chromeBinary = firstExistingPath(EsignetConfigManager.getProperty("chromeBinaryPath", ""),
					System.getenv("CHROME_BIN"), "/usr/bin/google-chrome", "/usr/bin/google-chrome-stable",
					"/usr/bin/chromium", "/usr/bin/chromium-browser");
			if (chromeBinary != null) {
				chromeOptions.setBinary(chromeBinary);
				LOGGER.info("Using Chrome binary: " + chromeBinary);
			}

			chromeOptions.addArguments("--use-fake-ui-for-media-stream");
			chromeOptions.addArguments("--use-fake-device-for-media-stream");
			chromeOptions.addArguments("--enable-media-stream");
			applyBrowserLocale(chromeOptions, null, null);

			if (ignoreUnhandledPrompts) {
				chromeOptions.setUnhandledPromptBehaviour(org.openqa.selenium.UnexpectedAlertBehaviour.IGNORE);
			}

			Map<String, Object> prefs = new HashMap<>();
			Map<String, Object> profile = new HashMap<>();
			Map<String, Object> contentSettings = new HashMap<>();
			contentSettings.put("media_stream_camera", 1);
			profile.put("managed_default_content_settings", contentSettings);
			prefs.put("profile", profile);
			applyLocalSbiAccessFlags(chromeOptions, prefs);
			chromeOptions.setExperimentalOption("prefs", prefs);

			if (isMobile) {
				chromeOptions.setExperimentalOption("mobileEmulation", buildMobileEmulationSettings(deviceName));
			}

			if (isHeadless) {
				LOGGER.info("Running in headless mode");
				chromeOptions.addArguments("--headless=new");
				chromeOptions.addArguments("--disable-gpu");
				chromeOptions.addArguments("--window-size=1920x1080");
			}

			chromeOptions.addArguments("--no-sandbox");
			chromeOptions.addArguments("--disable-dev-shm-usage");

			chromeOptions.addArguments("--remote-debugging-port=0");

			LOGGER.info("Chrome args: " + chromeOptions);
			driver = new ChromeDriver(chromeOptions);
			break;

		case "firefox":
			WebDriverManager.firefoxdriver().setup();
			FirefoxOptions firefoxOptions = new FirefoxOptions();
			firefoxOptions.addPreference("media.navigator.streams.fake", true);
			firefoxOptions.addPreference("media.navigator.permission.disabled", true);
			applyBrowserLocale(null, firefoxOptions, null);

			if (isHeadless)
				firefoxOptions.addArguments("--headless");
			driver = new FirefoxDriver(firefoxOptions);
			break;

		case "edge":
			WebDriverManager.edgedriver().setup();
			EdgeOptions edgeOptions = new EdgeOptions();

			edgeOptions.addArguments("--use-fake-ui-for-media-stream");
			edgeOptions.addArguments("--use-fake-device-for-media-stream");
			edgeOptions.addArguments("--enable-media-stream");
			applyBrowserLocale(null, null, edgeOptions);
			applyLocalSbiAccessFlags(edgeOptions);

			if (isHeadless)
				edgeOptions.addArguments("--headless=new");
			driver = new EdgeDriver(edgeOptions);
			break;

		case "safari":
			driver = new SafariDriver();
			LOGGER.info("Safari doesn’t support auto camera permissions via code");
			break;

		default:
			throw new IllegalArgumentException("Unsupported browser: " + browser);
		}

		return driver;
	}

	public static boolean isBrowserTagPresent(Scenario scenario) {
		return scenario.getSourceTagNames().stream().anyMatch(tag -> tag.toLowerCase().startsWith("@browser="));
	}

	public static String getBrowserForScenario(Scenario scenario) {
		return scenario.getSourceTagNames().stream().filter(tag -> tag.toLowerCase().startsWith("@browser="))
				.map(tag -> tag.split("=")[1]).findFirst().orElseGet(() -> {
					String fallback = getThreadLocalBrowser();
					return fallback != null ? fallback : EsignetConfigManager.getProperty("browserName", "chrome");
				});
	}

	public static List<String> getSupportedLocalBrowsers() {
		String browsers = EsignetConfigManager.getProperty("browsers", "chrome");
		return Arrays.stream(browsers.split(",")).map(String::toLowerCase).toList();
	}

	public static void setThreadLocalBrowser(String browser) {
		scenarioBrowserThreadLocal.set(browser);
	}

	public static String getThreadLocalBrowser() {
		return scenarioBrowserThreadLocal.get();
	}

	public static void setThreadLocalLanguage(String lang) {
		threadLocalLanguage.set(lang);
	}

	public static String getThreadLocalLanguage() {
		return threadLocalLanguage.get();
	}

	private static void applyLocalSbiAccessFlags(ChromeOptions chromeOptions, Map<String, Object> prefs) {
		if (!MockMdsManager.isEnabled()) {
			return;
		}
		chromeOptions.addArguments("--allow-insecure-localhost");
		chromeOptions.addArguments("--unsafely-treat-insecure-origin-as-secure=http://127.0.0.1,http://localhost");
		chromeOptions.addArguments("--disable-features=LocalNetworkAccessChecks,BlockInsecurePrivateNetworkRequests,"
				+ "PrivateNetworkAccessSendPreflights,PrivateNetworkAccessRespectPreflightResults");
		prefs.put("profile.default_content_setting_values.local_network_access", 1);
		prefs.put("profile.default_content_setting_values.mixed_script", 1);
	}

	private static void applyLocalSbiAccessFlags(EdgeOptions edgeOptions) {
		if (!MockMdsManager.isEnabled()) {
			return;
		}
		edgeOptions.addArguments("--allow-insecure-localhost");
		edgeOptions.addArguments("--unsafely-treat-insecure-origin-as-secure=http://127.0.0.1,http://localhost");
		edgeOptions.addArguments("--disable-features=LocalNetworkAccessChecks,BlockInsecurePrivateNetworkRequests,"
				+ "PrivateNetworkAccessSendPreflights,PrivateNetworkAccessRespectPreflightResults");
	}

	private static void applyBrowserLocale(ChromeOptions chromeOptions, FirefoxOptions firefoxOptions,
			EdgeOptions edgeOptions) {

		if (firefoxOptions != null) {
			String locale = LanguageUtil.getNeutralBrowserLocale();
			if (locale != null && !locale.isBlank()) {
				firefoxOptions.addPreference("intl.accept_languages", locale);
				LOGGER.info("Browser locale configured to: " + locale);
			}
		}
	}

	public static void applyLocaleOverrideViaCdp(WebDriver driver) {
		String locale = LanguageUtil.getNeutralBrowserLocale();
		if (locale == null || locale.isBlank() || driver == null) {
			return;
		}
		String escaped = locale.replace("\\", "\\\\").replace("'", "\\'");
		String script = "Object.defineProperty(navigator, 'language', {get: function() { return '"
				+ escaped + "'; }});"
				+ "Object.defineProperty(navigator, 'languages', {get: function() { return ['" + escaped + "']; }});";
		try {
			if (driver instanceof ChromeDriver chromeDriver) {
				Map<String, Object> params = new HashMap<>();
				params.put("source", script);
				chromeDriver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);
				LOGGER.info("Chrome navigator.language spoof applied: " + locale);
			} else if (driver instanceof EdgeDriver edgeDriver) {
				Map<String, Object> params = new HashMap<>();
				params.put("source", script);
				edgeDriver.executeCdpCommand("Page.addScriptToEvaluateOnNewDocument", params);
				LOGGER.info("Edge navigator.language spoof applied: " + locale);
			}
		} catch (Exception e) {
			LOGGER.warning("Could not apply navigator.language spoof: " + e.getMessage());
		}
	}

	public static void setCameraPermissionAtRuntime(WebDriver driver, String setting) {
		if (!(driver instanceof ChromeDriver)) {
			LOGGER.warning("CDP permission override skipped: not a ChromeDriver session");
			return;
		}
		Map<String, Object> permission = new HashMap<>();
		permission.put("name", "camera");

		URI currentUri = URI.create(driver.getCurrentUrl());
		String origin = currentUri.getScheme() + "://" + currentUri.getAuthority();

		Map<String, Object> params = new HashMap<>();
		params.put("permission", permission);
		params.put("setting", setting);
		params.put("origin", origin);

		((ChromeDriver) driver).executeCdpCommand("Browser.setPermission", params);
	}

	public static void setNetworkOffline(WebDriver driver, boolean offline) {
		if (!(driver instanceof ChromeDriver)) {
			LOGGER.warning("CDP network override skipped: not a ChromeDriver session");
			return;
		}
		Map<String, Object> params = new HashMap<>();
		params.put("offline", offline);
		params.put("latency", 0);
		params.put("downloadThroughput", offline ? 0 : -1);
		params.put("uploadThroughput", offline ? 0 : -1);

		((ChromeDriver) driver).executeCdpCommand("Network.emulateNetworkConditions", params);
	}

	public static List<Long> captureRequestTimestamps(WebDriver driver, String urlSubstring) {
		List<Long> timestamps = Collections.synchronizedList(new ArrayList<>());
		if (!(driver instanceof HasDevTools)) {
			LOGGER.warning("Network request capture skipped: driver does not support DevTools");
			return timestamps;
		}
		DevTools devTools = ((HasDevTools) driver).getDevTools();
		devTools.createSession();
		devTools.send(Network.enable(Optional.empty(), Optional.empty(), Optional.empty()));
		devTools.addListener(Network.requestWillBeSent(), request -> {
			if (request.getRequest().getUrl().contains(urlSubstring)) {
				timestamps.add(System.currentTimeMillis());
				LOGGER.info("Captured request to " + urlSubstring + " at " + System.currentTimeMillis());
			}
		});
		return timestamps;
	}

	private static String firstExistingPath(String... candidates) {
		if (candidates == null) {
			return null;
		}
		for (String candidate : candidates) {
			if (candidate == null || candidate.isBlank()) {
				continue;
			}
			File file = new File(candidate);
			if (file.isFile() && file.canExecute()) {
				return file.getAbsolutePath();
			}
		}
		return null;
	}

}
