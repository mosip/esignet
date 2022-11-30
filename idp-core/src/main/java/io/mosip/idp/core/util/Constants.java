/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.idp.core.util;

public class Constants {

    public static final String CLIENT_ACTIVE_STATUS = "ACTIVE";
    public static final String UTC_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final String SPACE = " ";
    public static final String COMMA = ",";
    public static final String BEARER = "Bearer";
    public static final String SCOPE_OPENID= "openid";

    public static final String PRE_AUTH_SESSION_CACHE = "preauthsessions";
    public static final String AUTHENTICATED_CACHE = "authenticated";
    public static final String KYC_CACHE = "kyc";
    public static final String CLIENT_DETAIL_CACHE = "clientdetails";

    public static final String ROOT_KEY = "ROOT";
    public static final String IDP_PARTNER_APP_ID = "IDP_PARTNER";
    public static final String IDP_SERVICE_APP_ID = "IDP_SERVICE";

    public static final String IDP_BINDING_PARTNER_APP_ID = "IDP_BINDING_PARTNER";
    public static final String IDP_BINDING_SERVICE_APP_ID = "IDP_BINDING_SERVICE";

    public static final String JWK_MODULUS = "n";
    public static final String JWK_EXPONENT = "e";
    public static final String JWK_KEY_ID = "kid";
    public static final String JWK_KEY_ALG = "alg";
    public static final String JWK_KEY_TYPE = "kty";
    public static final String JWK_KEY_USE= "use";
    public static final String JWK_KEY_CERT_CHAIN= "x5c";
    public static final String JWK_KEY_CERT_SHA256_THUMBPRINT = "x5t#S256";
    public static final String JWK_KEY_EXPIRE = "exp";
    public static final String OTP = "otp";
    
}
