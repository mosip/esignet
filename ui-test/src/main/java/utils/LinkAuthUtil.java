package utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.json.JSONObject;
import org.openqa.selenium.JavascriptExecutor;
import org.openqa.selenium.WebDriver;

import io.mosip.testrig.apirig.utils.RestClient;
import io.restassured.response.Response;

public final class LinkAuthUtil {

	private static final DateTimeFormatter REQUEST_TIME_FORMATTER = DateTimeFormatter
			.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");

	private static final AtomicReference<JSONObject> oauthDetailsResponse = new AtomicReference<>();
	private static final AtomicReference<JSONObject> latestLinkCodeResponse = new AtomicReference<>();

	private LinkAuthUtil() {
	}

	public static void attachNetworkCapture(WebDriver driver) {
		resolveOauthDetailsFromBrowser(driver);
	}

	public static void clearCapturedResponses() {
		latestLinkCodeResponse.set(null);
	}

	public static void clearAllCapturedResponses() {
		oauthDetailsResponse.set(null);
		latestLinkCodeResponse.set(null);
	}

	public static JSONObject waitForLatestLinkCodeResponse(WebDriver driver, Duration timeout) {
		long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			JSONObject response = latestLinkCodeResponse.get();
			if (response != null && response.has("linkCode")) {
				return response;
			}
			sleepQuietly(500);
		}
		return unwrapApiResponse(generateLinkCodeViaBrowser(driver));
	}

	public static JSONObject waitForOauthDetailsResponse(WebDriver driver, Duration timeout) {
		long deadline = System.currentTimeMillis() + timeout.toMillis();
		while (System.currentTimeMillis() < deadline) {
			JSONObject response = resolveOauthDetailsFromBrowser(driver);
			if (response != null && response.has("transactionId")) {
				return response;
			}
			sleepQuietly(500);
		}
		throw new IllegalStateException("Timed out waiting for oauth-details in browser URL hash");
	}

	public static long getLinkCodeExpirySeconds(JSONObject linkCodeResponse) {
		String expireDateTime = linkCodeResponse.optString("expireDateTime", null);
		if (expireDateTime == null || expireDateTime.isBlank()) {
			return getConfiguredLinkCodeExpireSeconds();
		}
		OffsetDateTime expiry = OffsetDateTime.parse(expireDateTime, DateTimeFormatter.ISO_OFFSET_DATE_TIME);
		return ChronoUnit.SECONDS.between(Instant.now(), expiry.toInstant());
	}

	public static int getConfiguredLinkCodeExpireSeconds() {
		return parseIntProperty("injiLinkCodeExpireSeconds", 60);
	}

	public static int getMaxUiWaitSeconds() {
		return parseIntProperty("injiMaxUiWaitSeconds", 120);
	}

	public static JSONObject generateLinkCodeViaBrowser(WebDriver driver) {
		JSONObject oauthDetails = waitForOauthDetailsResponse(driver, Duration.ofSeconds(30));
		String transactionId = oauthDetails.getString("transactionId");
		String oauthDetailsHash = computeOauthDetailsHash(oauthDetails);
		JSONObject response = executeLinkAuthPost(driver, "/v1/esignet/linked-authorization/link-code", transactionId,
				oauthDetailsHash, new JSONObject().put("transactionId", transactionId));
		JSONObject unwrapped = unwrapApiResponse(response);
		if (unwrapped.has("linkCode")) {
			latestLinkCodeResponse.set(unwrapped);
		}
		return response;
	}

	public static JSONObject getLinkStatusViaBrowser(WebDriver driver, String transactionId, String linkCode) {
		JSONObject oauthDetails = waitForOauthDetailsResponse(driver, Duration.ofSeconds(30));
		String oauthDetailsHash = computeOauthDetailsHash(oauthDetails);
		return executeLinkAuthPost(driver, "/v1/esignet/linked-authorization/link-status", transactionId,
				oauthDetailsHash, new JSONObject().put("transactionId", transactionId).put("linkCode", linkCode));
	}

	public static JSONObject postLinkTransaction(String linkCode) {
		String baseUrl = EsignetConfigManager.getproperty("eSignetbaseurl");
		String url = baseUrl + "/v1/esignet/linked-authorization/link-transaction";
		JSONObject request = new JSONObject();
		request.put("requestTime", formatRequestTime());
		request.put("request", new JSONObject().put("linkCode", linkCode));
		try {
			Response response = RestClient.post(url, request.toString());
			return new JSONObject(response.getBody().asString());
		} catch (Exception e) {
			throw new IllegalStateException("Failed to call link-transaction API", e);
		}
	}

	public static String extractLinkCode(JSONObject apiResponse) {
		JSONObject response = unwrapApiResponse(apiResponse);
		if (response.has("linkCode")) {
			return response.getString("linkCode");
		}
		throw new IllegalStateException("linkCode not found in API response: " + apiResponse);
	}

	public static String extractTransactionId(JSONObject apiResponse) {
		JSONObject response = unwrapApiResponse(apiResponse);
		if (response.has("transactionId")) {
			return response.getString("transactionId");
		}
		throw new IllegalStateException("transactionId not found in API response: " + apiResponse);
	}

	public static boolean hasErrorCode(JSONObject apiResponse, String expectedErrorCode) {
		if (!apiResponse.has("errors") || apiResponse.isNull("errors")) {
			return false;
		}
		for (Object entry : apiResponse.getJSONArray("errors")) {
			if (entry instanceof JSONObject jsonObject
					&& expectedErrorCode.equals(jsonObject.optString("errorCode"))) {
				return true;
			}
		}
		return false;
	}

	public static String extractLinkStatus(JSONObject apiResponse) {
		JSONObject response = unwrapApiResponse(apiResponse);
		return response.optString("linkStatus", "");
	}

	public static String computeOauthDetailsHash(JSONObject oauthDetails) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(oauthDetails.toString().getBytes(StandardCharsets.UTF_8));
			return Base64.getUrlEncoder().withoutPadding().encodeToString(hash);
		} catch (Exception e) {
			throw new IllegalStateException("Failed to compute oauth-details-hash", e);
		}
	}

	private static JSONObject resolveOauthDetailsFromBrowser(WebDriver driver) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		Object result = js.executeScript(
				"const hash = window.location.hash ? window.location.hash.substring(1) : '';"
						+ "if (!hash) return null;"
						+ "try {"
						+ "  const decoded = atob(hash);"
						+ "  const parsed = JSON.parse(decoded);"
						+ "  return parsed && parsed.transactionId ? parsed : null;"
						+ "} catch (e) { return null; }");
		if (!(result instanceof Map<?, ?> map)) {
			return oauthDetailsResponse.get();
		}
		JSONObject oauthDetails = new JSONObject(map);
		oauthDetailsResponse.set(oauthDetails);
		return oauthDetails;
	}

	private static JSONObject unwrapApiResponse(JSONObject apiResponse) {
		if (apiResponse.has("response") && !apiResponse.isNull("response")) {
			return apiResponse.getJSONObject("response");
		}
		return apiResponse;
	}

	private static JSONObject executeLinkAuthPost(WebDriver driver, String path, String transactionId,
			String oauthDetailsHash, JSONObject requestBody) {
		JavascriptExecutor js = (JavascriptExecutor) driver;
		Object result = js.executeAsyncScript(
				"const callback = arguments[arguments.length - 1];"
						+ "const path = arguments[0];"
						+ "const requestBodyJson = arguments[1];"
						+ "const hashInput = window.location.hash ? window.location.hash.substring(1) : '';"
						+ "if (!hashInput) { callback({ fetchError: 'oauth-details hash missing from URL' }); return; }"
						+ "const oauthDetails = JSON.parse(atob(hashInput));"
						+ "const transactionId = oauthDetails.transactionId;"
						+ "const toBase64Url = (buffer) => btoa(String.fromCharCode(...new Uint8Array(buffer)))"
						+ "  .replace(/\\+/g, '-').replace(/\\//g, '_').replace(/=+$/, '');"
						+ "crypto.subtle.digest('SHA-256', new TextEncoder().encode(JSON.stringify(oauthDetails)))"
						+ "  .then(digest => {"
						+ "    const oauthDetailsHash = toBase64Url(digest);"
						+ "    const csrfFromSession = sessionStorage.getItem('csrfToken');"
						+ "    const csrfPromise = csrfFromSession"
						+ "      ? Promise.resolve(csrfFromSession)"
						+ "      : fetch('/v1/esignet/csrf/token', { credentials: 'include' })"
						+ "          .then(r => r.json()).then(data => {"
						+ "            sessionStorage.setItem('csrfToken', data.token);"
						+ "            return data.token;"
						+ "          });"
						+ "    return csrfPromise.then(token => fetch(path, {"
						+ "      method: 'POST',"
						+ "      credentials: 'include',"
						+ "      headers: {"
						+ "        'Content-Type': 'application/json',"
						+ "        'X-XSRF-TOKEN': token,"
						+ "        'oauth-details-hash': oauthDetailsHash,"
						+ "        'oauth-details-key': transactionId"
						+ "      },"
						+ "      body: JSON.stringify({"
						+ "        requestTime: new Date().toISOString(),"
						+ "        request: JSON.parse(requestBodyJson)"
						+ "      })"
						+ "    }));"
						+ "  })"
						+ "  .then(r => r.text())"
						+ "  .then(text => {"
						+ "    try { callback(JSON.parse(text)); } catch (e) { callback({ parseError: text }); }"
						+ "  })"
						+ "  .catch(err => callback({ fetchError: String(err) }));",
				path, requestBody.toString());

		if (!(result instanceof Map<?, ?> map)) {
			throw new IllegalStateException("Unexpected browser fetch result: " + result);
		}
		JSONObject response = new JSONObject(map);
		if (response.has("fetchError") || response.has("parseError")) {
			throw new IllegalStateException("Browser link-auth request failed: " + response);
		}
		return response;
	}

	private static String formatRequestTime() {
		return ZonedDateTime.now(ZoneOffset.UTC).format(REQUEST_TIME_FORMATTER);
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

	private static void sleepQuietly(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}
}
