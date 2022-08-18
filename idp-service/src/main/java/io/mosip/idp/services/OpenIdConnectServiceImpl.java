/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.spi.AuthenticationWrapper;
import io.mosip.idp.core.spi.AuthorizationService;
import io.mosip.idp.core.spi.TokenGeneratorService;
import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.exception.NotAuthenticatedException;
import io.mosip.idp.repositories.ClientDetailRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class OpenIdConnectServiceImpl implements io.mosip.idp.core.spi.OpenIdConnectService {

    private static final Logger logger = LoggerFactory.getLogger(OpenIdConnectServiceImpl.class);

    @Autowired
    private ClientDetailRepository clientDetailRepository;

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private TokenGeneratorService tokenGeneratorService;

    @Autowired
    private AuthenticationWrapper authenticationWrapper;

    @Autowired
    private CacheUtilService cacheUtilService;


    @Override
    public String getUserInfo(String accessToken) throws NotAuthenticatedException {
        if(accessToken == null || accessToken.isBlank())
            throw new NotAuthenticatedException();

        //TODO - validate access token expiry - keymanager

        String accessTokenHash = ""; //TODO - generate access token hash - keymanager
        IdPTransaction transaction = cacheUtilService.getSetKycTransaction(accessTokenHash, null);
        if(transaction == null)
            throw new NotAuthenticatedException();

        return transaction.getEncryptedKyc();
    }






}
