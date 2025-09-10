/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.ECKey;
import com.nimbusds.jose.jwk.JWK;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.util.Base64URL;
import io.mosip.esignet.core.constants.ErrorConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@Component
public class SecurityHelperService {

    public static final String ALGO_SHA_256 = "SHA-256";

    @Autowired
    private ObjectMapper objectMapper;

    public String generateSecureRandomString(int length) {
        //TODO
        return IdentityProviderUtil.generateRandomAlphaNumeric(length);
    }

    public String computeJwkThumbprint(JWK jwk) throws Exception {
        if (jwk == null) {
            throw new IllegalArgumentException(ErrorConstants.INVALID_PUBLIC_KEY);
        }

        if (jwk.isPrivate()) {
            throw new IllegalArgumentException(ErrorConstants.INVALID_PUBLIC_KEY);
        }

        Map<String, Object> thumbprintInput = new LinkedHashMap<>();

        switch (jwk.getKeyType().getValue()) {
            case "RSA":
                if (!(jwk instanceof RSAKey)) {
                    throw new IllegalArgumentException(ErrorConstants.INVALID_ALGORITHM);
                }
                RSAKey rsaKey = (RSAKey) jwk;
                thumbprintInput.put("e", rsaKey.getPublicExponent().toString());
                thumbprintInput.put("kty", "RSA");
                thumbprintInput.put("n", rsaKey.getModulus().toString());
                break;

            case "EC":
                if (!(jwk instanceof ECKey)) {
                    throw new IllegalArgumentException(ErrorConstants.INVALID_ALGORITHM);
                }
                ECKey ecKey = (ECKey) jwk;
                thumbprintInput.put("crv", ecKey.getCurve().getName());
                thumbprintInput.put("kty", "EC");
                thumbprintInput.put("x", ecKey.getX().toString());
                thumbprintInput.put("y", ecKey.getY().toString());
                break;

            default:
                throw new IllegalArgumentException(ErrorConstants.INVALID_ALGORITHM);
        }

        // Canonical JSON: no whitespace, ordered keys
        String canonicalJson = objectMapper.writeValueAsString(thumbprintInput);

        MessageDigest digest = MessageDigest.getInstance(ALGO_SHA_256);
        byte[] hash = digest.digest(canonicalJson.getBytes(StandardCharsets.UTF_8));
        return Base64URL.encode(hash).toString();
    }

}
