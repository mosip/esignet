package io.mosip.testrig.apirig.testrunner;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.StringWriter;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.interfaces.RSAPublicKey;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.testng.TestNG;

import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import io.mosip.testrig.apirig.dataprovider.BiometricDataProvider;
import io.mosip.testrig.apirig.dbaccess.DBManager;
import io.mosip.testrig.apirig.report.EmailableReport;
import io.mosip.testrig.apirig.utils.AdminTestUtil;
import io.mosip.testrig.apirig.utils.AuthTestsUtil;
import io.mosip.testrig.apirig.utils.CertificateGenerationUtil;
import io.mosip.testrig.apirig.utils.CertsUtil;
import io.mosip.testrig.apirig.utils.EsignetConfigManager;
import io.mosip.testrig.apirig.utils.EsignetUtil;
import io.mosip.testrig.apirig.utils.GlobalConstants;
import io.mosip.testrig.apirig.utils.JWKKeyUtil;
import io.mosip.testrig.apirig.utils.KeyCloakUserAndAPIKeyGeneration;
import io.mosip.testrig.apirig.utils.KeycloakUserManager;
import io.mosip.testrig.apirig.utils.MispPartnerAndLicenseKeyGeneration;
import io.mosip.testrig.apirig.utils.OutputValidationUtil;
import io.mosip.testrig.apirig.utils.PartnerRegistration;
import io.mosip.testrig.apirig.utils.SkipTestCaseHandler;

/**
 * Class to initiate mosip api test execution
 * 
 * @author Vignesh
 *
 */
public class MosipTestRunner {
	private static final Logger LOGGER = Logger.getLogger(MosipTestRunner.class);
	private static String cachedPath = null;

	public static String jarUrl = MosipTestRunner.class.getProtectionDomain().getCodeSource().getLocation().getPath();
	public static List<String> languageList = new ArrayList<>();
	public static boolean skipAll = false;

	/**
	 * C Main method to start mosip test execution
	 * 
	 * @param arg
	 */
	public static void main(String[] arg) {

		try {
			Map<String, String> envMap = System.getenv();
			LOGGER.info("** ------------- Get ALL ENV varibales --------------------------------------------- **");
			for (String envName : envMap.keySet()) {
				LOGGER.info(String.format("ENV %s = %s%n", envName, envMap.get(envName)));
			}
			BaseTestCase.setRunContext(getRunType(), jarUrl);

			ExtractResource.removeOldMosipTestTestResource();
			if (getRunType().equalsIgnoreCase("JAR")) {
				ExtractResource.extractCommonResourceFromJar();
			} else {
				ExtractResource.copyCommonResources();
			}
			AdminTestUtil.init();
			EsignetConfigManager.init();
			suiteSetup(getRunType());
			SkipTestCaseHandler.loadTestcaseToBeSkippedList("testCaseSkippedList.txt");
			setLogLevels();

//			HealthChecker healthcheck = new HealthChecker();
//			healthcheck.setCurrentRunningModule(BaseTestCase.currentModule);
			
			//TODO: Implement this
			// healthcheck.setAcutatorList(getAcutuatorListFromHealthCheckfile())
			
			
//			Thread trigger = new Thread(healthcheck);
//			trigger.start();
			

			if (EsignetUtil.getIdentityPluginNameFromEsignetActuator().toLowerCase().contains("mockauthenticationservice") == false
					&& EsignetUtil.getIdentityPluginNameFromEsignetActuator().toLowerCase().contains("sunbird") == false) {				
				KeycloakUserManager.removeUser();
				KeycloakUserManager.createUsers();
				KeycloakUserManager.closeKeycloakInstance();

				List<String> localLanguageList = new ArrayList<>(BaseTestCase.getLanguageList());
				AdminTestUtil.getLocationData();
				
				
				String partnerKeyURL = "";
				String updatedPartnerKeyURL = "";
				String ekycPartnerKeyURL = "";

				PartnerRegistration.deleteCertificates();
				CertificateGenerationUtil.getThumbprints();
				AdminTestUtil.createAndPublishPolicy();
				AdminTestUtil.createEditAndPublishPolicy();
				partnerKeyURL = PartnerRegistration.generateAndGetPartnerKeyUrl();
				updatedPartnerKeyURL = PartnerRegistration.generateAndGetUpdatedPartnerKeyUrl();

				AdminTestUtil.createAndPublishPolicyForKyc();
				ekycPartnerKeyURL = PartnerRegistration.generateAndGetEkycPartnerKeyUrl();

				BiometricDataProvider.generateBiometricTestData("Registration");

				if (partnerKeyURL.isEmpty() || ekycPartnerKeyURL.isEmpty())
					LOGGER.error("partnerKeyURL is null");
				else
					startTestRunner();
			} else {
				startTestRunner();
			}

		} catch (Exception e) {
			LOGGER.error("Exception " + e.getMessage());
		}
		OTPListener.bTerminate = true;

		HealthChecker.bTerminate = true;

		System.exit(0);

	}
	
