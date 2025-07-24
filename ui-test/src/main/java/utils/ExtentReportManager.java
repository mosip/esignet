package utils;

import java.io.IOException;
import java.io.InputStream;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aventstack.extentreports.ExtentReports;
import com.aventstack.extentreports.ExtentTest;
import com.aventstack.extentreports.reporter.ExtentHtmlReporter;
import com.aventstack.extentreports.reporter.configuration.Theme;

import base.BaseTest;

public class ExtentReportManager {
	private static ExtentReports extent;
	private static ExtentTest test;
	private static String gitBranch;
	private static String gitCommitId;
	private static final Logger LOGGER = LoggerFactory.getLogger(ExtentReportManager.class);
	private static String currentDate = new SimpleDateFormat("yyyy-MM-dd").format(new Date());
	private static boolean systemInfoAdded = false;

	public static void initReport() {
		if (extent == null) {
			getGitDetails();
			String reportName = "Test Execution Report";
			reportName += " ---- Esignet UI Test ---- Report Date: " + currentDate + " ---- Tested Environment: "
					+ BaseTest.getEnvName() + " ---- Branch Name is: " + gitBranch + " & Commit Id is: " + gitCommitId;

			ExtentHtmlReporter htmlReporter = new ExtentHtmlReporter("test-output/ExtentReport.html");
			htmlReporter.config().setTheme(Theme.DARK);
			htmlReporter.config().setDocumentTitle("Automation Report");
			htmlReporter.config().setReportName(reportName);

			extent = new ExtentReports();
			extent.attachReporter(htmlReporter);
			addSystemInfo();
		}
	}

	private static void addSystemInfo() {
		if (extent != null && !systemInfoAdded) {
			LOGGER.info("Adding Git info to report: Branch = " + gitBranch + ", Commit ID = " + gitCommitId);
			extent.setSystemInfo("Git Branch", gitBranch);
			extent.setSystemInfo("Git Commit ID", gitCommitId);
			systemInfoAdded = true;
		}
	}

	private static void getGitDetails() {
		Properties properties = new Properties();
		try (InputStream is = ExtentReportManager.class.getClassLoader().getResourceAsStream("git.properties")) {
			properties.load(is);
			gitBranch = properties.getProperty("git.branch");
			gitCommitId = properties.getProperty("git.commit.id.abbrev");

		} catch (IOException e) {
			LOGGER.error("Error getting git branch information: " + e.getMessage());
		}

	}

	public static void createTest(String testName) {
		test = extent.createTest(testName);
	}

	public static void logStep(String message) {
		if (test != null) {
			test.info(message);
		}
	}

	public static void flushReport() {
		if (extent != null) {
			extent.flush();
		}
	}

	public static ExtentTest getTest() {
		return test;
	}
}