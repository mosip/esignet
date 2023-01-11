/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.context.request.async.DeferredResult;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.AuthenticationFactor;
import io.mosip.idp.core.dto.ClaimDetail;
import io.mosip.idp.core.dto.Claims;
import io.mosip.idp.core.dto.ClientDetail;
import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.dto.KycAuthResult;
import io.mosip.idp.core.dto.LinkAuthCodeRequest;
import io.mosip.idp.core.dto.LinkCodeRequest;
import io.mosip.idp.core.dto.LinkCodeResponse;
import io.mosip.idp.core.dto.LinkStatusRequest;
import io.mosip.idp.core.dto.LinkTransactionMetadata;
import io.mosip.idp.core.dto.LinkTransactionRequest;
import io.mosip.idp.core.dto.LinkTransactionResponse;
import io.mosip.idp.core.dto.LinkedConsentRequest;
import io.mosip.idp.core.dto.LinkedConsentResponse;
import io.mosip.idp.core.dto.LinkedKycAuthRequest;
import io.mosip.idp.core.dto.LinkedKycAuthResponse;
import io.mosip.idp.core.dto.OtpRequest;
import io.mosip.idp.core.dto.OtpResponse;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidTransactionException;
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.util.AuthenticationContextClassRefUtil;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.idp.core.validator.OtpChannel;

@RunWith(MockitoJUnitRunner.class)
public class LinkedAuthorizationServiceTest {

	@Mock
	CacheUtilService cacheUtilService;

	@InjectMocks
	LinkedAuthorizationServiceImpl linkAuthorizationServiceImpl;

	@Mock
	AuthorizationHelperService authorizationHelperService;

	@Value("${mosip.idp.link-code-expire-in-secs}")
	private int linkCodeExpiryInSeconds;

	@Mock
	ClientManagementService clientManagementService;

	@Value("${mosip.idp.link-status-deferred-response-timeout-secs:25}")
	private long linkStatusDeferredResponseTimeout;

	@Mock
	AuthenticationContextClassRefUtil authenticationContextClassRefUtil;

	@Mock
	IdentityProviderUtil identityProviderUtil;

	@Mock
	KafkaHelperService kafkaHelperService;

	@Mock
	ObjectMapper objectMapper = new ObjectMapper();

	private IdPTransaction transaction;

	@Before
	public void setup() {

		transaction = new IdPTransaction();
		transaction.setAuthTransactionId("5637465368573875875958");
		transaction.setIndividualId("345635");
		transaction.setClientId("gfhgfh");
		transaction.setNonce("5555");
	}

	@Test
	public void generateLinkCode_thenPass() {

		LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
		linkCodeRequest.setTransactionId("5637465368573875875958");
		ZonedDateTime expireDateTime = null;
		expireDateTime = ZonedDateTime.now(ZoneOffset.UTC).plus(linkCodeExpiryInSeconds, ChronoUnit.SECONDS);

		LinkCodeResponse linkCodeResponse = new LinkCodeResponse();
		linkCodeResponse.setLinkCode("123456");
		linkCodeResponse.setTransactionId(linkCodeRequest.getTransactionId());
		linkCodeResponse.setExpireDateTime(expireDateTime == null ? null
				: expireDateTime.format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));

