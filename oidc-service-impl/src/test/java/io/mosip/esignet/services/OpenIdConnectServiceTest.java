package io.mosip.esignet.services;


import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.spi.TokenService;

import java.util.HashMap;
import java.util.Map;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;
import org.springframework.test.util.ReflectionTestUtils;

@RunWith(MockitoJUnitRunner.class)
public class OpenIdConnectServiceTest {

    @InjectMocks
    private OpenIdConnectServiceImpl openIdConnectService;

    @Mock
    private TokenService tokenService;

    @Mock
    private CacheUtilService cacheUtilService;

    @Mock
    private AuditPlugin auditWrapper;

    @Test
    public void getOpenIdConfiguration_test() {
    	Map<String, Object> discoveryMap = new HashMap<>();
		ReflectionTestUtils.setField(openIdConnectService, "discoveryMap", discoveryMap);
        Assert.assertNotNull(openIdConnectService.getOpenIdConfiguration());
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getUserInfo_withNullAccessToken_thenFail() {
        openIdConnectService.getUserInfo(null);
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getUserInfo_withEmptyAccessToken_thenFail() {
        openIdConnectService.getUserInfo("");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getUserInfo_withNonBearerAccessToken_thenFail() {
        openIdConnectService.getUserInfo("access-token");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getUserInfo_withInvalidAccessToken_thenFail() {
        openIdConnectService.getUserInfo("Bearer1 access-token");
    }

    @Test(expected = NotAuthenticatedException.class)
    public void getUserInfo_withInvalidTransaction_thenFail() {
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(null);
        openIdConnectService.getUserInfo("Bearer access-token");
    }

    @Test(expected = EsignetException.class)
    public void getUserInfo_withInValidAccessToken_thenFail() {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setPartnerSpecificUserToken("p-s-u-t");
        oidcTransaction.setEncryptedKyc("encrypted-kyc");
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.doThrow(EsignetException.class).when(tokenService).verifyAccessToken(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        openIdConnectService.getUserInfo("Bearer access-token");
    }

    @Test
    public void getUserInfo_withValidTransaction_thenPass() {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setPartnerSpecificUserToken("p-s-u-t");
        oidcTransaction.setEncryptedKyc("encrypted-kyc");
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        String kyc = openIdConnectService.getUserInfo("Bearer access-token");
        Assert.assertNotNull(kyc);
        Assert.assertEquals("encrypted-kyc", kyc);
    }
}
