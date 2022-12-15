package io.mosip.idp.core.util;

public enum IdPAction {

    OIDC_CLIENT_CREATED("success"),
    OIDC_CLIENT_UPDATED("success"),
    OIDC_CLIENT_CREATE_FAILED("failed"),
    OIDC_CLIENT_UPDATE_FAILED("failed"),
    GET_OAUTH_DETAILS("success"),
    TRANSACTION_STARTED("success"),
    SEND_OTP_SUCCESS("success"),
    SEND_OTP_FAILED("failed"),
    AUTHENTICATE("success"),
    AUTHENTICATE_FAILED("failed"),
    AUTH_CODE_CREATED("success"),
    AUTH_CODE_FAILED("failed"),
    GENERATE_TOKEN_SUCCESS("success"),
    GENERATE_TOKEN_FAILED("failed"),
    USERINFO_SUCCESS("success"),
    USERINFO_FAILED("success"),
    KYC_AUTH_SUCCESS("success"),
    KYC_AUTH_FAILED("failed"),
    KYC_EXCHANGE_SUCCESS("success"),
    KYC_EXCHANGE_FAILED("failed");

    String state;

    IdPAction(String actionState) {
        state = actionState;
    }

    public String getState() {
        return state;
    }
}
