/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import static io.mosip.esignet.api.util.ErrorConstants.SEND_OTP_FAILED;
import static io.mosip.esignet.core.constants.Constants.UTC_DATETIME_PATTERN;
import static io.mosip.esignet.core.constants.ErrorConstants.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

import io.mosip.esignet.api.dto.AuthChallenge;
import io.mosip.esignet.api.dto.KeyBindingResult;
import io.mosip.esignet.api.dto.SendOtpResult;
import io.mosip.esignet.api.exception.KeyBindingException;
import io.mosip.esignet.api.exception.SendOtpException;
import io.mosip.esignet.api.spi.KeyBinder;
import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.mosip.esignet.entity.PublicKeyRegistry;
import io.mosip.esignet.core.spi.KeyBindingService;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
public class KeyBindingServiceImpl implements KeyBindingService {

	@Autowired
	private KeyBinder keyBindingWrapper;

	@Autowired
	private PublicKeyRegistryRepository publicKeyRegistryRepository;

	@Autowired
	private KeymanagerUtil keymanagerUtil;

	@Autowired
	private KeyBindingHelperService keyBindingHelperService;

	@Value("${mosip.esignet.binding.encrypt-binding-id:true}")
	private boolean encryptBindingId;


	@Override
	public BindingOtpResponse sendBindingOtp(BindingOtpRequest bindingOtpRequest, Map<String, String> requestHeaders) throws EsignetException {
		log.debug("sendBindingOtp :: Request headers >> {}", requestHeaders);
		SendOtpResult sendOtpResult;
		try {
			sendOtpResult = keyBindingWrapper.sendBindingOtp(bindingOtpRequest.getIndividualId(),
					bindingOtpRequest.getOtpChannels(), requestHeaders);
		} catch (SendOtpException e) {
			log.error("Failed to send binding otp: {}", e);
			throw new EsignetException(e.getErrorCode());
		}

		if (sendOtpResult == null) {
			log.error("send-otp Failed wrapper returned null result!");
			throw new EsignetException(SEND_OTP_FAILED);
		}

		BindingOtpResponse otpResponse = new BindingOtpResponse();
		otpResponse.setMaskedEmail(sendOtpResult.getMaskedEmail());
		otpResponse.setMaskedMobile(sendOtpResult.getMaskedMobile());
		return otpResponse;
	}

	private void validateChallengeListAuthFormat(List<AuthChallenge> challengeList){
		if(!challengeList.stream().allMatch(challenge->keyBindingWrapper.getSupportedChallengeFormats(challenge.getAuthFactorType()).
				contains(challenge.getFormat()))) {
			log.error("Invalid auth factor type or challenge format in the challenge list");
			throw new EsignetException(INVALID_AUTH_FACTOR_TYPE_OR_CHALLENGE_FORMAT);
		}
	}

	@Override
	public WalletBindingResponse bindWallet(WalletBindingRequest walletBindingRequest, Map<String, String> requestHeaders) throws EsignetException {
		log.debug("bindWallet :: Request headers >> {}", requestHeaders);
		validateChallengeListAuthFormat(walletBindingRequest.getChallengeList());

		//Do not store format, only check if the format is supported by the wrapper.
		if(!keyBindingWrapper.getSupportedChallengeFormats(walletBindingRequest.getAuthFactorType()).
				contains(walletBindingRequest.getFormat()))
			throw new EsignetException(INVALID_AUTH_FACTOR_TYPE_OR_CHALLENGE_FORMAT);

		String publicKey = IdentityProviderUtil.getJWKString(walletBindingRequest.getPublicKey());
		KeyBindingResult keyBindingResult;
		try {
			keyBindingResult = keyBindingWrapper.doKeyBinding(walletBindingRequest.getIndividualId(),
					walletBindingRequest.getChallengeList(), walletBindingRequest.getPublicKey(), walletBindingRequest.getAuthFactorType(), requestHeaders);
		} catch (KeyBindingException e) {
			log.error("Failed to bind the key", e);
			throw new EsignetException(e.getErrorCode());
		}

		if (keyBindingResult == null || keyBindingResult.getCertificate() == null || keyBindingResult.getPartnerSpecificUserToken() == null) {
			log.error("wallet binding failed with result : {}", keyBindingResult);
			throw new EsignetException(KEY_BINDING_FAILED);
		}

		//We will always keep this in binding-service control, as future features will be based on this registry.
		PublicKeyRegistry publicKeyRegistry = keyBindingHelperService.storeKeyBindingDetailsInRegistry(walletBindingRequest.getIndividualId(),
				keyBindingResult.getPartnerSpecificUserToken(), publicKey, keyBindingResult.getCertificate(),
				walletBindingRequest.getAuthFactorType());

		return new WalletBindingResponse(encryptBindingId ? getJWE(walletBindingRequest.getPublicKey(), publicKeyRegistry.getWalletBindingId()) :
						publicKeyRegistry.getWalletBindingId(),
				keyBindingResult.getCertificate(),
				publicKeyRegistry.getExpiredtimes().format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
	}

	private String getJWE(Map<String, Object> publicKey, String walletBindingId) {
		try {
			JsonWebEncryption jsonWebEncryption = new JsonWebEncryption();
			jsonWebEncryption.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.RSA_OAEP_256);
			jsonWebEncryption.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_256_GCM);
			jsonWebEncryption.setPayload(walletBindingId);
			jsonWebEncryption.setContentTypeHeaderValue("JWT");
			RsaJsonWebKey jsonWebKey = new RsaJsonWebKey(publicKey);
			jsonWebEncryption.setKey(jsonWebKey.getKey());
			jsonWebEncryption.setKeyIdHeaderValue(jsonWebKey.getKeyId());
			return jsonWebEncryption.getCompactSerialization();
		} catch (JoseException e) {
			log.error("Failed to create JWE", e);
			throw new EsignetException(FAILED_TO_CREATE_JWE);
		}
	}
}