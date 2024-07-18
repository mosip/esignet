/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package io.mosip.esignet.core.constants;

public class Constants {

    public static final String CLIENT_ACTIVE_STATUS = "ACTIVE";
    public static final String UTC_DATETIME_PATTERN = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'";
    public static final String SPACE = " ";
    public static final String BEARER = "Bearer";
    public static final String SCOPE_OPENID= "openid";

    public static final String PRE_AUTH_SESSION_CACHE = "preauth";
    public static final String AUTHENTICATED_CACHE = "authenticated";
    public static final String CONSENTED_CACHE = "consented";
    public static final String USERINFO_CACHE = "userinfo";
    public static final String CLIENT_DETAIL_CACHE = "clientdetails";
    public static final String LINKED_AUTH_CACHE = "linkedauth";
    public static final String LINK_CODE_GENERATED_CACHE = "linkcodegenerated";
    public static final String LINKED_SESSION_CACHE = "linked";
    public static final String LINKED_CODE_CACHE = "linkedcode";
    public static final String AUTH_CODE_GENERATED_CACHE = "authcodegenerated";
    public static final String HALTED_CACHE = "halted";
    public static final String RATE_LIMIT_CACHE = "apiratelimit";
    public static final String BLOCKED_CACHE = "blocked";


    public static final String ROOT_KEY = "ROOT";
    public static final String OIDC_PARTNER_APP_ID = "OIDC_PARTNER";
    public static final String OIDC_SERVICE_APP_ID = "OIDC_SERVICE";

    public static final String JWK_MODULUS = "n";
    public static final String JWK_EXPONENT = "e";
    public static final String JWK_KEY_ID = "kid";
    public static final String JWK_KEY_ALG = "alg";
    public static final String JWK_KEY_TYPE = "kty";
    public static final String JWK_KEY_USE= "use";
    public static final String JWK_KEY_CERT_CHAIN= "x5c";
    public static final String JWK_KEY_CERT_SHA256_THUMBPRINT = "x5t#S256";
    public static final String JWK_KEY_EXPIRE = "exp";
    public static final String ESSENTIAL = "essential";
    public static final String VOLUNTARY = "voluntary";
    public static final String LINKED_STATUS = "LINKED";
    public static final String NONE_LANG_KEY = "@none";
    public static final String S256 = "S256";

    public static final String SERVER_NONCE_SEPARATOR = "~###~";
    public static final String RESUMED = "RESUMED";
    public static final String RESUME_NOT_APPLICABLE = "RESUME_NOT_APPLICABLE";
}
