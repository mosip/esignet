package org.biometric.provider;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.NoSuchAlgorithmException;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.logging.Logger;

import org.apache.commons.codec.digest.DigestUtils;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;
import org.json.JSONException;
import org.json.JSONObject;

import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.squareup.okhttp.RequestBody;
import com.squareup.okhttp.Response;

import io.mosip.kernel.core.util.CryptoUtil;
import io.mosip.kernel.core.util.DateUtils;
import io.mosip.mock.sbi.util.ApplicationPropertyHelper;
import io.mosip.testrig.apirig.testrunner.BaseTestCase;
import io.mosip.testrig.apirig.utils.RestClient;
import io.mosip.testrig.apirig.utils.CertsUtil;
import io.mosip.testrig.apirig.utils.KernelAuthentication;

public class JwtUtility {
	private static final Logger LOGGER = Logger.getLogger(JwtUtility.class.getName());
	private static final String JSON_MEDIA_TYPE = "application/json";

	public static final String USER_DIR = "user.dir";

	private static final String SIGN_ALGORITHM = "RS256";
	private static final String AUTH_REQ_TEMPLATE = "{ \"id\": \"string\",\"metadata\": {},\"request\": { \"appId\": \"%s\", \"clientId\": \"%s\", \"secretKey\": \"%s\" }, \"requesttime\": \"%s\", \"version\": \"string\"}";
	private static final String X509 = "X.509";

	private static volatile String cachedIdaCertificate;

	public static void clearIdaCertificateCache() {
		cachedIdaCertificate = null;
	}

	public static String getJwt(byte[] data, PrivateKey privateKey, X509Certificate x509Certificate) {
		String jwsToken = null;
		JsonWebSignature jws = new JsonWebSignature();

		if (x509Certificate != null) {
			List<X509Certificate> certList = new ArrayList<>();
			certList.add(x509Certificate);
			X509Certificate[] certArray = certList.toArray(new X509Certificate[] {});
			jws.setCertificateChainHeaderValue(certArray);
		}

		jws.setPayloadBytes(data);
		jws.setAlgorithmHeaderValue(SIGN_ALGORITHM);
		jws.setHeader(org.jose4j.jwx.HeaderParameterNames.TYPE, "JWT");
		jws.setKey(privateKey);
		jws.setDoKeyValidation(false);
		try {
			jwsToken = jws.getCompactSerialization();
		} catch (JoseException e) {
			LOGGER.info("getJwt: " + e.getMessage());
		}
		return jwsToken;
	}

