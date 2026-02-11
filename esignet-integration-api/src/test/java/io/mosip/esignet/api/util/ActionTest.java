package io.mosip.esignet.api.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.junit.jupiter.api.Assertions.*;

class ActionTest {

    @Test
    void testValuesReturnsAllEnumConstants() {
        Action[] actions = Action.values();
        assertTrue(actions.length > 0);
    }

    @Test
    void testValueOfReturnsCorrectEnum() {
        assertEquals(Action.OIDC_CLIENT_CREATE, Action.valueOf("OIDC_CLIENT_CREATE"));
        assertEquals(Action.OIDC_CLIENT_UPDATE, Action.valueOf("OIDC_CLIENT_UPDATE"));
        assertEquals(Action.OAUTH_CLIENT_CREATE, Action.valueOf("OAUTH_CLIENT_CREATE"));
        assertEquals(Action.OAUTH_CLIENT_UPDATE, Action.valueOf("OAUTH_CLIENT_UPDATE"));
        assertEquals(Action.GET_OAUTH_DETAILS, Action.valueOf("GET_OAUTH_DETAILS"));
        assertEquals(Action.PAR_REQUEST, Action.valueOf("PAR_REQUEST"));
    }

    @ParameterizedTest
    @EnumSource(Action.class)
    void testGetModuleReturnsNonNullForAllActions(Action action) {
        assertNotNull(action.getModule());
        assertFalse(action.getModule().isEmpty());
    }

    @Test
    void testClientMgmtServiceModule() {
        assertEquals("client-mgmt-service", Action.OIDC_CLIENT_CREATE.getModule());
        assertEquals("client-mgmt-service", Action.OIDC_CLIENT_UPDATE.getModule());
        assertEquals("client-mgmt-service", Action.OAUTH_CLIENT_CREATE.getModule());
        assertEquals("client-mgmt-service", Action.OAUTH_CLIENT_UPDATE.getModule());
    }

    @Test
    void testEsignetServiceModule() {
        assertEquals("esignet-service", Action.GET_OAUTH_DETAILS.getModule());
        assertEquals("esignet-service", Action.GET_PAR_OAUTH_DETAILS.getModule());
        assertEquals("esignet-service", Action.TRANSACTION_STARTED.getModule());
        assertEquals("esignet-service", Action.PREPARE_SIGNUP_REDIRECT.getModule());
        assertEquals("esignet-service", Action.SEND_OTP.getModule());
        assertEquals("esignet-service", Action.AUTHENTICATE.getModule());
        assertEquals("esignet-service", Action.GET_AUTH_CODE.getModule());
        assertEquals("esignet-service", Action.GENERATE_TOKEN.getModule());
        assertEquals("esignet-service", Action.GET_USERINFO.getModule());
        assertEquals("esignet-service", Action.DO_KYC_AUTH.getModule());
        assertEquals("esignet-service", Action.DO_KYC_EXCHANGE.getModule());
        assertEquals("esignet-service", Action.LINK_CODE.getModule());
        assertEquals("esignet-service", Action.LINK_TRANSACTION.getModule());
        assertEquals("esignet-service", Action.LINK_STATUS.getModule());
        assertEquals("esignet-service", Action.LINK_AUTHENTICATE.getModule());
        assertEquals("esignet-service", Action.LINK_SEND_OTP.getModule());
        assertEquals("esignet-service", Action.LINK_AUTH_CODE.getModule());
        assertEquals("esignet-service", Action.CLAIM_DETAILS.getModule());
        assertEquals("esignet-service", Action.COMPLETE_SIGNUP_REDIRECT.getModule());
    }

    @Test
    void testKeymanagerModule() {
        assertEquals("keymanager", Action.GET_CERTIFICATE.getModule());
        assertEquals("keymanager", Action.UPLOAD_CERTIFICATE.getModule());
    }

    @Test
    void testConsentServiceModule() {
        assertEquals("consent-service", Action.SAVE_CONSENT.getModule());
        assertEquals("consent-service", Action.GET_USER_CONSENT.getModule());
        assertEquals("consent-service", Action.SAVE_USER_CONSENT.getModule());
        assertEquals("consent-service", Action.UPDATE_USER_CONSENT.getModule());
        assertEquals("consent-service", Action.DELETE_USER_CONSENT.getModule());
    }

    @Test
    void testKeyBindingModule() {
        assertEquals("key-binding", Action.SEND_BINDING_OTP.getModule());
        assertEquals("key-binding", Action.KEY_BINDING.getModule());
    }

    @Test
    void testVciServiceModule() {
        assertEquals("vci-service", Action.VC_ISSUANCE.getModule());
    }

    @Test
    void testParRequestModule() {
        assertEquals("par-request", Action.PAR_REQUEST.getModule());
    }

    @Test
    void testEnumNameMethod() {
        assertEquals("OIDC_CLIENT_CREATE", Action.OIDC_CLIENT_CREATE.name());
        assertEquals("PAR_REQUEST", Action.PAR_REQUEST.name());
    }
}
