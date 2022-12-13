/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.services;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static io.mosip.idp.core.util.ErrorConstants.AUTH_FAILED;
import static io.mosip.idp.core.util.ErrorConstants.DUPLICATE_PUBLIC_KEY;
import static io.mosip.idp.core.util.ErrorConstants.INVALID_AUTH_CHALLENGE;
import static io.mosip.idp.core.util.ErrorConstants.INVALID_INDIVIDUAL_ID;
import static io.mosip.idp.core.util.ErrorConstants.INVALID_PUBLIC_KEY;
import static io.mosip.idp.core.util.IdentityProviderUtil.ALGO_SHA_256;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
import io.mosip.idp.core.dto.BindingOtpRequest;
import io.mosip.idp.core.dto.KycAuthRequest;
import io.mosip.idp.core.dto.KycAuthResult;
import io.mosip.idp.core.dto.OtpResponse;
import io.mosip.idp.core.dto.SendOtpRequest;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.dto.ValidateBindingRequest;
import io.mosip.idp.core.dto.ValidateBindingResponse;
import io.mosip.idp.core.dto.WalletBindingRequest;
import io.mosip.idp.core.dto.WalletBindingResponse;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidTransactionException;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.WalletBindingService;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class WalletBindingServiceImpl implements WalletBindingService {

	@Autowired
	private CacheUtilService cacheUtilService;

	@Autowired
	private AuthenticationWrapper authenticationWrapper;

	@Autowired
	private PublicKeyRegistryRepository publicKeyRegistryRepository;

	@Value("${mosip.idp.binding.auth-partner-id}")
	private String authPartnerId;

	@Value("${mosip.idp.binding.auth-license-key}")
	private String apiKey;

	@Value("${mosip.idp.binding.public-key-expire-days}")
	private int expireDays;

	@Value("${mosip.idp.binding.salt-length}")
	private int saltLength;
	
	@Value("${mosip.idp.binding.validate-binding-issuer-id}")
	private String validateBindingIssuerId;
	
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
	public OtpResponse sendBindingOtp(BindingOtpRequest otpRequest) throws IdPException {
		// Cache the transaction
		final String transactionId = IdentityProviderUtil.createTransactionId(null);
		BindingTransaction transaction = new BindingTransaction();
		transaction.setIndividualId(otpRequest.getIndividualId());
		transaction.setAuthTransactionId(IdentityProviderUtil.createTransactionId(null));
		transaction.setAuthChallengeType("OTP");
		cacheUtilService.setTransaction(transactionId, transaction);

		SendOtpResult sendOtpResult;
		try {
			SendOtpRequest sendOtpRequest = new SendOtpRequest();
			sendOtpRequest.setTransactionId(transaction.getAuthTransactionId());
			sendOtpRequest.setIndividualId(otpRequest.getIndividualId());
			sendOtpRequest.setOtpChannels(otpRequest.getOtpChannels());
			sendOtpResult = authenticationWrapper.sendOtp(authPartnerId, apiKey, sendOtpRequest);
		} catch (SendOtpException e) {
			log.error("Failed to send otp for transaction : {}", transactionId, e);
			throw new IdPException(e.getErrorCode());
		}

		if (!transaction.getAuthTransactionId().equals(sendOtpResult.getTransactionId())) {
			log.error("Auth transactionId in request {} is not matching with send-otp response : {}",
					transaction.getAuthTransactionId(), sendOtpResult.getTransactionId());
			throw new IdPException(ErrorConstants.SEND_OTP_FAILED);
		}

		OtpResponse otpResponse = new OtpResponse();
		otpResponse.setTransactionId(transactionId);
		otpResponse.setMaskedEmail(sendOtpResult.getMaskedEmail());
		otpResponse.setMaskedMobile(sendOtpResult.getMaskedMobile());
		return otpResponse;
	}

	@Override
	public WalletBindingResponse bindWallet(WalletBindingRequest walletBindingRequest) throws IdPException {
		BindingTransaction bindingTransaction = cacheUtilService
				.getTransaction(walletBindingRequest.getTransactionId());
		if (bindingTransaction == null)
			throw new InvalidTransactionException();

		if (!bindingTransaction.getIndividualId().equals(walletBindingRequest.getIndividualId()))
			throw new IdPException(INVALID_INDIVIDUAL_ID);

		if (!bindingTransaction.getAuthChallengeType()
				.equals(walletBindingRequest.getChallengeList().get(0).getAuthFactorType()))
			throw new IdPException(INVALID_AUTH_CHALLENGE);

		log.info("Wallet Binding Request validated and sent for authentication");

		KycAuthResult kycAuthResult = authenticateIndividual(bindingTransaction, walletBindingRequest);

		log.info("Wallet Binding Request authentication is successful");

		PublicKeyRegistry publicKeyRegistry = storeData(walletBindingRequest,
				kycAuthResult.getPartnerSpecificUserToken());

		WalletBindingResponse walletBindingResponse = new WalletBindingResponse();
		walletBindingResponse.setTransactionId(walletBindingRequest.getTransactionId());
		walletBindingResponse.setExpireDateTime(
				publicKeyRegistry.getExpiredtimes().format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
		// TODO need to call Keymanager publickey-JWE encryption
		walletBindingResponse.setEncryptedWalletBindingId(
				getEncryptedWalletBindingId(walletBindingRequest, publicKeyRegistry,
						publicKeyRegistry.getWalletBindingId()));

		return walletBindingResponse;

	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	@Override
	public ValidateBindingResponse validateBinding(ValidateBindingRequest validateBindingRequest) throws IdPException {
		String individualId = validateBindingRequest.getIndividualId();

		Optional<PublicKeyRegistry> publicKeyRegistry = publicKeyRegistryRepository
				.findByIdHash(IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA_256, individualId));

		if (!publicKeyRegistry.isPresent()) {
			throw new IdPException(ErrorConstants.INVALID_INDIVIDUAL_ID);
		}
		
		try {		      
            JWSKeySelector keySelector = new JWSVerificationKeySelector(JWSAlgorithm.RS256,
                    new ImmutableJWKSet(new JWKSet(RSAKey.parse(publicKeyRegistry.get().getPublicKey()))));
            JWTClaimsSetVerifier claimsSetVerifier = new DefaultJWTClaimsVerifier(new JWTClaimsSet.Builder()
                    .audience(validateBindingIssuerId)
                    .subject(individualId)
                    .build(), REQUIRED_WLA_CLAIMS);

            ConfigurableJWTProcessor jwtProcessor = new DefaultJWTProcessor();
            jwtProcessor.setJWSKeySelector(keySelector);
            jwtProcessor.setJWTClaimsSetVerifier(claimsSetVerifier);
            jwtProcessor.process(validateBindingRequest.getWlaToken(), null); //If invalid throws exception
        } catch (Exception e) {
            log.error("Failed to verify WLA token", e);
            throw new IdPException(ErrorConstants.INVALID_AUTH_TOKEN);
        }
		
		ValidateBindingResponse validateBindingResponse = new ValidateBindingResponse();
		validateBindingResponse.setIndividualId(individualId);
		validateBindingResponse.setTransactionId(validateBindingRequest.getTransactionId());
		return validateBindingResponse;
	}

	private PublicKeyRegistry storeData(WalletBindingRequest walletBindingRequest,
			String partnerSpecificUserToken) throws IdPException {
		String publicKey = IdentityProviderUtil.getJWKString(walletBindingRequest.getPublicKey());
		String publicKeyHash = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA_256, publicKey);

		Optional<PublicKeyRegistry> optionalPublicKeyRegistryForDuplicateCheck = publicKeyRegistryRepository
				.findByPublicKeyHashNotEqualToPsuToken(publicKeyHash, partnerSpecificUserToken);

		if (optionalPublicKeyRegistryForDuplicateCheck.isPresent())
			throw new IdPException(DUPLICATE_PUBLIC_KEY);

		LocalDateTime expiredtimes = calculateExpiresdtimes();

		Optional<PublicKeyRegistry> optionalPublicKeyRegistry = publicKeyRegistryRepository
				.findOneByPsuToken(partnerSpecificUserToken);

		String walletBindingId = null;
		if (optionalPublicKeyRegistry.isPresent()) {
			walletBindingId = optionalPublicKeyRegistry.get().getWalletBindingId();
			int noOfUpdatedRecords = publicKeyRegistryRepository.updatePublicKeyRegistry(publicKey, publicKeyHash,
					expiredtimes,
					partnerSpecificUserToken);
			log.info("Number of records updated successfully {}", noOfUpdatedRecords);
		}
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		publicKeyRegistry.setIdHash(
				IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA_256, walletBindingRequest.getIndividualId()));
		publicKeyRegistry.setPsuToken(partnerSpecificUserToken);
		publicKeyRegistry.setPublicKey(publicKey);
		publicKeyRegistry.setExpiredtimes(expiredtimes);
		if (walletBindingId == null) {
			byte[] salt = IdentityProviderUtil.generateSalt(saltLength);

			walletBindingId = IdentityProviderUtil.digestAsPlainTextWithSalt(partnerSpecificUserToken.getBytes(), salt);
		}

		publicKeyRegistry.setWalletBindingId(walletBindingId);
		publicKeyRegistry.setPublicKeyHash(publicKeyHash);
		publicKeyRegistry.setCreatedtimes(LocalDateTime.now(ZoneId.of("UTC")));
		publicKeyRegistry = publicKeyRegistryRepository.save(publicKeyRegistry);

		log.info("Saved PublicKeyRegistry details successfully");

		return publicKeyRegistry;
	}

	private LocalDateTime calculateExpiresdtimes() {
		LocalDateTime currentDateTime = LocalDateTime.now(ZoneId.of("UTC"));
		return currentDateTime.plusDays(expireDays);
	}

	private KycAuthResult authenticateIndividual(BindingTransaction bindingTransaction,
			WalletBindingRequest walletBindingRequest) throws IdPException {
		KycAuthResult kycAuthResult;
		try {
			kycAuthResult = authenticationWrapper.doKycAuth(authPartnerId, apiKey,
					new KycAuthRequest(bindingTransaction.getAuthTransactionId(),
							walletBindingRequest.getIndividualId(), walletBindingRequest.getChallengeList()));
		} catch (KycAuthException e) {
			log.error("KYC auth failed for transaction : {}", bindingTransaction.getAuthTransactionId(), e);
			throw new IdPException(e.getErrorCode());
		}
		if (kycAuthResult == null || (StringUtils.isEmpty(kycAuthResult.getKycToken())
				|| StringUtils.isEmpty(kycAuthResult.getPartnerSpecificUserToken()))) {
			log.error("** authenticationWrapper : {} returned empty tokens received **", authenticationWrapper);
			throw new IdPException(AUTH_FAILED);
		}
		return kycAuthResult;
	}

	private String getJWE(Map<String, Object> publicKey, String walletBindingId) throws JoseException {
		JsonWebEncryption jsonWebEncryption = new JsonWebEncryption();
		jsonWebEncryption.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.RSA_OAEP_256);
		jsonWebEncryption.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_256_GCM);
		jsonWebEncryption.setPayload(walletBindingId);
		jsonWebEncryption.setContentTypeHeaderValue("JWT");
		RsaJsonWebKey jsonWebKey = new RsaJsonWebKey(publicKey);
		jsonWebEncryption.setKey(jsonWebKey.getKey());
		jsonWebEncryption.setKeyIdHeaderValue(jsonWebKey.getKeyId());
		return jsonWebEncryption.getCompactSerialization();
	}

	private String getEncryptedWalletBindingId(WalletBindingRequest walletBindingRequest,
			PublicKeyRegistry publicKeyRegistry, String walletBindingId) throws IdPException {
		try {
			return getJWE(walletBindingRequest.getPublicKey(), walletBindingId);
		} catch (JoseException e) {
			log.error(INVALID_PUBLIC_KEY, e);
			throw new IdPException(INVALID_PUBLIC_KEY);
		}
	}
	
}