	public static X509Certificate getCertificate() {
		try {
			FileInputStream certfis = new FileInputStream(
					new File(System.getProperty(USER_DIR) + "/files/keys/MosipTestCert.pem").getPath());

			String cert = getFileContent(certfis, StandardCharsets.UTF_8);

			cert = trimBeginEnd(cert);
			CertificateFactory cf = CertificateFactory.getInstance(X509);
			return (X509Certificate) cf.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(cert)));
		} catch (Exception ex) {
			LOGGER.severe("getCertificate: " + ex.getMessage());
		}
		return null;
	}

	public static PrivateKey getPrivateKey() {
		try {
			FileInputStream pkeyfis = new FileInputStream(
					new File(System.getProperty(USER_DIR) + "/files/keys/PrivateKey.pem").getPath());

			String pKey = getFileContent(pkeyfis, StandardCharsets.UTF_8);
			pKey = trimBeginEnd(pKey);
			KeyFactory kf = KeyFactory.getInstance("RSA");
			return kf.generatePrivate(new PKCS8EncodedKeySpec(Base64.getDecoder().decode(pKey)));
		} catch (Exception ex) {
			LOGGER.severe("getPrivateKey: " + ex.getMessage());
		}
		return null;
	}

	public static PublicKey getPublicKey() throws IOException, NoSuchAlgorithmException, InvalidKeySpecException {
		FileInputStream pkeyfis = new FileInputStream(
				new File(System.getProperty(USER_DIR) + "/files/keys/PublicKey.pem").getPath());
		String pKey = getFileContent(pkeyfis, StandardCharsets.UTF_8);
		pKey = trimBeginEnd(pKey);
		KeyFactory keyFactory = KeyFactory.getInstance("RSA");

		return keyFactory.generatePublic(new X509EncodedKeySpec(Base64.getDecoder().decode(pKey)));
	}

	public static String getFileContent(FileInputStream fis, Charset encoding) throws IOException {
		try (BufferedReader br = new BufferedReader(new InputStreamReader(fis, encoding))) {
			StringBuilder sb = new StringBuilder();
			String line;
			while ((line = br.readLine()) != null) {
				sb.append(line);
				sb.append('\n');
			}
			return sb.toString();
		}
	}

	public String getPropertyValue(String key) {
		return ApplicationPropertyHelper.getPropertyKeyValue(key);
	}

	public X509Certificate getCertificateToEncryptCaptureBioValue() throws Exception {
		String certificate = getCertificateFromIDA();
		if (certificate == null || certificate.isBlank()) {
			throw new IllegalStateException("IDA Biometric encryption Certificate not found");
		}
		certificate = trimBeginEnd(certificate);
		CertificateFactory cf = CertificateFactory.getInstance(X509);

		return (X509Certificate) cf
				.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(certificate)));
	}

	public String getThumbprint() throws Exception {
		String certificate = getCertificateFromIDA();
		certificate = trimBeginEnd(certificate);
		CertificateFactory cf = CertificateFactory.getInstance(X509);
		X509Certificate x509Certificate = (X509Certificate) cf
				.generateCertificate(new ByteArrayInputStream(Base64.getDecoder().decode(certificate)));

		return CryptoUtil.computeFingerPrint(x509Certificate.getEncoded(), null);
	}

	public static byte[] getCertificateThumbprint(Certificate cert) throws CertificateEncodingException {
		return DigestUtils.sha256(cert.getEncoded());
	}

	public String getCertificateFromIDA() throws Exception {
		if (cachedIdaCertificate != null && !cachedIdaCertificate.isBlank()) {
			return cachedIdaCertificate;
		}

		String certFromApitest = fetchIdaCertificateViaApitestRestClient();
		if (certFromApitest != null && !certFromApitest.isBlank()) {
			cachedIdaCertificate = certFromApitest;
			return cachedIdaCertificate;
		}

		try {
			String fromClientSecret = fetchIdaCertificateViaClientIdSecretKey();
			if (fromClientSecret != null && !fromClientSecret.isBlank()) {
				cachedIdaCertificate = fromClientSecret;
				return cachedIdaCertificate;
			}
		} catch (Exception e) {
			LOGGER.warning("IDA clientId/secretKey certificate fetch failed: " + e.getMessage());
		}

		String localFallback = loadLocalFallbackCertificate();
		if (localFallback != null && !localFallback.isBlank()) {
			cachedIdaCertificate = localFallback;
			return cachedIdaCertificate;
		}

		throw new IllegalStateException("IDA Biometric encryption Certificate not found");
	}

	private String fetchIdaCertificateViaApitestRestClient() {
		try {
			String certFromCache = CertsUtil.getCertificate("IDA-FIR");
			if (certFromCache != null && !certFromCache.isBlank()) {
				LOGGER.info("Loaded IDA FIR certificate via CertsUtil");
				return certFromCache;
			}
		} catch (Exception e) {
			LOGGER.warning("CertsUtil IDA certificate fetch failed: " + e.getMessage());
		}

		if (shouldSkipRemoteIdaCertificateFetch()) {
			return null;
		}

		for (String idaBaseUrl : resolveIdaCertificateBaseUrls()) {
			String certificate = fetchIdaCertificateFromBaseUrl(idaBaseUrl);
			if (certificate != null && !certificate.isBlank()) {
				LOGGER.info("Loaded IDA FIR certificate via " + idaBaseUrl);
				return certificate;
			}
		}
		return null;
	}

	private java.util.List<String> resolveIdaCertificateBaseUrls() {
		java.util.LinkedHashSet<String> candidates = new java.util.LinkedHashSet<>();
		String componentUrl = utils.EsignetUtil.getMosipComponentBaseUrl("idauthentication");
		if (componentUrl != null && !componentUrl.isBlank()) {
			candidates.add(normalizeIdaBaseUrl(componentUrl));
		}
		String derivedFromEsignet = deriveInternalApiBaseFromEsignetHost();
		if (derivedFromEsignet != null && !derivedFromEsignet.isBlank()) {
			candidates.add(derivedFromEsignet);
		}
		if (BaseTestCase.ApplnURI != null && !BaseTestCase.ApplnURI.isBlank()) {
			candidates.add(normalizeIdaBaseUrl(BaseTestCase.ApplnURI));
		}
		return new java.util.ArrayList<>(candidates);
	}

	private static boolean shouldSkipRemoteIdaCertificateFetch() {
		try {
			String plugin = utils.EsignetConfigManager.getproperty("pluginToExecute");
			String actuatorEnabled = utils.EsignetConfigManager.getproperty("esignetActuatorEnabled");
			return "mock".equalsIgnoreCase(plugin) && "false".equalsIgnoreCase(actuatorEnabled);
		} catch (Exception e) {
			return false;
		}
	}

	private static String normalizeIdaBaseUrl(String baseUrl) {
		String normalized = baseUrl.trim();
		if (!normalized.startsWith("http://") && !normalized.startsWith("https://")) {
			normalized = "https://" + normalized;
		}
		return normalized.replaceAll("/+$", "");
	}

	private static String deriveInternalApiBaseFromEsignetHost() {
		String esignetBase = io.mosip.testrig.apirig.utils.ConfigManager.getproperty("eSignetbaseurl");
		if (esignetBase == null || esignetBase.isBlank()) {
			esignetBase = utils.EsignetConfigManager.getproperty("eSignetbaseurl");
		}
		if (esignetBase == null || esignetBase.isBlank()) {
			return null;
		}
		try {
			java.net.URI uri = java.net.URI.create(normalizeIdaBaseUrl(esignetBase));
			String host = uri.getHost();
			if (host == null || host.isBlank()) {
				return null;
			}
			if (host.contains("esqa2")) {
				return "https://api-internal.esqa2.mosip.net";
			}
			if (host.contains("qa11new")) {
				return "https://api-internal.qa11new.mosip.net";
			}
		} catch (Exception e) {
			LOGGER.warning("Failed to derive internal API base from eSignet host: " + e.getMessage());
		}
		return null;
	}

	private String fetchIdaCertificateFromBaseUrl(String baseUrl) {
		try {
			String endpoint = normalizeIdaBaseUrl(baseUrl)
					+ "/idauthentication/v1/internal/getCertificate?applicationId=IDA&referenceId=IDA-FIR";
			String authToken = resolveIdaAuthToken();
			if (authToken == null || authToken.isBlank()) {
				return null;
			}
			io.restassured.response.Response response = RestClient.getRequestWithCookie(endpoint,
					JSON_MEDIA_TYPE, JSON_MEDIA_TYPE, "Authorization", authToken);
			if (response == null || response.getStatusCode() < 200 || response.getStatusCode() >= 300) {
				return null;
			}
			return response.jsonPath().getString("response.certificate");
		} catch (Exception e) {
			LOGGER.warning("IDA certificate fetch failed for " + baseUrl + ": " + e.getMessage());
			return null;
		}
	}

	private String resolveIdaAuthToken() {
		KernelAuthentication auth = new KernelAuthentication();
		for (String token : new String[] { auth.getAuthForIDA(), auth.getAuthForAdmin(), auth.getAuthForIDREPO(),
				auth.getAuthForRegistrationProcessor() }) {
			if (token != null && !token.isBlank()) {
				return token;
			}
		}
		return null;
	}

	private String fetchIdaCertificateViaClientIdSecretKey() throws Exception {
		String authServerUrl = getPropertyValue("mosip.auth.server.url");
		String idaServerUrl = getPropertyValue("mosip.ida.server.url");
		if (authServerUrl == null || authServerUrl.isBlank() || idaServerUrl == null || idaServerUrl.isBlank()) {
			return null;
		}

		OkHttpClient client = new OkHttpClient();
		String requestBody = String.format(AUTH_REQ_TEMPLATE, getPropertyValue("mosip.auth.appid"),
				getPropertyValue("mosip.auth.clientid"), getPropertyValue("mosip.auth.secretkey"),
				DateUtils.getUTCCurrentDateTime());

		MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
		RequestBody body = RequestBody.create(mediaType, requestBody);
		Request request = new Request.Builder().url(authServerUrl).post(body).build();
		try {
			Response response = client.newCall(request).execute();
			if (response.isSuccessful()) {
				String authToken = response.header("authorization");
				if (authToken == null) {
					authToken = response.header("Authorization");
				}
				Request idarequest = new Request.Builder().header("cookie", "Authorization=" + authToken)
						.url(idaServerUrl).get().build();

				Response idaResponse = new OkHttpClient().newCall(idarequest).execute();
				if (idaResponse.isSuccessful()) {
					JSONObject jsonObject = new JSONObject(idaResponse.body().string());
					jsonObject = jsonObject.getJSONObject("response");
					return jsonObject.getString("certificate");
				}
			}
		} catch (IOException | JSONException e) {
			LOGGER.severe("getCertificateFromIDA: " + e.getMessage());
			throw e;
		}
		return null;
	}

	/**
	 * Thunder/eSignet-go and mock-identity-system have no classic IDA
	 * {@code /idauthentication/v1/internal/getCertificate} endpoint. Mock BIO auth
	 * only requires a non-empty encrypted capture payload, so a local cert is enough
	 * for Mock MDS Auth CAPTURE to succeed.
	 */
	private String loadLocalFallbackCertificate() {
		String configured = null;
		try {
			configured = utils.EsignetConfigManager.getproperty("idaFirCertificate");
		} catch (Exception ignored) {
			configured = null;
		}
		if (configured != null && !configured.isBlank()) {
			String fromConfig = readCertificateValue(configured.trim());
			if (fromConfig != null && !fromConfig.isBlank()) {
				LOGGER.info("Loaded IDA FIR certificate from idaFirCertificate config");
				return fromConfig;
			}
		}

		for (String candidate : new String[] {
				"Biometric Devices/Finger/Slap/Keys/mosip-ida.cer",
				"resource/Biometric Devices/Finger/Slap/Keys/mosip-ida.cer",
				"../Biometric Devices/Finger/Slap/Keys/mosip-ida.cer",
				"files/keys/MosipTestCert.pem" }) {
			File file = new File(System.getProperty(USER_DIR), candidate);
			if (!file.isFile()) {
				continue;
			}
			try (FileInputStream in = new FileInputStream(file)) {
				String cert = getFileContent(in, StandardCharsets.UTF_8);
				if (cert != null && !cert.isBlank()) {
					LOGGER.info("Loaded IDA FIR certificate from " + file.getAbsolutePath());
					return cert;
				}
			} catch (IOException e) {
				LOGGER.warning("Could not read local IDA certificate " + file + ": " + e.getMessage());
			}
		}

		return generateSelfSignedIdaCertificate();
	}

	private static String readCertificateValue(String value) {
		if (value.contains("BEGIN CERTIFICATE") || !value.contains("/") && !value.contains("\\")) {
			return value;
		}
		File file = new File(value);
		if (!file.isFile()) {
			file = new File(System.getProperty(USER_DIR), value);
		}
		if (!file.isFile()) {
			return null;
		}
		try (FileInputStream in = new FileInputStream(file)) {
			return getFileContent(in, StandardCharsets.UTF_8);
		} catch (IOException e) {
			LOGGER.warning("Could not read idaFirCertificate file " + file + ": " + e.getMessage());
			return null;
		}
	}

	private String generateSelfSignedIdaCertificate() {
		try {
			java.security.KeyPairGenerator keyPairGenerator = java.security.KeyPairGenerator.getInstance("RSA");
			keyPairGenerator.initialize(2048);
			java.security.KeyPair keyPair = keyPairGenerator.generateKeyPair();
			long now = System.currentTimeMillis();
			org.bouncycastle.asn1.x500.X500Name dn = new org.bouncycastle.asn1.x500.X500Name("CN=IDA-FIR");
			org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder builder =
					new org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder(dn,
							java.math.BigInteger.valueOf(now),
							new java.util.Date(now - 86_400_000L),
							new java.util.Date(now + 3_650L * 86_400_000L), dn, keyPair.getPublic());
			org.bouncycastle.operator.ContentSigner signer = new org.bouncycastle.operator.jcajce.JcaContentSignerBuilder(
					"SHA256withRSA").build(keyPair.getPrivate());
			X509Certificate certificate = new org.bouncycastle.cert.jcajce.JcaX509CertificateConverter()
					.getCertificate(builder.build(signer));
			String pem = "-----BEGIN CERTIFICATE-----\n"
					+ Base64.getMimeEncoder(64, new byte[] { '\n' }).encodeToString(certificate.getEncoded())
					+ "\n-----END CERTIFICATE-----";
			LOGGER.info("Generated local self-signed IDA-FIR certificate for Mock MDS Auth capture");
			return pem;
		} catch (Exception e) {
			LOGGER.warning("Could not generate local IDA-FIR certificate: " + e.getMessage());
			return null;
		}
	}

	private static String trimBeginEnd(String pKey) {
		pKey = pKey.replaceAll("-*BEGIN([^-]*)-*(\r?\n)?", "");
		pKey = pKey.replaceAll("-*END([^-]*)-*(\r?\n)?", "");
		pKey = pKey.replaceAll("\\s", "");
		return pKey;
	}
}
