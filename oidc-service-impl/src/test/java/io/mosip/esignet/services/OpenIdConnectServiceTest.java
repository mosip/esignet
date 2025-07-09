package io.mosip.esignet.services;


import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.EsignetException;
import io.mosip.esignet.core.exception.NotAuthenticatedException;
import io.mosip.esignet.core.spi.TokenService;

import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
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
        Assertions.assertNotNull(openIdConnectService.getOpenIdConfiguration());
    }

    @Test
    public void getUserInfo_withNullAccessToken_thenFail() {
        Assertions.assertThrows(NotAuthenticatedException.class, () -> openIdConnectService.getUserInfo(null));
    }

    @Test
    public void getUserInfo_withEmptyAccessToken_thenFail() {
        Assertions.assertThrows(NotAuthenticatedException.class, () -> openIdConnectService.getUserInfo(""));
    }

    @Test
    public void getUserInfo_withNonBearerAccessToken_thenFail() {
        Assertions.assertThrows(NotAuthenticatedException.class, () -> openIdConnectService.getUserInfo("access-token"));
    }

    @Test
    public void getUserInfo_withInvalidAccessToken_thenFail() {
        Assertions.assertThrows(NotAuthenticatedException.class, () -> openIdConnectService.getUserInfo("Bearer1 access-token"));
    }

    @Test
    public void getUserInfo_withInvalidTransaction_thenFail() {
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(null);
        Assertions.assertThrows(NotAuthenticatedException.class, () -> openIdConnectService.getUserInfo("Bearer access-token"));
    }

    @Test
    public void getUserInfo_withInValidAccessToken_thenFail() {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setPartnerSpecificUserToken("p-s-u-t");
        oidcTransaction.setEncryptedKyc("encrypted-kyc");
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.doThrow(EsignetException.class).when(tokenService).verifyAccessToken(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        Assertions.assertThrows(EsignetException.class, () -> openIdConnectService.getUserInfo("Bearer access-token"));
    }

    @Test
    public void getUserInfo_withValidTransaction_thenPass() {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setPartnerSpecificUserToken("p-s-u-t");
        oidcTransaction.setEncryptedKyc("encrypted-kyc");
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        String kyc = openIdConnectService.getUserInfo("Bearer access-token");
        Assertions.assertNotNull(kyc);
        Assertions.assertEquals("encrypted-kyc", kyc);
    }
}
