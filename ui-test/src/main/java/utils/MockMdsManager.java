package utils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.Map;
import java.util.Properties;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.jose4j.jwt.consumer.JwtConsumerBuilder;

import io.mosip.mock.sbi.test.CentralizedMockSBI;
import io.mosip.testrig.apirig.dataprovider.BiometricDataProvider;
import io.mosip.testrig.apirig.dataprovider.mds.MDSClient;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;

/**
 * Starts and stops embedded Mock SBI (Mock MDS) for browser-based biometric login tests.
 * Uses the same device certificates and Default profile as {@link BiometricDataProvider}.
 */
public final class MockMdsManager {

	private static final Logger LOGGER = Logger.getLogger(MockMdsManager.class.getName());
	private static final String CONTEXT_KEY = "esignet-ui-auth";
	private static final Object LOCK = new Object();

	private static volatile int activePort;
	private static volatile boolean running;

	private MockMdsManager() {
	}

	public static boolean isEnabled() {
		String value = EsignetConfigManager.getproperty("useMockMds");
		return value != null && Boolean.parseBoolean(value.trim());
	}

	public static boolean isRunning() {
		return running;
	}

	public static int getActivePort() {
		return activePort;
	}

	public static void startForAuth() throws Exception {
		startForAuth(false);
	}

	/**
	 * Starts Mock MDS mid-scenario (e.g. after an initial device-not-found scan).
	 */
	public static void startForBiometricScan() throws Exception {
		if (!isEnabled()) {
			throw new IllegalStateException("useMockMds must be true to start Mock MDS for biometric scan");
		}
		// Mid-scenario start: ensure no Registration SBI from prerequisites is still listening.
		stopAll();
		resetMockSbiPropertyCache();
		startForAuth(true);
	}

	public static void startForAuth(boolean force) throws Exception {
		if (!force && !isEnabled()) {
			return;
		}

		synchronized (LOCK) {
			if (running && !force) {
				return;
			}
			if (running && force) {
				stop();
			}

			ensureMockMdsRuntimeLayout();
			resetMockSbiPropertyCache();
			ensureAuthProfileFromRegistration();
			ensureL1AuthDeviceMetadata();

			String p12Path = resolveP12Directory();
			int qualityScore = parseIntProperty("mdsQualityScore", 70);
			int maxLoopCount = parseIntProperty("mdsPortLoopCount", 20);
			int port = 0;

			while (maxLoopCount-- > 0 && port == 0) {
				try {
					int candidatePort = CentralizedMockSBI.startSBI(CONTEXT_KEY, "Auth", "Biometric Device", p12Path);
					if (isBrowserSbiPort(candidatePort)) {
						port = candidatePort;
					} else {
						LOGGER.warning("Mock MDS started on port " + candidatePort
								+ " outside browser range 4501-4510; retrying");
						CentralizedMockSBI.stopSBI(CONTEXT_KEY);
					}
				} catch (Exception e) {
					LOGGER.warning("Mock MDS start attempt failed: " + e.getMessage());
				}
			}

			if (port == 0) {
				throw new IllegalStateException("Unable to start Mock MDS for Auth on browser ports 4501-4510");
			}

			MDSClient client = new MDSClient(port);
			client.setProfile("Default", port, CONTEXT_KEY);
			BiometricDataProvider.setMDSscore(port, "Biometric Device", qualityScore);

			activePort = port;
			running = true;
			BiometricDataProvider.portmap.put("port_", port);
			LOGGER.info("Mock MDS Auth SBI started on port " + port);
			waitUntilBrowserDiscoveryReady();
			warmIdaFirCertificate();
		}
	}

	/**
	 * Pre-loads IDA FIR cert on the test JVM classpath so Auth CAPTURE encryption does not hang.
	 */
	public static void warmIdaFirCertificate() {
		try {
			org.biometric.provider.JwtUtility.clearIdaCertificateCache();
			String certificate = new org.biometric.provider.JwtUtility().getCertificateFromIDA();
			if (certificate != null && !certificate.isBlank()) {
				LOGGER.info("Pre-warmed IDA FIR certificate for Mock MDS Auth capture");
			} else {
				LOGGER.warning("IDA FIR certificate pre-warm returned empty");
			}
		} catch (Throwable e) {
			LOGGER.warning("IDA FIR certificate pre-warm failed: " + e.getMessage());
		}
	}

