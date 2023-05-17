package io.mosip.authentication.esignet.integration.dto;

import java.time.LocalDateTime;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * The Class AuditRequestDto.
 *
 * @author Manoj SP
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditRequest {
	
	private String eventId;
	private String eventName;
	private String eventType;
	private LocalDateTime actionTimeStamp;
	private String hostName;
	private String hostIp;
	private String applicationId;
	private String applicationName;
	private String sessionUserId;
	private String sessionUserName;
	private String id;
	private String idType;
	private String createdBy;
	private String moduleName;
	private String moduleId;
	private String description;

}
