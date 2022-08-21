package io.mosip.idp.core.spi;

import java.util.List;

public interface TokenGeneratorService {

    /**
     * iss: REQUIRED. Issuer Identifier for the Issuer of the response. The iss value is a case sensitive URL using
     * the https scheme that contains scheme, host, and optionally, port number and path components and no query or
     * fragment components.
     * sub: REQUIRED. Subject Identifier. Partner specific user token.
     * aud: REQUIRED. Audience(s) that this ID Token is intended for. client_id of the Relying Party as an audience value.
     * exp: REQUIRED. Expiration time on or after which the ID Token MUST NOT be accepted for processing. Its value is a
     * JSON number representing the number of seconds from 1970-01-01T0:0:0Z as measured in UTC until the date/time.
     * iat: REQUIRED. Time at which the JWT was issued. Its value is a JSON number representing the number of seconds
     * from 1970-01-01T0:0:0Z as measured in UTC until the date/time.
     * auth_time: Time when the End-User authentication occurred. Its value is a JSON number representing the number of
     * seconds from 1970-01-01T0:0:0Z as measured in UTC until the date/time.
     * nonce: String value used to associate a Client session with an ID Token, and to mitigate replay attacks. The value is
     * passed through unmodified from the Authentication Request to the ID Token.
     * acr: String specifying an Authentication Context Class Reference value that identifies the Authentication Context
     * Class that the authentication performed satisfied.
     * at_hash: Access token hash
     * @return
     */
     String getIDToken();

     List<String> getOptionalIdTokenClaims();

     String getAccessToken();
}
