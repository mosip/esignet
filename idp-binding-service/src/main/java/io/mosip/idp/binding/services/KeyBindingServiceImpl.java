/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.services;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static io.mosip.idp.core.util.ErrorConstants.*;
import static io.mosip.idp.core.util.IdentityProviderUtil.*;

import java.io.IOException;
import java.io.StringReader;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jwt.SignedJWT;
import io.mosip.idp.core.dto.*;
import io.mosip.idp.core.exception.*;
import io.mosip.idp.core.spi.KeyBindingWrapper;
import io.mosip.kernel.keymanagerservice.exception.KeymanagerServiceException;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import org.bouncycastle.util.io.pem.PemReader;
import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.keys.X509Util;
import org.jose4j.lang.JoseException;
import org.json.simple.JSONArray;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.proc.JWSKeySelector;
import com.nimbusds.jose.proc.JWSVerificationKeySelector;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.proc.ConfigurableJWTProcessor;
import com.nimbusds.jwt.proc.DefaultJWTClaimsVerifier;
import com.nimbusds.jwt.proc.DefaultJWTProcessor;
import com.nimbusds.jwt.proc.JWTClaimsSetVerifier;

import io.mosip.idp.binding.dto.BindingTransaction;
import io.mosip.idp.binding.entity.PublicKeyRegistry;
import io.mosip.idp.binding.repository.PublicKeyRegistryRepository;
import io.mosip.idp.core.spi.KeyBindingService;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

@Slf4j
@Service
@Transactional
public class KeyBindingServiceImpl implements KeyBindingService {

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private CacheUtilService cacheUtilService;

	@Autowired
	private KeyBindingWrapper keyBindingWrapper;

	@Autowired
	private PublicKeyRegistryRepository publicKeyRegistryRepository;

	@Autowired
	private KeymanagerUtil keymanagerUtil;

	@Value("${mosip.idp.binding.public-key-expire-days}")
	private int expireDays;

	@Value("${mosip.idp.binding.salt-length}")
	private int saltLength;

	@Value("${mosip.idp.binding.validate-binding-url}")
	private String validateBindingUrl;

	@Value("${mosip.idp.binding.encrypt-binding-id:true}")
	private boolean encryptBindingId;

	private static Set<String> REQUIRED_WLA_CLAIMS;

	static {
		REQUIRED_WLA_CLAIMS = new HashSet<>();
		REQUIRED_WLA_CLAIMS.add("sub");
		REQUIRED_WLA_CLAIMS.add("aud");
		REQUIRED_WLA_CLAIMS.add("exp");
		REQUIRED_WLA_CLAIMS.add("iss");
		REQUIRED_WLA_CLAIMS.add("iat");
	}

	@Override
	public OtpResponse sendBindingOtp(BindingOtpRequest bindingOtpRequest, Map<String, String> headers) throws IdPException {
		// Start and Cache the transaction
		final String transactionId = IdentityProviderUtil.createTransactionId(null);
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(bindingOtpRequest.getIndividualId());
		transaction.setAuthTransactionId(IdentityProviderUtil.createTransactionId(null));
		transaction.setAuthChallengeTypes(Arrays.asList("OTP"));
		transaction = cacheUtilService.setTransaction(transactionId, transaction);

		SendOtpResult sendOtpResult;
		try {
			sendOtpResult = keyBindingWrapper.sendBindingOtp(transaction.getAuthTransactionId(),
					bindingOtpRequest.getIndividualId(), bindingOtpRequest.getOtpChannels(), headers);
		} catch (SendOtpException e) {
			log.error("Failed to send otp for transaction : {}", transactionId, e);
			throw new IdPException(e.getErrorCode());
		}

		if (sendOtpResult == null || !transaction.getAuthTransactionId().equals(sendOtpResult.getTransactionId())) {
			log.error("send-otp Failed with result : {}", sendOtpResult);
			throw new IdPException(ErrorConstants.SEND_OTP_FAILED);
		}

		OtpResponse otpResponse = new OtpResponse();
		otpResponse.setTransactionId(transactionId);
		otpResponse.setMaskedEmail(sendOtpResult.getMaskedEmail());
		otpResponse.setMaskedMobile(sendOtpResult.getMaskedMobile());
		return otpResponse;
	}

