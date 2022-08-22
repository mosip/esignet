/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.mosip.idp.core.dto.ClientDetailCreateRequest;
import io.mosip.idp.core.dto.ClientDetailResponse;
import io.mosip.idp.core.dto.ClientDetailUpdateRequest;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.domain.ClientDetail;
import io.mosip.idp.repositories.ClientDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.security.KeyFactory;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

@Service
public class ClientManagementServiceImpl implements ClientManagementService {
    public static final String TAG = ClientManagementServiceImpl.class.getSimpleName();

    @Autowired
    ClientDetailRepository clientDetailRepository;

    ObjectMapper mapper = new ObjectMapper();

    private static final Logger logger = LoggerFactory.getLogger(ClientManagementServiceImpl.class);

    @Override
    public ClientDetailResponse createOIDCClient(ClientDetailCreateRequest clientDetailCreateRequest) throws IdPException {
        var clientDetailFromDb = clientDetailRepository.findById(clientDetailCreateRequest.getClientId());

        if (clientDetailFromDb.isPresent()) {
            logger.error(TAG, ErrorConstants.DUPLICATE_CLIENT_ID);
            throw new IdPException(ErrorConstants.DUPLICATE_CLIENT_ID);
        }

        String publicKey = clientDetailCreateRequest.getPublicKey();

        if (!validateBase64PublicKey(publicKey)) {
            logger.error(TAG, ErrorConstants.INVALID_BASE64_RSA_PUBLIC_KEY);
            throw new IdPException(ErrorConstants.INVALID_BASE64_RSA_PUBLIC_KEY);
        }

        var redirectUrisList = clientDetailCreateRequest.getRedirectUris();
        if (redirectUrisList.isEmpty() || redirectUrisList.stream().anyMatch(String::isBlank)) {
            logger.error(TAG, ErrorConstants.INVALID_REDIRECT_URI);
            throw new IdPException(ErrorConstants.INVALID_REDIRECT_URI);
        }

        var aCRList = clientDetailCreateRequest.getAuthContextRefs();
        if (aCRList.isEmpty() || aCRList.stream().anyMatch(String::isBlank)) {
            logger.error(TAG, ErrorConstants.INVALID_ACR);
            throw new IdPException(ErrorConstants.INVALID_ACR);
        }

        var claimsList = clientDetailCreateRequest.getUserClaims();
        if (claimsList.isEmpty() || claimsList.stream().anyMatch(String::isBlank)) {
            logger.error(TAG, ErrorConstants.INVALID_CLAIM);
            throw new IdPException(ErrorConstants.INVALID_CLAIM);
        }

        var grantTypesList = clientDetailCreateRequest.getGrantTypes();
        if (claimsList.isEmpty() || claimsList.stream().anyMatch(String::isBlank)) {
            logger.error(TAG, ErrorConstants.INVALID_GRANT_TYPE);
            throw new IdPException(ErrorConstants.INVALID_GRANT_TYPE);
        }

        String redirectUris = null;
        String aCR = null;
        String claims = null;
        String grandTypes = null;

        try {
            redirectUris = mapper.writeValueAsString(redirectUrisList);
            aCR = mapper.writeValueAsString(aCRList);
            claims = mapper.writeValueAsString(claimsList);
            grandTypes = mapper.writeValueAsString(grantTypesList);
        } catch (JsonProcessingException e) {
            logger.error(TAG, e.getMessage());
            throw new RuntimeException(e);
        }

        ClientDetail clientDetail = new ClientDetail();

        clientDetail.setId(clientDetailCreateRequest.getClientId());
        clientDetail.setName(clientDetailCreateRequest.getClientName());
        clientDetail.setRpId(clientDetailCreateRequest.getRelayingPartyId());
        clientDetail.setLogoUri(clientDetailCreateRequest.getLogoUri());
        clientDetail.setRedirectUris(redirectUris);
        clientDetail.setPublicKey(publicKey);
        clientDetail.setClaims(claims);
        clientDetail.setAcrValues(aCR);
        clientDetail.setStatus(clientDetailCreateRequest.getStatus());
        clientDetail.setGrantTypes(grandTypes);

        ClientDetail savedClientDetail = clientDetailRepository.save(clientDetail);

        var response = new ClientDetailResponse();
        response.setClientId(savedClientDetail.getId());
        response.setStatus(savedClientDetail.getStatus());
        return response;
    }

