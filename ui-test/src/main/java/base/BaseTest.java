package base;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Base64;
import java.util.Date;
import java.util.List;

import org.json.JSONObject;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.SkipException;

import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeAll;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.Scenario;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestStep;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.S3Adapter;
import models.Uin;
import models.Vid;
import utils.BaseTestUtil;
import utils.BrowserStackLocalManager;
import utils.EsignetConfigManager;
import utils.EsignetUtil;
import utils.ExtentReportManager;
import utils.ScreenshotUtil;
import utils.UINManager;
import utils.VIDManager;

public class BaseTest extends AdminTestUtil {

	private static final Logger LOGGER = LoggerFactory.getLogger(BaseTest.class);

	private static final ThreadLocal<WebDriver> driverThreadLocal = new ThreadLocal<>();
	private static final ThreadLocal<JavascriptExecutor> jseThreadLocal = new ThreadLocal<>();
	private static final ThreadLocal<Uin> threadUin = new ThreadLocal<>();
	private static final ThreadLocal<Vid> threadVid = new ThreadLocal<>();
	private static final ThreadLocal<Boolean> isKnownIssueScenario = new ThreadLocal<>();

	public static int passedCount = 0;
	public static int failedCount = 0;
	public static int totalCount = 0;
	public static int knownIssueCount = 0;

	@BeforeAll
	public static void beforeAll() {
		boolean runOnBrowserStack = Boolean.parseBoolean(EsignetConfigManager.getproperty("runOnBrowserStack"));

		if (runOnBrowserStack) {
			try {
				BrowserStackLocalManager.start();
				LOGGER.info("BrowserStack Local (WireGuard) started");
			} catch (Exception e) {
				throw new RuntimeException("Failed to start BrowserStack Local", e);
			}
		}
	}

