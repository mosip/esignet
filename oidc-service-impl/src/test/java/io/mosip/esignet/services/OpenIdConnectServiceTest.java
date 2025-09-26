package io.mosip.esignet.services;


import io.mosip.esignet.api.spi.AuditPlugin;
import io.mosip.esignet.core.dto.OIDCTransaction;
import io.mosip.esignet.core.exception.DpopNonceMissingException;
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
    public void getUserInfo_withInvalidTransaction_thenFail() {
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(null);
        openIdConnectService.getUserInfo("Bearer access-token", null);
    }

    @Test(expected = EsignetException.class)
    public void getUserInfo_withInvalidAccessToken_thenFail() {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setPartnerSpecificUserToken("p-s-u-t");
        oidcTransaction.setEncryptedKyc("encrypted-kyc");
        oidcTransaction.setDpopBoundAccessToken(false);
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.doThrow(EsignetException.class).when(tokenService).verifyAccessToken(Mockito.anyString(), Mockito.anyString(), Mockito.anyString());
        openIdConnectService.getUserInfo("Bearer access-token", null);
    }

    @Test
    public void getUserInfo_withValidTransaction_thenPass() {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setPartnerSpecificUserToken("p-s-u-t");
        oidcTransaction.setEncryptedKyc("encrypted-kyc");
        oidcTransaction.setDpopBoundAccessToken(false);
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        String kyc = openIdConnectService.getUserInfo("Bearer access-token", null);
        Assert.assertNotNull(kyc);
        Assert.assertEquals("encrypted-kyc", kyc);
    }

    @Test
    public void getUserInfo_withValidTransactionAndDpopNonce_thenPass() {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setPartnerSpecificUserToken("p-s-u-t");
        oidcTransaction.setEncryptedKyc("encrypted-kyc");
        oidcTransaction.setDpopBoundAccessToken(true);
        String dpopHeader = "dpop header";
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(tokenService.isValidDpopServerNonce(dpopHeader, oidcTransaction)).thenReturn(true);
        String kyc = openIdConnectService.getUserInfo("DPoP access-token", dpopHeader);
        Assert.assertNotNull(kyc);
        Assert.assertEquals("encrypted-kyc", kyc);
    }

    @Test
    public void getUserInfo_withDpopWithoutNonce_thenFail() throws Exception {
        OIDCTransaction oidcTransaction = new OIDCTransaction();
        oidcTransaction.setClientId("client-id");
        oidcTransaction.setPartnerSpecificUserToken("p-s-u-t");
        oidcTransaction.setEncryptedKyc("encrypted-kyc");
        oidcTransaction.setDpopBoundAccessToken(true);
        Mockito.when(cacheUtilService.getUserInfoTransaction(Mockito.anyString())).thenReturn(oidcTransaction);
        Mockito.when(tokenService.isValidDpopServerNonce(Mockito.anyString(), Mockito.any())).thenReturn(false);
        String nonce = "valid-nonce";
        Mockito.doThrow(new DpopNonceMissingException(nonce)).when(tokenService).generateAndStoreNewNonce(Mockito.anyString(), Mockito.anyString());
        try {
            openIdConnectService.getUserInfo("DPoP access-token", "dpop-header");
            Assert.fail();
        } catch (DpopNonceMissingException ex) {
            Assert.assertEquals(nonce, ex.getDpopNonceHeaderValue());
        }
    }

}