	public static void suiteSetup(String runType) {
		if (EsignetConfigManager.IsDebugEnabled())
			LOGGER.setLevel(Level.ALL);
		else
			LOGGER.info("Test Framework for Mosip api Initialized");
		BaseTestCase.initialize();
		LOGGER.info("Done with BeforeSuite and test case setup! su TEST EXECUTION!\n\n");

		if (!runType.equalsIgnoreCase("JAR")) {
			AuthTestsUtil.removeOldMosipTempTestResource();
		}
		BaseTestCase.currentModule = GlobalConstants.ESIGNET;
		BaseTestCase.certsForModule = GlobalConstants.ESIGNET;
		DBManager.executeDBQueries(EsignetConfigManager.getKMDbUrl(), EsignetConfigManager.getKMDbUser(),
				EsignetConfigManager.getKMDbPass(), EsignetConfigManager.getKMDbSchema(),
				getGlobalResourcePath() + "/" + "config/keyManagerDataDeleteQueriesForEsignet.txt");
		DBManager.executeDBQueries(EsignetConfigManager.getIdaDbUrl(), EsignetConfigManager.getIdaDbUser(),
				EsignetConfigManager.getPMSDbPass(), EsignetConfigManager.getIdaDbSchema(),
				getGlobalResourcePath() + "/" + "config/idaDeleteQueriesForEsignet.txt");
		DBManager.executeDBQueries(EsignetConfigManager.getMASTERDbUrl(), EsignetConfigManager.getMasterDbUser(),
				EsignetConfigManager.getMasterDbPass(), EsignetConfigManager.getMasterDbSchema(),
				getGlobalResourcePath() + "/" + "config/masterDataDeleteQueriesForEsignet.txt");
		AdminTestUtil.initiateesignetTest();
		BaseTestCase.otpListener = new OTPListener();
		BaseTestCase.otpListener.run();
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

	/**
	 * The method to start mosip testng execution
	 * 
	 * @throws IOException
	 */
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
		// List and sort the files
		File[] files = homeDir.listFiles();
		if (files != null) {
			Arrays.sort(files, (f1, f2) -> {
				// Customize the comparison based on file names
				if (f1.getName().toLowerCase().contains("prerequisite")) {
					return -1; // f1 should come before f2
				} else if (f2.getName().toLowerCase().contains("prerequisite")) {
					return 1; // f2 comes before f1
				}
				return f1.getName().compareTo(f2.getName()); // default alphabetical order
			});

			for (File file : files) {
				TestNG runner = new TestNG();
				List<String> suitefiles = new ArrayList<>();

				if (file.getName().toLowerCase().contains(GlobalConstants.ESIGNET)) {
					if (file.getName().toLowerCase().contains("prerequisite")) {
						BaseTestCase.setReportName(GlobalConstants.ESIGNET + "-prerequisite");
					} else {
						// if the prerequisite total skipped/failed count is greater than zero
						if (EmailableReport.getFailedCount() > 0 || EmailableReport.getSkippedCount() > 0) {
							// skipAll = true;
						}

						BaseTestCase.setReportName(GlobalConstants.ESIGNET);
					}
					suitefiles.add(file.getAbsolutePath());
					runner.setTestSuites(suitefiles);
					System.getProperties().setProperty("testng.outpur.dir", "testng-report");
					runner.setOutputDirectory("testng-report");
					runner.run();
				}
			}
		} else {
			LOGGER.error("No files found in directory: " + homeDir);
		}
	}

