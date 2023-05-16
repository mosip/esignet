package io.mosip.authentication.esignet.integration.service;

import org.json.JSONException;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.*;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.authentication.esignet.integration.dto.AuditRequest;
import io.mosip.authentication.esignet.integration.dto.AuditResponse;
import io.mosip.authentication.esignet.integration.helper.AuthTransactionHelper;
import io.mosip.esignet.api.dto.AuditDTO;
import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.kernel.core.http.RequestWrapper;
import io.mosip.kernel.core.http.ResponseWrapper;
import io.mosip.kernel.core.util.DateUtils;
import lombok.extern.slf4j.Slf4j;

@ConditionalOnProperty(value = "mosip.esignet.integration.audit-plugin", havingValue = "IdaAuditPluginImpl")
@Component
@Slf4j
public class IdaAuditPluginImpl implements AuditPlugin {

	private static final String ESIGNET = "e-signet";

	private static final String TRANSACTION = "transaction";

	@Autowired
	private AuthTransactionHelper authTransactionHelper;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private RestTemplate restTemplate;

	@Value("${mosip.esignet.authenticator.ida.audit-manager-url}")
	private String auditManagerUrl;

	@Override
	public void logAudit(Action action, ActionStatus status, AuditDTO audit, Throwable t) {
		audit(null, action, status, audit);
	}

	@Override
	public void logAudit(String username, Action action, ActionStatus status, AuditDTO audit, Throwable t) {
		audit(username, action, status, audit);
	}

	private void audit(String username, Action action, ActionStatus status, AuditDTO audit) {
		try {
			String authToken = authTransactionHelper.getAuthToken();

			RequestWrapper<AuditRequest> request = new RequestWrapper<>();

			AuditRequest auditRequest = new AuditRequest();
			auditRequest.setEventId(action.name());
			auditRequest.setEventName(action.name());
			auditRequest.setEventType(status.name());
			auditRequest.setActionTimeStamp(DateUtils.getUTCCurrentDateTime());
			auditRequest.setHostName("localhost");
			auditRequest.setHostIp("localhost");
			auditRequest.setApplicationId(ESIGNET);
			auditRequest.setApplicationName(ESIGNET);
			auditRequest.setSessionUserId(StringUtils.isEmpty(username)?"no-user":username);
			auditRequest.setSessionUserName(StringUtils.isEmpty(username)?"no-user":username);
			auditRequest.setIdType(TRANSACTION);
			auditRequest.setCreatedBy(this.getClass().getSimpleName());
			auditRequest.setModuleName(getModuleByAction(action));
			auditRequest.setModuleId(getModuleByAction(action));
			auditRequest.setDescription(getAuditDescription(audit));
			auditRequest.setId(audit.getTransactionId());

			request.setRequest(auditRequest);
			request.setId("ida");
			request.setRequesttime(DateUtils.getUTCCurrentDateTime());

			String requestBody = objectMapper.writeValueAsString(request);
			RequestEntity requestEntity = RequestEntity
					.post(UriComponentsBuilder.fromUriString(auditManagerUrl).build().toUri())
					.contentType(MediaType.APPLICATION_JSON).header(HttpHeaders.COOKIE, "Authorization=" + authToken)
					.body(requestBody);
			ResponseEntity<ResponseWrapper> responseEntity = restTemplate.exchange(requestEntity,
					new ParameterizedTypeReference<ResponseWrapper>() {
					});

			if (responseEntity.getStatusCode().is2xxSuccessful() && responseEntity.getBody() != null) {
				ResponseWrapper<AuditResponse> responseWrapper = responseEntity.getBody();
				if (responseWrapper.getErrors() != null && !responseWrapper.getErrors().isEmpty()) {
					log.error("Error response received from audit service with errors: {}",
							responseWrapper.getErrors());
				}
			}

			if(responseEntity.getStatusCode() == HttpStatus.FORBIDDEN ||
					responseEntity.getStatusCode() == HttpStatus.UNAUTHORIZED) {
				log.error("Audit call failed with error: {}, issue with auth-token hence purging the auth-token-cache",
						responseEntity.getStatusCode());
				authTransactionHelper.purgeAuthTokenCache();
			}
		} catch (Exception e) {
			log.error("LogAudit failed with error : {}", e);
		}
	}

	private String getAuditDescription(AuditDTO audit) throws JSONException {
		JSONObject json = new JSONObject();
		json.put("clientId", audit.getClientId());
		json.put("relyingPartyId", audit.getRelyingPartyId());
		json.put("state", audit.getState());
		json.put("codeHash", audit.getCodeHash());
		json.put("accessTokenHash", audit.getAccessTokenHash());
		return json.toString();
	}

	private String getModuleByAction(Action action) {
		switch (action) {
		case OIDC_CLIENT_CREATE:
		case OIDC_CLIENT_UPDATE:
			return "ClientManagementController";
		case GET_OAUTH_DETAILS:
		case TRANSACTION_STARTED:
		case SEND_OTP:
		case AUTHENTICATE:
		case GET_AUTH_CODE:
		case DO_KYC_AUTH:
		case DO_KYC_EXCHANGE:
			return "AuthorizationController";
		case GENERATE_TOKEN:
			return "OAuthController";
		case GET_USERINFO:
			return "OpenIdConnectController";
		case LINK_AUTH_CODE:
		case LINK_AUTHENTICATE:
		case LINK_CODE:
		case LINK_SEND_OTP:
		case LINK_STATUS:
		case LINK_TRANSACTION:
		case SAVE_CONSENT:
			return "LinkedAuthorizationController";
		case GET_CERTIFICATE:
		case UPLOAD_CERTIFICATE:
			return "SystemInfoController";
		default:
			return "EsignetService";
		}
	}

}