    @Override
    public ClientDetailResponse updateOIDCClient(String clientId, ClientDetailUpdateRequest clientDetailUpdateRequest) throws IdPException {

        var clientDetailFromDb = clientDetailRepository.findById(clientId);

        if (clientDetailFromDb.isEmpty()) {
            String msg = String.format("ClientId %s does not exist", clientId);
            logger.error(TAG, msg);
            throw new IdPException(msg);
        }

        var clientDetails = clientDetailFromDb.get();

        if (clientDetailUpdateRequest.getClientName() != null && !clientDetailUpdateRequest.getClientName().isEmpty()) {
            clientDetails.setName(clientDetailUpdateRequest.getClientName());
        }

        var redirectUrisList = clientDetailUpdateRequest.getRedirectUris();
        if (redirectUrisList.isEmpty() || redirectUrisList.stream().anyMatch(String::isBlank)) {
            logger.error(TAG, ErrorConstants.INVALID_REDIRECT_URI);
            throw new IdPException(ErrorConstants.INVALID_REDIRECT_URI);
        }

        var aCRList = clientDetailUpdateRequest.getAuthContextRefs();
        if (aCRList.isEmpty() || aCRList.stream().anyMatch(String::isBlank)) {
            logger.error(TAG, ErrorConstants.INVALID_ACR);
            throw new IdPException(ErrorConstants.INVALID_ACR);
        }

        var claimsList = clientDetailUpdateRequest.getUserClaims();
        if (claimsList.isEmpty() || claimsList.stream().anyMatch(String::isBlank)) {
            logger.error(TAG, ErrorConstants.INVALID_CLAIM);
            throw new IdPException(ErrorConstants.INVALID_CLAIM);
        }

        var grantTypesList = clientDetailUpdateRequest.getGrantTypes();
        if (claimsList.isEmpty() || claimsList.stream().anyMatch(String::isBlank)) {
            logger.error(TAG, ErrorConstants.INVALID_GRANT_TYPE);
            throw new IdPException(ErrorConstants.INVALID_GRANT_TYPE);
        }

        String redirectUris = null;
        String aCR = null;
        String claims = null;
        String grandTypes = null;

        try {
            redirectUris = mapper.writeValueAsString(redirectUrisList);
            aCR = mapper.writeValueAsString(aCRList);
            claims = mapper.writeValueAsString(claimsList);
            grandTypes = mapper.writeValueAsString(grantTypesList);
        } catch (JsonProcessingException e) {
            logger.error(TAG, e.getMessage());
            throw new RuntimeException(e);
        }

        clientDetails.setLogoUri(clientDetailUpdateRequest.getLogoUri());
        clientDetails.setRedirectUris(redirectUris);
        clientDetails.setClaims(claims);
        clientDetails.setAcrValues(aCR);
        clientDetails.setGrantTypes(grandTypes);
        clientDetails.setStatus(clientDetailUpdateRequest.getStatus());

        ClientDetail savedClientDetail = clientDetailRepository.save(clientDetails);

        var response = new ClientDetailResponse();
        response.setClientId(savedClientDetail.getId());
        response.setStatus(savedClientDetail.getStatus());
        return response;
    }

    private static boolean validateBase64PublicKey(String publicKey) {
        try {
            //if base64 is invalid, you will see an error here
            byte[] byteKey = Base64.getDecoder().decode(publicKey);
            //if it is not in RSA public key format, you will see error here as java.security.spec.InvalidKeySpecException
            X509EncodedKeySpec X509publicKey = new X509EncodedKeySpec(byteKey);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            kf.generatePublic(X509publicKey);
            return true;
        } catch (Exception e) {
            logger.error("Invalid public key", e);
        }
        return false;
    }
}
