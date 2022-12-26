/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.services;

import io.mosip.idp.core.dto.AuditDTO;
import io.mosip.idp.core.dto.IdPTransaction;
import io.mosip.idp.core.exception.IdPException;
import io.mosip.idp.core.spi.AuditWrapper;
import io.mosip.idp.core.spi.TokenService;
import io.mosip.idp.core.exception.NotAuthenticatedException;
import io.mosip.idp.core.util.Action;
import io.mosip.idp.core.util.ActionStatus;
import io.mosip.idp.core.util.Constants;
import io.mosip.idp.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class OpenIdConnectServiceImpl implements io.mosip.idp.core.spi.OpenIdConnectService {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private AuditWrapper auditWrapper;

    @Value("#{${mosip.idp.discovery.key-values}}")
    private Map<String, Object> discoveryMap;


    @Override
    public String getUserInfo(String accessToken) throws IdPException {
        if(accessToken == null || accessToken.isBlank())
            throw new NotAuthenticatedException();

        String[] tokenParts = IdentityProviderUtil.splitAndTrimValue(accessToken, Constants.SPACE);
        if(tokenParts.length <= 1)
            throw new NotAuthenticatedException();

        if(!Constants.BEARER.equals(tokenParts[0]))
            throw new NotAuthenticatedException();

        String accessTokenHash = IdentityProviderUtil.generateOIDCAtHash(tokenParts[1]);
        IdPTransaction transaction = cacheUtilService.getUserInfoTransaction(accessTokenHash);
        if(transaction == null)
            throw new NotAuthenticatedException();

        tokenService.verifyAccessToken(transaction.getClientId(), transaction.getPartnerSpecificUserToken(), tokenParts[1]);
        auditWrapper.logAudit(Action.GET_USERINFO, ActionStatus.SUCCESS, new AuditDTO(accessTokenHash,
                transaction), null);
        return transaction.getEncryptedKyc();
    }

    @Override
    public Map<String, Object> getOpenIdConfiguration() {
        return discoveryMap;
    }
}
