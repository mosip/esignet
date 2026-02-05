/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.esignet.core.constants.ErrorConstants;
import io.mosip.esignet.core.dto.ClientDetail;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.spi.ClientManagementService;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.kernel.cryptomanager.dto.JWTCipherResponseDto;
import io.mosip.kernel.cryptomanager.dto.JWTEncryptRequestDto;
import io.mosip.kernel.cryptomanager.service.CryptomanagerService;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jwk.PublicJsonWebKey;
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PublicKey;
import java.security.cert.X509Certificate;
import java.util.Base64;
import java.util.Date;
import java.util.Map;

import org.bouncycastle.asn1.x500.X500Name;
import org.bouncycastle.cert.X509CertificateHolder;
import org.bouncycastle.cert.X509v3CertificateBuilder;
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter;
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder;
import org.bouncycastle.operator.ContentSigner;
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder;

import static io.mosip.esignet.api.util.ErrorConstants.DATA_EXCHANGE_FAILED;
import static io.mosip.esignet.core.constants.Constants.OIDC_SERVICE_APP_ID;

/**
 * Helper service to sign and/or encrypt the userinfo response based on the input format
 * and client configuration.
 */
@Slf4j
@Component
public class UserInfoResponseHelper {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CryptomanagerService cryptomanagerService;

    @Autowired
    private ClientManagementService clientManagementService;

    @Value("${mosip.kernel.certificate.sign.algorithm}")
    private String certificateSignAlgorithm;

    private static final String LINE_SEPARATOR = System.lineSeparator();

    /**
     * Process the encrypted KYC data from KYC-exchange response and transform it based on client configuration.
     * @param transaction The OIDCTransaction containing encryptedKyc, clientId, and userInfoResponseType
     * @return Processed userinfo response (JWS or JWE based on client additional config)
     */
    public String processUserInfoResponse(OIDCTransaction transaction) {
        String encryptedKyc = transaction.getEncryptedKyc();
        if (encryptedKyc == null || encryptedKyc.isBlank()) {
            log.error("encryptedKyc is null or empty");
            throw new EsignetException(DATA_EXCHANGE_FAILED);
        }
        KycDataFormat dataFormat = detectKycDataFormat(encryptedKyc);
        log.info("Detected KYC data format: {}", dataFormat);
        String kycData = encryptedKyc;

        // If PLAIN_JSON, sign it and treat as JWS
        if (dataFormat == KycDataFormat.PLAIN_JSON) {
            kycData = signPayload(encryptedKyc);
            dataFormat = KycDataFormat.JWS;
        }
        // If JWS and userInfoResponseType is JWE, encrypt it
        if (dataFormat == KycDataFormat.JWS && "JWE".equalsIgnoreCase(transaction.getUserInfoResponseType())) {
            log.info("Encrypting JWS into JWE");
            ClientDetail clientDetail = clientManagementService.getClientDetails(transaction.getClientId());
            kycData = encryptJWSToJWE(kycData, clientDetail);
        }
        return kycData;
    }

    /**
     * Detect the type of KYC data based on segment count
     */
    private KycDataFormat detectKycDataFormat(String data) {
        int segmentCount = data.split("\\.").length;
        return segmentCount == 5 ? KycDataFormat.JWE
             : segmentCount == 3 ? KycDataFormat.JWS
             : KycDataFormat.PLAIN_JSON;
    }

    /**
     * Sign the payload using the same key used for ID/Access tokens.
     */
    private String signPayload(String base64EncodedPayload) {
        try {
            String decodedPayload = decodeBase64UrlOrBase64(base64EncodedPayload);
            return tokenService.getSignedJWT(OIDC_SERVICE_APP_ID,
                    new JSONObject(objectMapper.readValue(decodedPayload, Map.class)));
        } catch (Exception e) {
            log.error("Failed to sign payload", e);
            throw new EsignetException(DATA_EXCHANGE_FAILED);
        }
    }

