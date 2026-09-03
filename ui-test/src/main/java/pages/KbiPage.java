package pages;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.openqa.selenium.By;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.Keys;
import org.openqa.selenium.TimeoutException;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chromium.ChromiumDriver;
import org.openqa.selenium.chromium.ChromiumNetworkConditions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;

import base.BasePage;

public class KbiPage extends BasePage {

	public KbiPage(WebDriver driver) {
		super(driver);
	}

	private By fieldInputLocator(String fieldId) {
		return By.xpath("//*[self::input or self::select or self::textarea][@id='" + fieldId + "' or @name='" + fieldId
				+ "' or @data-field-id='" + fieldId + "']");
	}

	public void waitForKbiForm(List<String> fieldIds) {
		if (fieldIds == null || fieldIds.isEmpty()) {
			return;
		}
		waitForElementVisible(fieldInputLocator(fieldIds.get(0)));
	}

	public boolean isFieldRendered(String fieldId) {
		return !driver.findElements(fieldInputLocator(fieldId)).isEmpty();
	}

	private WebElement findLabel(String fieldId) {
		List<By> labelLocators = List.of(
				By.xpath("//label[@for='" + fieldId + "']"),
				By.xpath("//*[@id='" + fieldId + "']/ancestor::*[self::div or self::label][1]//label"),
				By.xpath("//*[@id='" + fieldId + "']/preceding::label[1]"));

		for (By by : labelLocators) {
			List<WebElement> els = driver.findElements(by);
			if (!els.isEmpty()) {
				return els.get(0);
			}
		}
		return null;
	}

	public String getFieldLabel(String fieldId) {
		WebElement label = findLabel(fieldId);
		return label != null ? textExcludingRequiredMarker(label) : "";
	}

	private String textExcludingRequiredMarker(WebElement label) {
		Object result = ((JavascriptExecutor) driver).executeScript(
				"var el = arguments[0].cloneNode(true);"
						+ "el.querySelectorAll('[class*=\"required\"]').forEach(function(n){ n.remove(); });"
						+ "return el.textContent;",
				label);
		return result != null ? result.toString().trim() : "";
	}

	public void enterFieldValue(String fieldId, String value) {

		WebElement dateInput = findRealDateInput(fieldId);
		if (dateInput != null) {
			setNativeInputValue(dateInput, value);
			return;
		}
		WebElement el = driver.findElement(fieldInputLocator(fieldId));
		clearField(el);
		if (!value.isEmpty()) {
			el.sendKeys(value);
		}
	}

	private WebElement findRealDateInput(String fieldId) {
		List<WebElement> els = driver.findElements(By.cssSelector("input.real-date-input[name='" + fieldId
				+ "'], input.real-date-input[id='" + fieldId + "'], input.real-date-input[data-field-id='" + fieldId + "']"));
		if (!els.isEmpty()) {
			return els.get(0);
		}

		List<WebElement> visible = driver.findElements(fieldInputLocator(fieldId));
		if (visible.isEmpty()) {
			return null;
		}
		List<WebElement> nearby = visible.get(0)
				.findElements(By.xpath("ancestor::*[self::div or self::label][1]//input[contains(@class,'real-date-input')]"));
		return nearby.isEmpty() ? null : nearby.get(0);
	}

	private void setNativeInputValue(WebElement input, String value) {
		((JavascriptExecutor) driver).executeScript(
				"var input = arguments[0], value = arguments[1];"
						+ "var setter = Object.getOwnPropertyDescriptor(window.HTMLInputElement.prototype, 'value').set;"
						+ "setter.call(input, value);"
						+ "input.dispatchEvent(new Event('input', {bubbles: true}));"
						+ "input.dispatchEvent(new Event('change', {bubbles: true}));"
						+ "input.dispatchEvent(new Event('blur', {bubbles: true}));",
				input, value);
	}

	public void blurField(String fieldId) {
		driver.findElement(fieldInputLocator(fieldId)).sendKeys(Keys.TAB);
	}

	public void touchThenClearField(String fieldId) {

		WebElement dateInput = findRealDateInput(fieldId);
		if (dateInput != null) {
			setNativeInputValue(dateInput, "1990-01-01");
			setNativeInputValue(dateInput, "");
			return;
		}
		WebElement el = driver.findElement(fieldInputLocator(fieldId));
		el.sendKeys("x");
		clearField(el);
	}

	public String getFieldErrorMessage(String fieldId) {

		By errorLocator = By.xpath("//*[@id='" + fieldId
				+ "']/ancestor::div[contains(@class,'form-field')][1]//div[contains(@class,'error-message')]");
		List<WebElement> els = driver.findElements(errorLocator);
		return els.isEmpty() ? "" : els.get(0).getText().trim();
	}

