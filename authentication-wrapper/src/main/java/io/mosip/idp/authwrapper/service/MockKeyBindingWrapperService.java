/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.authwrapper.service;

import com.nimbusds.jose.jwk.RSAKey;
import io.mosip.idp.core.dto.AuthChallenge;
import io.mosip.idp.core.dto.KeyBindingResult;
import io.mosip.idp.core.dto.SendOtpResult;
import io.mosip.idp.core.exception.KeyBindingException;
import io.mosip.idp.core.exception.SendOtpException;
import io.mosip.idp.core.spi.KeyBindingWrapper;
import lombok.extern.slf4j.Slf4j;
import net.minidev.json.JSONObject;
import org.bouncycastle.openssl.jcajce.JcaPEMWriter;
import org.bouncycastle.x509.X509V3CertificateGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import javax.security.auth.x500.X500Principal;
import java.io.StringWriter;
import java.math.BigInteger;
import java.util.*;

@ConditionalOnProperty(value = "mosip.idp.binding.wrapper.impl", havingValue = "MockKeyBindingWrapperService")
@Component
@Slf4j
public class MockKeyBindingWrapperService implements KeyBindingWrapper {

    private static final Map<String, List<String>> supportedFormats = new HashMap<>();

    static {
        supportedFormats.put("OTP", Arrays.asList("alpha-numeric"));
        supportedFormats.put("PIN", Arrays.asList("number"));
        supportedFormats.put("BIO", Arrays.asList("encoded-json"));
    }


    @Override
    public SendOtpResult sendBindingOtp(String individualId, List<String> otpChannels,
                                        Map<String, String> requestHeaders) throws SendOtpException {
        SendOtpResult sendOtpResult = new SendOtpResult(null,"mock", "mock");
        //TODO
        return sendOtpResult;
    }

    @Override
    public KeyBindingResult doKeyBinding(String individualId, List<AuthChallenge> challengeList,
                                         Map<String, Object> publicKeyJWK, Map<String, String> requestHeaders) throws KeyBindingException {
        KeyBindingResult keyBindingResult = new KeyBindingResult();
        //TODO
        //create a signed certificate, with cn as username
        //certificate validity based on configuration
        try {
            RSAKey rsaKey = RSAKey.parse(new JSONObject(publicKeyJWK));
            X509V3CertificateGenerator generator = new X509V3CertificateGenerator();
            X500Principal dnName = new X500Principal("CN=Test");
            generator.setSubjectDN(dnName);
            generator.setIssuerDN(dnName); // use the same
            generator.setNotBefore(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
            generator.setNotAfter(new Date(System.currentTimeMillis() + 2 * 365 * 24 * 60 * 60 * 1000));
            generator.setPublicKey(rsaKey.toPublicKey());
            generator.setSignatureAlgorithm("SHA256WITHRSA");
            generator.setSerialNumber(new BigInteger(String.valueOf(System.currentTimeMillis())));

            StringWriter stringWriter = new StringWriter();
            try (JcaPEMWriter pemWriter = new JcaPEMWriter(stringWriter)) {
                pemWriter.writeObject(generator.generate(rsaKey.toPrivateKey()));
                pemWriter.flush();
                keyBindingResult.setCertificate(stringWriter.toString());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        keyBindingResult.setPartnerSpecificUserToken("mock_psut");
        return keyBindingResult;
    }

    @Override
    public List<String> getSupportedChallengeFormats(String authFactorType) {
        return supportedFormats.getOrDefault(authFactorType, Arrays.asList());
    }
}
