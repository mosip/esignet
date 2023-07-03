package io.mosip.esignet.services;

import io.mosip.esignet.entity.PublicKeyRegistry;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.Optional;

import static io.mosip.esignet.core.constants.ErrorConstants.DUPLICATE_PUBLIC_KEY;
import static io.mosip.esignet.core.constants.ErrorConstants.INVALID_PUBLIC_KEY;
import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA3_256;
import static io.mosip.esignet.core.util.IdentityProviderUtil.b64Encode;

@Component
@Slf4j
public class KeyBindingHelperService {

    @Autowired
    private PublicKeyRegistryRepository publicKeyRegistryRepository;

    @Autowired
    private KeymanagerUtil keymanagerUtil;

    @Autowired
    private KeyBindingHelperService keyBindingHelperService;

    @Value("${mosip.esignet.binding.salt-length}")
    private int saltLength;

    public String getIndividualIdHash(String individualId) {
        return IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, individualId);
    }

    public PublicKeyRegistry storeKeyBindingDetailsInRegistry(String individualId, String partnerSpecificUserToken, String publicKey,
                                                               String certificateData, String authFactor) throws EsignetException {
        String publicKeyHash = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, publicKey);
        //check if any entry exists with same public key for different PSU-token
        Optional<PublicKeyRegistry> optionalPublicKeyRegistryForDuplicateCheck = publicKeyRegistryRepository
                .findOptionalByPublicKeyHashAndPsuTokenNot(publicKeyHash, partnerSpecificUserToken);
        if (optionalPublicKeyRegistryForDuplicateCheck.isPresent())
            throw new EsignetException(DUPLICATE_PUBLIC_KEY);

        X509Certificate certificate = (X509Certificate)keymanagerUtil.convertToCertificate(certificateData);
        LocalDateTime expireDTimes = certificate.getNotAfter().toInstant().atZone(ZoneOffset.UTC).toLocalDateTime();
        //same individual can be bound to different public keys each with different auth-factor-type.
        Optional<PublicKeyRegistry> optionalPublicKeyRegistry = publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(partnerSpecificUserToken, authFactor);
        String walletBindingId = null;
        //Entry exists, consider to use same wallet-binding-id. Only update public-key & expireDT
        if (optionalPublicKeyRegistry.isPresent()) {
            walletBindingId = optionalPublicKeyRegistry.get().getWalletBindingId();
            int noOfUpdatedRecords = publicKeyRegistryRepository.updatePublicKeyRegistry(publicKey, publicKeyHash,
                    expireDTimes, partnerSpecificUserToken, certificateData, authFactor);
            log.info("Number of records updated successfully {}", noOfUpdatedRecords);
        }

        //Upsert one entry for the provided individual-id
        PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
        publicKeyRegistry.setIdHash(getIndividualIdHash(individualId));
        publicKeyRegistry.setAuthFactor(authFactor);
        publicKeyRegistry.setPsuToken(partnerSpecificUserToken);
        publicKeyRegistry.setPublicKey(publicKey);
        publicKeyRegistry.setPublicKeyHash(publicKeyHash);
        publicKeyRegistry.setExpiredtimes(expireDTimes);
        publicKeyRegistry.setWalletBindingId(walletBindingId == null ? generateWalletBindingId(partnerSpecificUserToken) : walletBindingId);
        publicKeyRegistry.setCertificate(certificateData);
        publicKeyRegistry.setThumbprint(generateThumbprintByPublicKey(publicKey));
        publicKeyRegistry.setCreatedtimes(LocalDateTime.now(ZoneId.of("UTC")));
        publicKeyRegistry = publicKeyRegistryRepository.save(publicKeyRegistry);
        log.info("Saved PublicKeyRegistry details successfully");
        return publicKeyRegistry;
    }

    private String generateWalletBindingId(final String partnerSpecificUserToken) {
        MessageDigest messageDigest = null;
        try {
            messageDigest = MessageDigest.getInstance(ALGO_SHA3_256);
            messageDigest.update(partnerSpecificUserToken.getBytes());
            messageDigest.update(IdentityProviderUtil.generateSalt(saltLength));
        } catch (NoSuchAlgorithmException e) {
            throw new EsignetException(ErrorConstants.INVALID_ALGORITHM);
        }
        return b64Encode(messageDigest.digest());
    }

    public String getPublicKey(String psuToken, String thumbprint) {
        Optional<PublicKeyRegistry> publicKeyRegistryOptional= publicKeyRegistryRepository.
                findFirstByPsuTokenAndThumbprintOrderByExpiredtimesDesc(psuToken, thumbprint);
        if(publicKeyRegistryOptional.isPresent())
          return  publicKeyRegistryOptional.get().getPublicKey();
        throw new EsignetException("INVALID psuToken or thumbprint");
    }

    //generating Thumbprint when we get Public key Object
//    private String generateThumbprintByPublicKey(PublicKey publicKey) throws NoSuchAlgorithmException {
//        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
//        byte[] publicKeyBytes = publicKey.getEncoded();
//        byte[] digest = messageDigest.digest(publicKeyBytes);
//        String fingerprint = Base64.getEncoder().encodeToString(digest);
//        return fingerprint;
//    }

    //generating Thumbprint when we get Public key as String
    private String generateThumbprintByPublicKey(String publicKey)  {
        // Convert the public key string to bytes
        byte[] publicKeyBytes = publicKey.getBytes(StandardCharsets.UTF_8);

        // Hash the public key bytes using SHA-256
        try{
            MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
            byte[] hashBytes = messageDigest.digest(publicKeyBytes);
            // Base64-encode the hash value to create the thumbprint
            String thumbprint = Base64.getEncoder().encodeToString(hashBytes);
            return thumbprint;
        }catch (Exception e)
        {
            throw new EsignetException(INVALID_PUBLIC_KEY);
        }
    }

}