	@Before
	public void beforeAll(Scenario scenario) {
		LOGGER.info("Initializing WebDriver...");

		if (runners.Runner.knownIssues.containsKey(scenario.getName())) {
			String bugId = runners.Runner.knownIssues.get(scenario.getName());
			String browser = BaseTestUtil.getBrowserForScenario(scenario);
			String lang = BaseTestUtil.getThreadLocalLanguage();
			ExtentReportManager.createTest(scenario.getName() + " [" + browser + " | " + lang + "]");
			LOGGER.info("Skipping Known Issue Scenario: " + scenario.getName() + " | Bug: " + bugId);
			isKnownIssueScenario.set(true);
			throw new SkipException("Known Issue - Skipped: " + scenario.getName() + " | " + bugId);
		}
		isKnownIssueScenario.set(false);

		totalCount++;
		String browser = BaseTestUtil.getBrowserForScenario(scenario); // Start logging for the scenario
		String lang = BaseTestUtil.getThreadLocalLanguage();
		ExtentReportManager.createTest(scenario.getName() + " [" + browser + " | " + lang + "]");
		ExtentReportManager
				.logStep("Scenario Started: " + scenario.getName() + " | Browser: " + browser + " | Language: " + lang);

		try {
			String scenarioBrowser = BaseTestUtil.getBrowserForScenario(scenario);
			boolean browserTagPresent = BaseTestUtil.isBrowserTagPresent(scenario);
			boolean runOnBrowserStack = Boolean.parseBoolean(EsignetConfigManager.getproperty("runOnBrowserStack"));
			boolean runMultipleBrowsers = Boolean.parseBoolean(EsignetConfigManager.getproperty("runMultipleBrowsers"));

			WebDriver driver;

			if (runOnBrowserStack) {
				driver = setupBrowserStackDriver(scenario, runMultipleBrowsers, browserTagPresent, scenarioBrowser);
			} else {
				driver = setupLocalDriver(scenario, runMultipleBrowsers, browserTagPresent, scenarioBrowser);
			}

			driverThreadLocal.set(driver);
			jseThreadLocal.set((JavascriptExecutor) driver);

			// Browser settings
			driver.manage().window().maximize();
			driver.manage().timeouts().implicitlyWait(Duration.ofSeconds(10)); // Configurable if needed
			driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(30));

			String baseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
			String template = EsignetConfigManager.getproperty("authorizeUrlTemplate");

			String requestUri = EsignetUtil.generateParRequestUri();

			String updatedTemplate = template.replace("$REQUEST_URI$", requestUri);

			updatedTemplate = AdminTestUtil.replaceIdWithAutogeneratedId(updatedTemplate, "$ID:");

			String authorizeUrl = baseUrl + updatedTemplate;

			LOGGER.info("Authorize URL: " + authorizeUrl);

			driver.get(authorizeUrl);
			driver.manage().deleteAllCookies();

			LOGGER.info("Navigated to URL: " + authorizeUrl);

		} catch (Exception e) {
			LOGGER.error("Failed to initialize WebDriver: " + e.getMessage());
			ExtentReportManager.getTest().fail("❌ WebDriver setup failed: " + e.getMessage());
			ExtentReportManager.flushReport(); // Flush immediately to ensure it's written
			throw new RuntimeException(e);
		}
	}

	@Before("@NeedsUIN")
	public void handleUIN(Scenario scenario) throws InterruptedException {
		Uin uinDetails = UINManager.acquireUIN();
		threadUin.set(uinDetails);
	}

	@Before("@NeedsVID")
	public void handleVID(Scenario scenario) throws InterruptedException {
		Vid vidDetails = VIDManager.acquireVID();
		threadVid.set(vidDetails);
	}

	@BeforeStep
	public void beforeStep(Scenario scenario) {
		String stepName = getStepName(scenario);
		ExtentCucumberAdapter.getCurrentStep().log(Status.INFO, "➡️ Step Started: " + stepName);
	}

	@After
	public void clearUinVid() {
		threadUin.remove();
		threadVid.remove();
	}

	@After
	public void afterScenario(Scenario scenario) {
		WebDriver driver = driverThreadLocal.get();

		String publicUrl = null;
		String videoUrl = null;

		// Fetch BrowserStack URLs only if running on BrowserStack
		boolean runOnBrowserStack = Boolean.parseBoolean(EsignetConfigManager.getproperty("runOnBrowserStack"));

		if (runOnBrowserStack && driver instanceof RemoteWebDriver) {
			RemoteWebDriver remoteDriver = (RemoteWebDriver) driver;
			String sessionId = remoteDriver.getSessionId().toString();

			try {
				String jsonUrl = "https://api.browserstack.com/automate/sessions/" + sessionId + ".json";
				String username = EsignetConfigManager.getproperty("browserstack_username");
				String accessKey = EsignetConfigManager.getproperty("browserstack_access_key");
				String auth = username + ":" + accessKey;
				String basicAuth = "Basic " + Base64.getEncoder().encodeToString(auth.getBytes());

				HttpURLConnection conn = (HttpURLConnection) new URL(jsonUrl).openConnection();
				conn.setRequestMethod("GET");
				conn.setRequestProperty("Authorization", basicAuth);
				conn.setConnectTimeout(10000);
				conn.setReadTimeout(10000);

				if (conn.getResponseCode() == 200) {
					StringBuilder response = new StringBuilder();
					try (BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
						String inputLine;
						while ((inputLine = in.readLine()) != null)
							response.append(inputLine);
					}

					JSONObject jsonResponse = new JSONObject(response.toString());
					JSONObject session = jsonResponse.getJSONObject("automation_session");

					publicUrl = session.getString("public_url");
					videoUrl = session.getString("video_url");

					// Attach links to Extent report (only once)
					if (publicUrl != null) {
						ExtentReportManager.getTest()
								.info("<a href='" + publicUrl + "' target='_blank'>View on BrowserStack</a>");
					}
					if (videoUrl != null) {
						ExtentReportManager.getTest()
								.info("<a href='" + videoUrl + "' target='_blank'>Click here to view only Video</a>");
					}
					BrowserStackLocalManager.stop();

				} else {
					ExtentReportManager.getTest().warning(
							"Failed to fetch BrowserStack session JSON, response code: " + conn.getResponseCode());
				}

			} catch (Exception e) {
				ExtentReportManager.getTest()
						.warning("Failed to fetch BrowserStack build/session info:" + e.getMessage());
			}
		}

		try {
			if (scenario.isFailed()) {

				failedCount++;
				ExtentReportManager.incrementFailed();

				// Use scenario name + failed step (fallback to scenario name if step unknown)
				String failedStepName = scenario.getName().replaceAll("[^a-zA-Z0-9]", "_");

				// Attach single screenshot when a driver exists
				if (driver != null) {
					ScreenshotUtil.attachScreenshot(driver, failedStepName);
				} else {
					ExtentReportManager.getTest().warning("Screenshot skipped because WebDriver was not initialized.");
				}

				ExtentReportManager.getTest().fail("❌ Scenario Failed: " + scenario.getName());

			} else if (scenario.getStatus().toString().equalsIgnoreCase("SKIPPED")
					&& runners.Runner.knownIssues.containsKey(scenario.getName())) {

				String bugId = runners.Runner.knownIssues.get(scenario.getName());
				String bugUrl = "https://mosip.atlassian.net/browse/" + bugId;

				ExtentReportManager.incrementKnownIssue();
				ExtentReportManager.getTest().skip(
						"🟠 Skipped due to Known Issue → <a href='" + bugUrl + "' target='_blank'>" + bugId + "</a>");

			} else if (scenario.getStatus().toString().equalsIgnoreCase("SKIPPED")) {

				ExtentReportManager.incrementSkipped();
				ExtentReportManager.getTest().skip("⚠️ Scenario Skipped: " + scenario.getName());

			} else {
				passedCount++;
				ExtentReportManager.incrementPassed();
				ExtentReportManager.getTest().pass("✅ Scenario Passed: " + scenario.getName());
			}

			ExtentReportManager.flushReport();
		} finally {
			// Close driver and cleanup ThreadLocal
			if (driver != null) {
				try {
					LOGGER.info("Closing WebDriver session...");
					driver.quit();
				} catch (Exception e) {
					LOGGER.warn("Error while closing WebDriver: " + e.getMessage());
				} finally {
					driverThreadLocal.remove();
					jseThreadLocal.remove();
				}
			}
		}
	}

	@AfterAll
	public static void afterAll() {
		boolean runOnBrowserStack = Boolean.parseBoolean(EsignetConfigManager.getproperty("runOnBrowserStack"));

		if (runOnBrowserStack) {
			try {
				BrowserStackLocalManager.stop();
				LOGGER.info("BrowserStack Local (WireGuard) stopped");
			} catch (Exception e) {
				LOGGER.error("Error stopping BrowserStack Local", e);
			}
		}
	}

	public static WebDriver getDriver() {
		return driverThreadLocal.get();
	}

	public static JavascriptExecutor getJse() {
		return jseThreadLocal.get();
	}

	private WebDriver setupBrowserStackDriver(Scenario scenario, boolean isMulti, boolean tagPresent, String browser)
			throws Exception {
		LOGGER.info("Running scenario on BrowserStack browser: " + browser);
		return BaseTestUtil.getWebDriverInstance(browser);
	}

	private WebDriver setupLocalDriver(Scenario scenario, boolean isMulti, boolean tagPresent, String browser)
			throws IOException {
		LOGGER.info("Running scenario on local browser" + (isMulti ? " (multi)" : "") + ": " + browser);
		return BaseTestUtil.getLocalWebDriverInstance(browser);
	}

	private String getStepName(Scenario scenario) {
		try {
			Field testCaseField = scenario.getClass().getDeclaredField("testCase");
			testCaseField.setAccessible(true);
			TestCase testCase = (TestCase) testCaseField.get(scenario);
			List<TestStep> testSteps = testCase.getTestSteps();

			for (TestStep step : testSteps) {
				if (step instanceof PickleStepTestStep) {
					return ((PickleStepTestStep) step).getStep().getText();
				}
			}
		} catch (Exception e) {
			return "Unknown Step";
		}
		return "Unknown Step";
	}

	public static void pushReportsToS3(String lang) {
		// executeLsCommand(System.getProperty("user.dir") +
		// "/test-output/ExtentReport.html");
		// executeLsCommand(System.getProperty("user.dir") + "/screenshots/");

		// executeLsCommand(System.getProperty("user.dir") + "/test-output/");
		String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date());
		String name = getEnvName() + "-" + lang + "-" + timestamp + "-T-" + ExtentReportManager.getTotalCount() + "-P-"
				+ ExtentReportManager.getPassedCount() + "-F-" + ExtentReportManager.getFailedCount() + "-S-"
				+ ExtentReportManager.getSkippedCount() + "-KI-" + ExtentReportManager.getKnownIssueCount() + ".html";
		String newFileName = "EsignetUi-" + name;
		File originalReportFile = new File(System.getProperty("user.dir") + "/test-output/ExtentReport.html");
		File newReportFile = new File(System.getProperty("user.dir") + "/test-output/" + newFileName);

		// Rename the file
		if (originalReportFile.renameTo(newReportFile)) {
			LOGGER.info("Report renamed to: " + newFileName);
		} else {
			LOGGER.error("Failed to rename the report file.");
		}

		// executeLsCommand(newReportFile.getAbsolutePath());

		if (EsignetConfigManager.getPushReportsToS3().equalsIgnoreCase("yes")) {
			S3Adapter s3Adapter = new S3Adapter();
			boolean isStoreSuccess = false;
			try {
				isStoreSuccess = s3Adapter.putObject(EsignetConfigManager.getS3Account(), "", null, null, newFileName,
						newReportFile);
				LOGGER.info("isStoreSuccess:: " + isStoreSuccess);
			} catch (Exception e) {
				LOGGER.error("Error occurred while pushing the object: " + e.getLocalizedMessage());
				LOGGER.error(e.getMessage());
			}
		}
	}

