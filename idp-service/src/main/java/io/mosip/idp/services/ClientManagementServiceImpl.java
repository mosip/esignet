/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

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
    @Autowired
    ClientDetailRepository clientDetailRepository;

    @Autowired
    ObjectMapper mapper;

    private static final Logger logger = LoggerFactory.getLogger(ClientManagementServiceImpl.class);

    @Override
    public ClientDetailResponse createOIDCClient(ClientDetailCreateRequest clientDetailCreateRequest) throws IdPException {
        var clientDetailFromDb = clientDetailRepository.findById(clientDetailCreateRequest.getClientId());

        if (clientDetailFromDb.isPresent()) {
            logger.error(ErrorConstants.DUPLICATE_CLIENT_ID);
            throw new IdPException(ErrorConstants.DUPLICATE_CLIENT_ID);
        }

        String publicKey = clientDetailCreateRequest.getPublicKey();

        if (!validateBase64PublicKey(publicKey)) {
            logger.error(ErrorConstants.INVALID_BASE64_RSA_PUBLIC_KEY);
            throw new IdPException(ErrorConstants.INVALID_BASE64_RSA_PUBLIC_KEY);
        }

        //comma separated list
        String redirectUris = String.join(",", clientDetailCreateRequest.getRedirectUris());
        String aCR = String.join(",", clientDetailCreateRequest.getAuthContextRefs());
        String claims = String.join(",", clientDetailCreateRequest.getUserClaims());
        String grandTypes = String.join(",", clientDetailCreateRequest.getGrantTypes());

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
            logger.error(msg);
            throw new IdPException(msg);
        }

        var clientDetails = clientDetailFromDb.get();

        if (clientDetailUpdateRequest.getClientName() != null && !clientDetailUpdateRequest.getClientName().isEmpty()) {
            clientDetails.setName(clientDetailUpdateRequest.getClientName());
        }

        //comma separated list
        String redirectUris = String.join(",", clientDetailUpdateRequest.getRedirectUris());
        String aCR = String.join(",", clientDetailUpdateRequest.getAuthContextRefs());
        String claims = String.join(",", clientDetailUpdateRequest.getUserClaims());
        String grandTypes = String.join(",", clientDetailUpdateRequest.getGrantTypes());

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

    private boolean validateBase64PublicKey(String publicKey) {
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
