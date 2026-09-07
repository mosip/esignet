import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import io.github.bonigarcia.wdm.WebDriverManager;

/**
 * Reproduces testriq ChromeDriver session start. Run inside the Alpine Chromium
 * image with Selenium 4.14.1 (same as uitest-esignet).
 */
public class ChromeSessionSmoke {
	public static void main(String[] args) {
		String mode = args.length > 0 ? args[0] : "prefer-system";
		boolean useWdm = "wdm".equals(mode);
		String binary = firstExisting("/usr/bin/chromium", "/usr/bin/chromium-browser", "/usr/bin/google-chrome");
		String systemDriver = firstExisting("/usr/bin/chromedriver", "/usr/lib/chromium/chromedriver");
		System.out.println("mode=" + mode + " binary=" + binary + " os=" + System.getProperty("os.name")
				+ " java=" + System.getProperty("java.version"));
		if (useWdm) {
			System.out.println("Using WebDriverManager.chromedriver().setup() (broken on Alpine testriq)");
			WebDriverManager.chromedriver().setup();
			System.out.println("webdriver.chrome.driver=" + System.getProperty("webdriver.chrome.driver"));
		} else {
			if (systemDriver == null) {
				throw new IllegalStateException("No system ChromeDriver found");
			}
			System.setProperty("webdriver.chrome.driver", systemDriver);
			System.out.println("Using system ChromeDriver " + systemDriver);
		}

		ChromeOptions options = new ChromeOptions();
		if (binary != null) {
			options.setBinary(binary);
		}
		options.addArguments("--use-fake-ui-for-media-stream");
		options.addArguments("--use-fake-device-for-media-stream");
		options.addArguments("--enable-media-stream");
		options.addArguments("--headless=new");
		options.addArguments("--disable-gpu");
		options.addArguments("--window-size=1920x1080");
		options.addArguments("--no-sandbox");
		options.addArguments("--disable-dev-shm-usage");
		options.addArguments("--remote-debugging-port=0");
		Logger.getLogger("org.openqa.selenium").setLevel(Level.INFO);
		try {
			WebDriver driver = new ChromeDriver(options);
			System.out.println("PASS session=" + ((ChromeDriver) driver).getSessionId()
					+ " title=" + driver.getTitle());
			driver.quit();
		} catch (Exception e) {
			if (useWdm && systemDriver != null) {
				System.out.println("WDM failed (" + e.getMessage() + "); retrying with " + systemDriver);
				System.setProperty("webdriver.chrome.driver", systemDriver);
				WebDriver driver = new ChromeDriver(options);
				System.out.println("PASS-FALLBACK session=" + ((ChromeDriver) driver).getSessionId());
				driver.quit();
				return;
			}
			System.out.println("FAIL " + e.getClass().getName() + ": " + e.getMessage());
			Throwable c = e.getCause();
			while (c != null) {
				System.out.println("caused by " + c.getClass().getName() + ": " + c.getMessage());
				c = c.getCause();
			}
			e.printStackTrace(System.out);
			System.exit(1);
		}
	}

	private static String firstExisting(String... paths) {
		for (String path : paths) {
			File file = new File(path);
			if (file.exists()) {
				return file.getAbsolutePath();
			}
		}
		return null;
	}
}
