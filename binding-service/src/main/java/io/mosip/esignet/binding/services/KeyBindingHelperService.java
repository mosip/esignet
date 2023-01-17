package io.mosip.esignet.binding.services;

import io.mosip.esignet.binding.entity.PublicKeyRegistry;
import io.mosip.esignet.binding.repository.PublicKeyRegistryRepository;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.core.util.ErrorConstants;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Optional;

import static io.mosip.esignet.core.util.ErrorConstants.DUPLICATE_PUBLIC_KEY;
import static io.mosip.esignet.core.util.IdentityProviderUtil.ALGO_SHA3_256;
import static io.mosip.esignet.core.util.IdentityProviderUtil.b64Encode;

@Component
@Slf4j
public class KeyBindingHelperService {

    @Autowired
    private PublicKeyRegistryRepository publicKeyRegistryRepository;

    @Autowired
    private KeymanagerUtil keymanagerUtil;

    @Value("${mosip.idp.binding.salt-length}")
    private int saltLength;

    protected String getIndividualIdHash(String individualId) {
        return IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, individualId);
    }

    protected PublicKeyRegistry storeKeyBindingDetailsInRegistry(String individualId, String partnerSpecificUserToken, String publicKey,
                                                               String certificateData, String authFactor) throws IdPException {
        String publicKeyHash = IdentityProviderUtil.generateB64EncodedHash(ALGO_SHA3_256, publicKey);
        //check if any entry exists with same public key for different PSU-token
        Optional<PublicKeyRegistry> optionalPublicKeyRegistryForDuplicateCheck = publicKeyRegistryRepository
                .findOptionalByPublicKeyHashAndPsuTokenNot(publicKeyHash, partnerSpecificUserToken);
        if (optionalPublicKeyRegistryForDuplicateCheck.isPresent())
            throw new IdPException(DUPLICATE_PUBLIC_KEY);

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
            throw new IdPException(ErrorConstants.INVALID_ALGORITHM);
        }
        return b64Encode(messageDigest.digest());
    }
}
