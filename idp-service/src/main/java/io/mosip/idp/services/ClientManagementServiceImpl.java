/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.ClientDetailCreateRequest;
import io.mosip.idp.core.dto.ClientDetailResponse;
import io.mosip.idp.core.dto.ClientDetailUpdateRequest;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.ClientManagementService;
import io.mosip.idp.core.util.ErrorConstants;
import io.mosip.idp.entity.ClientDetail;
import io.mosip.idp.repository.ClientDetailRepository;
import lombok.extern.slf4j.Slf4j;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.lang.JoseException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

@Slf4j
@Service
public class ClientManagementServiceImpl implements ClientManagementService {

    @Autowired
    ClientDetailRepository clientDetailRepository;

    @Override
    public ClientDetailResponse createOIDCClient(ClientDetailCreateRequest clientDetailCreateRequest) throws IdPException {
        Optional<ClientDetail> result = clientDetailRepository.findById(clientDetailCreateRequest.getClientId());

        if (result.isPresent()) {
            throw new IdPException(ErrorConstants.DUPLICATE_CLIENT_ID);
        }

        ClientDetail clientDetail = new ClientDetail();
        clientDetail.setJwk(getJWKString(clientDetailCreateRequest.getJwk()));
        clientDetail.setId(clientDetailCreateRequest.getClientId());
        clientDetail.setName(clientDetailCreateRequest.getClientName());
        clientDetail.setRpId(clientDetailCreateRequest.getRelayingPartyId());
        clientDetail.setLogoUri(clientDetailCreateRequest.getLogoUri());
        clientDetail.setRedirectUris(String.join(",", clientDetailCreateRequest.getRedirectUris()));
        clientDetail.setClaims(String.join(",", clientDetailCreateRequest.getUserClaims()));
        clientDetail.setAcrValues(String.join(",", clientDetailCreateRequest.getAuthContextRefs()));
        clientDetail.setStatus(clientDetailCreateRequest.getStatus());
        clientDetail.setGrantTypes(String.join(",", clientDetailCreateRequest.getGrantTypes()));
        clientDetail.setClientAuthMethods(String.join(",", clientDetailCreateRequest.getClientAuthMethods()));
        clientDetail = clientDetailRepository.save(clientDetail);

        var response = new ClientDetailResponse();
        response.setClientId(clientDetail.getId());
        response.setStatus(clientDetail.getStatus());
        return response;
    }

    @Override
    public ClientDetailResponse updateOIDCClient(String clientId, ClientDetailUpdateRequest clientDetailUpdateRequest) throws IdPException {

        Optional<ClientDetail> result = clientDetailRepository.findById(clientId);

        if (!result.isPresent()) {
            throw new IdPException(ErrorConstants.INVALID_CLIENT_ID);
        }

        ClientDetail clientDetail = result.get();
        clientDetail.setLogoUri(clientDetailUpdateRequest.getLogoUri());
        clientDetail.setRedirectUris(String.join(",", clientDetailUpdateRequest.getRedirectUris()));
        clientDetail.setClaims(String.join(",", clientDetailUpdateRequest.getUserClaims()));
        clientDetail.setAcrValues(String.join(",", clientDetailUpdateRequest.getAuthContextRefs()));
        clientDetail.setGrantTypes(String.join(",", clientDetailUpdateRequest.getGrantTypes()));
        clientDetail.setClientAuthMethods(String.join(",", clientDetailUpdateRequest.getClientAuthMethods()));
        clientDetail.setStatus(clientDetailUpdateRequest.getStatus());
        clientDetail = clientDetailRepository.save(clientDetail);

        var response = new ClientDetailResponse();
        response.setClientId(clientDetail.getId());
        response.setStatus(clientDetail.getStatus());
        return response;
    }

    private String getJWKString(Map<String, Object> jwk) throws IdPException {
        try {
            RsaJsonWebKey jsonWebKey = new RsaJsonWebKey(jwk);
            return jsonWebKey.toJson();
        } catch (JoseException e) {
            log.error(ErrorConstants.INVALID_JWKS, e);
            throw new IdPException(ErrorConstants.INVALID_JWKS);
        }
    }
}
