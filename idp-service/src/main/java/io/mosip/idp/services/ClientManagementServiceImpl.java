/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import com.nimbusds.jose.jwk.JWK;
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

import java.util.Map;

@Service
public class ClientManagementServiceImpl implements ClientManagementService {
    @Autowired
    ClientDetailRepository clientDetailRepository;

    private static final Logger logger = LoggerFactory.getLogger(ClientManagementServiceImpl.class);

    @Override
    public ClientDetailResponse createOIDCClient(ClientDetailCreateRequest clientDetailCreateRequest) throws IdPException {
        var clientDetailFromDb = clientDetailRepository.findById(clientDetailCreateRequest.getClientId());

        if (clientDetailFromDb.isPresent()) {
            logger.error(ErrorConstants.DUPLICATE_CLIENT_ID);
            throw new IdPException(ErrorConstants.DUPLICATE_CLIENT_ID);
        }

        var publicKeyJson = getJwkJson(clientDetailCreateRequest.getPublicKey());

        if (publicKeyJson == null) {
            logger.error(ErrorConstants.INVALID_JWK_KEY);
            throw new IdPException(ErrorConstants.INVALID_JWK_KEY);
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
        clientDetail.setPublicKey(publicKeyJson);
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

    private String getJwkJson(Map<String, Object> publicKey) {
        try {
            JWK jwk = JWK.parse(publicKey);
            JWK publicJwk = jwk.toPublicJWK();
            return publicJwk.toJSONString();
        } catch (Exception e) {
            logger.error("JWK parsing failed", e);
        }
        return null;
    }
}