    /**
     * Encrypt JWS to JWE (nested JWT) using client's encryption public key.
     * @param jws The JWS string to encrypt
     * @param clientDetail Client configuration containing enc_public_key (JWK format)
     * @return JWE compact serialization containing the nested JWS
     */
    private String encryptJWSToJWE(String jws, ClientDetail clientDetail) {
        String encPublicKey = clientDetail.getEncPublicKey();

        if (encPublicKey == null || encPublicKey.isBlank()) {
            log.error("Client encryption public key is not configured");
            throw new EsignetException(ErrorConstants.INVALID_PUBLIC_KEY);
        }

        try {
            // Convert JWK to X.509 certificate PEM format
            String pemCertificate = convertJwkToCertificatePem(encPublicKey);

            // Create JWE encrypt request DTO with certificate in PEM format
            JWTEncryptRequestDto jwtEncryptRequestDto = new JWTEncryptRequestDto();
            jwtEncryptRequestDto.setData(jws);
            jwtEncryptRequestDto.setX509Certificate(pemCertificate);

            // Use CryptomanagerService to create JWE
            JWTCipherResponseDto responseDto = cryptomanagerService.jwtEncrypt(jwtEncryptRequestDto);

            if (responseDto == null || responseDto.getData() == null) {
                log.error("CryptomanagerService returned null response for JWE encryption");
                throw new EsignetException(DATA_EXCHANGE_FAILED);
            }

            return responseDto.getData();
        } catch (EsignetException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to create JWE using CryptomanagerService: {}", e.getMessage(), e);
            throw new EsignetException(DATA_EXCHANGE_FAILED);
        }
    }

    /**
     * Convert a JWK (JSON Web Key) to a self-signed X.509 certificate in PEM format.
     * Creates a self-signed certificate with the public key from the JWK.
     * @param jwkJson The JWK in JSON string format
     * @return X.509 certificate in PEM format
     */
    @SuppressWarnings("unchecked")
    private String convertJwkToCertificatePem(String jwkJson) {
        try {
            Map<String, Object> jwkMap = objectMapper.readValue(jwkJson, Map.class);
            PublicJsonWebKey jsonWebKey = PublicJsonWebKey.Factory.newPublicJwk(jwkMap);
            PublicKey publicKey = jsonWebKey.getPublicKey();

            // Generate a self-signed X.509 certificate
            X509Certificate certificate = generateSelfSignedCertificate(publicKey);

            // Convert certificate to PEM format
            String base64Cert = Base64.getEncoder().encodeToString(certificate.getEncoded());
            StringBuilder pemBuilder = new StringBuilder();
            pemBuilder.append("-----BEGIN CERTIFICATE-----").append(LINE_SEPARATOR);

            int index = 0;
            while (index < base64Cert.length()) {
                int endIndex = Math.min(index + 64, base64Cert.length());
                pemBuilder.append(base64Cert, index, endIndex).append(LINE_SEPARATOR);
                index = endIndex;
            }

            pemBuilder.append("-----END CERTIFICATE-----");

            log.debug("Converted JWK to X.509 certificate PEM format successfully");
            return pemBuilder.toString();
        } catch (Exception e) {
            log.error("Failed to convert JWK to certificate PEM: {}", e.getMessage(), e);
            throw new EsignetException(ErrorConstants.INVALID_PUBLIC_KEY);
        }
    }

    /**
     * Generate a self-signed X.509 certificate for the given public key.
     * Uses BouncyCastle for certificate generation. Supports both RSA and EC keys.
     */
    private X509Certificate generateSelfSignedCertificate(PublicKey publicKey) throws Exception {
        long now = System.currentTimeMillis();
        Date startDate = new Date(now);
        Date endDate = new Date(now + 365L * 24 * 60 * 60 * 1000);

        String algorithm = publicKey.getAlgorithm();
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance(algorithm);

        // Initialize key pair generator based on algorithm type
        if ("RSA".equals(algorithm)) {
            keyPairGenerator.initialize(2048);
        } else if ("EC".equals(algorithm)) {
            keyPairGenerator.initialize(256); // P-256 curve
        } else {
            throw new EsignetException(ErrorConstants.INVALID_PUBLIC_KEY);
        }

        KeyPair tempKeyPair = keyPairGenerator.generateKeyPair();

        X500Name issuer = new X500Name("CN=eSignet Encryption Certificate");
        BigInteger serialNumber = BigInteger.valueOf(now);

        X509v3CertificateBuilder certBuilder = new JcaX509v3CertificateBuilder(
                issuer, serialNumber, startDate, endDate, issuer, publicKey);

        ContentSigner signer = new JcaContentSignerBuilder(certificateSignAlgorithm).build(tempKeyPair.getPrivate());

        X509CertificateHolder certHolder = certBuilder.build(signer);
        return new JcaX509CertificateConverter().getCertificate(certHolder);
    }

    private String decodeBase64Url(String data) {
        byte[] decoded = Base64.getUrlDecoder().decode(data);
        return new String(decoded, StandardCharsets.UTF_8);
    }

    private String decodeBase64UrlOrBase64(String data) {
        try {
            return decodeBase64Url(data);
        } catch (IllegalArgumentException e) {
            byte[] decoded = Base64.getDecoder().decode(data);
            return new String(decoded, StandardCharsets.UTF_8);
        }
    }

}
