/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.controllers;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idp.core.dto.LinkCodeRequest;
import io.mosip.idp.core.dto.RequestWrapper;
import io.mosip.idp.core.dto.ResponseWrapper;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.cryptomanager.exception.KeymanagerServiceException;
import io.mosip.kernel.keymanagerservice.constant.KeymanagerErrorConstant;
import io.mosip.kernel.keymanagerservice.dto.KeyPairGenerateResponseDto;
import io.mosip.kernel.keymanagerservice.dto.UploadCertificateRequestDto;
import io.mosip.kernel.keymanagerservice.dto.UploadCertificateResponseDto;
import io.mosip.kernel.keymanagerservice.service.KeymanagerService;

@RunWith(SpringRunner.class)
@WebMvcTest(value = SystemInfoController.class)
public class SystemInfoControllerTest {

	@Autowired
	MockMvc mockMvc;

	@MockBean
	KeymanagerService keymanagerService;;

	ObjectMapper objectMapper = new ObjectMapper();

	@Test
	public void getCertificate_withInvalidReferenceId_returnSuccessResponse() throws Exception {

		String applicationId = "123456";
		Optional<String> referenceId = Optional.of("");
		;

		KeyPairGenerateResponseDto KeyPairGenerateResponseDto = new KeyPairGenerateResponseDto();
		KeyPairGenerateResponseDto.setCertificate("hegfgr74567346hfgerhfgre");
		KeyPairGenerateResponseDto.setCertSignRequest("request");
		KeyPairGenerateResponseDto.setIssuedAt(LocalDateTime.now());
		KeyPairGenerateResponseDto.setTimestamp(LocalDateTime.now());
		KeyPairGenerateResponseDto
				.setExpiryAt(LocalDateTime.now(ZoneOffset.UTC).plus(Long.parseLong("30"), ChronoUnit.SECONDS));

		ResponseWrapper<KeyPairGenerateResponseDto> responseWrapper = new ResponseWrapper<>();
		responseWrapper.setResponse(KeyPairGenerateResponseDto);
		responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());

		when(keymanagerService.getCertificate(applicationId, referenceId)).thenReturn(responseWrapper.getResponse());

		mockMvc.perform(get("/system-info/certificate").param("applicationId", applicationId)
				.param("referenceId", referenceId.get()).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

	}

	@Test
	public void getCertificate_withApplicationIdAndReferenceId_returnSuccessResponse() throws Exception {

		String applicationId = "45674654";
		Optional<String> referenceId = Optional.of("12345678909867");

		KeyPairGenerateResponseDto KeyPairGenerateResponseDto = new KeyPairGenerateResponseDto();
		KeyPairGenerateResponseDto.setCertificate("hegfgr74567346hfgerhfgre");
		KeyPairGenerateResponseDto.setCertSignRequest("request");
		KeyPairGenerateResponseDto.setIssuedAt(LocalDateTime.now());
		KeyPairGenerateResponseDto.setTimestamp(LocalDateTime.now());
		KeyPairGenerateResponseDto
				.setExpiryAt(LocalDateTime.now(ZoneOffset.UTC).plus(Long.parseLong("30"), ChronoUnit.SECONDS));

		ResponseWrapper<KeyPairGenerateResponseDto> responseWrapper = new ResponseWrapper<>();
		responseWrapper.setResponse(KeyPairGenerateResponseDto);
		responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());

		when(keymanagerService.getCertificate(applicationId, referenceId)).thenReturn(responseWrapper.getResponse());

		mockMvc.perform(get("/system-info/certificate").param("applicationId", applicationId)
				.param("referenceId", referenceId.get()).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

	}

	@Test
	public void getCertificate_withInvalidApplicationIdAndReferenceId_returnSuccessResponse() throws Exception {

		String applicationId = null;
		Optional<String> referenceId = Optional.of("");

		KeyPairGenerateResponseDto KeyPairGenerateResponseDto = new KeyPairGenerateResponseDto();
		KeyPairGenerateResponseDto.setCertificate("hegfgr74567346hfgerhfgre");
		KeyPairGenerateResponseDto.setCertSignRequest("request");
		KeyPairGenerateResponseDto.setIssuedAt(LocalDateTime.now());
		KeyPairGenerateResponseDto.setTimestamp(LocalDateTime.now());
		KeyPairGenerateResponseDto
				.setExpiryAt(LocalDateTime.now(ZoneOffset.UTC).plus(Long.parseLong("30"), ChronoUnit.SECONDS));

		ResponseWrapper<KeyPairGenerateResponseDto> responseWrapper = new ResponseWrapper<>();
		responseWrapper.setResponse(KeyPairGenerateResponseDto);
		responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());

		when(keymanagerService.getCertificate(applicationId, referenceId)).thenReturn(responseWrapper.getResponse());

		mockMvc.perform(get("/system-info/certificate").param("applicationId", applicationId)
				.param("referenceId", referenceId.get()).accept(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

	}

	@Test
	public void uploadSignedCertificate_returnSuccessResponse() throws Exception {

		UploadCertificateRequestDto uploadCertificateRequestDto = new UploadCertificateRequestDto();
		uploadCertificateRequestDto.setApplicationId("12345");
		uploadCertificateRequestDto.setReferenceId("1234756yt5yhhfg");
		uploadCertificateRequestDto.setCertificateData("fgdgfhsdgfgsdhfgsdgfvdh");

		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<UploadCertificateRequestDto> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(uploadCertificateRequestDto);

		UploadCertificateResponseDto uploadCertificateResponseDto = new UploadCertificateResponseDto();
		uploadCertificateResponseDto.setStatus("upload success");
		uploadCertificateResponseDto.setTimestamp(LocalDateTime.now());

		ResponseWrapper<UploadCertificateResponseDto> responseWrapper = new ResponseWrapper<>();
		responseWrapper.setResponse(uploadCertificateResponseDto);
		responseWrapper.setResponseTime(IdentityProviderUtil.getUTCDateTime());

		when(keymanagerService.uploadCertificate(uploadCertificateRequestDto))
				.thenReturn(responseWrapper.getResponse());

		mockMvc.perform(post("/system-info/uploadCertificate").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk());

	}

	@Test
	public void uploadSignedCertificate_withInvalidCertificatData_returnErrorResponse() throws Exception {

		UploadCertificateRequestDto uploadCertificateRequestDto = new UploadCertificateRequestDto();
		uploadCertificateRequestDto.setApplicationId("12345");
		uploadCertificateRequestDto.setReferenceId("1234756yt5yhhfg");
		uploadCertificateRequestDto.setCertificateData(" ");
		ZonedDateTime requestTime = ZonedDateTime.now(ZoneOffset.UTC);

		RequestWrapper<UploadCertificateRequestDto> wrapper = new RequestWrapper<>();
		wrapper.setRequestTime(requestTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		wrapper.setRequest(uploadCertificateRequestDto);

		when(keymanagerService.uploadCertificate(uploadCertificateRequestDto))
				.thenThrow(KeymanagerServiceException.class);

		mockMvc.perform(post("/system-info/uploadCertificate").content(objectMapper.writeValueAsString(wrapper))
				.contentType(MediaType.APPLICATION_JSON)).andExpect(status().isOk())
				.andExpect(jsonPath("$.errors").isNotEmpty());
	}

}
