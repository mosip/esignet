package utils;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

import org.apache.log4j.Logger;
import org.json.JSONObject;
import org.testng.SkipException;

public final class ConsentDbUtil {

	private static final Logger logger = Logger.getLogger(ConsentDbUtil.class);
	private static final String DEFAULT_DB_NAME = "mosip_esignet_go";
	private static final String DEFAULT_SCHEMA = "esignet";
	private static final String DEFAULT_PORT = "5432";

	public static final String PRIMARY_CLIENT_ID_KEY = "$ID:CreateOIDCClient_all_Valid_Smoke_sid_clientId$";
	public static final String SECONDARY_CLIENT_ID_KEY = "$ID:CreateOIDCClient_secondary_Smoke_sid_clientId$";

	private ConsentDbUtil() {
	}

	public record ConsentRecord(String psuToken, String claims, String acceptedClaims) {
	}

	public static void requireDbConfigured() {
		if (!isDbConfigured()) {
			String reason = "Consent DB verification skipped - configure esignetDbHost and esignetDbPassword "
					+ "in config.properties";
			ExtentReportManager.logStep("⚠️ " + reason);
			throw new SkipException(reason);
		}
	}

	public static boolean isDbConfigured() {
		return resolveDbUrl() != null && resolveDbUsername() != null && !resolveDbUsername().isBlank()
				&& resolveDbPassword() != null && !resolveDbPassword().isBlank();
	}

	public static Optional<ConsentRecord> findLatestByClientId(String clientId) {
		requireDbConfigured();
		String schema = getSchema();
		String sql = "SELECT psu_token, claims, accepted_claims FROM " + schema
				+ ".consent_detail WHERE client_id = ? ORDER BY cr_dtimes DESC LIMIT 1";

		try (Connection connection = openConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, clientId);
			try (ResultSet resultSet = statement.executeQuery()) {
				if (!resultSet.next()) {
					return Optional.empty();
				}
				return Optional.of(new ConsentRecord(resultSet.getString("psu_token"),
						resultSet.getString("claims"), resultSet.getString("accepted_claims")));
			}
		} catch (SQLException e) {
			throw new RuntimeException("Failed to query consent_detail for clientId=" + clientId, e);
		}
	}

	public static void assertConsentStoredWithPsuToken(String clientIdKey) {
		String clientId = EsignetUtil.resolveClientId(clientIdKey);
		ConsentRecord record = findLatestByClientId(clientId)
				.orElseThrow(() -> new AssertionError("No consent_detail row found for clientId=" + clientId));

		if (record.psuToken() == null || record.psuToken().isBlank()) {
			throw new AssertionError("consent_detail.psu_token is empty for clientId=" + clientId);
		}
		if (record.claims() == null || record.claims().isBlank()) {
			throw new AssertionError("consent_detail.claims is empty for clientId=" + clientId);
		}
		try {
			new JSONObject(record.claims());
		} catch (Exception e) {
			throw new AssertionError(
					"consent_detail.claims is not valid JSON for clientId=" + clientId + ": " + record.claims(), e);
		}
		logger.info("Verified consent_detail row for clientId=" + clientId + " with psu_token present and claims JSON");
	}

	public static void assertAcceptedClaimsEmpty(String clientIdKey) {
		String clientId = EsignetUtil.resolveClientId(clientIdKey);
		ConsentRecord record = findLatestByClientId(clientId)
				.orElseThrow(() -> new AssertionError("No consent_detail row found for clientId=" + clientId));

		String acceptedClaims = record.acceptedClaims();
		if (acceptedClaims == null || acceptedClaims.isBlank() || "{}".equals(acceptedClaims.trim())
				|| "[]".equals(acceptedClaims.trim())) {
			logger.info("Verified empty accepted_claims for clientId=" + clientId);
			return;
		}
		throw new AssertionError(
				"Expected empty accepted_claims for clientId=" + clientId + " but found: " + acceptedClaims);
	}

	private static String resolveDbUrl() {
		String override = firstConfigured("esignetDbUrl");
		if (override != null) {
			return withJdbcDefaults(override);
		}
		String server = firstConfigured("esignetDbHost");
		if (server == null) {
			return null;
		}
		String port = firstConfigured("esignetDbPort");
		if (port == null) {
			port = DEFAULT_PORT;
		}
		String dbName = firstConfigured("esignetDbName");
		if (dbName == null) {
			dbName = DEFAULT_DB_NAME;
		}
		return withJdbcDefaults("jdbc:postgresql://" + server + ":" + port + "/" + dbName);
	}

	private static String resolveDbUsername() {
		return firstConfigured("esignetDbUsername");
	}

	private static String resolveDbPassword() {
		return firstConfigured("esignetDbPassword");
	}

	private static Connection openConnection() throws SQLException {
		return DriverManager.getConnection(resolveDbUrl(), resolveDbUsername(), resolveDbPassword());
	}

	private static String getSchema() {
		String schema = firstConfigured("esignetDbSchema");
		return schema != null ? schema : DEFAULT_SCHEMA;
	}

	private static String withJdbcDefaults(String url) {
		String jdbcUrl = url.trim();
		if (!jdbcUrl.contains("sslmode=")) {
			jdbcUrl += jdbcUrl.contains("?") ? "&sslmode=disable" : "?sslmode=disable";
		}
		if (!jdbcUrl.toLowerCase().contains("currentschema=")) {
			jdbcUrl += "&currentSchema=" + getSchema();
		}
		return jdbcUrl;
	}

	private static String firstConfigured(String... keys) {
		for (String key : keys) {
			String value = blankToNull(EsignetConfigManager.getproperty(key));
			if (value != null) {
				return value;
			}
		}
		return null;
	}

	private static String blankToNull(String value) {
		return (value == null || value.isBlank()) ? null : value.trim();
	}
}
