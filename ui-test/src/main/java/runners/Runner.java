package runners;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.runner.RunWith;
import org.testng.TestNG;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import com.aventstack.extentreports.service.ExtentService;

import base.BaseTest;
import constants.ESignetConstants;
import io.cucumber.junit.Cucumber;
import io.cucumber.testng.AbstractTestNGCucumberTests;
import io.cucumber.testng.CucumberOptions;
import io.cucumber.testng.FeatureWrapper;
import io.cucumber.testng.PickleWrapper;
import io.mosip.testrig.apirig.dataprovider.BiometricDataProvider;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.testrunner.ExtractResource;
import io.mosip.testrig.apirig.testrunner.OTPListener;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.AuthTestsUtil;
import io.mosip.testrig.apirig.utils.CertsUtil;
import io.mosip.testrig.apirig.utils.JWKKeyUtil;
import io.mosip.testrig.apirig.utils.KeyCloakUserAndAPIKeyGeneration;
import io.mosip.testrig.apirig.utils.KeycloakUserManager;
import io.mosip.testrig.apirig.utils.MispPartnerAndLicenseKeyGeneration;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.PartnerRegistration;
import utils.BaseTestUtil;
import utils.EsignetConfigManager;
import utils.EsignetUtil;
import utils.ExtentReportManager;
import utils.LanguageUtil;

@RunWith(Cucumber.class)
@CucumberOptions(
		features = {
//				"classpath:featurefiles/ConsentPage.feature"
				"classpath:featurefiles"
		},
		glue = {"stepdefinitions", "base"},
        monochrome = true,
        plugin = {"pretty",
                "html:reports",
                "html:target/cucumber.html", "json:target/cucumber.json",
                "summary", "com.aventstack.extentreports.cucumber.adapter.ExtentCucumberAdapter:"}
//      tags = "@smoke"
)
public class Runner extends AbstractTestNGCucumberTests {
	private static final Logger LOGGER = Logger.getLogger(BaseTestUtil.class.getName());
	private static String cachedPath = null;
	public static String jarUrl = Runner.class.getProtectionDomain().getCodeSource().getLocation().getPath();
	public static boolean skipAll = false;
	public static Map<String, String> knownIssues = new ConcurrentHashMap<>();

	@Override
	@DataProvider(parallel = true, name = "scenarios")
	public Object[][] scenarios() {
		int threadCount = Integer.parseInt(EsignetConfigManager.getproperty("threadCount"));

		System.out.println("Executing with thread count: " + threadCount);
		LOGGER.info("Executing DataProvider with thread count: " + threadCount);

		System.setProperty("dataproviderthreadcount", String.valueOf(threadCount));

		Object[][] base = filterByFeatureFiles(super.scenarios());
		boolean runMultipleBrowsers = Boolean.parseBoolean(EsignetConfigManager.getproperty("runMultipleBrowsers"));
		List<String> browsers = BaseTestUtil.getSupportedLocalBrowsers();

		String lang = System.getProperty("currentRunLanguage");

		if (runMultipleBrowsers && base.length > 0 && !browsers.isEmpty()) {
			List<Object[]> expanded = new ArrayList<>();
			for (Object[] scenario : base) {
				for (String browser : browsers) {
					expanded.add(new Object[] { scenario[0], scenario[1], browser, lang });
				}
			}
			return expanded.toArray(new Object[0][]);
		}

		// Single browser fallback
		List<Object[]> fallback = new ArrayList<>();
		for (Object[] scenario : base) {
			fallback.add(new Object[] { scenario[0], scenario[1], browsers.getFirst(), lang });
		}

		System.setProperty("testng.threadcount", String.valueOf(EsignetConfigManager.getproperty("threadCount")));

		return fallback.toArray(new Object[0][]);
	}

