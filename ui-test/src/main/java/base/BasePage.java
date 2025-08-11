package base;

import java.io.File;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;

import org.openqa.selenium.Alert;
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

import utils.EsignetConfigManager;
import utils.WaitUtil;

public class BasePage {
	protected WebDriver driver;
	private static final Logger LOGGER = LoggerFactory.getLogger(BasePage.class);

	public BasePage(WebDriver driver) {
		this.driver = driver;
	}

	public void waitForElementVisible(WebElement element) {
		WaitUtil.waitForVisibility(driver, element);
	}

	public void clickOnElement(WebElement element) {
		waitForElementVisible(element);
		LOGGER.info("Clicking on element: {}", element);
		element.click();
	}

	public boolean isElementVisible(WebElement element) {
		try {
			waitForElementVisible(element);
			return element.isDisplayed();
		} catch (NoSuchElementException e) {
			LOGGER.warn("Element not visible: {}", element);
			return false;
		}
	}

	public String getText(WebElement element) {
		waitForElementVisible(element);
		String text = element.getText();
		LOGGER.info("Retrieved text: {}", text);
		return text;
	}

	public boolean isButtonEnabled(WebElement element) {
		waitForElementVisible(element);
		boolean enabled = element.isEnabled();
		LOGGER.info("Button enabled status: {}", enabled);
		return enabled;
	}

	public void enterText(WebElement element, String text) {
		waitForElementVisible(element);
		element.clear();
		LOGGER.info("Entering text: {}", text);
		element.sendKeys(text);
	}

	public void refreshBrowser() {
		LOGGER.info("Refreshing browser");
		driver.navigate().refresh();
	}

	public void browserBackButton() {
		LOGGER.info("Navigating back");
		driver.navigate().back();
	}

	public void uploadFile(WebElement element, String filePath) {
		String absolutePath = Paths.get(System.getProperty("user.dir"), filePath).toString();
		waitForElementVisible(element);
		LOGGER.info("Uploading file: {}", absolutePath);
		element.sendKeys(absolutePath);
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

	public void scrollToElement(WebElement element) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		LOGGER.info("Scrolling to element: {}", element);
		js.executeScript("arguments[0].scrollIntoView(true);", element);
	}

	public void jsClick(WebElement element) {
		try {
			waitForElementVisible(element);
			element.click();
		} catch (Exception e) {
			LOGGER.warn("Normal click failed, using JavaScript click.");
			JavascriptExecutor js = (JavascriptExecutor) driver;
			js.executeScript("arguments[0].click();", element);
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
	        
	        ((JavascriptExecutor) driver).executeScript(
	            "arguments[0].dispatchEvent(new Event('input', { bubbles: true }));" +
	            "arguments[0].dispatchEvent(new Event('change', { bubbles: true }));" +
	            "arguments[0].blur();",
	            element
	        );
	        
	        String finalValue = element.getAttribute("value");
	        if (finalValue == null) {
	            throw new RuntimeException("Value was rejected by frontend (null).");
	        }
	    } 
	    
	    catch (Exception e) {
	    	throw new RuntimeException("Failed to set filedvalue due to UI behavior", e);
	    }
	}
	
	public String getElementValue(WebElement element) {
	    waitForElementVisible(element);
	    return element.getAttribute("value");
	}
	
	public String getElementAttribute(WebElement element, String attribute) {
	    waitForElementVisible(element);
	    return element.getAttribute(attribute);
	}
	
	public void clearField(WebElement element) {
	    waitForElementVisible(element);
	    element.click();
	    element.sendKeys(Keys.CONTROL + "a");
	    element.sendKeys(Keys.DELETE);
	}
}
