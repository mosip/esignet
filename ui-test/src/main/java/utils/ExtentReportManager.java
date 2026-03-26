package utils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;
import com.aventstack.extentreports.reporter.ExtentSparkReporter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;

import com.aventstack.extentreports.reporter.configuration.Theme;

import base.BaseTest;

import java.io.*;
import java.net.MalformedURLException;
import java.net.URL;

public class ExtentReportManager {
	private static ExtentReports extent;
	private static String gitBranch;
	private static String gitCommitId;
	private static final Logger LOGGER = LoggerFactory.getLogger(ExtentReportManager.class);
	private static final ThreadLocal<ExtentTest> testThread = new ThreadLocal<>();
	private static final String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
	private static boolean systemInfoAdded = false;
	private static String currentLang;

	private static int passedCount = 0;
	private static int failedCount = 0;
	private static int skippedCount = 0;
	private static int knownIssueCount = 0;

	public static void initReport(String lang) {
		currentLang = lang;
		if (extent == null) {
			getGitDetails();
			String reportName = "Test Execution Report ---- " + lang;
			reportName += " ---- Esignet UI Test ---- Report Date: " + currentDate + " ---- Tested Environment: "
					+ BaseTest.getEnvName() + " ---- Branch Name is: " + gitBranch + " & Commit Id is: " + gitCommitId;

			ExtentSparkReporter spark = new ExtentSparkReporter("test-output/ExtentReport.html");
			spark.config().setTheme(Theme.DARK);
			spark.config().setDocumentTitle("Automation Report");
			spark.config().setReportName(reportName);

			extent = new ExtentReports();
			extent.attachReporter(spark);
			addSystemInfo();
		}
	}

	public static synchronized ExtentTest createTest(String testName) {
		ExtentTest test = extent.createTest(testName);
		testThread.set(test);
		return test;
	}

	public static ExtentTest getTest() {
		return testThread.get();
	}

	public static void removeTest() {
		testThread.remove();
	}

	private static void addSystemInfo() {
		if (extent != null && !systemInfoAdded) {
			LOGGER.info("Adding Git info to report: Branch = " + gitBranch + ", Commit ID = " + gitCommitId);
			extent.setSystemInfo("Git Branch", gitBranch);
			extent.setSystemInfo("Git Commit ID", gitCommitId);
			extent.setSystemInfo("Language", currentLang);
			systemInfoAdded = true;
		}
	}

	private static String runCommand(String... command) throws IOException {
		Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
		try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
			return reader.readLine().trim();
		}
	}

	private static void getGitDetails() {
		try {
			// Try Git CLI first
			gitBranch = runCommand("git", "rev-parse", "--abbrev-ref", "HEAD");
			gitCommitId = runCommand("git", "rev-parse", "--short", "HEAD");
			LOGGER.info("Fetched Git details using CLI: Branch={}, Commit={}", gitBranch, gitCommitId);
		} catch (Exception e) {
			LOGGER.warn("Failed to get Git details from CLI: {}. Falling back to git.properties...", e.getMessage());
			loadFromGitProperties();
		}
	}

	private static void loadFromGitProperties() {
		Properties properties = new Properties();
		try (InputStream is = ExtentReportManager.class.getClassLoader().getResourceAsStream("git.properties")) {
			if (is != null) {
				properties.load(is);
				gitBranch = properties.getProperty("git.branch", "unknown");
				gitCommitId = properties.getProperty("git.commit.id.abbrev", "unknown");
				LOGGER.info("Fetched Git details from git.properties: Branch={}, Commit={}", gitBranch, gitCommitId);
			} else {
				LOGGER.warn("git.properties not found in classpath. Using defaults.");
				gitBranch = "unknown";
				gitCommitId = "unknown";
			}
		} catch (IOException ex) {
			LOGGER.error("Error loading git.properties: {}", ex.getMessage());
			gitBranch = "unknown";
			gitCommitId = "unknown";
		}
	}

	public static String getEnvName(String url) {
		if (url == null || url.isEmpty())
			return "unknown";

		try {
			URL parsedUrl = new URL(url);
			String host = parsedUrl.getHost(); // e.g., api-internal.qa-esignet.mosip.net

			// Remove known prefix if present
			host = host.replaceFirst("^api-internal\\.", "");

			// Remove suffix
			host = host.replaceFirst("\\.mosip\\.net$", "");

			return host;

		} catch (MalformedURLException e) {
			LOGGER.error("Error getting env name: {}", e.getMessage());
			e.printStackTrace();
			return "unknown";
		}
	}

	public static synchronized void incrementPassed() {
		passedCount++;
	}

	public static synchronized void incrementFailed() {
		failedCount++;
	}

	public static synchronized void incrementSkipped() {
		skippedCount++;
	}

	public static synchronized void incrementKnownIssue() {
		knownIssueCount++;
	}

	public static int getPassedCount() {
		return passedCount;
	}

	public static int getFailedCount() {
		return failedCount;
	}

	public static int getSkippedCount() {
		return skippedCount;
	}

	public static int getKnownIssueCount() {
		return knownIssueCount;
	}

	public static int getTotalCount() {
		return passedCount + failedCount + skippedCount + knownIssueCount;
	}

	public static void logStep(String message) {
		if (message != null && message.trim().startsWith("ℹ️ Step completed successfully:")) {
			return;
		}
		ExtentTest test = testThread.get();
		if (test != null) {
			test.info(message);
		} else {
			LOGGER.warn("logStep called but no test is active: {}", message);
		}
	}

	public static void flushReport() {
		if (extent != null) {
			extent.flush();
		}
	}
}