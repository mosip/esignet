/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.authentication.esignet.integration.service;

import java.math.BigInteger;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.security.interfaces.ECPrivateKey;
import java.security.interfaces.ECPublicKey;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.util.Date;
import java.util.UUID;


import com.nimbusds.jose.jwk.Curve;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.KeyUse;
import com.nimbusds.jose.jwk.RSAKey;

import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.x509.X509V3CertificateGenerator;

import javax.security.auth.x500.X500Principal;

@Slf4j
public class TestUtil {

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
        } catch (NoSuchAlgorithmException e) {
            log.error("generateJWK_RSA failed", e);
        }
        return null;
    }

    public static X509Certificate getCertificate() throws Exception {
        JWK clientJWK = TestUtil.generateJWK_RSA();
        X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
        X500Principal dnName = new X500Principal("CN=Test");
        generator.setSubjectDN(dnName);
        generator.setIssuerDN(dnName); // use the same
        generator.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
        generator.setNotAfter(new Date(System.currentTimeMillis() + 24 * 365 * 24 * 60 * 60 * 1000));
        generator.setPublicKey(clientJWK.toRSAKey().toPublicKey());
        generator.setSignatureAlgorithm("SHA256WITHRSA");
        generator.setSerialNumber(new BigInteger(String.valueOf(System.currentTimeMillis())));
        return generator.generate(clientJWK.toRSAKey().toPrivateKey());
    }

    public static X509Certificate getExpiredCertificate() throws Exception {
        JWK clientJWK = TestUtil.generateJWK_RSA();
        X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
        X500Principal dnName = new X500Principal("CN=Test");
        generator.setSubjectDN(dnName);
        generator.setIssuerDN(dnName); // use the same
        generator.setNotBefore(new Date(System.currentTimeMillis()));
        generator.setNotAfter(new Date(System.currentTimeMillis()));
        generator.setPublicKey(clientJWK.toRSAKey().toPublicKey());
        generator.setSignatureAlgorithm("SHA256WITHRSA");
        generator.setSerialNumber(new BigInteger(String.valueOf(System.currentTimeMillis())));
        return generator.generate(clientJWK.toRSAKey().toPrivateKey());
    }
}