	public String getRenderedInputType(String fieldId) {
		String script = "var id = arguments[0];"
				+ "if (document.querySelector('input.real-date-input[name=\"'+id+'\"]')"
				+ "    || document.querySelector('input#'+CSS.escape(id)+'.date-display-input')) return 'Date';"
				+ "var el = document.querySelector('#'+CSS.escape(id))"
				+ "    || document.querySelector('[name=\"'+id+'\"]')"
				+ "    || document.querySelector('[data-field-id=\"'+id+'\"]');"
				+ "if (!el) return 'NotRendered';"
				+ "var tag = el.tagName.toLowerCase();"
				+ "if (tag === 'select') return 'Dropdown';"
				+ "if (tag === 'textarea') return 'Text';"
				+ "if (tag === 'input') {"
				+ "  var t = (el.getAttribute('type') || 'text').toLowerCase();"
				+ "  if (t === 'checkbox') return 'Checkbox';"
				+ "  if (t === 'radio') return 'Radio';"
				+ "  if (t === 'email') return 'Email';"
				+ "  if (t === 'number') return 'Number';"
				+ "  if (t === 'date') return 'Date';"
				+ "  if (t === 'text') return 'Text';"
				+ "  return 'Unsupported:input[type=' + t + ']';"
				+ "}"
				+ "return 'Unsupported:' + tag;";
		Object result = ((JavascriptExecutor) driver).executeScript(script, fieldId);
		return result == null ? "NotRendered" : result.toString();
	}

	public List<String> getDropdownOptionTexts(String fieldId) {

		List<WebElement> options = driver.findElements(By.xpath("//select[@id='" + fieldId + "' or @name='" + fieldId
				+ "' or @data-field-id='" + fieldId + "']//option[not(contains(@class,'select-placeholder'))][@value!='']"));
		List<String> texts = new ArrayList<>();
		for (WebElement option : options) {
			String text = option.getText().trim();
			if (!text.isEmpty()) {
				texts.add(text);
			}
		}
		return texts;
	}

	public boolean isFieldMarkedRequired(String fieldId) {
		WebElement label = findLabel(fieldId);
		if (label == null) {
			return false;
		}
		List<WebElement> req = label.findElements(By.xpath(".//span[contains(@class,'required')]"));
		return !req.isEmpty() && req.get(0).isDisplayed();
	}

	public boolean isNetworkControlSupported() {
		return driver instanceof ChromiumDriver;
	}

	public void setOffline(boolean offline) {
		if (!offline) {

			((ChromiumDriver) driver).deleteNetworkConditions();
			return;
		}
		ChromiumNetworkConditions conditions = new ChromiumNetworkConditions();
		conditions.setOffline(true);
		((ChromiumDriver) driver).setNetworkConditions(conditions);
	}

	public boolean isNetworkErrorShown(int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(By.id("try_again")));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	public String getNetworkErrorText() {
		List<WebElement> paragraphs = driver.findElements(By.xpath("//button[@id='try_again']/preceding-sibling::p"));
		List<String> texts = new ArrayList<>();
		for (WebElement p : paragraphs) {
			String text = p.getText().trim();
			if (!text.isEmpty()) {
				texts.add(text);
			}
		}
		return String.join(" ", texts);
	}

	public void clickTryAgain() {
		clickOnElement(driver.findElement(By.id("try_again")), "Clicked Try Again on the network error screen");
	}

	private static final By LOGIN_BUTTON = By.cssSelector(
			"#form-submit-button, #kbi_authenticate, #action_submit, button[type='submit']");

	public void clickLoginButton() {
		clickOnElement(findLoginButton(), "Clicked KBI Login button");
	}

	public boolean isLoginButtonEnabled() {
		return isButtonEnabled(findLoginButton(), "Verified KBI login button state");
	}

	public String getLoginButtonText() {
		return findLoginButton().getText().trim();
	}

	private WebElement findLoginButton() {
		List<WebElement> buttons = driver.findElements(LOGIN_BUTTON);
		for (WebElement button : buttons) {
			if (button.isDisplayed()) {
				return button;
			}
		}
		return driver.findElement(By.id("form-submit-button"));
	}

	public String getFieldValue(String fieldId) {
		WebElement dateInput = findRealDateInput(fieldId);
		WebElement el = dateInput != null ? dateInput : driver.findElement(fieldInputLocator(fieldId));
		Object value = ((JavascriptExecutor) driver).executeScript("return arguments[0].value;", el);
		return value != null ? value.toString() : "";
	}

	public List<String> getVisibleFieldIds() {
		List<String> ids = new ArrayList<>();
		List<WebElement> fields = driver.findElements(
				By.cssSelector("form input:not([type='hidden']):not([type='checkbox']), form select, form textarea"));
		if (fields.isEmpty()) {
			fields = driver.findElements(By.cssSelector(
					"input:not([type='hidden']):not([type='checkbox']):not([type='radio']), select, textarea"));
		}
		for (WebElement field : fields) {
			if (!field.isDisplayed()) {
				continue;
			}
			String id = field.getAttribute("id");
			if (id == null || id.isBlank()) {
				id = field.getAttribute("name");
			}
			if (id == null || id.isBlank()) {
				id = field.getAttribute("data-field-id");
			}
			if (id != null && !id.isBlank() && !id.toLowerCase().contains("language")
					&& !id.toLowerCase().contains("captcha")) {
				ids.add(id);
			}
		}
		return ids;
	}

	public boolean areFieldsEmpty(List<String> fieldIds) {
		for (String fieldId : fieldIds) {
			if (!getFieldValue(fieldId).isEmpty()) {
				return false;
			}
		}
		return true;
	}
}
