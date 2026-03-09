package pages;

import org.apache.log4j.Logger;
import org.openqa.selenium.*;
import org.openqa.selenium.remote.LocalFileDetector;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.openqa.selenium.support.ui.Select;

import utils.EsignetUtil;
import utils.EsignetUtil.RegisteredDetails;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class SignupFormDynamicFiller {

	private WebDriver driver;
	private static final Logger logger = Logger.getLogger(SignupFormDynamicFiller.class);
	SignUpPage signUpPage;

	public SignupFormDynamicFiller(WebDriver driver) {
		this.driver = driver;
		signUpPage = new SignUpPage(driver);
	}

	public void fillFormFromUiSpec(Map<String, Map<String, Object>> uiSpecFields) throws Exception {

		for (String fieldId : uiSpecFields.keySet()) {

			Map<String, Object> fieldProps = uiSpecFields.get(fieldId);
			String controlType = (String) fieldProps.get("controlType");

			if (fieldId.equalsIgnoreCase("phone")) {
				continue;
			}

			List<WebElement> matchingElements = driver
					.findElements(By.xpath("//*[@id='" + fieldId + "' or @data-field-id='" + fieldId + "']"));

			if (matchingElements.isEmpty()) {
				logger.info("No element found for fieldId: " + fieldId);
				continue;
			}

			WebElement element = matchingElements.get(0);
			String tag = element.getTagName();
			String type = element.getAttribute("type");

			if ("photo".equalsIgnoreCase(controlType)) {
				signUpPage.clickOnUploadPhoto();
				signUpPage.clickOnCaptureButton();
				continue;
			}

			if ("checkbox".equalsIgnoreCase(controlType) && matchingElements.size() > 1) {
				int index = new Random().nextInt(matchingElements.size());
				WebElement randomCheckbox = matchingElements.get(index);

				((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block:'center'});",
						randomCheckbox);
				((JavascriptExecutor) driver).executeScript("arguments[0].click();", randomCheckbox);

				continue;
			}

			if ("checkbox".equalsIgnoreCase(controlType)) {
				if (element.isDisplayed() && element.isEnabled() && !element.isSelected()) {
					((JavascriptExecutor) driver).executeScript("arguments[0].scrollIntoView({block: 'center'});",
							element);
					((JavascriptExecutor) driver).executeScript("arguments[0].click();", element);
				}
				continue;
			}

			if ("hidden".equalsIgnoreCase(type) || !element.isEnabled()) {
				continue;
			}

			if ("textbox".equalsIgnoreCase(controlType) && fieldId.toLowerCase().contains("name")) {
				EsignetUtil.FullName names = EsignetUtil.generateNamesFromUiSpec();

				for (WebElement nameField : matchingElements) {
					String lang = nameField.getAttribute("data-lang");

					nameField.clear();

					if ("eng".equalsIgnoreCase(lang)) {
						nameField.sendKeys(names.english);
					} else if ("khm".equalsIgnoreCase(lang)) {
						nameField.sendKeys(names.khmer);
						RegisteredDetails.setFullName(names.khmer);
					}
				}
				continue;
			}

			if ("password".equalsIgnoreCase(controlType)) {
				String password = EsignetUtil.generateValidPasswordFromActuator();
				RegisteredDetails.setPassword(password);
				element.clear();
				element.sendKeys(password);

				WebElement confirmPwd = driver.findElement(By.id("password_confirm"));
				confirmPwd.clear();
				confirmPwd.sendKeys(password);
				continue;
			}

			if ("textbox".equalsIgnoreCase(controlType) && fieldId.equalsIgnoreCase("email")) {
				String email = EsignetUtil.generateEmailFromRegex(fieldId);
				element.clear();
				element.sendKeys(email);
				continue;
			}

			if ("dropdown".equalsIgnoreCase(controlType)) {
				Select dropdown = new Select(element);
				List<WebElement> options = dropdown.getOptions();
				if (options.size() > 1) {
					dropdown.selectByIndex(new Random().nextInt(options.size() - 1) + 1);
				}
				continue;
			}

			if ("date".equalsIgnoreCase(controlType)) {
				String dob = EsignetUtil.getRandomDOB().replace("-", "/");
				WebElement visibleDob = element;
				JavascriptExecutor js = (JavascriptExecutor) driver;
				js.executeScript("arguments[0].removeAttribute('readonly')", visibleDob);
				js.executeScript("arguments[0].value=arguments[1];", visibleDob, dob);

				continue;
			}

			if ("textbox".equalsIgnoreCase(controlType) || "textarea".equalsIgnoreCase(controlType)) {

				if ("hidden".equalsIgnoreCase(type) || !element.isEnabled())
					continue;

				String regex = EsignetUtil.getRegexForField(fieldId);
				String value = EsignetUtil.generateValueFromRegex(regex);

				element.clear();
				element.sendKeys(value);
				continue;
			}

			if ("radio".equalsIgnoreCase(controlType)) {

				List<WebElement> radios = driver.findElements(By.xpath(
						"//input[@type='radio' and (@name='" + fieldId + "' or @data-field-id='" + fieldId + "')]"));

				if (!radios.isEmpty()) {
					radios.get(new Random().nextInt(radios.size())).click();
				}
				continue;
			}

			if ("fileupload".equalsIgnoreCase(controlType)) {
				selectDocumentType(fieldId);
				uploadFile(fieldId, matchingElements);
				continue;
			}
		}
	}

	private void uploadFile(String fieldId, List<WebElement> matchingElements) throws IOException {
		String fileName = fieldId.toLowerCase().contains("photo") ? "Photo.jpg" : "Passport.pdf";

		File tempFile = File.createTempFile("upload-", "-" + fileName);
		tempFile.deleteOnExit();

		String resourcePath = "config/" + fileName;
		try (InputStream resourceStream = getClass().getClassLoader().getResourceAsStream(resourcePath)) {
			if (resourceStream == null) {
				throw new IOException("Upload resource not found: " + resourcePath);
			}
			Files.copy(resourceStream, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}

		if (driver.getClass().getName().contains("RemoteWebDriver")) {
			((RemoteWebDriver) driver).setFileDetector(new LocalFileDetector());
		}

		WebElement uploadInput = driver.findElement(By.xpath("//input[@type='file' and (contains(@id,'" + fieldId
				+ "') or contains(@data-field-id,'" + fieldId + "'))]"));

		uploadInput.sendKeys(tempFile.getAbsolutePath());

		logger.info("Uploaded file for " + fieldId);
	}

	private void selectDocumentType(String fieldId) {
		List<WebElement> dropdowns = driver
				.findElements(By.xpath("//*[contains(@data-field-id,'" + fieldId + "')]//select"));
		if (dropdowns.isEmpty()) {
			logger.info("No Document Type dropdown found for " + fieldId);
			return;
		}
		Select select = new Select(dropdowns.get(0));
		List<WebElement> options = select.getOptions();
		if (options.size() > 1) {
			select.selectByIndex(1);
		} else if (!options.isEmpty()) {
			select.selectByIndex(0);
		}
		logger.info("Selected Document Type for " + fieldId);
	}
}
