package utils;

import java.io.File;
import java.io.IOException;
import java.util.Base64;

import org.apache.commons.io.FileUtils;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;

import com.aventstack.extentreports.MediaEntityBuilder;

public class ScreenshotUtil {

	public static String captureScreenshot(WebDriver driver, String screenshotName) throws IOException {
		if (driver == null) {
			System.err.println("WebDriver instance is null. Cannot take a screenshot.");
			throw new IllegalStateException("WebDriver instance is null.");
		}
		TakesScreenshot ts = (TakesScreenshot) driver;
		File source = ts.getScreenshotAs(OutputType.FILE);

		String destinationPath = System.getProperty("user.dir") + "/screenshots/" + screenshotName + "_"
				+ System.currentTimeMillis() + ".png";
		System.err.println("destinationpath:-" + destinationPath);
		File destination = new File(destinationPath);
		destination.getParentFile().mkdirs();
		FileUtils.copyFile(source, destination);

		return destinationPath;
	}

	public static void attachScreenshot(WebDriver driver, String screenshotName) {
		try {
			String screenshotPath = captureScreenshot(driver, screenshotName);

			File screenshotFile = new File(screenshotPath);
			if (screenshotFile.exists()) {
				ExtentReportManager.getTest().info("Screenshot Captured", MediaEntityBuilder
						.createScreenCaptureFromBase64String(encodeFileToBase64Binary(screenshotPath)).build());
			} else {
				ExtentReportManager.getTest().warning("Screenshot file not found: " + screenshotPath);
			}
		} catch (IOException e) {
			ExtentReportManager.getTest().warning("Failed to attach screenshot to report: " + e.getMessage());
		}
	}

	public static String encodeFileToBase64Binary(String filePath) throws IOException {
		byte[] fileContent = FileUtils.readFileToByteArray(new File(filePath));
		return Base64.getEncoder().encodeToString(fileContent);
	}
}