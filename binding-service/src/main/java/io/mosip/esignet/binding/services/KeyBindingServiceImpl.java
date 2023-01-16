/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.binding.services;

import static io.mosip.esignet.core.util.Constants.UTC_DATETIME_PATTERN;
import static io.mosip.esignet.core.util.ErrorConstants.*;

import java.time.format.DateTimeFormatter;
import java.util.*;

import io.mosip.esignet.core.dto.*;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.exception.KeyBindingException;
import io.mosip.esignet.core.exception.SendOtpException;
import io.mosip.esignet.core.spi.KeyBindingWrapper;
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

import io.mosip.esignet.binding.entity.PublicKeyRegistry;
import io.mosip.esignet.binding.repository.PublicKeyRegistryRepository;
import io.mosip.esignet.core.spi.KeyBindingService;
import io.mosip.esignet.core.util.ErrorConstants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@Transactional
public class KeyBindingServiceImpl implements KeyBindingService {

	@Autowired
	private KeyBindingWrapper keyBindingWrapper;

	@Autowired
	private PublicKeyRegistryRepository publicKeyRegistryRepository;

	@Autowired
	private KeymanagerUtil keymanagerUtil;

	@Autowired
	private KeyBindingHelperService keyBindingHelperService;

	@Value("${mosip.idp.binding.encrypt-binding-id:true}")
	private boolean encryptBindingId;


	@Override
	public BindingOtpResponse sendBindingOtp(BindingOtpRequest bindingOtpRequest, Map<String, String> requestHeaders) throws IdPException {
		SendOtpResult sendOtpResult;
		try {
			sendOtpResult = keyBindingWrapper.sendBindingOtp(bindingOtpRequest.getIndividualId(),
					bindingOtpRequest.getOtpChannels(), requestHeaders);
		} catch (SendOtpException e) {
			log.error("Failed to send binding otp: {}", e);
			throw new IdPException(e.getErrorCode());
		}

		if (sendOtpResult == null) {
			log.error("send-otp Failed wrapper returned null result!");
			throw new IdPException(ErrorConstants.SEND_OTP_FAILED);
		}

		BindingOtpResponse otpResponse = new BindingOtpResponse();
		otpResponse.setMaskedEmail(sendOtpResult.getMaskedEmail());
		otpResponse.setMaskedMobile(sendOtpResult.getMaskedMobile());
		return otpResponse;
	}

	@Override
	public WalletBindingResponse bindWallet(WalletBindingRequest walletBindingRequest, Map<String, String> requestHeaders) throws IdPException {
		//Do not store format, only check if the format is supported by the wrapper.
		if(!keyBindingWrapper.getSupportedChallengeFormats(walletBindingRequest.getAuthFactorType()).
				contains(walletBindingRequest.getFormat()))
			throw new IdPException(INVALID_CHALLENGE_FORMAT);

		String publicKey = IdentityProviderUtil.getJWKString(walletBindingRequest.getPublicKey());
		KeyBindingResult keyBindingResult;
		try {
			keyBindingResult = keyBindingWrapper.doKeyBinding(walletBindingRequest.getIndividualId(),
					walletBindingRequest.getChallengeList(), walletBindingRequest.getPublicKey(), requestHeaders);
		} catch (KeyBindingException e) {
			log.error("Failed to bind the key", e);
			throw new IdPException(e.getErrorCode());
		}

		if (keyBindingResult == null || keyBindingResult.getCertificate() == null || keyBindingResult.getPartnerSpecificUserToken() == null) {
			log.error("wallet binding failed with result : {}", keyBindingResult);
			throw new IdPException(KEY_BINDING_FAILED);
		}

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
			throw new IdPException(FAILED_TO_CREATE_JWE);
		}
	}
}