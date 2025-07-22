package base;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.List;

import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.Status;
import com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter;

import io.cucumber.java.After;
import io.cucumber.java.AfterAll;
import io.cucumber.java.AfterStep;
import io.cucumber.java.Before;
import io.cucumber.java.BeforeStep;
import io.cucumber.java.Scenario;
import io.cucumber.plugin.event.PickleStepTestStep;
import io.cucumber.plugin.event.TestCase;
import io.cucumber.plugin.event.TestStep;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.utils.S3Adapter;
import utils.BaseTestUtil;
import utils.EsignetConfigManager;
import utils.ExtentReportManager;

public class BaseTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(BaseTest.class);

	private static final ThreadLocal<WebDriver> driverThreadLocal = new ThreadLocal<>();
	private static final ThreadLocal<JavascriptExecutor> jseThreadLocal = new ThreadLocal<>();

	private final String url = EsignetConfigManager.getproperty("baseurl");

	private static int passedCount = 0;
	private static int failedCount = 0;
	private static int totalCount = 0;
	private static ExtentReports extent;

	@Before
	public void beforeAll(Scenario scenario) {
		LOGGER.info("Initializing WebDriver...");

		totalCount++;
		String browser = BaseTestUtil.getBrowserForScenario(scenario); // Start logging for the scenario
		ExtentReportManager.createTest(scenario.getName() + " [" + browser + "]");
		ExtentReportManager.logStep("Scenario Started: " + scenario.getName());

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

			driver.get(url);
			driver.manage().deleteAllCookies();

			LOGGER.info("Navigated to URL: " + url);

		} catch (Exception e) {
			LOGGER.error("Failed to initialize WebDriver: " + e.getMessage());
			ExtentReportManager.getTest().fail("❌ WebDriver setup failed: " + e.getMessage());
			ExtentReportManager.flushReport(); // Flush immediately to ensure it's written
			throw new RuntimeException(e);
		}
	}

	@BeforeStep
	public void beforeStep(Scenario scenario) {
		String stepName = getStepName(scenario);
		ExtentCucumberAdapter.getCurrentStep().log(Status.INFO, "➡️ Step Started: " + stepName);
	}

	@AfterStep
	public void afterStep(Scenario scenario) {
		String stepName = getStepName(scenario);

		if (scenario.isFailed()) {
			ExtentCucumberAdapter.getCurrentStep().log(Status.FAIL, "❌ Step Failed: " + stepName);
			captureScreenshot();
		} else {
			ExtentCucumberAdapter.getCurrentStep().log(Status.PASS, "✅ Step Passed: " + stepName);
		}
	}

	@After
	public void afterScenario(Scenario scenario) {
		if (scenario.isFailed()) {
			failedCount++;
			ExtentReportManager.getTest().fail("❌ Scenario Failed: " + scenario.getName());
		} else {
			passedCount++;
			ExtentReportManager.getTest().pass("✅ Scenario Passed: " + scenario.getName());
		}

		ExtentReportManager.flushReport();
	}

	@After
	public void afterAll() {
		WebDriver driver = driverThreadLocal.get();
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

	@AfterAll
	public static void afterAllReportUpdation() {
		Runtime.getRuntime().addShutdownHook(new Thread(() -> {
			LOGGER.info("Shutdown hook triggered. Uploading report...");
			if (extent != null) {
				extent.flush();
			}
			pushReportsToS3();
		}));

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

	private void captureScreenshot() {
		WebDriver driver = driverThreadLocal.get();
		if (driver != null) {
			byte[] screenshot = ((TakesScreenshot) driver).getScreenshotAs(OutputType.BYTES);
			ExtentCucumberAdapter.getCurrentStep().addScreenCaptureFromBase64String(
					java.util.Base64.getEncoder().encodeToString(screenshot), "Failure Screenshot");
		}
	}

	public static void pushReportsToS3() {
		executeLsCommand(System.getProperty("user.dir") + "/test-output/ExtentReport.html");
		executeLsCommand(System.getProperty("user.dir") + "/screenshots/");

		try {
			Thread.sleep(10000);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}

		executeLsCommand(System.getProperty("user.dir") + "/test-output/");
		String timestamp = new SimpleDateFormat("yyyy-MM-dd-HH-mm").format(new Date());
		String name = getEnvName() + "-" + timestamp + "-T-" + totalCount + "-P-" + passedCount + "-F-" + failedCount
				+ ".html";
		String newFileName = "EsignetUi-" + name;
		File originalReportFile = new File(System.getProperty("user.dir") + "/test-output/ExtentReport.html");
		File newReportFile = new File(System.getProperty("user.dir") + "/test-output/" + newFileName);

		// Rename the file
		if (originalReportFile.renameTo(newReportFile)) {
			LOGGER.info("Report renamed to: " + newFileName);
		} else {
			LOGGER.error("Failed to rename the report file.");
		}

		executeLsCommand(newReportFile.getAbsolutePath());

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

	private static void executeLsCommand(String directoryPath) {
		try {
			String os = System.getProperty("os.name").toLowerCase();
			Process process;

			if (os.contains("win")) {
				// Windows command (show all files including hidden)
				String windowsDirectoryPath = directoryPath.replace("/", File.separator);
				process = Runtime.getRuntime().exec(new String[] { "cmd.exe", "/c", "dir /a " + windowsDirectoryPath });
			} else {
				// Unix-like command (show all files including hidden)
				process = Runtime.getRuntime().exec(new String[] { "/bin/sh", "-c", "ls -al " + directoryPath });
			}

			BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
			String line;
			LOGGER.info("--- Directory listing for " + directoryPath + " ---");
			while ((line = reader.readLine()) != null) {
				LOGGER.info(line);
			}

			int exitCode = process.waitFor();
			if (exitCode != 0) {
				BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
				String errorLine;
				System.err.println("--- Directory listing error ---");
				while ((errorLine = errorReader.readLine()) != null) {
					System.err.println(errorLine);
				}
			}
			LOGGER.info("--- End directory listing ---");

		} catch (IOException | InterruptedException e) {
			System.err.println("Error executing directory listing command: " + e.getMessage());
		}
	}

	public static String getEnvName() {
		String baseUrl = EsignetConfigManager.getproperty("baseurl"); // e.g., https://healthservices.es-qa.mosip.net/
		String domainPart = baseUrl.replace("https://", "").replace("http://", ""); // remove protocol
		domainPart = domainPart.split("/")[0]; // remove path if any
		String[] parts = domainPart.split("\\.");
		
		LOGGER.info("--- ApplnURI ---" + BaseTestCase.ApplnURI);
		BaseTestCase.ApplnURI = System.getProperty("env.endpoint");

		String envName = "";
		if (parts.length >= 3) {
			envName = parts[1];
		}

		return envName;
	}
}
