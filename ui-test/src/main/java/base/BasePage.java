package base;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

import org.openqa.selenium.Alert;
import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.NoAlertPresentException;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.OutputType;
import org.openqa.selenium.TakesScreenshot;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.aventstack.extentreports.Status;
import utils.ClaimsUtil;
import utils.EsignetConfigManager;
import utils.ExtentReportManager;
import utils.WaitUtil;

public class BasePage {
	protected WebDriver driver;
	private static final Logger LOGGER = LoggerFactory.getLogger(BasePage.class);

	public BasePage(WebDriver driver) {
		this.driver = driver;
	}

	private void logStep(String description, WebElement element) {
		ExtentReportManager.getTest().log(Status.INFO,
				description + "<details><summary>Locator Details</summary><pre>" + element + "</pre></details>");
	}

	private void logStep(String description, By locator) {
		ExtentReportManager.getTest().log(Status.INFO, description + "<details><summary>Locator Details</summary><pre>"
				+ formatLocator(locator) + "</pre></details>");
	}

	private String formatLocator(By locator) {
		String locatorStr = locator.toString();
		if (locatorStr.contains(": ")) {
			String[] parts = locatorStr.split(": ", 2);
			String method = parts[0].replace("By.", "");
			String value = parts[1];
			return "By." + method + "(\"" + value + "\")";
		}
		return locatorStr;
	}

	private String describeElement(WebElement element) {
		try {
			String contentDesc = element.getAttribute("content-desc");
			String id = element.getAttribute("resource-id");
			String text = element.getText();
			if (contentDesc != null && !contentDesc.isEmpty()) {
				return "\"" + contentDesc + "\"";
			} else if (text != null && !text.isEmpty()) {
				return "\"" + text + "\"";
			} else if (id != null && !id.isEmpty()) {
				return "\"" + id.substring(id.lastIndexOf("/") + 1) + "\""; // just the id name
			} else {
				return "[Unnamed element]";
			}
		} catch (Exception e) {
			return "[Element details unavailable]";
		}
	}

	public void waitForElementVisible(WebElement element) {
		WaitUtil.waitForVisibility(driver, element);
	}

	public void clickOnElement(WebElement element, String stepDesc) {
		try {
			waitForElementVisible(element);

			element.click();
			logStep(stepDesc, element);
			LOGGER.info("Clicking on element: {}", element);
		} catch (Exception e) {

			ExtentReportManager.getTest().log(Status.FAIL, "Failed to click on element: " + describeElement(element));
			throw e;
		}
	}

	public boolean isElementVisible(WebElement element, String stepDesc) {
		try {
			waitForElementVisible(element);
			logStep(stepDesc + " - Verified visibility", element);
			return element.isDisplayed();
		} catch (NoSuchElementException e) {
			LOGGER.warn("Element not visible: {}", element);
			ExtentReportManager.getTest().log(Status.WARNING, "Element not visible: " + describeElement(element));
			return false;
		}
	}

	public String getText(WebElement element, String stepDesc) {
		waitForElementVisible(element);
		String text = element.getText();
		logStep(stepDesc + " - Verified Text", element);
		LOGGER.info("Retrieved text: {}", text);
		return text;
	}

	public boolean isButtonEnabled(WebElement element, String stepDesc) {
		try {
			waitForElementVisible(element);
			boolean enabled = element.isEnabled();
			logStep(stepDesc + " - Verified Button", element);
			LOGGER.info("Button enabled status: {}", enabled);
			return enabled;
		} catch (NoSuchElementException e) {
			LOGGER.warn("Element not visible: {}", element);
			ExtentReportManager.getTest().log(Status.WARNING, "Element not visible: " + describeElement(element));
			return false;
		}
	}

	public void enterText(WebElement element, String text, String stepDesc) {
		if (isElementVisible(element, stepDesc)) {
			element.clear();
			element.sendKeys(text);
			logStep(stepDesc, element);
			LOGGER.info("Entered text into {}", describeElement(element));
		}
	}

	public void refreshBrowser(String stepDesc) {
		try {
			LOGGER.info("Refreshing browser");
			driver.navigate().refresh();
			ExtentReportManager.getTest().log(Status.INFO, stepDesc);
		} catch (Exception e) {
			LOGGER.error("Failed to refresh browser", e);
			ExtentReportManager.getTest().log(Status.WARNING,
					stepDesc + " - Failed to refresh browser: " + e.getMessage());
			throw e;
		}
	}

	public void browserBackButton(String stepDesc) {
		try {
			LOGGER.info("Navigating back");
			driver.navigate().back();
			ExtentReportManager.getTest().log(Status.INFO, stepDesc);
		} catch (Exception e) {
			LOGGER.error("Failed to navigate back", e);
			ExtentReportManager.getTest().log(Status.WARNING,
					stepDesc + " - Failed to navigate back: " + e.getMessage());
			throw e;
		}
	}

	public void browserBackButton() {
		driver.navigate().back();
	}

	public void uploadFile(WebElement element, String filePath, String stepDesc) {
		String absolutePath = Paths.get(System.getProperty("user.dir"), filePath).toString();
		waitForElementVisible(element);
		element.sendKeys(absolutePath);
		logStep(stepDesc + " - uploaded file: '" + absolutePath + "'", element);
		LOGGER.info("Uploading file: {}", absolutePath);
	}

	public void verifyHomePageLinks(List<WebElement> links) {
		for (WebElement link : links) {
			String url = link.getAttribute("href");
			if (url != null && !url.isEmpty()) {
				validateLink(url);
			}
		}
	}