	public static void stop() {
		synchronized (LOCK) {
			if (!running) {
				return;
			}
			try {
				CentralizedMockSBI.stopSBI(CONTEXT_KEY);
			} finally {
				running = false;
				activePort = 0;
				LOGGER.info("Mock MDS Auth SBI stopped");
			}
		}
	}

	/**
	 * Stops every embedded SBI instance (including Registration SBI left running after mosipid
	 * prerequisites) so the browser's first biometric scan sees no local device.
	 */
	public static void stopAll() {
		synchronized (LOCK) {
			try {
				CentralizedMockSBI.stopAllSBI();
			} finally {
				running = false;
				activePort = 0;
				LOGGER.info("All Mock MDS/SBI instances stopped");
			}
		}
	}

	/**
	 * Auth capture reads ISO files from Profile/Default/Auth; registration prerequisites use
	 * Profile/Default/Registration. Copy registration profile data so auth captures match IDA.
	 */
	private static void ensureAuthProfileFromRegistration() throws IOException {
		// "../" variants cover running from ui-test/target (AGENTS.md "cd target && java -jar") - same
		// CWD mismatch as findBundledDevicePartnerP12()'s "../certs" candidate below.
		copyProfileBetweenPurposes(Paths.get("Profile", "Default"));
		copyProfileBetweenPurposes(Paths.get("resource", "Profile", "Default"));
		copyProfileBetweenPurposes(Paths.get("..", "Profile", "Default"));
		copyProfileBetweenPurposes(Paths.get("..", "resource", "Profile", "Default"));
	}

	/**
	 * io.mosip.mock.sbi reads device JSON files at paths hardcoded in application.properties (e.g.
	 * "mosip.mock.sbi.file.finger.slap.digitalid.json=/Biometric Devices/Finger/Slap/DigitalId.json"),
	 * resolved relative to the JVM's working directory with no override - unlike this module's own
	 * candidate-list lookups (below), it can't be pointed at "../Biometric Devices". Confirmed live:
	 * FileNotFoundException for "target/Biometric Devices/..." when running from ui-test/target
	 * (AGENTS.md "cd target && java -jar"), since that directory only ever exists at the repo root.
	 * Self-heals by copying the whole tree (24 small files, ~200KB) into the working directory.
	 */
	private static void ensureBiometricDevicesDirectoryAvailable() {
		Path target = Paths.get(System.getProperty("user.dir"), "Biometric Devices");
		if (Files.isDirectory(target)) {
			return;
		}
		for (String candidate : new String[] { "../Biometric Devices", "../resource/Biometric Devices",
				"resource/Biometric Devices" }) {
			Path source = Paths.get(System.getProperty("user.dir"), candidate).normalize();
			if (!Files.isDirectory(source)) {
				continue;
			}
			try (var paths = Files.walk(source)) {
				for (Path path : (Iterable<Path>) paths::iterator) {
					Path dest = target.resolve(source.relativize(path));
					if (Files.isDirectory(path)) {
						Files.createDirectories(dest);
					} else {
						Files.createDirectories(dest.getParent());
						Files.copy(path, dest, StandardCopyOption.REPLACE_EXISTING);
					}
				}
				LOGGER.info("Copied Biometric Devices from " + source + " to " + target);
				return;
			} catch (IOException e) {
				LOGGER.warning("Could not copy Biometric Devices from " + source + ": " + e.getMessage());
			}
		}
	}

	/**
	 * oidc-ui accepts only L1 Auth Ready devices; apitest resources ship with L0 metadata.
	 */
	private static void ensureL1AuthDeviceMetadata() throws IOException {
		ensureBiometricDevicesDirectoryAvailable();
		// "../Biometric Devices" covers running from ui-test/target (AGENTS.md "cd target && java
		// -jar") - same CWD mismatch as findBundledDevicePartnerP12()'s "../certs" candidate below.
		for (String rootDir : new String[] { "Biometric Devices", "resource/Biometric Devices",
				"../Biometric Devices", "../resource/Biometric Devices" }) {
			Path biometricDevicesDir = Paths.get(System.getProperty("user.dir"), rootDir);
			if (!Files.isDirectory(biometricDevicesDir)) {
				continue;
			}
			try (var paths = Files.walk(biometricDevicesDir)) {
				paths.filter(Files::isRegularFile)
						.filter(path -> {
							String name = path.getFileName().toString();
							return "DeviceInfo.json".equals(name) || "DeviceDiscovery.json".equals(name);
						})
						.forEach(MockMdsManager::patchDeviceJsonForL1Auth);
			}
		}
	}

