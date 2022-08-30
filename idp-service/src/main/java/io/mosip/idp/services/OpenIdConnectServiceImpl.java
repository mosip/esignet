/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.DiscoveryResponse;
import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.AuthorizationService;
import io.mosip.idp.core.spi.TokenService;
import io.mosip.idp.core.exception.NotAuthenticatedException;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import io.mosip.idp.repository.ClientDetailRepository;
import io.mosip.kernel.signature.dto.JWTSignatureRequestDto;
import io.mosip.kernel.signature.dto.JWTSignatureResponseDto;
import io.mosip.kernel.signature.service.SignatureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class OpenIdConnectServiceImpl implements io.mosip.idp.core.spi.OpenIdConnectService {

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private SignatureService signatureService;

    @Value("${mosip.idp.cache.key.hash.algorithm}")
    private String hashingAlgorithm;

    @Value("#{${mosip.idp.discovery.key-values}}")
    private Map<String, Object> discoveryMap;


    @Override
    public String getUserInfo(String accessToken) throws IdPException {
        if(accessToken == null || accessToken.isBlank())
            throw new NotAuthenticatedException();

        String accessTokenHash = IdentityProviderUtil.generateAccessTokenHash(accessToken);
        IdPTransaction transaction = cacheUtilService.getSetKycTransaction(accessTokenHash, null);
        if(transaction == null)
            throw new NotAuthenticatedException();

        tokenService.verifyAccessToken(transaction.getClientId(), transaction.getUserToken(), accessToken);

        JWTSignatureRequestDto jwtSignatureRequestDto = new JWTSignatureRequestDto();
        jwtSignatureRequestDto.setApplicationId(Constants.IDP_SERVICE_APP_ID);
        jwtSignatureRequestDto.setReferenceId(Constants.SIGN_REFERENCE_ID);
        jwtSignatureRequestDto.setIncludePayload(true);
        jwtSignatureRequestDto.setIncludeCertificate(true);
        jwtSignatureRequestDto.setDataToSign(transaction.getEncryptedKyc());
        jwtSignatureRequestDto.setIncludeCertHash(true);
        JWTSignatureResponseDto responseDto = signatureService.jwtSign(jwtSignatureRequestDto);
        return responseDto.getJwtSignedData();
    }

    @Override
    public Map<String, Object> getOpenIdConfiguration() {
        return discoveryMap;
    }
}
