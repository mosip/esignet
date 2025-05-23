package utils;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.edge.EdgeDriver;
import org.openqa.selenium.edge.EdgeOptions;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.openqa.selenium.firefox.FirefoxOptions;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.safari.SafariDriver;
import org.yaml.snakeyaml.Yaml;

import io.cucumber.java.Scenario;
import io.github.bonigarcia.wdm.WebDriverManager;

public class BaseTestUtil {
	private static final Logger LOGGER = Logger.getLogger(BaseTestUtil.class.getName());
	private static final ThreadLocal<String> scenarioBrowserThreadLocal = new ThreadLocal<>();

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
			caps.setCapability("bstack:options", bsOptions);

			capsList.add(caps);
		}

		return capsList;
	}

	public static WebDriver getWebDriverInstance(String browserName) throws MalformedURLException {
		URL remoteUrl = getBrowserStackUrl().toURL();

		List<DesiredCapabilities> allCaps = getAllCapabilities();
		DesiredCapabilities caps = allCaps.stream()
				.filter(c -> c.getCapability("browserName").toString().equalsIgnoreCase(browserName)).findFirst()
				.orElse(allCaps.get(0)); // fallback

		LOGGER.info("Running on BrowserStack with browser: " + browserName);
		LOGGER.info("Running with capabilities: " + caps.toString());
		return new RemoteWebDriver(remoteUrl, caps);
	}

	public static WebDriver getLocalWebDriverInstance(String browser) throws IOException {
		browser = browser.toLowerCase();
		boolean isHeadless = Boolean.parseBoolean(EsignetConfigManager.getproperty("headless"));
		boolean isMobile = Boolean.parseBoolean(EsignetConfigManager.getproperty("mobileEmulation"));
		String deviceName = EsignetConfigManager.getproperty("mobileDevice");

		WebDriver driver;

		switch (browser) {
		case "chrome":
			WebDriverManager.chromedriver().setup();
			ChromeOptions chromeOptions = new ChromeOptions();

			// Enable mobile emulation if requested
			if (isMobile) {
				Map<String, String> mobileEmulation = new HashMap<>();
				mobileEmulation.put("deviceName", deviceName);
				chromeOptions.setExperimentalOption("mobileEmulation", mobileEmulation);
			}

			// Always set headless flags if needed
			if (isHeadless) {
				LOGGER.info("Running in headless mode");
				chromeOptions.addArguments("--headless=new");
				chromeOptions.addArguments("--disable-gpu");
				chromeOptions.addArguments("--window-size=1920x1080");
			}

			// Always add these for Docker safety
			chromeOptions.addArguments("--no-sandbox");
			chromeOptions.addArguments("--disable-dev-shm-usage");

			// Optional: allow Chrome to open a debugging port (harmless)
			chromeOptions.addArguments("--remote-debugging-port=0");

			LOGGER.info("Chrome args: " + chromeOptions);
			driver = new ChromeDriver(chromeOptions);
			break;

		case "firefox":
			WebDriverManager.firefoxdriver().setup();
			FirefoxOptions firefoxOptions = new FirefoxOptions();
			if (isHeadless)
				firefoxOptions.addArguments("--headless");
			driver = new FirefoxDriver(firefoxOptions);
			break;

		case "edge":
			WebDriverManager.edgedriver().setup();
			EdgeOptions edgeOptions = new EdgeOptions();
			if (isHeadless)
				edgeOptions.addArguments("--headless=new");
			driver = new EdgeDriver(edgeOptions);
			break;

		case "safari":
			driver = new SafariDriver();
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

}