package io.mosip.esignet.api.util;

public enum Action {
    OIDC_CLIENT_CREATE("client-mgmt-service"),
    OIDC_CLIENT_UPDATE("client-mgmt-service"),
    OAUTH_CLIENT_CREATE("client-mgmt-service"),
    OAUTH_CLIENT_UPDATE("client-mgmt-service"),
    GET_OAUTH_DETAILS("esignet-service"),
    TRANSACTION_STARTED("esignet-service"),
    PREPARE_SIGNUP_REDIRECT("esignet-service"),
    SEND_OTP("esignet-service"),
    AUTHENTICATE("esignet-service"),
    GET_AUTH_CODE("esignet-service"),
    GENERATE_TOKEN("esignet-service"),
    GET_USERINFO("esignet-service"),
    DO_KYC_AUTH("esignet-service"),
    DO_KYC_EXCHANGE("esignet-service"),
    GET_CERTIFICATE("keymanager"),
    UPLOAD_CERTIFICATE("keymanager"),
    LINK_CODE("esignet-service"),
    LINK_TRANSACTION("esignet-service"),
    LINK_STATUS("esignet-service"),
    LINK_AUTHENTICATE("esignet-service"),
    SAVE_CONSENT("consent-service"),
    LINK_SEND_OTP("esignet-service"),
    LINK_AUTH_CODE("esignet-service"),
    GET_USER_CONSENT("consent-service"),
    SAVE_USER_CONSENT("consent-service"),
    UPDATE_USER_CONSENT("consent-service"),
    DELETE_USER_CONSENT("consent-service"),
    SEND_BINDING_OTP("key-binding"),
    KEY_BINDING("key-binding"),
    VC_ISSUANCE("vci-service"),
    CONSENT_DETAILS("esignet-service");

    String module;

    Action(String module) {
        this.module = module;
    }

    public String getModule() {
        return this.module;
    }
}