	// featureFilesToExecute (config.properties) is a comma-separated list of feature file names
	// (without .feature extension); empty/unset means run every discovered scenario.
	private static Object[][] filterByFeatureFiles(Object[][] scenarios) {
		String featureFilesToExecute = EsignetConfigManager.getproperty("featureFilesToExecute");
		if (featureFilesToExecute == null || featureFilesToExecute.trim().isEmpty()) {
			return scenarios;
		}

		Set<String> requestedFeatures = new HashSet<>();
		for (String name : featureFilesToExecute.split(",")) {
			if (!name.trim().isEmpty()) {
				requestedFeatures.add(name.trim().toLowerCase(Locale.ROOT));
			}
		}

		if (requestedFeatures.isEmpty()) {
			return scenarios;
		}

		List<Object[]> filtered = new ArrayList<>();
		for (Object[] scenario : scenarios) {
			PickleWrapper pickle = (PickleWrapper) scenario[0];
			String uri = pickle.getPickle().getUri().toString();
			String fileName = uri.substring(Math.max(uri.lastIndexOf('/'), uri.lastIndexOf('\\')) + 1);
			if (fileName.toLowerCase(Locale.ROOT).endsWith(".feature")) {
				fileName = fileName.substring(0, fileName.length() - ".feature".length());
			}
			if (requestedFeatures.contains(fileName.toLowerCase(Locale.ROOT))) {
				filtered.add(scenario);
			}
		}

		if (filtered.isEmpty()) {
			LOGGER.warning("featureFilesToExecute=" + requestedFeatures
					+ " matched no scenarios out of " + scenarios.length
					+ " - check for a typo/stale entry; running all scenarios instead of silently running none.");
			return scenarios;
		}

		LOGGER.info("featureFilesToExecute=" + requestedFeatures + " selected " + filtered.size() + "/"
				+ scenarios.length + " scenarios");

		return filtered.toArray(new Object[0][]);
	}

	@Test(dataProvider = "scenarios")
	public void runCustomScenario(PickleWrapper pickle, FeatureWrapper feature, String browser, String lang)
			throws Throwable {
		BaseTestUtil.setThreadLocalBrowser(browser);
		BaseTestUtil.setThreadLocalLanguage(lang);
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

			BaseTestCase.setRunContext(getRunType(), jarUrl);
			ExtractResource.removeOldMosipTestTestResource();
			if (getRunType().equalsIgnoreCase("JAR")) {
				ExtractResource.extractCommonResourceFromJar();
			} else {
				ExtractResource.copyCommonResources();
			}

			AdminTestUtil.init();
			EsignetConfigManager.init();
			EsignetUtil.getPluginName();
			suiteSetup(getRunType());
			setLogLevels();
			otpListener.run();

			// Populates BaseTestCase.languageList from esignetSupportedLanguage - needed by both
			// plugins (e.g. $1STLANG$ template resolution during OIDC client creation), not just mock.
			EsignetUtil.getSupportedLanguage();

			if (EsignetUtil.pluginName.equals("mosipid")) {
				KeycloakUserManager.removeUser();
				KeycloakUserManager.createUsers();
				KeycloakUserManager.closeKeycloakInstance();
				AdminTestUtil.getRequiredField();

				PartnerRegistration.deleteCertificates();
				AdminTestUtil.createAndPublishPolicy();
				AdminTestUtil.createEditAndPublishPolicy();
				PartnerRegistration.deviceGeneration();

				BiometricDataProvider.generateBiometricTestData("Registration");
			}

			List<String> languages = new ArrayList<>();
			String runLang = EsignetConfigManager.getproperty("runLanguage");

			if (runLang != null && !runLang.trim().isEmpty()) {
				LOGGER.info("Using runLanguage from config: " + runLang);
				// split by comma and trim spaces
				String[] langs = runLang.split(",");
				for (String lang : langs) {
					if (!lang.trim().isEmpty()) {
						languages.add(lang.trim());
					}
				}
			} else {
				LOGGER.info("No runLanguage in config, loading from LanguageUtil");
				languages = LanguageUtil.supportedLanguages;
			}

			for (String lang : languages) {
				System.setProperty("currentRunLanguage", lang);
				resetCounters();
				ExtentReportManager.initReport(lang);

				LOGGER.info("=== Starting run for language: " + lang + " ===");
				startTestRunner();

				// flush & upload this language report
				ExtentReportManager.flushReport();
				BaseTest.pushReportsToS3(lang);
			}
			updateFeaturesPath();

		} catch (Exception e) {
			LOGGER.severe("Exception " + e.getMessage());
		}
		otpListener.bTerminate = true;

