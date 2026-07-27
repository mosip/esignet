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

/**
 * Classpath override for mock-mds JwtUtility so Auth capture can fetch the IDA FIR
 * certificate using the apitest RestClient (already authenticated for qa11new).
 */
public class JwtUtility {
	private static final Logger LOGGER = Logger.getLogger(JwtUtility.class.getName());
	private static final String JSON_MEDIA_TYPE = "application/json";

	public static final String USER_DIR = "user.dir";

	private static final String SIGN_ALGORITHM = "RS256";
	private static final String AUTH_REQ_TEMPLATE = "{ \"id\": \"string\",\"metadata\": {},\"request\": { \"appId\": \"%s\", \"clientId\": \"%s\", \"secretKey\": \"%s\" }, \"requesttime\": \"%s\", \"version\": \"string\"}";
	private static final String X509 = "X.509";

	private static volatile String cachedIdaCertificate;

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

		return fetchIdaCertificateViaClientIdSecretKey();
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

		try {
			if (BaseTestCase.ApplnURI == null || BaseTestCase.ApplnURI.isBlank()) {
				return null;
			}
			String endpoint = BaseTestCase.ApplnURI
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
			String certificate = response.jsonPath().getString("response.certificate");
			if (certificate != null && !certificate.isBlank()) {
				LOGGER.info("Loaded IDA FIR certificate via apitest RestClient");
				return certificate;
			}
		} catch (Exception e) {
			LOGGER.warning("Apitest RestClient IDA certificate fetch failed: " + e.getMessage());
		}
		return null;
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
		OkHttpClient client = new OkHttpClient();
		String requestBody = String.format(AUTH_REQ_TEMPLATE, getPropertyValue("mosip.auth.appid"),
				getPropertyValue("mosip.auth.clientid"), getPropertyValue("mosip.auth.secretkey"),
				DateUtils.getUTCCurrentDateTime());

		MediaType mediaType = MediaType.parse("application/json; charset=utf-8");
		RequestBody body = RequestBody.create(mediaType, requestBody);
		Request request = new Request.Builder().url(getPropertyValue("mosip.auth.server.url")).post(body).build();
		try {
			Response response = client.newCall(request).execute();
			if (response.isSuccessful()) {
				String authToken = response.header("authorization");
				if (authToken == null) {
					authToken = response.header("Authorization");
				}
				Request idarequest = new Request.Builder().header("cookie", "Authorization=" + authToken)
						.url(getPropertyValue("mosip.ida.server.url")).get().build();

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

	private static String trimBeginEnd(String pKey) {
		pKey = pKey.replaceAll("-*BEGIN([^-]*)-*(\r?\n)?", "");
		pKey = pKey.replaceAll("-*END([^-]*)-*(\r?\n)?", "");
		pKey = pKey.replaceAll("\\s", "");
		return pKey;
	}
}