	private static void patchDeviceJsonForL1Auth(Path jsonFile) {
		try {
			String content = Files.readString(jsonFile);
			String updated = content.replaceAll("\"certification\"\\s*:\\s*\"L0\"", "\"certification\": \"L1\"")
					.replaceAll("\"purpose\"\\s*:\\s*\"\"", "\"purpose\": \"Auth\"");
			if (!updated.equals(content)) {
				Files.writeString(jsonFile, updated);
				LOGGER.info("Patched L1 Auth metadata in " + jsonFile);
			}
		} catch (IOException e) {
			LOGGER.warning("Could not patch device metadata in " + jsonFile + ": " + e.getMessage());
		}
	}

	private static void copyProfileBetweenPurposes(Path defaultProfileDir) throws IOException {
		Path registrationDir = defaultProfileDir.resolve("Registration");
		Path authDir = defaultProfileDir.resolve("Auth");

		if (!Files.isDirectory(registrationDir)) {
			return;
		}

		Files.createDirectories(authDir);
		try (DirectoryStream<Path> stream = Files.newDirectoryStream(registrationDir)) {
			for (Path source : stream) {
				if (Files.isRegularFile(source)) {
					Files.copy(source, authDir.resolve(source.getFileName()), StandardCopyOption.REPLACE_EXISTING);
				}
			}
		}
		LOGGER.info("Copied Mock MDS profile from " + registrationDir + " to " + authDir);
	}

	private static int parseIntProperty(String propertyName, int defaultValue) {
		try {
			String value = EsignetConfigManager.getproperty(propertyName);
			if (value == null || value.isBlank()) {
				return defaultValue;
			}
			return Integer.parseInt(value.trim());
		} catch (NumberFormatException e) {
			return defaultValue;
		}
	}

	/**
	 * io.mosip.mock.sbi's ApplicationPropertyHelper reads "application.properties" via a plain
	 * relative FileInputStream (not classpath-aware), so it needs a real file at the JVM's working
	 * directory - confirmed live via FileNotFoundException when running from ui-test/target as
	 * documented (AGENTS.md "cd target && java -jar"), since the module's own application.properties
	 * only ever gets copied to target/classes (on the classpath) and into the shaded jar, never to
	 * target/ itself. Self-heals by extracting the classpath copy on first use.
	 */
	/**
	 * Mock SBI reads application.properties, Biometric Devices, Profile, and
	 * device-dsk-partner.p12 from the JVM working directory (not the classpath).
	 */
	private static void ensureMockMdsRuntimeLayout() {
		ensureApplicationPropertiesAvailable();
		ensureDevicePartnerP12AtWorkingDirectory();
		ensureBiometricDevicesDirectoryAvailable();
		try {
			ensureAuthProfileFromBioValues();
			ensureAuthProfileFromRegistration();
		} catch (IOException e) {
			LOGGER.warning("Could not prepare Mock MDS Auth profile: " + e.getMessage());
		}
	}

	private static void ensureApplicationPropertiesAvailable() {
		Path target = Paths.get(System.getProperty("user.dir"), "application.properties");
		if (Files.isRegularFile(target)) {
			return;
		}
		try (var in = MockMdsManager.class.getClassLoader().getResourceAsStream("application.properties")) {
			if (in == null) {
				LOGGER.warning("application.properties not found on classpath - Mock MDS will fail to start");
				return;
			}
			Files.copy(in, target);
			LOGGER.info("Copied application.properties to " + target + " for Mock MDS");
		} catch (IOException e) {
			LOGGER.warning("Could not copy application.properties for Mock MDS: " + e.getMessage());
		}
	}

	private static void ensureDevicePartnerP12AtWorkingDirectory() {
		Path cwdP12 = Paths.get(System.getProperty("user.dir"), "device-dsk-partner.p12");
		if (Files.isRegularFile(cwdP12)) {
			return;
		}
		Path bundled = findBundledDevicePartnerP12();
		if (bundled == null) {
			return;
		}
		try {
			Files.copy(bundled, cwdP12, StandardCopyOption.REPLACE_EXISTING);
			LOGGER.info("Copied device-dsk-partner.p12 to " + cwdP12);
		} catch (IOException e) {
			LOGGER.warning("Could not copy device-dsk-partner.p12 to working directory: " + e.getMessage());
		}
	}