		if (EsignetUtil.pluginName.equals("mosipid")) {
			KeycloakUserManager.removeUser();
		}

		System.exit(0);
	}

	public static void suiteSetup(String runType) {
		BaseTestCase.initialize();
		LOGGER.info("Done with BeforeSuite and test case setup! su TEST EXECUTION!\n\n");

		if (!runType.equalsIgnoreCase("JAR")) {
			AuthTestsUtil.removeOldMosipTempTestResource();
		}

		BaseTestCase.currentModule = ESignetConstants.ESIGNETUI_MODULENAME + BaseTestCase.runContext;
		BaseTestCase.certsForModule = ESignetConstants.ESIGNETUI_MODULENAME + BaseTestCase.runContext;
		BaseTestCase.initializePMSDetails();
		AdminTestUtil.copymoduleSpecificAndConfigFile(ESignetConstants.ESIGNETUI_MODULENAME);
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
				TestNG runner = new TestNG();
				List<String> suitefiles = new ArrayList<>();
				if (file.getName().toLowerCase().contains("mastertestsuite")) {
					BaseTestCase.setReportName("esignet");
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

	public static void resetCounters() {
		BaseTest.totalCount = 0;
		BaseTest.passedCount = 0;
		BaseTest.failedCount = 0;
		BaseTest.knownIssueCount = 0;
	}

	public static String getGlobalResourcePath() {
		if (cachedPath != null) {
			return cachedPath;
		}

		String path = null;
		if (getRunType().equalsIgnoreCase("JAR")) {
			path = new File(jarUrl).getParentFile().getAbsolutePath() + "/MosipTestResource/MosipTemporaryTestResource";
		} else if (getRunType().equalsIgnoreCase("IDE")) {
			path = new File(Runner.class.getClassLoader().getResource("").getPath()).getAbsolutePath()
					+ "/MosipTestResource/MosipTemporaryTestResource";
			if (path.contains(ESignetConstants.TESTCLASSES))
				path = path.replace(ESignetConstants.TESTCLASSES, "classes");
		}

		if (path != null) {
			cachedPath = path;
			return path;
		} else {
			return "Global Resource File Path Not Found";
		}
	}

	public static String getResourcePath() {
		return getGlobalResourcePath();
	}

	private static void setLogLevels() {
		AdminTestUtil.setLogLevel();
		OutputValidationUtil.setLogLevel();
		PartnerRegistration.setLogLevel();
		KeyCloakUserAndAPIKeyGeneration.setLogLevel();
		MispPartnerAndLicenseKeyGeneration.setLogLevel();
		JWKKeyUtil.setLogLevel();
		CertsUtil.setLogLevel();
	}

	public static void updateFeaturesPath() {
		File homeDir = null;
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("windows")) {
			System.setProperty("cucumber.features", "src\\test\\resources\\featurefiles\\");
		} else {
			System.setProperty("cucumber.features", "/home/mosip/featurefiles/");
		}
	}
	
	static {
		loadKnownIssues();
	}

	private static void loadKnownIssues() {
		InputStream knownIssuesStream = Runner.class.getClassLoader().getResourceAsStream("config/Known_Issues.txt");
		if (knownIssuesStream == null) {
			LOGGER.warning("Known issues file not found in classpath: config/Known_Issues.txt");
			return;
		}
		try (BufferedReader br = new BufferedReader(new InputStreamReader(knownIssuesStream, StandardCharsets.UTF_8))) {

			String line;
			while ((line = br.readLine()) != null) {

				line = line.trim();

				if (line.isEmpty() || line.startsWith("#") || !line.contains("------")) {
					continue;
				}

				String[] parts = line.split("------", 2);

				if (parts.length == 2) {
					String bugId = parts[0].trim();
					String scenarioName = parts[1].trim().replaceAll("\\s+", " ");

					knownIssues.put(scenarioName, bugId);
				}
			}

			LOGGER.info("Known Issues Loaded: " + knownIssues);

		} catch (Exception e) {
			LOGGER.warning("Error reading Known_Issues.txt: " + e.getMessage());
		}
	}
}