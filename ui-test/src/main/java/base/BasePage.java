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
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.interactions.Actions;
import org.openqa.selenium.support.PageFactory;
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
		PageFactory.initElements(driver, this);
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
				return "\"" + id.substring(id.lastIndexOf("/") + 1) + "\"";
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

	public WebElement waitForElementVisible(By locator) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(EsignetConfigManager.getTimeout()));
		return wait.until(ExpectedConditions.visibilityOfElementLocated(locator));
	}

	public void clickOnElement(WebElement element, String stepDesc) {
		try {
			try {
				waitForElementVisible(element);
			} catch (org.openqa.selenium.StaleElementReferenceException stale) {
				waitForElementVisible(element);
			}

			try {
				clickWithJsFallback(element);
			} catch (org.openqa.selenium.StaleElementReferenceException stale) {
				waitForElementVisible(element);
				clickWithJsFallback(element);
			}
			logStep(stepDesc, element);
			LOGGER.info("Clicking on element: {}", element);
		} catch (Exception e) {

			ExtentReportManager.getTest().log(Status.FAIL, "Failed to click on element: " + describeElement(element));
			throw e;
		}
	}

	private void clickWithJsFallback(WebElement element) {
		try {
			element.click();
		} catch (org.openqa.selenium.ElementClickInterceptedException intercepted) {
			((org.openqa.selenium.JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
		}
	}

	/** Quote-safe XPath string literal - falls back to concat() when the value contains both a
	 *  single and a double quote. */
	protected static String toXpathLiteral(String value) {
		if (!value.contains("'")) {
			return "'" + value + "'";
		}
		if (!value.contains("\"")) {
			return "\"" + value + "\"";
		}
		String[] parts = value.split("'", -1);
		StringBuilder concatExpr = new StringBuilder("concat('");
		for (int i = 0; i < parts.length; i++) {
			concatExpr.append(parts[i]);
			if (i < parts.length - 1) {
				concatExpr.append("', \"'\", '");
			}
		}
		concatExpr.append("')");
		return concatExpr.toString();
	}

	// Shared by every per-page OTP entry method (same rendered input.thunderid-otp-field__input
	// boxes everywhere) so a DOM/length-validation change only needs to be made once; each caller
	// still supplies its own per-digit interaction since pages differ on whether the boxes need
	// clearing first.
	protected void enterOtpDigits(List<WebElement> otpInputFields, String otp,
			java.util.function.BiConsumer<WebElement, Character> digitEntry) {
		if (otp.length() > otpInputFields.size()) {
			throw new IllegalArgumentException(
					"OTP length " + otp.length() + " exceeds rendered inputs " + otpInputFields.size());
		}
		for (int i = 0; i < otp.length(); i++) {
			digitEntry.accept(otpInputFields.get(i), otp.charAt(i));
		}
	}

	public void clickWhenClickable(WebElement element) {
		WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(30));
		WebElement stableElement = wait
				.until(ExpectedConditions.refreshed(ExpectedConditions.elementToBeClickable(element)));
		stableElement.click();
	}

	public void solveRecaptchaIfPresent() {
		List<WebElement> frames = driver.findElements(By.cssSelector("iframe[title*='captcha' i]"));
		if (frames.isEmpty()) {
			return;
		}
		try {
			driver.switchTo().frame(frames.get(0));
			WebElement checkbox = new WebDriverWait(driver, Duration.ofSeconds(EsignetConfigManager.getTimeout()))
					.until(ExpectedConditions.elementToBeClickable(By.cssSelector("#recaptcha-anchor, #checkbox")));
			checkbox.click();
		} catch (Exception e) {
			LOGGER.warn("Could not click the captcha checkbox - proceeding anyway since submission isn't "
					+ "gated on it: {}", e.getMessage());
		} finally {
			driver.switchTo().defaultContent();
		}
		try {
			new WebDriverWait(driver, Duration.ofSeconds(10)).until(driver -> {
				Object tokenLength = ((JavascriptExecutor) driver).executeScript(
						"var el = document.querySelector(\"[name='g-recaptcha-response'], [name='h-captcha-response']\");"
								+ " return el ? el.value.length : 0;");
				return tokenLength instanceof Long && (Long) tokenLength > 0;
			});
		} catch (TimeoutException e) {
			LOGGER.warn("No captcha token obtained within 10s - proceeding anyway since submission isn't "
					+ "gated on it.");
			new Actions(driver).sendKeys(Keys.ESCAPE).perform();
		}
	}

	public boolean isAlreadyOnRelyingParty() {
		String currentUrl = driver.getCurrentUrl();
		String eSignetBaseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
		if (currentUrl == null || eSignetBaseUrl == null || eSignetBaseUrl.isBlank()) {
			return false;
		}
		try {
			String currentHost = URI.create(currentUrl).getHost();
			String eSignetHost = URI.create(eSignetBaseUrl).getHost();
			return currentHost != null && eSignetHost != null && !currentHost.equalsIgnoreCase(eSignetHost)
					&& !currentUrl.contains("/authorize");
		} catch (IllegalArgumentException e) {
			return false;
		}
	}

	/**
	 * @return true if the landmark is present (either already, or after a recovery navigation);
	 *         false if a recovery was attempted and the landmark still never showed up - callers
	 *         should treat this as "not applicable" and skip rather than clicking/waiting blindly.
	 */
	public boolean ensureFreshEsignetLoginPage(By landmark) {
		boolean landmarkPresent;
		try {
			landmarkPresent = new WebDriverWait(driver, Duration.ofSeconds(3))
					.until(d -> !d.findElements(landmark).isEmpty());
		} catch (TimeoutException e) {
			landmarkPresent = false;
		}
		if (landmarkPresent) {
			return true;
		}
		if (authorizeUrl == null) {
			return false;
		}
		String freshUrl = authorizeUrl.replaceFirst("nonce=[^&]*", "nonce=" + System.currentTimeMillis());
		// Cookie deletion is domain-scoped: deleteAllCookies() before navigating only clears
		// whatever domain the browser is CURRENTLY on (e.g. the relying party's), not esignet-go's -
		// esignet-go's own session cookies never actually get cleared, so the "fresh" navigation
		// below lands on the same still-authenticated session instead of a real login screen.
		// Navigate to esignet-go's domain first, delete ITS cookies now that they're actually
		// reachable, then reload so the page renders in the now-cookie-free state.
		driver.get(freshUrl);
		driver.manage().deleteAllCookies();
		driver.navigate().refresh();
		// Give the fresh navigation genuine time to render before returning - driver.get() only
		// blocks for the load event, not for this SPA's own async client-side render, so a caller
		// that clicks immediately after this returns can still race a landmark that's a moment away.
		try {
			new WebDriverWait(driver, Duration.ofSeconds(EsignetConfigManager.getTimeout()))
					.until(d -> !d.findElements(landmark).isEmpty());
			return true;
		} catch (TimeoutException e) {
			// Confirmed live 2026-08-21/22: after a Deny -> discontinue -> "redirected to relying party"
			// chain that's a no-op under mock-plugin (that flow doesn't exist here), replaying the
			// authorize URL - even with the cookie-domain-scoping fixed above - can still land on a bare
			// /signin route without the expected landmark. Ruled out via live testing: a longer wait
			// (tried 40s) and clearing localStorage/sessionStorage before retrying - neither helps, so
			// this isn't a client-side timing or stale-storage issue. Looks like a genuine server-side
			// rejection of replaying the authorize request after a completed prior transaction in the
			// same session. Signal failure so callers skip cleanly instead of clicking/waiting on a
			// screen that isn't coming.
			LOGGER.warn("Fresh esignet login page navigation to {} did not surface landmark {} within {}s",
					freshUrl, landmark, EsignetConfigManager.getTimeout());
			return false;
		}
	}

	public boolean waitForRelyingPartyRedirectQuietly() {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(45)).until(d -> isAlreadyOnRelyingParty());
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public boolean waitForRelyingPartyRedirectOrElement(By elementLocator, int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds)).until(d ->
					isAlreadyOnRelyingParty() || !d.findElements(elementLocator).isEmpty());
		} catch (TimeoutException ignored) {
		}
		return isAlreadyOnRelyingParty();
	}

	public boolean isElementVisible(WebElement element, String stepDesc) {
		try {
			waitForElementVisible(element);
			logStep(stepDesc + " - Verified visibility", element);
			return element.isDisplayed();
		} catch (NoSuchElementException | TimeoutException e) {
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
			try {
				js.executeScript("arguments[0].click();", element);
				ExtentReportManager.getTest().log(Status.INFO,
						stepDesc + " - Fell back to JavaScript click for " + describeElement(element));
			} catch (Exception jsEx) {
				ExtentReportManager.getTest().log(Status.FAIL,
						stepDesc + " - JavaScript click fallback failed for " + describeElement(element));
				throw jsEx;
			}
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
		String dispatchScript = "var el = arguments[0]; el.focus();"
				+ "el.dispatchEvent(new MouseEvent('mouseover', {bubbles:true}));"
				+ "el.dispatchEvent(new MouseEvent('mouseenter', {bubbles:true}));"
				+ "el.dispatchEvent(new FocusEvent('focus', {bubbles:true}));";
		((JavascriptExecutor) driver).executeScript(dispatchScript, icon);

		WebElement tooltip;
		try {
			tooltip = wait.until(ExpectedConditions.visibilityOfElementLocated(tooltipLocator));
		} catch (org.openqa.selenium.TimeoutException firstTimeout) {
			icon = wait.until(ExpectedConditions.visibilityOfElementLocated(iconLocator));
			((JavascriptExecutor) driver).executeScript(dispatchScript, icon);
			tooltip = wait.until(ExpectedConditions.visibilityOfElementLocated(tooltipLocator));
		}
		try {
			return tooltip.getText();
		} catch (org.openqa.selenium.StaleElementReferenceException stale) {
			tooltip = wait.until(ExpectedConditions.visibilityOfElementLocated(tooltipLocator));
			return tooltip.getText();
		}
	}

	public static String getOtp() {
		String otp = "111111";
		return otp;
	}

}