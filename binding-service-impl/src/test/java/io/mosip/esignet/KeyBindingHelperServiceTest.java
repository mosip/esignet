package io.mosip.esignet;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.mosip.esignet.core.exception.IdPException;
import io.mosip.esignet.entity.PublicKeyRegistry;
import io.mosip.esignet.repository.PublicKeyRegistryRepository;
import io.mosip.esignet.services.KeyBindingHelperService;
import io.mosip.kernel.keymanagerservice.util.KeymanagerUtil;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

import javax.security.auth.x500.X500Principal;
import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.cert.X509Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.Optional;
import java.util.UUID;

import static io.mosip.esignet.core.constants.ErrorConstants.DUPLICATE_PUBLIC_KEY;

@RunWith(MockitoJUnitRunner.class)
public class KeyBindingHelperServiceTest {

    @InjectMocks
    private KeyBindingHelperService keyBindingHelperService;

    @Mock
    private KeymanagerUtil keymanagerUtil;

    @Mock
    private PublicKeyRegistryRepository publicKeyRegistryRepository;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ReflectionTestUtils.setField(keyBindingHelperService, "saltLength", 10);
    }

    @Test
    public void getIndividualIdHash_withValidValue_thenPass() {
        Assert.assertNotNull(keyBindingHelperService.getIndividualIdHash("individualId"));
    }

    @Test
    public void storeKeyBindingDetailsInRegistry_withValidValue_thenPass() throws Exception {
        X509Certificate certificate = getCertificate(generateJWK_RSA());
        Mockito.when(publicKeyRegistryRepository
                .findOptionalByPublicKeyHashAndPsuTokenNot(Mockito.anyString(), Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(keymanagerUtil.convertToCertificate(Mockito.anyString())).thenReturn(certificate);
        PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
        publicKeyRegistry.setWalletBindingId("wallet-binding-id");
        Mockito.when(publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Optional.of(publicKeyRegistry));
        Mockito.when(publicKeyRegistryRepository.save(Mockito.any(PublicKeyRegistry.class))).thenReturn(publicKeyRegistry);

        publicKeyRegistry = keyBindingHelperService.storeKeyBindingDetailsInRegistry("individualId", "psut", "publicKey",
                "certificate", "WLA");
        Assert.assertNotNull(publicKeyRegistry);
    }

    @Test
    public void storeKeyBindingDetailsInRegistry_withDuplicatePublicKey_thenFail() {
        PublicKeyRegistry publicKeyRegistry = new PublicKeyRegistry();
        Mockito.when(publicKeyRegistryRepository
                .findOptionalByPublicKeyHashAndPsuTokenNot(Mockito.anyString(), Mockito.anyString())).thenReturn(Optional.of(publicKeyRegistry));
        try {
            keyBindingHelperService.storeKeyBindingDetailsInRegistry("individualId", "psut", "publicKey",
                    "certificate", "WLA");
            Assert.fail();
        } catch (IdPException e) {
            Assert.assertEquals(DUPLICATE_PUBLIC_KEY, e.getErrorCode());
        }
    }

    @Test
    public void storeKeyBindingDetailsInRegistry_withFirstTimeBinding_thenPass() throws Exception {
        X509Certificate certificate = getCertificate(generateJWK_RSA());
        Mockito.when(publicKeyRegistryRepository
                .findOptionalByPublicKeyHashAndPsuTokenNot(Mockito.anyString(), Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(keymanagerUtil.convertToCertificate(Mockito.anyString())).thenReturn(certificate);
        Mockito.when(publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(publicKeyRegistryRepository.save(Mockito.any(PublicKeyRegistry.class))).thenReturn(new PublicKeyRegistry());
        Assert.assertNotNull(keyBindingHelperService.storeKeyBindingDetailsInRegistry("individualId", "psut", "publicKey",
                "certificate", "WLA"));
    }

    private X509Certificate getCertificate(JWK jwk) throws Exception {
        X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
        X500Principal dnName = new X500Principal("CN=Test");
        generator.setSubjectDN(dnName);
        generator.setIssuerDN(dnName); // use the same
        generator.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
        generator.setNotAfter(new Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000));
        generator.setPublicKey(jwk.toRSAKey().toPublicKey());
        generator.setSignatureAlgorithm("SHA256WITHRSA");
        generator.setSerialNumber(new BigInteger(String.valueOf(System.currentTimeMillis())));
        return generator.generate(jwk.toRSAKey().toPrivateKey());
    }

    public static JWK generateJWK_RSA() {
        // Generate the RSA key pair
        try {
            KeyPairGenerator gen = KeyPairGenerator.getInstance("RSA");
            gen.initialize(2048);
            KeyPair keyPair = gen.generateKeyPair();
            // Convert public key to JWK format
            return new RSAKey.Builder((RSAPublicKey)keyPair.getPublic())
                    .privateKey((RSAPrivateKey)keyPair.getPrivate())
                    .keyUse(KeyUse.SIGNATURE)
                    .keyID(UUID.randomUUID().toString())
                    .build();
        } catch (Exception e) {}
        return null;
    }

}
