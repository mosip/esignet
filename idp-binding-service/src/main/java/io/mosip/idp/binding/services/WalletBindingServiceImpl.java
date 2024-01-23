/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.binding.services;

import static io.mosip.idp.core.util.Constants.UTC_DATETIME_PATTERN;
import static io.mosip.idp.core.util.ErrorConstants.AUTH_FAILED;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jose4j.jwe.ContentEncryptionAlgorithmIdentifiers;
import org.jose4j.jwe.JsonWebEncryption;
import org.jose4j.jwe.KeyManagementAlgorithmIdentifiers;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mosip.idp.binding.dto.BindingTransaction;
import io.mosip.idp.binding.entity.PublicKeyRegistry;
import io.mosip.idp.binding.repository.PublicKeyRegistryRepository;
import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.KycAuthRequest;
import io.mosip.idp.core.dto.KycAuthResult;
import io.mosip.idp.core.dto.WalletBindingRequest;
import io.mosip.idp.core.dto.WalletBindingResponse;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.exception.InvalidAuthFactorTypeException;
import io.mosip.idp.core.exception.InvalidIndividualIdException;
import io.mosip.idp.core.exception.InvalidTransactionException;
import io.mosip.idp.core.exception.KycAuthException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.WalletBindingService;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
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
    
    private SignatureService signatureService;
    
    private ObjectMapper objectMapper;
    
    @Value("${mosip.idp.binding.auth-partner-id}")
    private String authPartnerId;
    
    @Value("${mosip.idp.binding.auth-license-key}")
    private String apiKey;
    
    @Value("${mosip.idp.binding.public-key-expire-days}")
    private long expireDays;
    
    @Value("${mosip.idp.binding.issuer-id}")
    private String issuerId;

    @Override
	public void sendBindingOtp() throws IdPException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public WalletBindingResponse bindWallet(WalletBindingRequest walletBindingRequest) throws IdPException {
		BindingTransaction bindingTransaction = cacheUtilService
				.getTransaction(walletBindingRequest.getTransactionId());
		 if(bindingTransaction == null)
	            throw new InvalidTransactionException();
		 
		 if(!bindingTransaction.getIndividualId().equals(walletBindingRequest.getIndividualId()))
	            throw new InvalidIndividualIdException();
		 
		 if(!walletBindingRequest.getAuthChallenge().getAuthFactorType().equalsIgnoreCase(Constants.OTP))
	            throw new InvalidAuthFactorTypeException();
		 
		 KycAuthResult kycAuthResult= authenticateIndividual(bindingTransaction,walletBindingRequest);
		 
		 PublicKeyRegistry publicKeyRegistry= savePublicKeyRegistry(walletBindingRequest,kycAuthResult.getPartnerSpecificUserToken());		 
		 byte[] salt=IdentityProviderUtil.generateSalt(16);
		 String  walletBindingId=IdentityProviderUtil.digestAsPlainTextWithSalt(kycAuthResult.getPartnerSpecificUserToken().getBytes(), salt);
		 WalletBindingResponse walletBindingResponse=new WalletBindingResponse();
		 walletBindingResponse.setTransactionId(walletBindingRequest.getTransactionId());
		 walletBindingResponse.setExpireEpoch(publicKeyRegistry.getExpiresOn().format(DateTimeFormatter.ofPattern(UTC_DATETIME_PATTERN)));
         // TODO need to call Keymanager publickey-JWE encryption
		 walletBindingResponse.setEncryptedWalletBindingToken(getEncryptedWalletBindingId(walletBindingRequest,publicKeyRegistry,walletBindingId));
		return walletBindingResponse;

	}

	@Override
	public void validateBinding() throws IdPException {
		// TODO Auto-generated method stub
		
	}
	private String getJWKString(Map<String, Object> jwk) throws IdPException {
        try {
            RsaJsonWebKey jsonWebKey = new RsaJsonWebKey(jwk);
            return jsonWebKey.toJson();
        } catch (JoseException e) {
            log.error(ErrorConstants.INVALID_PUBLIC_KEY, e);
            throw new IdPException(ErrorConstants.INVALID_PUBLIC_KEY);
        }
    }
	private PublicKeyRegistry savePublicKeyRegistry(WalletBindingRequest walletBindingRequest, String partnerSpecificUserToken) throws IdPException {
		
		PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
		     String publicKey=getJWKString(walletBindingRequest.getPublicKey());
			 publicKeyRegistry.setIndividualId(walletBindingRequest.getIndividualId());
			 publicKeyRegistry.setPsuToken(partnerSpecificUserToken);
			 publicKeyRegistry.setPublicKey(publicKey);
			 publicKeyRegistry.setExpiresOn(calculateExpiresOn());
			 publicKeyRegistry.setCreatedtimes(LocalDateTime.now(ZoneId.of("UTC")));
			 publicKeyRegistryRepository.saveAndFlush(publicKeyRegistry);
		return publicKeyRegistry;
	}

	private LocalDateTime calculateExpiresOn() {
		LocalDateTime currentDateTime=LocalDateTime.now(ZoneId.of("UTC"));
		return currentDateTime.plusDays(expireDays);
	}

	private KycAuthResult authenticateIndividual(BindingTransaction bindingTransaction,WalletBindingRequest walletBindingRequest) throws IdPException {
		List<AuthChallenge> authChallengeList=new ArrayList<AuthChallenge>();
		authChallengeList.add(walletBindingRequest.getAuthChallenge());
		KycAuthResult kycAuthResult;
		try {
			 kycAuthResult=authenticationWrapper.doKycAuth(authPartnerId, apiKey, new KycAuthRequest(bindingTransaction.getAuthTransactionId(), walletBindingRequest.getIndividualId(),
					authChallengeList));
		} catch (KycAuthException e) {
			log.error("KYC auth failed for transaction : {}", bindingTransaction.getAuthTransactionId(), e);
            throw new IdPException(e.getErrorCode());
		}
		if(kycAuthResult == null || (StringUtils.isEmpty(kycAuthResult.getKycToken()) ||
                StringUtils.isEmpty(kycAuthResult.getPartnerSpecificUserToken()))) {
            log.error("** authenticationWrapper : {} returned empty tokens received **", authenticationWrapper);
            throw new IdPException(AUTH_FAILED);
        }
		return kycAuthResult;
	}
	 
	 private String signWalletBindingId(Map<String,Object> walletBindingIdMap) throws JsonProcessingException{

	        String payload = objectMapper.writeValueAsString(walletBindingIdMap);
	        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
	        jwtSignatureRequestDto.setApplicationId(Constants.IDP_BINDING_SERVICE_APP_ID);
	        jwtSignatureRequestDto.setReferenceId("");
	        jwtSignatureRequestDto.setIncludePayload(true);
	        jwtSignatureRequestDto.setIncludeCertificate(false);
	        jwtSignatureRequestDto.setDataToSign(IdentityProviderUtil.b64Encode(payload));
	        jwtSignatureRequestDto.setIncludeCertHash(false);
	        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
	        return responseDto.getJwtSignedData();
	    }
	 private String getJWE(Map<String, Object> publicKey, String signWalletBindingId) throws JoseException{
	        JsonWebEncryption jsonWebEncryption = new JsonWebEncryption();
	        jsonWebEncryption.setAlgorithmHeaderValue(KeyManagementAlgorithmIdentifiers.RSA_OAEP_256);
	        jsonWebEncryption.setEncryptionMethodHeaderParameter(ContentEncryptionAlgorithmIdentifiers.AES_256_GCM);
	        jsonWebEncryption.setPayload(signWalletBindingId);
	        jsonWebEncryption.setContentTypeHeaderValue("JWT");
	        RsaJsonWebKey jsonWebKey = new RsaJsonWebKey(publicKey);
	     	jsonWebEncryption.setKey(jsonWebKey.getKey());
	        jsonWebEncryption.setKeyIdHeaderValue(jsonWebKey.getKeyId());
	        return jsonWebEncryption.getCompactSerialization();
	    }

		private String getEncryptedWalletBindingId(WalletBindingRequest walletBindingRequest,
				PublicKeyRegistry publicKeyRegistry, String walletBindingId) throws IdPException {
			Map<String,Object> walletBindingIdMap=new HashMap<String,Object>();
			walletBindingIdMap.put("sub", walletBindingId);
			walletBindingIdMap.put("iss", issuerId);
			walletBindingIdMap.put("exp", publicKeyRegistry.getExpiresOn().toEpochSecond(ZoneOffset.UTC));
			walletBindingIdMap.put("iat", IdentityProviderUtil.getEpochSeconds());
			try {
				return getJWE(walletBindingRequest.getPublicKey(), signWalletBindingId(walletBindingIdMap));
			} catch (JsonProcessingException e) {
				 log.error(ErrorConstants.JSON_PROCESSING_ERROR, e);
		            throw new IdPException(ErrorConstants.JSON_PROCESSING_ERROR);
			} catch (JoseException e) {
				 log.error(ErrorConstants.INVALID_PUBLIC_KEY, e);
		            throw new IdPException(ErrorConstants.INVALID_PUBLIC_KEY);
			}
		}
}
