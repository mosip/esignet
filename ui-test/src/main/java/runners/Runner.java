package runners;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.runner.RunWith;
import org.testng.TestNG;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.aventstack.extentreports.service.ExtentService;

import io.cucumber.junit.Cucumber;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import io.cucumber.testng.FeatureWrapper;
import io.cucumber.testng.PickleWrapper;
import io.mosip.testrig.apirig.testrunner.OTPListener;
import utils.BaseTestUtil;
import utils.EsignetConfigManager;
import utils.ExtentReportManager;

@RunWith(Cucumber.class)
@CucumberOptions(
        features = {"classpath:featurefiles"},
        glue = {"stepdefinitions", "base"},
        monochrome = true,
        plugin = {"pretty",
        			"html:reports",
        			"html:target/cucumber.html", "json:target/cucumber.json",
        			"summary","com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter:"}
//      tags = "@smoke"
)
public class Runner extends AbstractTestNGCucumberTests {
	private static final Logger LOGGER = Logger.getLogger(BaseTestUtil.class.getName());

	@Override
	@DataProvider(parallel = true, name = "scenarios")
	public Object[][] scenarios() {
		int threadCount = Integer.parseInt(EsignetConfigManager.getproperty("threadCount"));

		System.out.println("Executing with thread count: " + threadCount);
		LOGGER.info("Executing DataProvider with thread count: " + threadCount);

		System.setProperty("dataproviderthreadcount", String.valueOf(threadCount));

		Object[][] base = super.scenarios();
		boolean runMultipleBrowsers = Boolean.parseBoolean(EsignetConfigManager.getproperty("runMultipleBrowsers"));
		List<String> browsers = BaseTestUtil.getSupportedLocalBrowsers();

		if (runMultipleBrowsers && base.length > 0 && browsers != null && !browsers.isEmpty()) {
			List<Object[]> expanded = new ArrayList<>();
			for (Object[] scenario : base) {
				for (String browser : browsers) {
					expanded.add(new Object[] { scenario[0], scenario[1], browser });
				}
			}
			return expanded.toArray(new Object[0][]);
		}

		// Single browser fallback
		List<Object[]> fallback = new ArrayList<>();
		for (Object[] scenario : base) {
			fallback.add(new Object[] { scenario[0], scenario[1], browsers.get(0) });
		}

		System.setProperty("testng.threadcount", String.valueOf(EsignetConfigManager.getproperty("threadCount")));

		return fallback.toArray(new Object[0][]);
	}

	@Test(dataProvider = "scenarios")
	public void runCustomScenario(PickleWrapper pickle, FeatureWrapper feature, String browser) throws Throwable {
		BaseTestUtil.setThreadLocalBrowser(browser);
		super.runScenario(pickle, feature);
	}

	@Override
	@Test(enabled = false)
	public void runScenario(PickleWrapper pickle, FeatureWrapper feature) {
		// Disable default runner to avoid conflict
	}

	public static void main(String[] args) {
		OTPListener otpListener = new OTPListener();
		try {
			LOGGER.info("** ------------- Esignet UI Automation run started---------------------------- **");
			EsignetConfigManager.init();
			ExtentReportManager.initReport();
			otpListener.run();
			startTestRunner();
		} catch (Exception e) {
			LOGGER.severe("Exception " + e.getMessage());
		}
		
		otpListener.bTerminate = true;
		System.exit(0);
	}

	public static void startTestRunner() {
		File homeDir = null;
		String os = System.getProperty("os.name");
		LOGGER.info(os);

		if (getRunType().contains("IDE") || os.toLowerCase().contains("windows")) {
			homeDir = new File(System.getProperty("user.dir") + "/testNgXmlFiles");
			LOGGER.info("IDE :" + homeDir);
		} else {
			File dir = new File(System.getProperty("user.dir"));
			homeDir = new File(dir.getParent() + "/mosip/testNgXmlFiles");
			LOGGER.info("ELSE :" + homeDir);
		}

		File[] files = homeDir.listFiles();
		if (files != null) {
			for (File file : files) {
				if (file.getName().toLowerCase().contains("testng") && file.getName().endsWith(".xml")) {
					TestNG runner = new TestNG();
					List<String> suitefiles = new ArrayList<>();
					suitefiles.add(file.getAbsolutePath());

					runner.setTestSuites(suitefiles);
					runner.setOutputDirectory("testng-report");
					System.getProperties().setProperty("testng.output.dir", "testng-report");

					LOGGER.info("Running suite: " + file.getName());

					try (InputStream input = Thread.currentThread().getContextClassLoader()
							.getResourceAsStream("extent.properties")) {
						Properties prop = new Properties();
						if (input != null) {
							prop.load(input);
							for (String name : prop.stringPropertyNames()) {
								System.setProperty(name, prop.getProperty(name));
							}
						} else {
							LOGGER.severe("extent.properties not found in classpath.");
						}
					} catch (IOException ex) {
						LOGGER.log(Level.SEVERE, "Error loading extent.properties", ex);
					}
					ExtentService.getInstance();

					runner.run();
				}
			}
		} else {
			LOGGER.severe("No files found in directory: " + homeDir);
		}
	}

	public static String getRunType() {
		if (Runner.class.getResource("Runner.class").getPath().contains(".jar"))
			return "JAR";
		else
			return "IDE";
	}
}