	public static String getGlobalResourcePath() {
		if (cachedPath != null) {
			return cachedPath;
		}

		String path = null;
		if (getRunType().equalsIgnoreCase("JAR")) {
			path = new File(jarUrl).getParentFile().getAbsolutePath() + "/MosipTestResource/MosipTemporaryTestResource";
		} else if (getRunType().equalsIgnoreCase("IDE")) {
			path = new File(MosipTestRunner.class.getClassLoader().getResource("").getPath()).getAbsolutePath()
					+ "/MosipTestResource/MosipTemporaryTestResource";
			if (path.contains(GlobalConstants.TESTCLASSES))
				path = path.replace(GlobalConstants.TESTCLASSES, "classes");
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

	public static String generatePulicKey() {
		String publicKey = null;
		try {
			KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
			keyGenerator.initialize(2048, BaseTestCase.secureRandom);
			final KeyPair keypair = keyGenerator.generateKeyPair();
			publicKey = java.util.Base64.getEncoder().encodeToString(keypair.getPublic().getEncoded());
		} catch (NoSuchAlgorithmException e) {
			LOGGER.error(e.getMessage());
		}
		return publicKey;
	}

	public static KeyPairGenerator keyPairGen = null;

	public static KeyPairGenerator getKeyPairGeneratorInstance() {
		if (keyPairGen != null)
			return keyPairGen;
		try {
			keyPairGen = KeyPairGenerator.getInstance("RSA");
			keyPairGen.initialize(2048);

		} catch (NoSuchAlgorithmException e) {
			LOGGER.error(e.getMessage());
		}

		return keyPairGen;
	}

	public static String generatePublicKeyForMimoto() {

		String vcString = "";
		try {
			KeyPairGenerator keyPairGenerator = getKeyPairGeneratorInstance();
			KeyPair keyPair = keyPairGenerator.generateKeyPair();
			PublicKey publicKey = keyPair.getPublic();
			StringWriter stringWriter = new StringWriter();
			try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
				pemWriter.writeObject(publicKey);
				pemWriter.flush();
				vcString = stringWriter.toString();
				if (System.getProperty("os.name").toLowerCase().contains("windows")) {
					vcString = vcString.replaceAll("\r\n", "\\\\n");
				} else {
					vcString = vcString.replaceAll("\n", "\\\\n");
				}
			} catch (Exception e) {
				throw e;
			}
		} catch (Exception e) {
			LOGGER.error(e.getMessage());
		}
		return vcString;
	}

	public static String generateJWKPublicKey() {
		try {
			KeyPairGenerator keyGenerator = KeyPairGenerator.getInstance("RSA");
			keyGenerator.initialize(2048, BaseTestCase.secureRandom);
			final KeyPair keypair = keyGenerator.generateKeyPair();
			RSAKey jwk = new RSAKey.Builder((RSAPublicKey) keypair.getPublic()).keyID("RSAKeyID")
					.keyUse(KeyUse.SIGNATURE).privateKey(keypair.getPrivate()).build();

			return jwk.toJSONString();
		} catch (NoSuchAlgorithmException e) {
			LOGGER.error(e.getMessage());
			return null;
		}
	}

	public static Properties getproperty(String path) {
		Properties prop = new Properties();
		FileInputStream inputStream = null;
		try {
			File file = new File(path);
			inputStream = new FileInputStream(file);
			prop.load(inputStream);
		} catch (Exception e) {
			LOGGER.error(GlobalConstants.EXCEPTION_STRING_2 + e.getMessage());
		} finally {
			AdminTestUtil.closeInputStream(inputStream);
		}
		return prop;
	}

	/**
	 * The method will return mode of application started either from jar or eclipse
	 * ide
	 * 
	 * @return
	 */
	public static String getRunType() {
		if (MosipTestRunner.class.getResource("MosipTestRunner.class").getPath().contains(".jar"))
			return "JAR";
		else
			return "IDE";
	}

}
