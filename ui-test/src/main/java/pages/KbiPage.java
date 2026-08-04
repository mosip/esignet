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

/** KBI (Knowledge-Based Identity) login form. */
public class KbiPage extends BasePage {

	public KbiPage(WebDriver driver) {
		super(driver);
	}

	// A KBI field renders as an input/select/textarea keyed by the schema field id. Match on id,
	// name, or data-field-id to stay resilient to how the frontend wires the attribute.
	private By fieldInputLocator(String fieldId) {
		return By.xpath("//*[self::input or self::select or self::textarea][@id='" + fieldId + "' or @name='" + fieldId
				+ "' or @data-field-id='" + fieldId + "']");
	}

	/** Blocks until the KBI form has rendered by waiting for the first schema field to be visible. */
	public void waitForKbiForm(List<String> fieldIds) {
		if (fieldIds == null || fieldIds.isEmpty()) {
			return;
		}
		waitForElementVisible(fieldInputLocator(fieldIds.get(0)));
	}

	public boolean isFieldRendered(String fieldId) {
		return !driver.findElements(fieldInputLocator(fieldId)).isEmpty();
	}

	/** Visible label for a field, or "" if none found. */
	public String getFieldLabel(String fieldId) {
		List<By> labelLocators = List.of(
				By.xpath("//label[@for='" + fieldId + "']"),
				By.xpath("//*[@id='" + fieldId + "']/ancestor::*[self::div or self::label][1]//label"),
				By.xpath("//*[@id='" + fieldId + "']/preceding::label[1]"));

		for (By by : labelLocators) {
			List<WebElement> els = driver.findElements(by);
			if (!els.isEmpty()) {
				String text = textExcludingRequiredMarker(els.get(0));
				if (!text.isEmpty()) {
					return text;
				}
			}
		}
		return "";
	}

	// Strips the nested "*" required-marker span so it doesn't get included in the label text.
	private String textExcludingRequiredMarker(WebElement label) {
		Object result = ((JavascriptExecutor) driver).executeScript(
				"var el = arguments[0].cloneNode(true);"
						+ "el.querySelectorAll('.required').forEach(function(n){ n.remove(); });"
						+ "return el.textContent;",
				label);
		return result != null ? result.toString().trim() : "";
	}

	public void enterFieldValue(String fieldId, String value) {
		// A date field renders as a readonly display input plus a hidden native type="date" input
		// (class "real-date-input") whose calendar is the browser's own popup - unreachable from the
		// DOM. Set the native input directly (it takes yyyy-MM-dd, our fixture format) instead.
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

	// The native date input backing a date field, or null for ordinary text fields.
	private WebElement findRealDateInput(String fieldId) {
		List<WebElement> els = driver
				.findElements(By.cssSelector("input.real-date-input[name='" + fieldId + "']"));
		return els.isEmpty() ? null : els.get(0);
	}

	// Sets an input's value through React's own value setter so its onChange fires (a plain
	// element.value assignment doesn't). Empty string clears it. Also fires blur for touched-state.
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

	// Tabs out of the field to trigger any on-blur validation.
	public void blurField(String fieldId) {
		driver.findElement(fieldInputLocator(fieldId)).sendKeys(Keys.TAB);
	}

	// clear() on a field that's already empty is a DOM no-op - nothing to remove, so no
	// input/change event fires, and the form never marks the field "touched" for validation.
	// Typing a throwaway character first makes the clear() that follows fire a real event.
	public void touchThenClearField(String fieldId) {
		// Date fields have no typeable text input - set the native input to a value then clear it,
		// which leaves it touched-and-empty so its required error shows.
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

	/** Inline error text shown below a field (the ".error-message" div in its form-field container), or "". */
	public String getFieldErrorMessage(String fieldId) {
		// Scope to the field's form-field container rather than #id siblings - date fields nest the
		// input inside a wrapper div, so the error div isn't a direct sibling of the input.
		By errorLocator = By.xpath("//*[@id='" + fieldId
				+ "']/ancestor::div[contains(@class,'form-field')][1]//div[contains(@class,'error-message')]");
		List<WebElement> els = driver.findElements(errorLocator);
		return els.isEmpty() ? "" : els.get(0).getText().trim();
	}

	// Classifies the control the schema field actually rendered as, into one of the UI-schema-supported
	// input types: Text, Email, Number, Checkbox, Radio, Dropdown, Date. Returns "NotRendered" if no
	// control exists, or "Unsupported:<detail>" for anything outside those types. A date field is the
	// readonly display input plus a hidden native type="date" input (see enterFieldValue).
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

	/** Visible option texts of a native-select dropdown field (excluding a blank placeholder), or empty. */
	public List<String> getDropdownOptionTexts(String fieldId) {
		List<WebElement> options = driver.findElements(By.xpath("//select[@id='" + fieldId + "' or @name='" + fieldId
				+ "' or @data-field-id='" + fieldId + "']//option"));
		List<String> texts = new ArrayList<>();
		for (WebElement option : options) {
			String text = option.getText().trim();
			if (!text.isEmpty()) {
				texts.add(text);
			}
		}
		return texts;
	}

	/** Whether a field's label shows the visible required-marker (the "*" span). */
	public boolean isFieldMarkedRequired(String fieldId) {
		List<WebElement> req = driver
				.findElements(By.xpath("//label[@for='" + fieldId + "']//span[contains(@class,'required')]"));
		return !req.isEmpty() && req.get(0).isDisplayed();
	}

	// Network-condition control (offline simulation) is only available on a local Chromium driver -
	// not on a remote/BrowserStack session.
	public boolean isNetworkControlSupported() {
		return driver instanceof ChromiumDriver;
	}

	public void setOffline(boolean offline) {
		ChromiumNetworkConditions conditions = new ChromiumNetworkConditions();
		conditions.setOffline(offline);
		((ChromiumDriver) driver).setNetworkConditions(conditions);
	}

	// The offline "Network Error!" screen auto-appears a few seconds after the connection drops; its
	// Try Again button reloads the schema fresh.
	public boolean isNetworkErrorShown(int timeoutSeconds) {
		try {
			new WebDriverWait(driver, Duration.ofSeconds(timeoutSeconds))
					.until(ExpectedConditions.visibilityOfElementLocated(By.id("try_again")));
			return true;
		} catch (TimeoutException e) {
			return false;
		}
	}

	/** The heading + message text shown on the offline "Network Error!" screen, or "". */
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

	public void clickLoginButton() {
		clickOnElement(driver.findElement(By.xpath("//button[normalize-space()='Login']")), "Clicked KBI Login button");
	}
}
