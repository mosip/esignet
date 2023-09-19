package io.mosip.esignet;

import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;
import io.mosip.esignet.core.exception.EsignetException;
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

    private static final String certificateString="-----BEGIN CERTIFICATE-----\n" +
            "MIICrzCCAZegAwIBAgIGAYk++jfeMA0GCSqGSIb3DQEBCwUAMBMxETAPBgNVBAMT\n" +
            "CE1vY2stSURBMB4XDTIzMDcxMDAzMTUzM1oXDTIzMDcyMDAzMTUzM1owHjEcMBoG\n" +
            "A1UEAxMTU2lkZGhhcnRoIEsgTWFuc291cjCCASIwDQYJKoZIhvcNAQEBBQADggEP\n" +
            "ADCCAQoCggEBAJy7TzHJJNkjlnSi87fkUr8NMM9k3UIkoAtAqiH7J4uPG1wcdgQK\n" +
            "luX1wfhsed7TUnblrZCZXOaxqT2kN1uniC28bekQPkWs/e0Mm8s3r7ncxyTtCMlS\n" +
            "kSlg6ZFN3bV2m3x893vFx81yOGk534Jc9O9qxouxB7WMHn8ynM9BE8k0VaNXyj2/\n" +
            "z0E7IXqpei4UDNdTU0avmqYGjw/YTsTdlrwQebwn9clwVvld2ZFV4jdgErTqLJ/Y\n" +
            "u7wIZmYzL3ib5kf2+tVZhY/MnqsT0Bx+TFatnd2Aout5/Hs2V2HdwSBY6ET6SXVT\n" +
            "NXKDtH3Sw6AyNPj+jo6l5IARsuOvWioTrfsCAwEAATANBgkqhkiG9w0BAQsFAAOC\n" +
            "AQEAgOtPRuk9IyrRGOFWyFlwJdqZxqVO+78UAJKJmBiko6xxeezkYqqiAuwcyWFj\n" +
            "XWmvvcwlTdCyfEnGWRi74r4ma7u0h5O4U3AJxPF0/BKklCF9nabRqtSC9ENPKHpf\n" +
            "/MAsZF/dQkzQ+k8oqCVKgg/OpgmLGg1dBFvBUOsSUtzp2Mv3GhQO8cjHb32YsS2C\n" +
            "EL2oRcBvJ0SQ9kmYaZ4Pb08xlbTTWbNtPJDj58w4S5Xs2PFlbJr/Ibe3DZM7nYym\n" +
            "zfeCZDzlkLcSCpEaFCMdeuZSpmdSrRaJ9gquR+Ix3uYrqKNmd6eVq+yr1F5DXu9e\n" +
            "c6Ny6Ira8ylf96JLLRfh3b5G4w==\n" +
            "-----END CERTIFICATE-----\n";

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
        publicKeyRegistry.setCertificate(certificateString);
        Mockito.when(publicKeyRegistryRepository.findLatestByPsuTokenAndAuthFactor(Mockito.anyString(),
                Mockito.anyString())).thenReturn(Optional.of(publicKeyRegistry));
        Mockito.when(publicKeyRegistryRepository.save(Mockito.any(PublicKeyRegistry.class))).thenReturn(publicKeyRegistry);

        publicKeyRegistry = keyBindingHelperService.storeKeyBindingDetailsInRegistry("individualId", "psut", "publicKey",
                certificateString, "WLA");
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
        } catch (EsignetException e) {
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
                certificateString, "WLA"));
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