//  This is not required so commented it for now will remove this once tested in Rancher

//	private static void executeLsCommand(String directoryPath) {
//		try {
//			String os = System.getProperty("os.name").toLowerCase();
//			Process process;
//
//			if (os.contains("win")) {
//				// Windows command (show all files including hidden)
//				String windowsDirectoryPath = directoryPath.replace("/", File.separator);
//				process = Runtime.getRuntime().exec(new String[] { "cmd.exe", "/c", "dir /a " + windowsDirectoryPath });
//			} else {
//				// Unix-like command (show all files including hidden)
//				process = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", "ls -al " + directoryPath });
//			}
//
//			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
//			String line;
//			LOGGER.info("--- Directory listing for " + directoryPath + " ---");
//			while ((line = reader.readLine()) != null) {
//				LOGGER.info(line);
//			}
//
//			int exitCode = process.waitFor();
//			if (exitCode != 0) {
//				BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
//				String errorLine;
//				LOGGER.info("--- Directory listing error ---");
//				while ((errorLine = errorReader.readLine()) != null) {
//					System.err.println(errorLine);
//				}
//			}
//			LOGGER.info("--- End directory listing ---");
//
//		} catch (IOException | InterruptedException e) {
//			System.err.println("Error executing directory listing command: " + e.getMessage());
//		}
//	}

	public static String getEnvName() {
		String baseUrl = EsignetConfigManager.getproperty("baseurl");
		String host = URI.create(baseUrl).getHost();
		String[] parts = host.split("\\.");

		LOGGER.info("--- ApplnURI ---" + BaseTestCase.ApplnURI);
		BaseTestCase.ApplnURI = System.getProperty("env.endpoint");

		String envName = "";
		if (parts.length >= 3) {
			envName = parts[1];
		}

		return envName;
	}

	public Uin getUinDetails() {
		return threadUin.get();
	}

	public String getUin() {
		return getUinDetails() != null ? getUinDetails().getUin() : null;
	}

	public Vid getVidDetails() {
		return threadVid.get();
	}

	public String getVid() {
		return getVidDetails() != null ? getVidDetails().getVid() : null;
	}

}