	private void validateLink(String url) {
		try {
			HttpURLConnection httpConn = (HttpURLConnection) new URI(url).toURL().openConnection();
			httpConn.connect();
			int responseCode = httpConn.getResponseCode();

			if (responseCode >= 200 && responseCode < 300) {
				LOGGER.info("{} - Valid link (Status {})", url, responseCode);
			} else {
				LOGGER.warn("{} - Broken link (Status {})", url, responseCode);
			}
			httpConn.disconnect();
		} catch (Exception e) {
			LOGGER.error("{} - Exception occurred: {}", url, e.getMessage());
		}
	}

	public void acceptAlert() {
		try {
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(EsignetConfigManager.getTimeout()));
			Alert alert = wait.until(ExpectedConditions.alertIsPresent());
			LOGGER.info("Accepting alert: {}", alert.getText());
			alert.accept();
		} catch (NoAlertPresentException e) {
			LOGGER.warn("No alert found to accept.");
		}
	}

	public void dismissAlert() {
		try {
			WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(EsignetConfigManager.getTimeout()));
			Alert alert = wait.until(ExpectedConditions.alertIsPresent());
			LOGGER.info("Dismissing alert: {}", alert.getText());
			alert.dismiss();
		} catch (NoAlertPresentException e) {
			LOGGER.warn("No alert found to dismiss.");
		}
	}

	public void scrollToElement(WebElement element, String stepDesc) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		LOGGER.info("Scrolling to element: {}", element);
		js.executeScript("arguments[0].scrollIntoView(true);", element);
		logStep(stepDesc + " - Scrolled to Element", element);
	}

	public void jsClick(WebElement element, String stepDesc) {
		try {
			waitForElementVisible(element);
			logStep(stepDesc + " - Attempting click", element);
			LOGGER.info("Clicking element: {}", element);
			element.click();
		} catch (Exception e) {
			LOGGER.warn("Normal click failed, using JavaScript click.");
			JavascriptExecutor js = (JavascriptExecutor) driver;
			js.executeScript("arguments[0].click();", element);
			ExtentReportManager.getTest().log(Status.INFO,
					stepDesc + " - Fell back to JavaScript click for " + describeElement(element));
		}
	}

	public void waitForPageToLoad() {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(EsignetConfigManager.getTimeout()));
		wait.until(webDriver -> ((JavascriptExecutor) webDriver).executeScript("return document.readyState")
				.equals("complete"));
		LOGGER.info("Page fully loaded");
	}

	public void captureScreenshot(String filename) {
		try {
			TakesScreenshot ts = (TakesScreenshot) driver;
			File src = ts.getScreenshotAs(OutputType.FILE);
			File dest = new File(System.getProperty("user.dir") + "/screenshots/" + filename + ".png");
			Files.copy(src.toPath(), dest.toPath());
			LOGGER.info("Screenshot saved: {}", dest.getAbsolutePath());
		} catch (Exception e) {
			LOGGER.error("Failed to capture screenshot: {}", e.getMessage());
		}
	}

	public void enterTextJS(WebElement element, String text) {
		try {
			waitForElementVisible(element);

			((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});", element);
			new Actions(driver).moveToElement(element).click().perform();
			((JavascriptExecutor) driver).executeScript("arguments[0].value = '';", element);

			Actions actions = new Actions(driver);
			for (char c : text.toCharArray()) {
				actions.sendKeys(String.valueOf(c)).pause(Duration.ofMillis(150));
			}
			actions.perform();

			((JavascriptExecutor) driver)
					.executeScript("arguments[0].dispatchEvent(new Event('input', { bubbles: true }));"
							+ "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));"
							+ "arguments[0].blur();", element);

			String finalValue = element.getAttribute("value");
			if (!Objects.equals(finalValue, text)) {
				throw new RuntimeException(
						"Field value mismatch. Expected '" + text + "' but found '" + finalValue + "'.");
			}
		}

		catch (Exception e) {
			throw new RuntimeException("Failed to set filedvalue due to UI behavior", e);
		}
	}

	public void clearField(WebElement element) {
		waitForElementVisible(element);
		element.click();
		element.sendKeys(Keys.CONTROL + "a");
		element.sendKeys(Keys.DELETE);
	}

	public String getElementTagName(WebElement element) {
		waitForElementVisible(element);
		String text = element.getTagName();
		LOGGER.info("Retrieved text: {}", text);
		return text;
	}

	public static String authorizeUrl;

	public String getAuthorizeUrl() {
		return authorizeUrl;
	}

	public void setAuthorizeUrl(String url) {
		authorizeUrl = url;
		ClaimsUtil.parseFromUrl(url);
	}

	public List<String> getClaims(String type) {
		if (authorizeUrl == null) {
			System.out.println("Authorize URL not set.");
			return Collections.emptyList();
		}

		if ("mandatory".equalsIgnoreCase(type)) {
			return ClaimsUtil.getMandatoryClaims();
		} else {
			return ClaimsUtil.getVoluntaryClaims();
		}
	}

	public boolean isElementDisplayed(WebElement element) {
		try {
			return element.isDisplayed();
		} catch (Exception e) {
			return false;
		}
	}

	public String getTooltipText(By iconLocator, By tooltipLocator) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(5));

		WebElement icon = wait.until(ExpectedConditions.visibilityOfElementLocated(iconLocator));
		new Actions(driver).moveToElement(icon).perform();

		WebElement tooltip = wait.until(ExpectedConditions.visibilityOfElementLocated(tooltipLocator));
		return tooltip.getText();
	}

}