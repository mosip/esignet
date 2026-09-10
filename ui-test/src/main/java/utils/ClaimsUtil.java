package utils;

import org.apache.log4j.Logger;
import org.json.JSONArray;
import org.json.JSONObject;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;

import base.BaseTest;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

public class ClaimsUtil {

	private static JSONObject root;
	private static final Logger logger = Logger.getLogger(ClaimsUtil.class);

	public static void parseFromUrl(String url) {
		try {
			if (url == null || !url.contains("#")) {
				logger.info("No encoded part found in URL: " + url);
				root = null;
				return;
			}

			String base64Part = url.substring(url.indexOf('#') + 1).trim();
			if (base64Part.isEmpty()) {
				logger.info("Empty encoded part in URL");
				root = null;
				return;
			}

			byte[] decoded = Base64.getDecoder().decode(base64Part);
			String jsonString = new String(decoded, StandardCharsets.UTF_8);
			root = new JSONObject(jsonString);

			logger.info("Decoded URL JSON: " + root.toString());
		} catch (Exception e) {
			logger.info("Failed to decode URL: " + e.getMessage());
			root = null;
		}
	}

	public static List<String> getMandatoryClaims() {
		if (root == null)
			return Collections.emptyList();
		return normalizeList(toStringList(root.optJSONArray("essentialClaims")));
	}

	public static List<String> getVoluntaryClaims() {
		if (root == null)
			return Collections.emptyList();
		return normalizeList(toStringList(root.optJSONArray("voluntaryClaims")));
	}

	private static List<String> toStringList(JSONArray arr) {
		List<String> list = new ArrayList<>();
		if (arr != null) {
			for (int i = 0; i < arr.length(); i++) {
				list.add(arr.optString(i));
			}
		}
		return list;
	}

	public static String normalizeClaim(String claim) {
		if (claim == null)
			return "";
		String normalized = claim.trim().toLowerCase().replace("_", "").replace(" ", "");
		if (normalized.equals("fullname"))
			return "name";
		if (normalized.equals("emailaddress"))
			return "email";
		return normalized;
	}

	public static String mapLangToName(String code) {
		return switch (code.toLowerCase()) {
		case "en", "eng" -> "English";
		case "hi", "hin" -> "Hindi";
		case "ar", "ara" -> "Arabic";
		case "kn", "kan" -> "Kannada";
		case "ta", "tam" -> "Tamil";
		case "km", "khm" -> "Khmer";
		default -> code;
		};
	}

	public static List<String> normalizeList(List<String> claims) {
		List<String> normalized = new ArrayList<>();
		for (String claim : claims) {
			normalized.add(normalizeClaim(claim));
		}
		return normalized;
	}

	private static final Map<String, String> ACR_BUTTON_SUFFIX_TO_FACTOR = Map.of(
			"otp", "OTP",
			"password", "PWD",
			"bio", "BIO",
			"kbi", "KBI",
			"wallet", "WLA",
			"pin", "PIN");

	public static List<String> getRenderedAuthFactors(WebDriver driver) {
		List<WebElement> buttons = driver.findElements(By.cssSelector("[id^='acr_']"));
		List<String> factors = new ArrayList<>();
		for (WebElement button : buttons) {
			String id = button.getAttribute("id");
			if (id == null) {
				continue;
			}
			String suffix = id.substring("acr_".length());
			String factor = ACR_BUTTON_SUFFIX_TO_FACTOR.get(suffix);
			if (factor != null) {
				factors.add(factor);
			}
		}

		if (factors.isEmpty() && !driver.findElements(By.id("username_input")).isEmpty()) {
			factors.add("SINGLE_FACTOR");
		}
		return factors;
	}

	private static List<String> cachedRenderedAuthFactors = null;

	public static void captureRenderedAuthFactors(WebDriver driver) {
		cachedRenderedAuthFactors = getRenderedAuthFactors(driver);
	}

	public static List<String> getCachedRenderedAuthFactors() {
		return cachedRenderedAuthFactors != null ? cachedRenderedAuthFactors : Collections.emptyList();
	}

	public static void clearCachedRenderedAuthFactors() {
		cachedRenderedAuthFactors = null;
	}

	public static List<String> getAuthFactors() {
		if (root == null)
			return Collections.emptyList();
		List<String> factors = new ArrayList<>();
		JSONArray groups = root.optJSONArray("authFactors");
		if (groups != null) {
			for (int i = 0; i < groups.length(); i++) {
				JSONArray group = groups.optJSONArray(i);
				if (group != null && group.length() > 0) {
					JSONObject obj = group.optJSONObject(0);
					if (obj != null)
						factors.add(obj.optString("type"));
				}
			}
		}
		return factors;
	}

	public static JSONObject getConfigs() {
		return root != null ? root.optJSONObject("configs") : null;
	}

	public static String normalizeFactor(String factor) {

		switch (factor) {
		case "OTP":
		case "mosip:idp:acr:generated-code":
			return "OTP";

		case "BIO":
		case "mosip:idp:acr:biometrics":
			return "BIO";

		case "WLA":
		case "mosip:idp:acr:linked-wallet":
			return "WLA";

		case "PWD":
		case "mosip:idp:acr:password":
			return "PWD";

		case "KBI":
		case "mosip:idp:acr:knowledge":
			return "KBI";

		default:
			return factor;
		}
	}
}