	/**
	 * mock-mds capture reads ISO files from resource/Profile/Default/Auth. Those
	 * files are not packaged in the mock-mds jar; materialize them from the same
	 * bioValue.properties apitest-commons uses for biometric fixtures.
	 */
	private static void ensureAuthProfileFromBioValues() throws IOException {
		Properties bioValues = loadBioValueProperties();
		if (bioValues.isEmpty()) {
			return;
		}
		Path[] profileRoots = {
				Paths.get(System.getProperty("user.dir"), "resource", "Profile", "Default"),
				Paths.get(System.getProperty("user.dir"), "Profile", "Default")
		};
		java.util.LinkedHashMap<String, String> isoFiles = new java.util.LinkedHashMap<>();
		isoFiles.put("Face.iso", firstBioValue(bioValues, "FaceBioValue"));
		isoFiles.put("Left_Index.iso", firstBioValue(bioValues, "LeftIndexFingerBioValue"));
		isoFiles.put("Left_Middle.iso", firstBioValue(bioValues, "LeftMiddleFingerBioValue"));
		isoFiles.put("Left_Ring.iso", firstBioValue(bioValues, "LeftRingFingerBioValue"));
		isoFiles.put("Left_Little.iso", firstBioValue(bioValues, "LeftLittleFingerBioValue"));
		isoFiles.put("Left_Thumb.iso", firstBioValue(bioValues, "LeftThumbBioValue"));
		isoFiles.put("Right_Index.iso", firstBioValue(bioValues, "RightIndexFingerBioValue"));
		isoFiles.put("Right_Middle.iso", firstBioValue(bioValues, "RightMiddleFinger", "RightMiddleFingerBioValue"));
		isoFiles.put("Right_Ring.iso", firstBioValue(bioValues, "RightRingFingerBioValue"));
		isoFiles.put("Right_Little.iso", firstBioValue(bioValues, "RightLittleFingerBioValue"));
		isoFiles.put("Right_Thumb.iso", firstBioValue(bioValues, "RightThumbBioValue"));
		isoFiles.put("Left_Iris.iso", firstBioValue(bioValues, "LeftIrisBioValue"));
		isoFiles.put("Right_Iris.iso", firstBioValue(bioValues, "RightIrisBioValue"));
		for (Path profileRoot : profileRoots) {
			writeIsoProfile(profileRoot.resolve("Auth"), isoFiles);
			writeIsoProfile(profileRoot.resolve("Registration"), isoFiles);
		}
	}

	private static Properties loadBioValueProperties() {
		Properties properties = new Properties();
		try (InputStream in = MockMdsManager.class.getClassLoader()
				.getResourceAsStream("config/bioValue.properties")) {
			if (in == null) {
				LOGGER.warning("config/bioValue.properties not found on classpath");
				return properties;
			}
			properties.load(in);
		} catch (IOException e) {
			LOGGER.warning("Could not load bioValue.properties: " + e.getMessage());
		}
		return properties;
	}

	private static String firstBioValue(Properties properties, String... keys) {
		for (String key : keys) {
			String value = properties.getProperty(key);
			if (value != null && !value.isBlank()) {
				return value.trim();
			}
		}
		return null;
	}

	private static void writeIsoProfile(Path directory, Map<String, String> isoFiles) throws IOException {
		Files.createDirectories(directory);
		for (Map.Entry<String, String> entry : isoFiles.entrySet()) {
			if (entry.getValue() == null) {
				continue;
			}
			Path target = directory.resolve(entry.getKey());
			if (Files.isRegularFile(target) && Files.size(target) > 0) {
				continue;
			}
			byte[] decoded = decodeBioValue(entry.getValue());
			if (decoded.length == 0) {
				continue;
			}
			Files.write(target, decoded);
		}
		copyIfMissing(directory, "Right_Index.iso", "Finger_UKNOWN.iso");
		copyIfMissing(directory, "Right_Index.iso", "Finger_UKNOWN_wsq.iso");
		copyIfMissing(directory, "Left_Iris.iso", "Iris_UNKNOWN.iso");
		copyIfMissing(directory, "Left_Index.iso", "Left_Index_wsq.iso");
		copyIfMissing(directory, "Left_Middle.iso", "Left_Middle_wsq.iso");
		copyIfMissing(directory, "Left_Ring.iso", "Left_Ring_wsq.iso");
		copyIfMissing(directory, "Left_Little.iso", "Left_Little_wsq.iso");
		copyIfMissing(directory, "Left_Thumb.iso", "Left_Thumb_wsq.iso");
		copyIfMissing(directory, "Right_Index.iso", "Right_Index_wsq.iso");
		copyIfMissing(directory, "Right_Middle.iso", "Right_Middle_wsq.iso");
		copyIfMissing(directory, "Right_Ring.iso", "Right_Ring_wsq.iso");
		copyIfMissing(directory, "Right_Little.iso", "Right_Little_wsq.iso");
		copyIfMissing(directory, "Right_Thumb.iso", "Right_Thumb_wsq.iso");
		LOGGER.info("Prepared Mock MDS ISO profile in " + directory);
	}