	@Override
	public WalletBindingResponse bindWallet(WalletBindingRequest walletBindingRequest, Map<String, String> headers) throws IdPException {
		BindingTransaction bindingTransaction = cacheUtilService.getTransaction(walletBindingRequest.getTransactionId());
		if (bindingTransaction == null)
			throw new InvalidTransactionException();

		if (!bindingTransaction.getIndividualId().equals(walletBindingRequest.getIndividualId()))
			throw new IdPException(INVALID_INDIVIDUAL_ID);

		if (walletBindingRequest.getChallengeList().stream().noneMatch(keyBindingAuthChallenge ->
				bindingTransaction.getAuthChallengeTypes().contains(keyBindingAuthChallenge.getAuthFactorType())))
			throw new IdPException(AUTH_FACTOR_MISMATCH);

		String publicKey = IdentityProviderUtil.getJWKString(walletBindingRequest.getPublicKey());

		KeyBindingResult keyBindingResult;
		try {
			keyBindingResult = keyBindingWrapper.doKeyBinding(bindingTransaction.getAuthTransactionId(),
					walletBindingRequest.getIndividualId(), walletBindingRequest.getChallengeList(),
					walletBindingRequest.getPublicKey(), headers);
		} catch (KeyBindingException e) {
			log.error("Failed to bind the key : {}", walletBindingRequest.getTransactionId(), e);
			throw new IdPException(e.getErrorCode());
		}

		if (keyBindingResult == null || keyBindingResult.getCertificate() == null || keyBindingResult.getPartnerSpecificToken() == null) {
			log.error("wallet binding failed with result : {}", keyBindingResult);
			throw new IdPException(KEY_BINDING_FAILED);
		}

		PublicKeyRegistry publicKeyRegistry = storeKeyBindingDetailsInRegistry(walletBindingRequest.getIndividualId(),
				keyBindingResult.getPartnerSpecificToken(), publicKey, keyBindingResult.getCertificate(),
				walletBindingRequest.getAuthFactorTypes());

		return new WalletBindingResponse(walletBindingRequest.getTransactionId(),
				encryptBindingId ? getJWE(walletBindingRequest.getPublicKey(), publicKeyRegistry.getWalletBindingId()) :
						publicKeyRegistry.getWalletBindingId(),
				keyBindingResult.getCertificate(),
				publicKeyRegistry.getExpiredtimes().format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
	}

	@Override
	public ValidateBindingResponse validateBinding(ValidateBindingRequest validateBindingRequest) throws IdPException {
		String individualIdHash = getIndividualIdHash(validateBindingRequest.getIndividualId());
		Optional<PublicKeyRegistry> publicKeyRegistry = publicKeyRegistryRepository.findByIdHashAndExpiredtimesGreaterThan(individualIdHash,
				LocalDateTime.now(ZoneOffset.UTC));
		if (!publicKeyRegistry.isPresent())
			throw new IdPException(ErrorConstants.KEY_BINDING_NOT_FOUND);

		List<String> supportedAuthFactors = null;
		try {
			supportedAuthFactors = objectMapper.readValue(publicKeyRegistry.get().getAuthFactors(), new TypeReference<List<String>>() {});
		} catch (IllegalArgumentException | JsonProcessingException e) {
			log.error("Failed to parse supported auth-factors", e);
		}

		//check if provided challenge auth-factor is the bound auth-factor-type for the provided individualId
		if(CollectionUtils.isEmpty(supportedAuthFactors) || !supportedAuthFactors.contains(validateBindingRequest.getChallenge().getAuthFactorType()))
			throw new IdPException(ErrorConstants.UNBOUND_AUTH_FACTOR);

		X509Certificate x509Certificate = null;
		try {
			x509Certificate = (X509Certificate) keymanagerUtil.convertToCertificate(publicKeyRegistry.get().getCertificate());
			//TODO should we validate the certificate?
		} catch (KeymanagerServiceException e) {
			log.error("Certificate parsing failed", e);
			throw new IdPException(ErrorConstants.INVALID_CERTIFICATE);
		}

		switch (validateBindingRequest.getChallenge().getAuthFactorType()) {
			case "WLA" :
				verifyWLAToken(validateBindingRequest.getIndividualId(), validateBindingRequest.getChallenge().getChallenge(),
						validateBindingRequest.getChallenge().getFormat(), publicKeyRegistry.get().getPublicKey(),
						X509Util.x5t(x509Certificate));
				break;
			default:
				throw new IdPException(UNKNOWN_BINDING_CHALLENGE);
		}

		return new ValidateBindingResponse(validateBindingRequest.getIndividualId(), validateBindingRequest.getTransactionId());
	}

	private void verifyWLAToken(String individualId, String wlaToken, String format, String publicKey, String thumbprint) {
		if(format == null) { format = "JWT"; }

		switch (format) {
			case "JWT" :
				try {
					JWSKeySelector keySelector = new JWSVerificationKeySelector(JWSAlgorithm.RS256,
							new ImmutableJWKSet(new JWKSet(RSAKey.parse(publicKey))));
					JWTClaimsSetVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
							.audience(validateBindingUrl)
							.subject(individualId)
							.build(), REQUIRED_WLA_CLAIMS);

					ConfigurableJWTProcessor jwtProcessor = new DefaultJWTProcessor();
					jwtProcessor.setJWSKeySelector(keySelector);
					jwtProcessor.setJWTClaimsSetVerifier(claimsSetVerifier);
					jwtProcessor.process(wlaToken, null); //If invalid throws exception

					SignedJWT signedJWT = SignedJWT.parse(wlaToken);
					if(signedJWT.getHeader().getX509CertThumbprint() == null ||
							!signedJWT.getHeader().getX509CertThumbprint().toString().equals(thumbprint)) {
						log.error("SHA-1 thumbprint is invalid {}", signedJWT.getHeader().getX509CertThumbprint());
						throw new IdPException(ErrorConstants.INVALID_WLA_TOKEN);
					}

				} catch (Exception e) {
					log.error("Failed to verify WLA token", e);
					throw new IdPException(ErrorConstants.INVALID_WLA_TOKEN);
				}
				break;
			default:
				throw new IdPException(UNKNOWN_WLA_FORMAT);
		}
	}

