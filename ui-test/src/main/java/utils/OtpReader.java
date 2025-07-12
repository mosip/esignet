package utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class OtpReader {

	private static final String WS_URL = "wss://smtp.es-qa.mosip.net/mocksmtp/websocket";
	private static final Pattern OTP_PATTERN = Pattern.compile("\\b\\d{6}\\b");
	private static final int TIMEOUT_SECONDS = 60;
	private static final ObjectMapper mapper = new ObjectMapper();

	public static String readOtpFromWebSocket(String mobileNumber) throws Exception {
		OkHttpClient client = new OkHttpClient();
		CountDownLatch latch = new CountDownLatch(1);
		final StringBuilder otpResult = new StringBuilder();

		Request request = new Request.Builder().url(WS_URL).build();

		WebSocketListener listener = new WebSocketListener() {
			@Override
			public void onMessage(WebSocket webSocket, String message) {
				try {
					JsonNode root = mapper.readTree(message);
					String toText = root.path("to").path("text").asText();

					if (mobileNumber.equals(toText)) {
						Matcher matcher = OTP_PATTERN.matcher(root.path("text").asText());
						if (matcher.find()) {
							otpResult.append(matcher.group());
							webSocket.close(1000, "OTP received");
							latch.countDown();
						}
					}
				} catch (Exception e) {
					latch.countDown();
				}
			}

			@Override
			public void onFailure(WebSocket webSocket, Throwable t, Response response) {
				latch.countDown();
			}
		};

		client.newWebSocket(request, listener);

		boolean success = latch.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);

		if (!success || otpResult.length() == 0) {
			throw new RuntimeException("OTP not received in time");
		}

		return otpResult.toString();
	}
}