		when(cacheUtilService.getPreAuthTransaction(linkCodeRequest.getTransactionId())).thenReturn(transaction);
		LinkCodeResponse generateLinkCode = linkAuthorizationServiceImpl.generateLinkCode(linkCodeRequest);
		Assert.assertNotNull(generateLinkCode);
		Assert.assertFalse(generateLinkCode.getTransactionId().isEmpty());
		Assert.assertFalse(generateLinkCode.getExpireDateTime().isEmpty());
	}

	@Test
	public void generateLinkCode_withInvalidTransation_thenFail() {

		LinkCodeRequest linkCodeRequest = new LinkCodeRequest();
		linkCodeRequest.setTransactionId(null);

		when(cacheUtilService.getPreAuthTransaction(linkCodeRequest.getTransactionId())).thenReturn(null);
		try {
			linkAuthorizationServiceImpl.generateLinkCode(linkCodeRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_TRANSACTION));
		}

	}

	@Test
	public void linkTransaction_returnSuccessResponse_thenPass() {
		LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
		linkTransactionRequest.setLinkCode("123456");
		String linkCodeHash = "qwerty";

		when(authorizationHelperService.getKeyHash(linkTransactionRequest.getLinkCode())).thenReturn(linkCodeHash);
		LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("123456789", "dvhfgvhfdhgvhbvj");
		when(cacheUtilService.getLinkCodeGenerated(linkCodeHash)).thenReturn(linkTransactionMetadata);
		when(cacheUtilService.getPreAuthTransaction(linkTransactionMetadata.getTransactionId()))
				.thenReturn(transaction);
		ClientDetail clientDetail = new ClientDetail();
		clientDetail.setId("34567");
		clientDetail.setClaims(Arrays.asList("email", "given_name"));
		clientDetail.setAcrValues(Arrays.asList("mosip:idp:acr:wallet"));

		String[] str = { "gffh" };
		ClaimDetail claimDetails = new ClaimDetail("54736547", str, false);
		Claims claims = new Claims();
		Map<String, ClaimDetail> claimMap = new HashMap<String, ClaimDetail>();
		claimMap.put("acr", claimDetails);
		claimMap.put("jti", claimDetails);
		claims.setId_token(claimMap);
		claims.setUserinfo(claimMap);
		transaction.setRequestedClaims(claims);
		List<String> essentialClaims = new ArrayList<>();

		Map<String, List> mapList = new HashMap<String, List>();
		mapList.put(linkCodeHash, new ArrayList<>(essentialClaims));
		when(clientManagementService.getClientDetails(transaction.getClientId())).thenReturn(clientDetail);
		when(cacheUtilService.setLinkedTransaction(linkTransactionMetadata.getTransactionId(), transaction))
				.thenReturn(transaction);
		when(authorizationHelperService.getClaimNames(transaction.getRequestedClaims())).thenReturn(mapList);
		Mockito.doNothing().when(kafkaHelperService).publish(any(), any());

		LinkTransactionResponse linkTransaction = linkAuthorizationServiceImpl.linkTransaction(linkTransactionRequest);

		Assert.assertNotNull(linkTransaction);
		Assert.assertFalse(linkTransaction.getLinkTransactionId().isEmpty());
	}

	@Test
	public void linkTransaction_withInvalidLinkCode_thenFail() {

		LinkTransactionRequest linkTransactionRequest = new LinkTransactionRequest();
		linkTransactionRequest.setLinkCode("");
		String linkCodeHash = null;

		when(authorizationHelperService.getKeyHash(linkTransactionRequest.getLinkCode())).thenReturn(linkCodeHash);
		when(cacheUtilService.getLinkCodeGenerated(linkCodeHash)).thenReturn(null);
		try {
			linkAuthorizationServiceImpl.linkTransaction(linkTransactionRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_LINK_CODE));
		}
	}

	@Test
	public void sendOtp_returnSuccessResponse_thenPass() {

		List<@OtpChannel String> otpChannel = new ArrayList<>();
		otpChannel.add("sms");
		OtpRequest otpRequest = new OtpRequest();
		otpRequest.setIndividualId("12345");
		otpRequest.setTransactionId("gfdfvuy4534");
		otpRequest.setOtpChannels(otpChannel);

		when(cacheUtilService.getLinkedSessionTransaction(otpRequest.getTransactionId())).thenReturn(transaction);
		SendOtpResult sendOtpResult = new SendOtpResult("gfdfvuy4534", "xyz***@gmail.com", "8765467***");
		when(authorizationHelperService.delegateSendOtpRequest(otpRequest, transaction)).thenReturn(sendOtpResult);

		OtpResponse sendOtp = linkAuthorizationServiceImpl.sendOtp(otpRequest);
		Assert.assertNotNull(sendOtp);
		Assert.assertFalse(sendOtp.getTransactionId().isEmpty());
	}

	@Test
	public void sendOtp_withInvalidLinkCode_thenFail() {

		List<@OtpChannel String> otpChannel = new ArrayList<>();
		otpChannel.add("sms");
		OtpRequest otpRequest = new OtpRequest();
		otpRequest.setIndividualId("12345");
		otpRequest.setTransactionId("");
		otpRequest.setOtpChannels(otpChannel);

		when(cacheUtilService.getLinkedSessionTransaction(otpRequest.getTransactionId())).thenReturn(null);

		try {
			linkAuthorizationServiceImpl.sendOtp(otpRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_TRANSACTION));
		}
	}

	@Test
	public void authenticate_returnSuccessResponse_thenPass() {

		LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
		AuthChallenge authChallenge = new AuthChallenge();
		List<AuthChallenge> listAuthChallenge = new ArrayList<AuthChallenge>();
		authChallenge.setAuthFactorType("ghregh");
		authChallenge.setChallenge("53756hhikn");
		listAuthChallenge.add(authChallenge);
		linkedKycAuthRequest.setIndividualId("1234");
		linkedKycAuthRequest.setLinkedTransactionId("hfrehgfu65746t57");
		linkedKycAuthRequest.setChallengeList(listAuthChallenge);

		when(cacheUtilService.getLinkedSessionTransaction(linkedKycAuthRequest.getLinkedTransactionId()))
				.thenReturn(transaction);
		Set<List<AuthenticationFactor>> authenticationSet = new HashSet<List<AuthenticationFactor>>();
		AuthenticationFactor authenticationFactor = new AuthenticationFactor();
		authenticationFactor.setCount(1);
		authenticationFactor.setType("type");
		List<AuthenticationFactor> list = new ArrayList<AuthenticationFactor>();
		list.add(authenticationFactor);
		authenticationSet.add(list);
		when(authorizationHelperService.getProvidedAuthFactors(transaction, listAuthChallenge))
				.thenReturn(authenticationSet);
		KycAuthResult kycAuthResult = new KycAuthResult();
		kycAuthResult.setKycToken("token");
		kycAuthResult.setPartnerSpecificUserToken("partner token");

		when(authorizationHelperService.delegateAuthenticateRequest(linkedKycAuthRequest.getLinkedTransactionId(),
				linkedKycAuthRequest.getIndividualId(), listAuthChallenge, transaction)).thenReturn(kycAuthResult);

		LinkedKycAuthResponse authenticateUser = linkAuthorizationServiceImpl.authenticateUser(linkedKycAuthRequest);
		Assert.assertNotNull(authenticateUser);
		Assert.assertFalse(authenticateUser.getLinkedTransactionId().isEmpty());
	}

	@Test
	public void authenticate_withInvalidTransation_thenFail() {

		LinkedKycAuthRequest linkedKycAuthRequest = new LinkedKycAuthRequest();
		AuthChallenge authChallenge = new AuthChallenge();
		List<AuthChallenge> listAuthChallenge = new ArrayList<AuthChallenge>();
		authChallenge.setAuthFactorType("ghregh");
		authChallenge.setChallenge("53756hhikn");
		listAuthChallenge.add(authChallenge);
		linkedKycAuthRequest.setIndividualId("1234");
		linkedKycAuthRequest.setLinkedTransactionId("");
		linkedKycAuthRequest.setChallengeList(listAuthChallenge);

		when(cacheUtilService.getLinkedSessionTransaction(linkedKycAuthRequest.getLinkedTransactionId()))
				.thenReturn(null);

		try {
			linkAuthorizationServiceImpl.authenticateUser(linkedKycAuthRequest);
			Assert.fail();
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_TRANSACTION));
		}
	}

	@Test
	public void saveConsent_returnSuccessResponse_thenPass() {

		LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
		linkedConsentRequest.setLinkedTransactionId("ghdfgfh");
		List<String> acceptedClaims = new ArrayList<String>();
		acceptedClaims.add("token");
		List<String> permittedAuthorizeScopes = new ArrayList<String>();
		permittedAuthorizeScopes.add("local");
		linkedConsentRequest.setPermittedAuthorizeScopes(permittedAuthorizeScopes);

		when(cacheUtilService.getLinkedAuthTransaction(linkedConsentRequest.getLinkedTransactionId()))
				.thenReturn(transaction);

		LinkedConsentResponse authRespDto = new LinkedConsentResponse();
		authRespDto.setLinkedTransactionId(linkedConsentRequest.getLinkedTransactionId());

		LinkedConsentResponse saveConsent = linkAuthorizationServiceImpl.saveConsent(linkedConsentRequest);
		Assert.assertNotNull(saveConsent);
		Assert.assertFalse(saveConsent.getLinkedTransactionId().isEmpty());
	}

	@Test
	public void saveConsent_withInvalidTransaction_thenFail() {

		LinkedConsentRequest linkedConsentRequest = new LinkedConsentRequest();
		linkedConsentRequest.setLinkedTransactionId("");
		List<String> acceptedClaims = new ArrayList<String>();
		acceptedClaims.add("token");
		List<String> permittedAuthorizeScopes = new ArrayList<String>();
		permittedAuthorizeScopes.add("local");
		linkedConsentRequest.setPermittedAuthorizeScopes(permittedAuthorizeScopes);

		when(cacheUtilService.getLinkedAuthTransaction(linkedConsentRequest.getLinkedTransactionId())).thenReturn(null);

		try {
			linkAuthorizationServiceImpl.saveConsent(linkedConsentRequest);
			Assert.fail();
		} catch (InvalidTransactionException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_TRANSACTION));
		}
	}

	@Test
	public void getLinkStatus_returnSuccessResponse_thenPass() {

		LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
		linkStatusRequest.setLinkCode("4556657");
		linkStatusRequest.setTransactionId("gfyg754587685ghf");
		String linkCodeHash = "linkhashcode";
		boolean assertFlag = false;
		when(authorizationHelperService.getKeyHash(linkStatusRequest.getLinkCode())).thenReturn(linkCodeHash);
		LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("gfyg754587685ghf",
				"dvhfgvhfdhgvhbvj");
		when(cacheUtilService.getLinkCodeGenerated(linkCodeHash)).thenReturn(linkTransactionMetadata);
		DeferredResult deferredResult = new DeferredResult<>(linkStatusDeferredResponseTimeout * 1000);

		try {
			linkAuthorizationServiceImpl.getLinkStatus(deferredResult, linkStatusRequest);
			assertFlag = true;
			Assert.assertTrue(assertFlag);
		} catch (IdPException e) {
			Assert.assertTrue(assertFlag);
		}
	}

	@Test
	public void getLinkStatus_withInvalidLinkCode_thenFail() {

		LinkStatusRequest linkStatusRequest = new LinkStatusRequest();
		linkStatusRequest.setLinkCode("");
		linkStatusRequest.setTransactionId("gfyg754587685ghf");
		String linkCodeHash = "linkhashcode";
		boolean assertFlag = false;
		when(authorizationHelperService.getKeyHash(linkStatusRequest.getLinkCode())).thenReturn(linkCodeHash);
		when(cacheUtilService.getLinkCodeGenerated(linkCodeHash)).thenReturn(null);
		DeferredResult deferredResult = new DeferredResult<>(linkStatusDeferredResponseTimeout * 1000);
		when(cacheUtilService.getLinkedTransactionMetadata(linkCodeHash)).thenReturn(null);

		try {
			linkAuthorizationServiceImpl.getLinkStatus(deferredResult, linkStatusRequest);
			assertFlag = true;
			Assert.assertTrue(assertFlag);
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_LINK_CODE));
		}
	}

	@Test
	public void getLinkAuthCode_returnSuccessResponse_thenPass() {

		LinkAuthCodeRequest linkAuthCodeRequest = new LinkAuthCodeRequest();
		linkAuthCodeRequest.setLinkedCode("4556657");
		linkAuthCodeRequest.setTransactionId("gfyg754587685ghf");
		String linkCodeHash = "linkhashcode";
		boolean assertFlag = false;

		when(authorizationHelperService.getKeyHash(linkAuthCodeRequest.getLinkedCode())).thenReturn(linkCodeHash);
		LinkTransactionMetadata linkTransactionMetadata = new LinkTransactionMetadata("gfyg754587685ghf",
				"dvhfgvhfdhgvhbvj");
		when(cacheUtilService.getLinkedTransactionMetadata(linkCodeHash)).thenReturn(linkTransactionMetadata);
		DeferredResult deferredResult = new DeferredResult<>(linkStatusDeferredResponseTimeout * 1000);

		try {
			linkAuthorizationServiceImpl.getLinkAuthCode(deferredResult, linkAuthCodeRequest);
			assertFlag = true;
			Assert.assertTrue(assertFlag);
		} catch (IdPException e) {
			Assert.assertTrue(assertFlag);
		}

	}

	@Test
	public void getLinkAuthCode_withInvalidLinkCode_thenFail() {

		LinkAuthCodeRequest linkAuthCodeRequest = new LinkAuthCodeRequest();
		linkAuthCodeRequest.setLinkedCode("");
		linkAuthCodeRequest.setTransactionId("gfyg754587685ghf");
		String linkCodeHash = "linkhashcode";
		boolean assertFlag = false;

		when(authorizationHelperService.getKeyHash(linkAuthCodeRequest.getLinkedCode())).thenReturn(linkCodeHash);
		when(cacheUtilService.getLinkedTransactionMetadata(linkCodeHash)).thenReturn(null);
		DeferredResult deferredResult = new DeferredResult<>(linkStatusDeferredResponseTimeout * 1000);

		try {
			linkAuthorizationServiceImpl.getLinkAuthCode(deferredResult, linkAuthCodeRequest);
			assertFlag = true;
			Assert.assertTrue(assertFlag);
		} catch (IdPException e) {
			Assert.assertTrue(e.getErrorCode().equals(ErrorConstants.INVALID_LINK_CODE));
		}

	}

}