	private PublicKeyRegistry storeKeyBindingDetailsInRegistry(String individualId, String partnerSpecificUserToken, String publicKey,
															   String certificate, List<String> authFactors) throws IdPException {

		String publicKeyHash = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, publicKey);

		//check if any entry exists with same public key for different PSU-token
		Optional<PublicKeyRegistry> optionalPublicKeyRegistryForDuplicateCheck = publicKeyRegistryRepository
				.findByPublicKeyHashNotEqualToPsuToken(publicKeyHash, partnerSpecificUserToken);
		if (optionalPublicKeyRegistryForDuplicateCheck.isPresent())
			throw new IdPException(DUPLICATE_PUBLIC_KEY);

		String walletBindingId = null;
		LocalDateTime expireDTimes = calculateExpiresDTimes();
		String supportedAuthFactorTypes = JSONArray.toJSONString(authFactors);
		Optional<PublicKeyRegistry> optionalPublicKeyRegistry = publicKeyRegistryRepository.findOneByPsuToken(partnerSpecificUserToken);
		//Entry exists, consider to use same wallet-binding-id. Only update public-key & expireDT
		if (optionalPublicKeyRegistry.isPresent()) {
			walletBindingId = optionalPublicKeyRegistry.get().getWalletBindingId();
			int noOfUpdatedRecords = publicKeyRegistryRepository.updatePublicKeyRegistry(publicKey, publicKeyHash,
					expireDTimes, partnerSpecificUserToken, certificate, supportedAuthFactorTypes);
			log.info("Number of records updated successfully {}", noOfUpdatedRecords);
		}

		//Upsert one entry for the provided individual-id
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash(getIndividualIdHash(individualId));
		publicKeyRegistry.setPsuToken(partnerSpecificUserToken);
		publicKeyRegistry.setPublicKey(publicKey);
		publicKeyRegistry.setExpiredtimes(expireDTimes);
		publicKeyRegistry.setWalletBindingId((walletBindingId == null) ?
				IdentityProviderUtil.digestAsPlainTextWithSalt(partnerSpecificUserToken.getBytes(),	IdentityProviderUtil.generateSalt(saltLength)) : walletBindingId);
		publicKeyRegistry.setPublicKeyHash(publicKeyHash);
		publicKeyRegistry.setCertificate(certificate);
		publicKeyRegistry.setAuthFactors(supportedAuthFactorTypes);
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now(ZoneId.of("UTC")));
		publicKeyRegistry = publicKeyRegistryRepository.save(publicKeyRegistry);
		log.info("Saved PublicKeyRegistry details successfully");
		return publicKeyRegistry;
	}

	private LocalDateTime calculateExpiresDTimes() {
		LocalDateTime currentDateTime = LocalDateTime.now(ZoneId.of("UTC"));
		return currentDateTime.plusDays(expireDays);
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

	private String getIndividualIdHash(String individualId) {
		return IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, individualId);
	}
}