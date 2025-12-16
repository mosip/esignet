package utils;

import org.json.JSONArray;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.Base64;

public class ClaimsParser {

	private static JSONObject root;

	// Decode and parse the base64 part from the URL (after #)
	public static void parseFromUrl(String url) {
		try {
			if (url == null || !url.contains("#")) {
				System.out.println("No encoded part found in URL: " + url);
				root = null;
				return;
			}

			String base64Part = url.substring(url.indexOf('#') + 1).trim();
			if (base64Part.isEmpty()) {
				System.out.println("Empty encoded part in URL");
				root = null;
				return;
			}

			byte[] decoded = Base64.getDecoder().decode(base64Part);
			String jsonString = new String(decoded, StandardCharsets.UTF_8);
			root = new JSONObject(jsonString);

			System.out.println("Decoded URL JSON: " + root.toString());
		} catch (Exception e) {
			System.out.println("Failed to decode URL: " + e.getMessage());
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

	public static String getDefaultLanguage() {
		if (root == null)
			return null;
		JSONObject configs = root.optJSONObject("configs");
		if (configs == null)
			return null;
		JSONObject kbi = configs.optJSONObject("auth.factor.kbi.field-details");
		if (kbi == null)
			return null;
		JSONObject lang = kbi.optJSONObject("language");
		if (lang == null)
			return null;
		JSONArray mandatory = lang.optJSONArray("mandatory");
		return (mandatory != null && mandatory.length() > 0) ? mandatory.optString(0) : null;
	}

	public static String mapClaimToUiLabel(String claim) {
		if (claim.equalsIgnoreCase("birthdate"))
			return "DOB";
		if (claim.equalsIgnoreCase("gender"))
			return "Gender";
		if (claim.equalsIgnoreCase("phone_number"))
			return "Mobile No";
		if (claim.equalsIgnoreCase("address"))
			return "Address";
		if (claim.equalsIgnoreCase("name"))
			return "Full Name";
		if (claim.equalsIgnoreCase("email"))
			return "Email";
		if (claim.equalsIgnoreCase("picture"))
			return "Profile";
		return claim;
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
		if (normalized.equals("phonenumber"))
			return "phone_number";
		return normalized;
	}

	public static List<String> normalizeList(List<String> claims) {
		List<String> normalized = new ArrayList<>();
		for (String claim : claims) {
			normalized.add(normalizeClaim(claim));
		}
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
	
	public static JSONObject getRoot() {
	    return root;
	}

}
