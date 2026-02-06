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
import org.json.simple.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;

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

    /**
     * Process the encrypted KYC data from KYC-exchange response and transform it based on client configuration.
     * @param transaction The OIDCTransaction containing encryptedKyc, clientId, and userInfoResponseType
     * @return Processed userinfo response (JWS or JWE based on client additional config)
     */
    public String processUserInfoResponse(OIDCTransaction transaction) {
        String kycData = transaction.getEncryptedKyc(); //Integrated ID system will not return encrypted data always
        if (kycData == null || kycData.isBlank()) {
            log.error("encryptedKyc is null or empty");
            throw new EsignetException(DATA_EXCHANGE_FAILED);
        }
        KycDataFormat dataFormat = detectKycDataFormat(kycData);
        log.info("Detected KYC data format: {}", dataFormat);

        // If PLAIN_JSON, sign it and treat as JWS
        if (dataFormat == KycDataFormat.PLAIN_JSON) {
            kycData = signPayload(kycData);
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
     * Detect the type of KYC data based on segment count.
     * - 5 segments: JWE (header.encryptedKey.iv.ciphertext.tag)
     * - 3 segments: JWS (header.payload.signature)
     * - Other: PLAIN_JSON - ID system/Plugin is expected to send plain JSON as base64 encoded
     *          value in the encryptedKyc field of KycExchangeResult DTO.
     */
    private KycDataFormat detectKycDataFormat(String data) {
        int segmentCount = data.split("\\.").length;
        return segmentCount == 5 ? KycDataFormat.JWE
             : segmentCount == 3 ? KycDataFormat.JWS
             : KycDataFormat.PLAIN_JSON;
    }

    /**
     * Sign the payload using the same key used for ID/Access tokens.
     * Expects base64/base64url encoded JSON payload from ID system/Plugin.
     * @param payload The payload to sign - must be base64/base64url encoded JSON
     * @return Signed JWT (JWS) string
     */
    private String signPayload(String payload) {
        try {
            Map<String, Object> payloadMap = parsePayloadToMap(payload);
            return tokenService.getSignedJWT(OIDC_SERVICE_APP_ID, new JSONObject(payloadMap));
        } catch (Exception e) {
            log.error("Failed to sign payload", e);
            throw new EsignetException(DATA_EXCHANGE_FAILED);
        }
    }

    /**
     * Parse payload to Map - decodes base64/base64url encoded payload and parses as JSON.
     * ID system/Plugin is expected to send plain JSON as base64 encoded value.
     * @param payload The payload string (base64/base64url encoded JSON)
     * @return Parsed payload as Map
     * @throws Exception if decoding or JSON parsing fails
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parsePayloadToMap(String payload) throws Exception {
        String decodedPayload = decodeBase64UrlOrBase64(payload);
        return objectMapper.readValue(decodedPayload, Map.class);
    }

    /**
     * Encrypt JWS to JWE (nested JWT) using client's pre-generated encryption certificate.
     * @param jws The JWS string to encrypt
     * @param clientDetail Client configuration containing enc_public_key_cert (PEM format)
     * @return JWE compact serialization containing the nested JWS
     */
    private String encryptJWSToJWE(String jws, ClientDetail clientDetail) {
        String encPublicKeyCert = clientDetail.getEncPublicKeyCert();

        if (encPublicKeyCert == null || encPublicKeyCert.isBlank()) {
            log.error("Client encryption public key certificate is not configured");
            throw new EsignetException(ErrorConstants.INVALID_PUBLIC_KEY);
        }

        try {
            // Create JWE encrypt request DTO with pre-generated certificate in PEM format
            JWTEncryptRequestDto jwtEncryptRequestDto = new JWTEncryptRequestDto();
            jwtEncryptRequestDto.setData(jws);
            jwtEncryptRequestDto.setX509Certificate(encPublicKeyCert);
            jwtEncryptRequestDto.setIncludeCertificate(false);
            jwtEncryptRequestDto.setIncludeCertHash(false);
            jwtEncryptRequestDto.setEnableDefCompression(false);
            jwtEncryptRequestDto.setJwkSetUrl(null);
            jwtEncryptRequestDto.setReferenceId(null);
            jwtEncryptRequestDto.setApplicationId(null);

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
