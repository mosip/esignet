/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.services;

import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.api.util.Action;
import io.mosip.esignet.api.util.ActionStatus;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.spi.OpenIdConnectService;
import io.mosip.esignet.core.spi.TokenService;
import io.mosip.esignet.core.constants.Constants;
import io.mosip.esignet.core.util.AuditHelper;
import io.mosip.esignet.core.util.IdentityProviderUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
public class OpenIdConnectServiceImpl implements OpenIdConnectService {

    @Autowired
    private TokenService tokenService;

    @Autowired
    private CacheUtilService cacheUtilService;

    @Autowired
    private AuditPlugin auditWrapper;

    @Value("#{${mosip.esignet.discovery.key-values}}")
    private Map<String, Object> discoveryMap;


    @Override
    public String getUserInfo(String accessToken) throws EsignetException {
        String accessTokenHash = null;
        OIDCTransaction transaction = null;
        try {
            if(accessToken == null || accessToken.isBlank())
                throw new NotAuthenticatedException();

            String[] tokenParts = IdentityProviderUtil.splitAndTrimValue(accessToken, Constants.SPACE);
            if(tokenParts.length <= 1)
                throw new NotAuthenticatedException();

            if(!Constants.BEARER.equals(tokenParts[0]))
                throw new NotAuthenticatedException();

            accessTokenHash = IdentityProviderUtil.generateOIDCAtHash(tokenParts[1]);
            transaction = cacheUtilService.getUserInfoTransaction(accessTokenHash);
            if(transaction == null)
                throw new NotAuthenticatedException();

            tokenService.verifyAccessToken(transaction.getClientId(), transaction.getPartnerSpecificUserToken(), tokenParts[1]);
            auditWrapper.logAudit(Action.GET_USERINFO, ActionStatus.SUCCESS, AuditHelper.buildAuditDto(transaction.getTransactionId(),
                    transaction), null);
            log.info("Userinfo response >> {}", transaction.getEncryptedKyc());
            return transaction.getEncryptedKyc();

        } catch (EsignetException ex) {
            auditWrapper.logAudit(Action.GET_USERINFO, ActionStatus.ERROR, AuditHelper.buildAuditDto(accessTokenHash,
                   "accessTokenHash", transaction), ex);
            throw ex;
        }
    }

    @Override
    public Map<String, Object> getOpenIdConfiguration() {
        return discoveryMap;
    }
}