	private static void copyIfMissing(Path directory, String sourceName, String targetName) throws IOException {
		Path source = directory.resolve(sourceName);
		Path target = directory.resolve(targetName);
		if (Files.isRegularFile(source) && !Files.isRegularFile(target)) {
			Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static byte[] decodeBioValue(String value) {
		try {
			return Base64.getDecoder().decode(value);
		} catch (IllegalArgumentException e) {
			try {
				return Base64.getUrlDecoder().decode(value);
			} catch (IllegalArgumentException ex) {
				LOGGER.warning("Could not decode biometric ISO value");
				return new byte[0];
			}
		}
	}

	private static Path findBundledDevicePartnerP12() {
		String[] relativePaths = {
				"certs/device-dsk-partner.p12",
				"../certs/device-dsk-partner.p12",
				"device-dsk-partner.p12"
		};
		Path cwd = Paths.get(System.getProperty("user.dir"));
		for (String relative : relativePaths) {
			Path candidate = cwd.resolve(relative).normalize();
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static String resolveP12Directory() {
		try {
			String prerequisitePath = BiometricDataProvider.getKeysDirPath("", BaseTestCase.certsForModule);
			if (Files.isRegularFile(Paths.get(prerequisitePath, "device-dsk-partner.p12"))) {
				LOGGER.info("Using partner device p12 from AUTHCERTS: " + prerequisitePath);
				return prerequisitePath;
			}
		} catch (Exception e) {
			LOGGER.warning("Could not resolve prerequisite p12 path: " + e.getMessage());
		}
		String configuredPath = EsignetConfigManager.getproperty("mdsP12Path");
		if (configuredPath != null && !configuredPath.isBlank()) {
			Path configured = Paths.get(configuredPath.trim());
			if (Files.isRegularFile(configured.resolve("device-dsk-partner.p12"))) {
				return configured.toString();
			}
		}
		Path bundledP12 = findBundledDevicePartnerP12();
		if (bundledP12 != null) {
			return bundledP12.getParent().toString();
		}
		return System.getProperty("java.io.tmpdir");
	}

	public static boolean isDevicePartnerP12Available() {
		return findBundledDevicePartnerP12() != null
				|| Files.isRegularFile(Paths.get(System.getProperty("user.dir"), "device-dsk-partner.p12"));
	}

	/**
	 * Copies bundled {@code device-dsk-partner.p12} into the AUTHCERTS directory used by Registration
	 * prerequisites and Mock SBI when partner device generation was skipped or failed.
	 */
	public static void ensureDevicePartnerP12Available() {
		Path projectP12 = findBundledDevicePartnerP12();
		if (projectP12 == null) {
			projectP12 = Paths.get(System.getProperty("user.dir"), "device-dsk-partner.p12");
		}
		if (!Files.isRegularFile(projectP12)) {
			LOGGER.warning("device-dsk-partner.p12 not found under ui-test/certs or " + System.getProperty("user.dir"));
			return;
		}
		try {
			String keysDir = BiometricDataProvider.getKeysDirPath("", BaseTestCase.certsForModule);
			Path targetDir = Paths.get(keysDir);
			Files.createDirectories(targetDir);
			Path targetP12 = targetDir.resolve("device-dsk-partner.p12");
			if (!Files.isRegularFile(targetP12)) {
				Files.copy(projectP12, targetP12, StandardCopyOption.REPLACE_EXISTING);
				LOGGER.info("Copied device-dsk-partner.p12 to " + targetP12);
			}
		} catch (Exception e) {
			LOGGER.warning("Could not copy device-dsk-partner.p12 to AUTHCERTS: " + e.getMessage());
		}
	}

	/**
	 * Waits until MOSIPDISC and MOSIPDINFO succeed with L1 + Auth + Ready, matching oidc-ui validation.
	 */
	public static void waitUntilBrowserDiscoveryReady() throws InterruptedException {
		int timeoutSeconds = parseIntProperty("biometricDeviceDiscoveryTimeoutSeconds", 30);
		long deadline = System.currentTimeMillis() + timeoutSeconds * 1000L;
		while (System.currentTimeMillis() < deadline) {
			if (running && activePort != 0 && probePortWithBrowserValidation(activePort)) {
				Thread.sleep(500);
				return;
			}
			Thread.sleep(1000);
		}
		throw new IllegalStateException("Mock MDS device discovery (L1/Auth/Ready) probe failed within "
				+ timeoutSeconds + "s");
	}

	/**
	 * Probes localhost SBI with MOSIPDISC (same host/port scan the browser widget uses).
	 */
	public static boolean verifyDeviceDiscoveryOnLocalhost() {
		if (!running || activePort == 0) {
			return false;
		}
		if (!probePortWithMosipdisc(activePort)) {
			return false;
		}
		if (!probePortWithBrowserValidation(activePort)) {
			LOGGER.warning("Mock MDS MOSIPDISC succeeded on port " + activePort
					+ " but device-info is not L1/Auth/Ready for the browser widget");
			logDeviceInfoProbeFailure(activePort);
			return false;
		}
		return true;
	}

	private static boolean isBrowserSbiPort(int port) {
		return port >= 4501 && port <= 4510;
	}

	private static boolean probePortWithBrowserValidation(int port) {
		if (!probePortWithMosipdisc(port)) {
			return false;
		}
		try {
			String dinfoBody = io.restassured.RestAssured.given()
					.request("MOSIPDINFO", "http://127.0.0.1:" + port + "/info").asString();
			JsonNode root = new ObjectMapper().readTree(dinfoBody);
			if (!root.isArray() || root.isEmpty()) {
				return false;
			}
			String jwt = root.get(0).path("deviceInfo").asText(null);
			if (jwt == null || jwt.isBlank()) {
				return false;
			}
			return isBrowserCompatibleDeviceInfo(decodeJwtPayload(jwt));
		} catch (Exception e) {
			LOGGER.warning("Mock MDS device-info probe failed on port " + port + ": " + e.getMessage());
			return false;
		}
	}

	private static void logDeviceInfoProbeFailure(int port) {
		try {
			String dinfoBody = io.restassured.RestAssured.given()
					.request("MOSIPDINFO", "http://127.0.0.1:" + port + "/info").asString();
			LOGGER.warning("Mock MDS MOSIPDINFO response: "
					+ dinfoBody.substring(0, Math.min(500, dinfoBody.length())));
			JsonNode root = new ObjectMapper().readTree(dinfoBody);
			if (root.isArray() && !root.isEmpty()) {
				String jwt = root.get(0).path("deviceInfo").asText("");
				if (!jwt.isBlank()) {
					LOGGER.warning("Mock MDS device-info JWT payload: " + decodeJwtPayload(jwt));
				}
			}
		} catch (Exception e) {
			LOGGER.warning("Unable to log device-info probe details: " + e.getMessage());
		}
	}

	private static boolean probePortWithMosipdisc(int port) {
		try {
			int status = io.restassured.RestAssured.given().contentType(io.restassured.http.ContentType.JSON)
					.body("{\"type\":\"Biometric Device\"}")
					.request("MOSIPDISC", "http://127.0.0.1:" + port + "/device").getStatusCode();
			return status >= 200 && status < 300;
		} catch (Exception e) {
			LOGGER.warning("Mock MDS discovery probe failed on port " + port + ": " + e.getMessage());
			return false;
		}
	}

	private static boolean isBrowserCompatibleDeviceInfo(String payload) {
		if (payload == null || payload.isBlank() || "null".equalsIgnoreCase(payload.trim())) {
			return false;
		}
		try {
			JsonNode node = new ObjectMapper().readTree(payload);
			String certification = node.path("certification").asText("");
			String purpose = node.path("purpose").asText("");
			String deviceStatus = node.path("deviceStatus").asText("");
			return "L1".equals(certification) && "Auth".equals(purpose) && "Ready".equals(deviceStatus);
		} catch (Exception e) {
			return payload.contains("L1") && payload.contains("Auth") && payload.contains("Ready");
		}
	}

	private static void resetMockSbiPropertyCache() {
		try {
			java.lang.reflect.Field propertiesField = Class
					.forName("io.mosip.mock.sbi.util.ApplicationPropertyHelper")
					.getDeclaredField("properties");
			propertiesField.setAccessible(true);
			propertiesField.set(null, null);
		} catch (Exception e) {
			LOGGER.warning("Could not reset Mock SBI property cache: " + e.getMessage());
		}
	}

	/**
	 * Builds browser localStorage entries matching oidc-ui sbiService cache shape for the active port.
	 */
	public static java.util.Map<String, String> buildBrowserSbiCacheEntries(int port) {
		java.util.Map<String, String> entries = new java.util.HashMap<>();
		if (port <= 0) {
			return entries;
		}
		try {
			String discBody = io.restassured.RestAssured.given()
					.contentType(io.restassured.http.ContentType.JSON)
					.body("{\"type\":\"Biometric Device\"}")
					.request("MOSIPDISC", "http://127.0.0.1:" + port + "/device").asString();
			String dinfoBody = io.restassured.RestAssured.given()
					.request("MOSIPDINFO", "http://127.0.0.1:" + port + "/info").asString();
			JsonNode dinfoRoot = new ObjectMapper().readTree(dinfoBody);
			if (!dinfoRoot.isArray() || dinfoRoot.isEmpty()) {
				return entries;
			}
			com.fasterxml.jackson.databind.node.ArrayNode deviceDetails = new ObjectMapper().createArrayNode();
			for (JsonNode item : dinfoRoot) {
				String jwt = item.path("deviceInfo").asText(null);
				if (jwt == null || jwt.isBlank()) {
					continue;
				}
				String payloadJson = decodeJwtPayload(jwt);
				if (!isBrowserCompatibleDeviceInfo(payloadJson)) {
					continue;
				}
				JsonNode payloadNode = new ObjectMapper().readTree(payloadJson);
				com.fasterxml.jackson.databind.node.ObjectNode decoded = new ObjectMapper().createObjectNode();
				decoded.setAll((com.fasterxml.jackson.databind.node.ObjectNode) payloadNode);
				String digitalIdJwt = payloadNode.path("digitalId").asText(null);
				if (digitalIdJwt != null && !digitalIdJwt.isBlank()) {
					decoded.set("digitalId", new ObjectMapper().readTree(decodeJwtPayload(digitalIdJwt)));
				}
				deviceDetails.add(decoded);
			}
			if (deviceDetails.isEmpty()) {
				return entries;
			}
			com.fasterxml.jackson.databind.node.ObjectNode discover = new ObjectMapper().createObjectNode();
			discover.set(String.valueOf(port), new ObjectMapper().readTree(discBody));
			com.fasterxml.jackson.databind.node.ObjectNode deviceInfo = new ObjectMapper().createObjectNode();
			deviceInfo.set(String.valueOf(port), deviceDetails);
			entries.put("discover", discover.toString());
			entries.put("deviceInfo", deviceInfo.toString());
		} catch (Exception e) {
			LOGGER.warning("Could not build browser SBI cache entries for port " + port + ": " + e.getMessage());
		}
		return entries;
	}

	private static String decodeJwtPayload(String jwt) {
		if (jwt == null || jwt.isBlank()) {
			return "";
		}
		try {
			return new JwtConsumerBuilder().build().processToClaims(jwt).toJson();
		} catch (Exception e) {
			String[] parts = jwt.split("\\.");
			if (parts.length < 2) {
				return "";
			}
			byte[] decoded;
			try {
				decoded = java.util.Base64.getUrlDecoder().decode(parts[1]);
			} catch (IllegalArgumentException ex) {
				String normalized = parts[1].replace('-', '+').replace('_', '/');
				int padLength = (4 - normalized.length() % 4) % 4;
				normalized = normalized + "=".repeat(padLength);
				decoded = java.util.Base64.getDecoder().decode(normalized);
			}
			return new String(decoded, StandardCharsets.UTF_8);
		}
	}
}
