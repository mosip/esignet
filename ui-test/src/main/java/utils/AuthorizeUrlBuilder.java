package utils;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.json.JSONObject;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

public class AuthorizeUrlBuilder {

	public static void main(String[] args) throws Exception {
		String endpoint = "https://esignet-mock.es-qa1.mosip.net/par-oauth-details";
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest request = HttpRequest.newBuilder().uri(URI.create(endpoint)).GET().build();
		HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
		JSONObject json = new JSONObject(response.body());

		JSONObject resp = json.getJSONObject("response");

		String issuer = resp.getJSONObject("configs").getString("issuer");
		String redirectUri = resp.getString("redirectUri");
		String clientId = resp.getJSONObject("clientName").getString("@none");
		var essentialClaims = resp.getJSONArray("essentialClaims");
		var voluntaryClaims = resp.getJSONArray("voluntaryClaims");

		JSONObject userinfo = new JSONObject();
		for (int i = 0; i < essentialClaims.length(); i++) {
			userinfo.put(essentialClaims.getString(i), new JSONObject().put("essential", true));
		}
		for (int i = 0; i < voluntaryClaims.length(); i++) {
			userinfo.put(voluntaryClaims.getString(i), new JSONObject().put("essential", false));
		}

		JSONObject claims = new JSONObject().put("userinfo", userinfo).put("id_token", new JSONObject());

		String nonce = UUID.randomUUID().toString();
		String state = "state_" + System.currentTimeMillis();
		String scope = "openid profile";
		String responseType = "code";
		String acrValues = "mosip:idp:acr:generated-code mosip:idp:acr:biometrics mosip:idp:acr:linked-wallet";
		String claimsLocales = "en";
		String display = "page";
		String uiLocales = "en";

		String authorizeUrl = issuer + "/authorize?" + "nonce=" + nonce + "&state=" + state + "&client_id="
				+ URLEncoder.encode(clientId, StandardCharsets.UTF_8) + "&redirect_uri="
				+ URLEncoder.encode(redirectUri, StandardCharsets.UTF_8) + "&scope="
				+ URLEncoder.encode(scope, StandardCharsets.UTF_8) + "&response_type=" + responseType + "&acr_values="
				+ URLEncoder.encode(acrValues, StandardCharsets.UTF_8) + "&claims="
				+ URLEncoder.encode(claims.toString(), StandardCharsets.UTF_8) + "&claims_locales=" + claimsLocales
				+ "&display=" + display + "&ui_locales=" + uiLocales;

		System.out.println("Authorize URL: \n" + authorizeUrl);

		WebDriver driver = new ChromeDriver();
		driver.get(authorizeUrl);
	}
}
