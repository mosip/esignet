# Development and Integration with eSignet

This guide provides a step-by-step approach for developers who want to integrate their application as a **Relying Party (RP)** with **eSignet** (Go backend, v2.0.0+).

> **Migrating from the Java-based eSignet?** See [Changes from the Java Implementation](#changes-from-the-java-implementation) at the bottom — several endpoint paths and behaviours have changed.

### Prerequisites

Before integrating your Relying Party application with eSignet, ensure the following are in place:

| Requirement | Description |
|---|---|
| **Client registered with eSignet** | RP must be onboarded and issued a `client_id`. `client_secret` is not applicable. |
| **eSignet well-known endpoint access** | `/.well-known/jwks.json` and `/.well-known/openid-configuration` |
| **Client Authentication Method** | Only **private_key_jwt** is supported for token endpoint client authentication. Access to **RSA/EC Private Key** used by RP to sign the JWT during token request. Note: Should be securely stored and rotated periodically. |
| **Registered Redirect URI** | Must be pre-configured with eSignet (during onboarding). It may be a frontend page, a mobile app deeplink/custom scheme, or a backend endpoint. |
| **Scopes/Claims required by RP** | `openid` is mandatory. Optional scopes: `profile`, `email`, `phone`, or custom scopes if supported. Check `/.well-known/openid-configuration` for supported scopes and claims. |
| **Choose libraries for JWT Creation, Signing & OIDC Integration** | Since **private_key_jwt** authentication requires the RP to generate a signed JWT for token requests, use well-supported cryptographic and OIDC client libraries. Reference: https://openid.net/developers/certified-openid-connect-implementations/ |

> Always fetch the authorize, PAR, token, userinfo endpoint URL dynamically from `.well-known/openid-configuration`. This ensures your integration remains compatible even if environments or endpoints change.

### Step-by-Step Implementation

#### Step 1: Redirect User to eSignet Authorization Endpoint

Add a **Sign-in with eSignet** button to your login page that links to the authorize URL. A lightweight JavaScript plugin is available from eSignet to render this button automatically. By default, the plugin can be loaded from:

```
https://<eSignet-domain>/plugins/sign-in-button-plugin.js
```

The button may follow specific branding guidelines such as name, logo usage, color scheme, and size. These are typically defined by the Identity Provider. eSignet provides a UI Storybook that demonstrates recommended styles and customization options for relying party implementations: [UI Storybook](https://mosip.github.io/mosip-sdk/?path=/docs/javascript-sign-in-with-esignet--docs)

Additionally, `sign-in-button-plugin.js` provides **PAR** and **DPoP** support. Refer to the Storybook to know more details on how to configure and use the `par_callback` and `dpop_callback` parameters.

**eSignet Authorization Endpoint Specification**

```yaml
openapi: 3.1.0
info:
  title: eSignet
  version: '1.0'
servers:
  - url: 'https://esignet.collab.mosip.net/v1/esignet'
paths:
  /oauth2/authorize:
    get:
      tags:
        - OIDC
      summary: Authorization Endpoint
      description: |-
        This is the authorize endpoint of Open ID Connect (OIDC). The relying party applications will do a browser redirect to this endpoint with all required details passed as query parameters.

        This endpoint responds with a redirect (HTTP 302) to the UI application's login page, with `authId` and `executionId` query parameters identifying the authorization session and the underlying ThunderID flow execution respectively. The UI application then drives the authentication ceremony step by step via the "/flow/execute" endpoint. Once the flow reaches `flowStatus: COMPLETE`, the UI calls "/oauth2/auth/callback" with the signed assertion to obtain the authorization code redirect.

        **Authentication & Authorization**: None
      operationId: get-authorize
      parameters:
        - name: scope
          in: query
          description: Specifies what access privileges are being requested for Access Tokens. The scopes associated with Access Tokens determine what resources will be available when they are used to access OAuth 2.0 protected endpoints. OpenID Connect requests MUST contain the OpenID scope value.
          required: true
          schema:
            type: string
            examples:
              - openid profile
              - openid email
              - openid
        - name: response_type
          in: query
          description: 'The value set here determines the authorization processing flow. To use the Authorization Code Flow, the value should be configured to "code".'
          required: true
          schema:
            const: code
        - name: client_id
          in: query
          description: Valid OAuth 2.0 Client Identifier in the Authorization Server.
          required: true
          schema:
            type: string
            maxLength: 256
        - name: redirect_uri
          in: query
          description: Redirection URI to which the response would be sent. This URI must match one of the redirection URI values during the client ID creation.
          required: true
          schema:
            type: string
            format: uri
        - name: state
          in: query
          description: 'Opaque value used to maintain state between the request and the callback. Typically, Cross-Site Request Forgery (CSRF, XSRF) mitigation is done by cryptographically binding the value of this parameter with a browser cookie.'
          schema:
            type: string
            maxLength: 256
        - name: nonce
          in: query
          description: 'String value used to associate a Client session with an ID Token, and to mitigate replay attacks. The value is passed through unmodified from the Authentication Request to the ID Token.'
          schema:
            type: string
        - name: display
          in: query
          description: ASCII string value that specifies how the Authorization Server displays the authentication and consent user interface pages to the end user.
          schema:
            type: string
            enum:
              - page
              - popup
              - touch
              - wap
        - name: prompt
          in: query
          description: Space delimited case-sensitive list of ASCII string values that specifies whether the Authorization Server prompts the End-User for re-authentication and consent.
          schema:
            type: string
            enum:
              - consent
            examples:
              - consent
        - name: max_age
          in: query
          description: 'Maximum Authentication Age. Specifies the allowable elapsed time in seconds since the last time the end user was actively authenticated. When max_age is used, the ID Token returned MUST include an auth_time claim value.'
          schema:
            type: number
        - name: ui_locales
          in: query
          description: 'End-user''s preferred languages and scripts for the user interface, represented as a space-separated list of BCP47 [RFC5646] language tag values, ordered by preference.'
          schema:
            type: string
        - name: acr_values
          in: query
          description: 'Requested Authentication Context Class Reference values. Space-separated string that specifies the acr values that the Authorization Server is being requested to use for processing this Authentication Request, with the values appearing in order of preference. Unknown ACR values are ignored. If none of the provided acr values match registered values, all registered ACRs are considered.'
          schema:
            type: string
            enum:
              - 'mosip:idp:acr:password'
              - 'mosip:idp:acr:generated-code'
              - 'mosip:idp:acr:biometrics'
              - 'mosip:idp:acr:knowledge'
        - name: claims_locales
          in: query
          description: 'End-User''s preferred languages and scripts for Claims being returned, represented as a space-separated list of BCP47 [RFC5646] language tag values, ordered by preference.'
          schema:
            type: string
        - name: claims
          in: query
          description: This parameter is used to request specific claims to be returned. The value is a JSON object listing the requested claims. The claims parameter value is represented in an OAuth 2.0 request as UTF-8 encoded JSON.
          schema:
            type: string
        - name: code_challenge
          in: query
          description: 'A challenge derived from the code_verifier. Required if it is a VC scoped request.'
          schema:
            type: string
        - name: code_challenge_method
          in: query
          description: 'A method that was used to derive code challenge. Required if code_challenge is provided. Only S256 is accepted.'
          schema:
            const: S256
            type: string
        - name: id_token_hint
          in: query
          description: ID Token previously issued by the Authorization Server being passed as a hint about the End-User's current or past authenticated session with the Client.
          schema:
            type: string
        - name: request_uri
          in: query
          description: 'The request URI corresponding to the pushed authorization request posted. This URI is a single-use reference to the respective request data in the subsequent authorization request.'
          schema:
            type: string
      responses:
        '302':
          description: |-
            Redirect to the UI application's login page with `authId` and `executionId` query parameters, or to the client's registered redirect_uri on error.
          headers:
            Location:
              schema:
                type: string
        '400':
          description: 'Bad Request — malformed or duplicate query parameters.'
      security: []
```

**Key query parameters:**
- `scope` (required) — e.g., `openid profile`
- `response_type` (required) — must be `code`
- `client_id` (required)
- `redirect_uri` (required) — must match one of the pre-registered URIs
- `state`, `nonce`, `display`, `prompt`, `max_age`, `ui_locales`, `acr_values`, `claims_locales`, `claims`, `code_challenge`, `code_challenge_method`, `id_token_hint`, `request_uri`

Supported `acr_values`:
- `mosip:idp:acr:generated-code` — OTP sent to the user's registered mobile/email
- `mosip:idp:acr:password` — static password
- `mosip:idp:acr:biometrics` — biometric capture via MOSIP SBI device
- `mosip:idp:acr:knowledge` — Knowledge-Based Identity (full name + date of birth)

Notes:
- The `redirect_uri` provided must be an absolute, fully qualified URL. Matching against registered URIs may use wildcards/patterns.
- `prompt=consent` forces a consent screen on every auth flow; without it, consent is shown only on first authorization or after the granted consent expires.

#### PAR Support in Authorization Request

eSignet supports **Pushed Authorization Requests (PAR)** per OAuth 2.0 standards. The RP first submits authorization request parameters directly to eSignet via the **back-channel PAR endpoint**, receives a `request_uri`, and uses it in the authorize URL.

PAR enforcement is **per-client**: when a client is registered with `require_pushed_authorization_requests: true` in its `additionalConfig`, that client must use PAR. For other clients, PAR is optional but recommended for FAPI 2.0 compliance.

**eSignet PAR Endpoint Specification**

```yaml
openapi: 3.1.0
info:
  title: eSignet
  version: '1.0'
servers:
  - url: 'https://esignet.collab.mosip.net/v1/esignet'
paths:
  /oauth2/par:
    parameters:
      - name: DPoP
        in: header
        description: 'A DPoP proof JWT [RFC7519] signed (using JWS [RFC7515]) with a private key chosen by the client. For more details refer to https://datatracker.ietf.org/doc/html/rfc9449#section-4.2'
        schema:
          type: string
    post:
      tags:
        - OIDC
      summary: PAR Endpoint
      description: |-
        **PAR - Pushed Authorization Request**

        1. Message body of this request must be formatted with x-www-form-urlencoded using a character encoding of UTF-8.
        2. The client must add its authentication credentials to the request body using the same rules as for the token endpoint request.
        3. Authenticate the client in the same way as at the token endpoint.
        4. Reject the request if the request_uri authorization request parameter is provided.
        5. Validate the request parameters in the body as they would be validated in the oauth-details request.
        6. Upon successful verification, the server MUST generate a request URI and provide it in the response with a 201 HTTP status code.

        **request_uri** should be in this format: `urn:ietf:params:oauth:request_uri:<secure random alpha-numeric string with max length of 25>`

        Successfully verified request parameters are stored in the PAR cache with request_uri as the key and a configurable TTL.
        Default TTL: **3600 seconds** (configurable via `MOSIP_ESIGNET_OAUTH_PAR_EXPIRY_SECONDS`).

        **Not supported:**
          1. Client authentication parameters in the PAR request header (body only).
          2. The request parameter as defined in JAR [RFC9101].
          3. API rate limiting (left to infrastructure).
          4. Use of non-registered redirect URIs.
      operationId: post-oauth-par
      requestBody:
        content:
          application/x-www-form-urlencoded:
            schema:
              type: object
              required:
                - scope
                - response_type
                - client_id
                - redirect_uri
                - client_assertion
                - client_assertion_type
              properties:
                scope:
                  type: string
                  description: 'Specifies what access privileges are being requested. Space-separated list; must contain openid.'
                  examples:
                    - openid
                    - openid profile
                    - openid email phone
                response_type:
                  type: string
                  description: 'Value that determines the authorization processing flow. Must be code.'
                  enum:
                    - code
                client_id:
                  type: string
                  description: OAuth 2.0 Client Identifier valid at the Authorization Server.
                  example: 785b806d0e594657b05aabdb30fff8a4
                redirect_uri:
                  type: string
                  description: Redirection URI to which the response will be sent. Must exactly match one of the pre-registered Redirection URI values for the Client.
                  examples:
                    - 'https://relying-party-portal/callback'
                    - 'app://oauth/redirect'
                state:
                  type: string
                  description: Client state value echoed back on redirect.
                nonce:
                  type: string
                  description: Client nonce value echoed back in the ID token.
                display:
                  type: string
                  description: ASCII string value that specifies how the Authorization Server displays the authentication and consent user interface pages to the End-User.
                prompt:
                  type: string
                  description: 'Space delimited, case-sensitive list of ASCII string values that specifies whether the Authorization Server prompts the End-User for re-authentication and consent.'
                  examples:
                    - consent
                acr_values:
                  type: string
                  description: |-
                    Space-separated ACR values. Unknown ACR values are ignored. Only registered ACR values are considered.
                    If none of the provided values match registered ACR values, all registered ACR values are considered.
                  examples:
                    - 'mosip:idp:acr:biometrics'
                    - 'mosip:idp:acr:password mosip:idp:acr:biometrics'
                claims:
                  type: string
                  description: 'JSON object listing the requested claims (UTF-8 encoded).'
                max_age:
                  type: number
                  description: 'Maximum Authentication Age. Specifies the allowable elapsed time in seconds since the last time the End-User was actively authenticated.'
                claims_locales:
                  type: string
                  description: 'End-User''s preferred languages and scripts for Claims being returned, as a space-separated BCP47 list.'
                ui_locales:
                  type: string
                  description: 'End-User''s preferred languages and scripts for the user interface, as a space-separated BCP47 list.'
                code_challenge:
                  type: string
                  description: 'A challenge derived from the code_verifier, to be verified against later.'
                code_challenge_method:
                  const: S256
                  type: string
                  description: 'A method that was used to derive code challenge. Only S256 is accepted.'
                id_token_hint:
                  type: string
                  description: ID Token previously issued by the Authorization Server being passed as a hint about the End-User's current or past authenticated session with the Client.
                client_assertion_type:
                  const: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
                  type: string
                  description: Type of the client assertion part of this request.
                client_assertion:
                  type: string
                  description: 'A single JWT signed with the client''s private key (private_key_jwt). See Token Endpoint for the required JWT payload claims.'
                dpop_jkt:
                  type: string
                  description: 'The value of the dpop_jkt authorization request parameter is the JWK Thumbprint [RFC7638] of the proof-of-possession public key using the SHA-256 hash function. Binds this PAR request to a specific DPoP key before token exchange.'
            examples:
              example-1:
                value:
                  client_id: WMX5pO6dYdCFR3iaVWGclVPNxTNSADDv
                  scope: openid
                  response_type: code
                  redirect_uri: 'https://fastlane.com/homepage'
                  display: popup
                  prompt: consent
                  acr_values: 'mosip:idp:acr:generated-code'
                  claims: '{"userinfo":{"name":{"essential":true},"phone_number":{"essential":true},"email":{"essential":false}},"id_token":{}}'
                  nonce: 973eieljzng
                  state: eree2311
                  claims_locales: en
                  code_challenge: UK95aVX_y3R44DF3hssd3wATvtZmO_WejE0P33-pwTs
                  code_challenge_method: S256
                  client_assertion: eyJ0eXAiOiJKV1QiLCJhbGciOiJQUzI1NiJ9...
                  client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
      responses:
        '201':
          description: CREATED
          content:
            application/json:
              schema:
                type: object
                properties:
                  request_uri:
                    type: string
                    description: 'The request URI corresponding to the authorization request posted. This URI is a single-use reference to the respective request data in the subsequent authorization request.'
                  expires_in:
                    type: number
                    description: 'A JSON number representing the lifetime of the request URI in seconds. Default: 3600.'
              examples:
                example-1:
                  value:
                    request_uri: 'urn:ietf:params:oauth:request_uri:6esc_11ACC5bwc014ltc14eY22c'
                    expires_in: 3600
        '400':
          description: Bad Request
          headers:
            Cache-Control:
              schema:
                type: string
              description: 'no-cache, no-store'
          content:
            application/json:
              schema:
                type: object
                required:
                  - error
                  - error_description
                properties:
                  error:
                    type: string
                    enum:
                      - invalid_request
                      - invalid_client_id
                      - invalid_redirect_uri
                      - invalid_scope
                      - invalid_acr
                      - invalid_response_type
                      - invalid_display
                      - invalid_prompt
                  error_description:
                    type: string
      security: []
```

Not supported: client authentication in PAR request header, JAR (RFC 9101) `request` parameter, non-registered redirect URIs.

After receiving the `request_uri`, redirect the user's browser to:

```
GET /oauth2/authorize?client_id=<client_id>&request_uri=<request_uri>
```

#### DPoP Support

eSignet supports **Demonstrating Proof of Possession (DPoP)** per [RFC 9449](https://datatracker.ietf.org/doc/html/rfc9449).

DPoP enforcement is **per-client**: when a client is registered with `dpop_bound_access_tokens: true` in its `additionalConfig`, all token requests for that client must include a valid DPoP proof, and the issued access token will carry `token_type: DPoP`. For clients without this flag, DPoP is not required.

A DPoP proof is a JWT [RFC 7519] signed (using JWS [RFC 7515]) with a private key chosen by the client. For more details refer to [RFC 9449 §4.2](https://datatracker.ietf.org/doc/html/rfc9449#section-4.2).

**DPoP proof JWT structure:**

| Part | Field | Value |
|---|---|---|
| Header | `typ` | `dpop+jwt` |
| Header | `alg` | One of: ES256, PS256, ES384, ES512, EdDSA, RS256 |
| Header | `jwk` | Public key JWK (must not include a `kid`) |
| Payload | `jti` | Unique string per request — prevents replay |
| Payload | `htm` | HTTP method in uppercase (e.g. `POST`) |
| Payload | `htu` | Endpoint URI without query string or fragment |
| Payload | `iat` | Current Unix time — must be within ±60 s of server time; an additional 10 s clock-skew leeway is applied |
| Payload | `nonce` | Server-supplied nonce, if previously returned via `DPoP-Nonce` response header |

Send the DPoP proof in the `DPoP` HTTP header on:
- `POST /oauth2/par` (when using PAR with a DPoP-bound client)
- `POST /oauth2/token` (required for DPoP-bound clients)
- `GET /oauth2/userinfo` (required when using a DPoP-bound access token; send `Authorization: DPoP <token>`)

**DPoP nonce**: if the server returns error code `use_dpop_nonce`, a `DPoP-Nonce` response header will be present. Include its value in the `nonce` claim of the next DPoP proof and retry.

> **Notes:**
>
> - `scope` defines what user attributes the RP can request.
> - `claims` enables the RP to decide which user attributes are optional and which are mandatory.
> - Always generate a fresh `state` and `nonce` on every authorization request to prevent replay attacks and CSRF.
> - `acr_values` defines authentication method options. The RP must choose based on the required assurance level.
> - The `redirect_uri` provided must be an absolute, fully qualified URL (without any wildcard or regex). This URI is then matched against the redirect URIs stored in the eSignet database for the same Client ID. Since the stored URIs may include wildcards or patterns, the matching process allows partial or regex-based checks rather than requiring an exact match.
> - The `prompt=consent` parameter should be used if the Relying Party (RP) requires eSignet to present a consent screen to the user during every authentication flow. If this parameter is omitted, consent is shown only during the first authorization request, and will be shown again only when the previously granted consent expires (expiry duration is configured per client).

#### Step 2: User Authenticates and Consents on eSignet Screen

eSignet handles:

🔐 Authentication with the chosen authentication method. E.g.: OTP / Biometrics / Wallet

🔐 Consent screen (only if claims are shared)

**Successful authentication**

If authentication succeeds, the user is redirected to the `redirect_uri` specified in the authorization request, along with an authorization code returned in the `code` query parameter.

**Failed authentication**

If authentication fails, the user is redirected to the same `redirect_uri`, but with an error code returned in the `error` query parameter. It is the RP's responsibility to handle the error appropriately.

#### Step 3: Exchange Code for Tokens

Exchange the authorization code for an Access Token using the token endpoint. It is always suggested to use the token endpoint URL published in the `.well-known/openid-configuration`. This ensures your integration remains compatible even if environments or endpoints change.

Refer the below for token endpoint details:

**eSignet Token Endpoint Specification**

```yaml
openapi: 3.1.0
info:
  title: eSignet
  version: '1.0'
servers:
  - url: 'https://esignet.collab.mosip.net/v1/esignet'
paths:
  /oauth2/token:
    parameters:
      - name: DPoP
        in: header
        description: 'A DPoP proof JWT [RFC7519] signed (using JWS [RFC7515]) with a private key chosen by the client. For more details refer to https://datatracker.ietf.org/doc/html/rfc9449#section-4.2'
        schema:
          type: string
    post:
      tags:
        - OIDC
      summary: Token Endpoint
      description: |-
        Once the client / relying party application receives the authorization code through redirect, this OIDC compliant endpoint will be called from the relying party backend application to get the ID and access token.

        1. The only supported client authentication methods: **private_key_jwt**
        2. clientAssertion is a signed JWT with the Client's private key. The corresponding public key should be shared with eSignet during the OIDC client registration process.
        3. clientAssertion JWT payload must contain the following claims:

        **iss*** (Issuer): This MUST contain the client_id of the OAuth Client.
        **sub*** (Subject): This MUST contain the client_id of the OAuth Client.
        **aud*** (Audience): Value that identifies the authorization server as an intended audience.
        **exp*** (Expiration): Time on or after which the ID token MUST NOT be accepted for processing.
        **iat***: Time at which the JWT was issued.
        **jti*** (JWT ID): This MUST be unique for each client assertion generated.

        **Note**: The Client Assertion JWT can contain other Claims. Any Claims used that are not understood WILL be ignored.
      operationId: post-token
      requestBody:
        content:
          application/x-www-form-urlencoded:
            schema:
              type: object
              required:
                - grant_type
                - code
                - client_assertion_type
                - client_assertion
                - redirect_uri
              properties:
                grant_type:
                  const: authorization_code
                  type: string
                  description: Authorization code grant type.
                code:
                  type: string
                  description: 'Authorization code, sent as query param in the client''s redirect URI.'
                client_id:
                  type: string
                  description: Client Id of the OIDC client. Optional — the client is identified by the client_assertion.
                client_assertion_type:
                  const: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
                  type: string
                  description: Type of the client assertion part of this request.
                client_assertion:
                  type: string
                  description: 'Private key signed JWT. JWT payload structure is defined above as part of the request description.'
                redirect_uri:
                  type: string
                  description: 'Valid client redirect_uri. Must be same as the one sent in the authorize call.'
                code_verifier:
                  type: string
                  description: |-
                    A cryptographically random string that is used to correlate the authorization request to the token request (PKCE).
            examples:
              Example 1:
                value:
                  grant_type: authorization_code
                  code: tyemdnjdfornfedg
                  client_id: WMX5pO6dYdCFR3iaVWGclVPNxTNSADDv
                  client_assertion_type: 'urn:ietf:params:oauth:client-assertion-type:jwt-bearer'
                  client_assertion: eyJ0eXAiOiJKV1QiLCJhbGciOiJQUzI1NiJ9...
                  redirect_uri: 'https://fastlane.com/homepage'
                  code_verifier: MN1Q0nNAKkqOu5EaNBKf2gYD4maYv9ZxLd-48N2_kTM
      responses:
        '200':
          description: OK
          headers:
            Cache-Control:
              schema:
                const: no-store
            Pragma:
              schema:
                const: no-cache
          content:
            application/json:
              schema:
                type: object
                required:
                  - access_token
                  - token_type
                  - expires_in
                properties:
                  token_type:
                    type: string
                    enum:
                      - Bearer
                      - DPoP
                    description: 'The type of the access token, set to either Bearer or DPoP'
                  access_token:
                    type: string
                    description: 'The access token in JWT format (RFC 9068). This token will be used to call the UserInfo endpoint.'
                  id_token:
                    type: string
                    description: |-
                      Identity token in JWT format. Will have the below claims in the payload:
                      iss, sub (PSUT), aud, exp, iat, auth_time, nonce, acr, at_hash.
                      It is non-null only in OIDC flow (when openid scope was requested). Note: eSignet does NOT include user claims in the ID token.
                  expires_in:
                    type: number
                    description: 'The lifetime of the access token, in seconds. Default: 3600.'
                    format: duration
                  c_nonce:
                    type: string
                    description: JSON string containing a nonce to be used to create a proof of possession of key material when requesting a Verifiable Credential.
                  c_nonce_expires_in:
                    type: number
                    description: JSON integer denoting the lifetime in seconds of the c_nonce.
              examples:
                Example 1:
                  value:
                    token_type: Bearer
                    access_token: eyJraWQiOiJLT19tVHBfc1QwemxGRVVkX25UdGhmbzl0RT...
                    id_token: eyJraWQiOiJlU0dtNm5LcGppUHRJMnAzbVVWNHBWWm9nY0VH...
                    expires_in: 3600
                    c_nonce: Ct9rpQFb96QSSwgHAfDO
                    c_nonce_expires_in: 40
        '400':
          description: Bad Request
          content:
            application/json:
              schema:
                type: object
                required:
                  - error
                properties:
                  error:
                    type: string
                    enum:
                      - invalid_transaction
                      - invalid_assertion
                      - invalid_redirect_uri
                      - invalid_input
                      - unknown_error
                      - invalid_request
                      - invalid_grant
                      - invalid_assertion_type
                      - invalid_pkce_code_verifier
                      - unsupported_pkce_challenge_method
                      - pkce_failed
                      - invalid_dpop_proof
                      - use_dpop_nonce
                    description: The error code.
                  error_description:
                    type: string
                    description: Optional text providing additional information about the error.
      security: []
```

Only supported client authentication method: **private_key_jwt**

Required `client_assertion` JWT claims:
- `iss` — must be `client_id`
- `sub` — must be `client_id`
- `aud` — authorization server's token endpoint URL
- `exp`, `iat`, `jti` — expiry, issued-at, and unique JWT ID

Request body (form-encoded) required fields: `grant_type` (= `authorization_code`), `code`, `client_assertion_type`, `client_assertion`, `redirect_uri`

Optional: `client_id`, `code_verifier`

DPoP header: `DPoP` — proof JWT per RFC 9449. Required when the client is registered with `dpop_bound_access_tokens: true`.

Successful response (HTTP 200):
```json
{
  "token_type": "Bearer",
  "access_token": "<JWT>",
  "id_token": "<JWT>",
  "expires_in": 3600,
  "c_nonce": "<string>",
  "c_nonce_expires_in": 40
}
```

`token_type` is `"DPoP"` instead of `"Bearer"` when the client is DPoP-bound. `id_token` is present only in OIDC flows (when `openid` scope was included).

Error codes (HTTP 400): `invalid_transaction`, `invalid_assertion`, `invalid_redirect_uri`, `invalid_input`, `unknown_error`, `invalid_request`, `invalid_assertion_type`, `invalid_pkce_code_verifier`, `unsupported_pkce_challenge_method`, `pkce_failed`, `invalid_dpop_proof`, `use_dpop_nonce`

> **Note:** No refresh tokens are issued by default (`renew_on_grant: false`). Token revocation is also disabled by default. RPs must re-initiate the authorization flow when the access token expires.

#### Step 4: Verify and Parse the Access and ID Token

Access tokens generated by eSignet follow [RFC 9068] JSON Web Token (JWT) Profile for OAuth 2.0 Access Tokens.

**eSignet JWKS Endpoint Specification**

```yaml
openapi: 3.1.0
info:
  title: eSignet
  version: '1.0'
servers:
  - url: 'https://esignet.collab.mosip.net/v1/esignet'
paths:
  /oauth2/jwks:
    get:
      tags:
        - OIDC
      summary: JSON Web Key Set Endpoint
      description: |-
        Endpoint to fetch all the public keys of the eSignet server. Returns public key set in the JWKS format.
        Use the `jwks_uri` value from `/.well-known/openid-configuration` to locate this endpoint.
        Also accessible at `/.well-known/jwks.json` via the reverse proxy in production deployments.
        Cache the JWKS with a TTL and refresh on kid miss.
      operationId: get-jwks
      responses:
        '200':
          description: OK
          content:
            application/json:
              schema:
                type: object
                properties:
                  keys:
                    type: array
                    items:
                      type: object
                      required:
                        - kid
                        - use
                        - kty
                        - e
                        - 'n'
                        - x5t#S256
                        - x5c
                        - exp
                      properties:
                        kid:
                          type: string
                          description: The certificate's Key ID.
                        use:
                          const: sig
                          description: 'How the Key is used. Valid value: sig'
                        kty:
                          type: string
                          description: 'Cryptographic algorithm family for the key pair. RSA (default), EC, or OKP depending on the configured signing key.'
                        e:
                          type: string
                          description: 'RSA Key value (exponent) for Key blinding. Present on RSA keys.'
                        'n':
                          type: string
                          description: 'RSA modulus value. Present on RSA keys.'
                        crv:
                          type: string
                          description: 'Curve name. Present on EC keys (e.g. P-256, P-384) and OKP keys (e.g. Ed25519).'
                        x:
                          type: string
                          description: 'EC/OKP public key x-coordinate (base64url). Present on EC and OKP keys.'
                        y:
                          type: string
                          description: 'EC public key y-coordinate (base64url). Present on EC keys.'
                        x5t#S256:
                          type: string
                          description: SHA-256 thumbprint of the certificate.
                        x5c:
                          type: array
                          description: Certificate chain to validate the OAuth server trust.
                          items:
                            type: string
                        exp:
                          type: string
                          description: Expiry datetime of the key in ISO 8601 format.
                          format: date-time
                          examples:
                            - '2026-02-05T13:43:07.979Z'
              examples:
                Example 1:
                  value:
                    keys:
                      - kty: RSA
                        x5t#S256: Apdg6S6RmjkiBjvEUYYCa-KF-yrJbl6x1wzKrc4smt0
                        e: AQAB
                        use: sig
                        kid: GTERCOmvD5PlZ65lo2Na-4Udc2xgA6EkaHuEsnMevRA
                        x5c:
                          - MIIDvTCCAqWgAwIBAgIIvSVFZ0ayWuswDQYJ...
                        exp: '2026-02-05T13:43:07.979Z'
                        'n': rqUzQUe5G3wtFfBQTp1YIynICEleAXm1rJkDb04jOEqOJ...
      security: []
```

Validate JWT signature using public key published on eSignet `/.well-known/jwks.json` (served via the `/oauth2/jwks` endpoint; also accessible at `/.well-known/jwks.json` via reverse proxy in production). Match the public key using the `kid` header claim from the token.

Validate `iss`, `exp`, `iat` in both the tokens.

Validate `aud` in both tokens — note that the expected value differs:
- **ID token** `aud` — must equal the RP's `client_id`
- **Access token** `aud` — must equal the resource server identifier (not the `client_id`)

Additionally validate `auth_time`, `nonce`, `acr`, `at_hash` in the ID token.

Default signing algorithm: **PS256** (RSASSA-PSS with SHA-256). Supported signing algorithms: PS256, ES256, ES256K, EdDSA. Always check the token's `alg` header and reject tokens signed with an algorithm outside this set.

ID token claims:

| Claim | Description |
|---|---|
| `iss` | Issuer URL |
| `sub` | Pairwise pseudonymous subject identifier (PSUT — Partner Specific User Token) |
| `aud` | Client ID |
| `exp`, `iat` | Expiry and issued-at times |
| `auth_time` | Time of user authentication |
| `nonce` | Echo of the nonce from the authorization request |
| `acr` | Authentication Context Class Reference used |
| `at_hash` | Hash of the access token |

Access token claims (RFC 9068):

| Claim | Description |
|---|---|
| `sub` | PSUT (same pairwise identifier as ID token) |
| `iss` | Issuer URL |
| `aud` | Resource server identifier |
| `exp`, `iat` | Expiry and issued-at times |
| `client_id` | The RP's client ID |
| `scope` | Granted scopes |
| `cnf.jkt` | DPoP public key thumbprint — present only on DPoP-bound tokens |

> **Key Notes:**
>
> - eSignet does **not** support user claims in the ID token.
> - The `sub` claim in the ID token and access token is a **pairwise pseudonymous identifier** (PSUT — Partner Specific User Token).
> - Avoid storing ID Tokens or Access Tokens in browser `localStorage` or `sessionStorage`. If an attacker gains access to the browser context, they can extract the tokens and impersonate the user.
> - Instead, it is recommended to perform the token exchange and validation on the RP backend and maintain a secure server-side session. The user's browser should only store a short-lived, HTTP-only, SameSite cookie that maps to this session for a stronger protection model.

#### Step 5: Get Consented User Claims Using Access Token

If the Relying Party (RP) needs user attributes (e.g., eKYC data), the developer must implement a call to the userinfo endpoint using the Access Token obtained during token exchange.

Always fetch the endpoint URL dynamically from `.well-known/openid-configuration`. This ensures your integration remains compatible even if environments or endpoints change.

The userinfo response is returned as a **signed JWT (JWS)** by default. The RP must validate the JWT signature using the public keys from `/.well-known/jwks.json`.

If required, the RP can be configured to request an **encrypted (JWE)** userinfo response for additional security. When the client is registered with `userinfo_response_type: JWE` in `additionalConfig`, the response is a nested JWT — signed using JWS and then encrypted using JWE.

> **Key Notes:**
>
> - The `sub` claim in the userinfo JWT will match the `sub` present in both the Access Token and ID Token, ensuring user identity continuity.
> - Use the `claims_locales` parameter in the authorize request if user attributes need to be returned in a specific language. (This is supported only when the identity system maintains multilingual claims.)

Refer the below for userinfo endpoint details:

**eSignet UserInfo Endpoint Specification**

```yaml
openapi: 3.1.0
info:
  title: eSignet
  version: '1.0'
servers:
  - url: 'https://esignet.collab.mosip.net/v1/esignet'
paths:
  /oauth2/userinfo:
    parameters:
      - name: DPoP
        in: header
        description: 'A DPoP proof JWT [RFC7519] signed (using JWS [RFC7515]) with a private key chosen by the client. For more details refer to https://datatracker.ietf.org/doc/html/rfc9449#section-4.2'
        schema:
          type: string
    get:
      tags:
        - OIDC
      summary: UserInfo Endpoint
      description: |-
        Once the access token is received via the token endpoint, relying party backend application can call this OIDC compliant endpoint to request for the user claims.

        Consented user claims will be returned as a JWT. The response format depends on the client's registration:
        - **Default (JWS)**: A signed JWT only. Validate the signature using public keys from `/.well-known/jwks.json`.
        - **JWE (opt-in)**: When the client is registered with `userinfo_response_type: JWE` in additionalConfig, the response is a nested JWT — signed using JWS and then encrypted using JWE. First decrypt with the client's encryption private key (key algorithm from the client's registered encPublicKey JWK `alg` field, e.g. RSA-OAEP or RSA-OAEP-256; content encryption is always A256GCM), then validate the inner JWS signature.

        **Example**: Assuming the below are the requested claims by the relying party

        name : { "essential" : true }
        phone_number: { "essential" : true }

        **Response 1**: When consent is provided for both name and phone_number:
        { "name" : "John Doe", "phone_number" : "033456743" }

        **Response 2**: When consent is provided for only name:
        { "name" : "John Doe" }

        **Response 3**: When Claims are requested with claims_locales : "en fr"
        { "name#en" : "John Doe", "name#fr" : "Jean Doe", "phone_number" : "033456743" }

        **Supported User Info Claims**
        - sub — Partner Specific User Token (PSUT)
        - name, address, gender, birthdate, profile photo, email, phone_number, locale
        - Custom: individual_id (UIN, perceptual VID, or temporary VID)
      operationId: get-userinfo
      responses:
        '200':
          description: OK
          content:
            application/jwt:
              schema:
                type: string
                description: |-
                  JWT containing consented user claims.
                  Default: a signed JWS compact JWT — validate signature with public keys from /.well-known/jwks.json.
                  When client registered with userinfo_response_type=JWE: a nested JWT (signed JWS then encrypted JWE).
                format: jwt
              examples:
                Example 1:
                  value: eyJraWQiOiJlU0dtNm5LcGppUHRJMnAzbVVWNHBWWm9nY0VHaExMV2dCNXNuUzNvbUNzIiwiYWxnIjoiUFMyNTYifQ...
        '401':
          description: Unauthorized
          headers:
            WWW-AUTHENTICATE:
              schema:
                type: string
                enum:
                  - invalid_token
                  - unknown_error
                  - invalid_dpop_proof
                  - use_dpop_nonce
              description: 'Bearer error=invalid_token, error_description=A user info request was made with an access token that was not recognized.'
      security:
        - Authorization-Bearer: []
        - Authorization-DPoP: []
```

Authentication: Bearer (access token) or DPoP-bound access token.

- Standard flow: `Authorization: Bearer <access_token>`
- DPoP-bound flow: `Authorization: DPoP <access_token>` with `DPoP: <proof>` header (proof bound to this endpoint URL and `GET` method)

Response (HTTP 200): `application/jwt` — a signed JWS JWT by default; a nested JWT (signed with JWS, then encrypted with JWE) when the client is registered with `userinfo_response_type: JWE`.

Error (HTTP 401): `WWW-AUTHENTICATE` header with values `invalid_token`, `unknown_error`, `invalid_dpop_proof`, `use_dpop_nonce`.

Supported claims in userinfo JWT:
- `sub` (Partner Specific User Token — PSUT)
- `name`, `address`, `gender`, `birthdate`, `profile photo`, `email`, `phone_number`, `locale`
- Custom: `individual_id` (UIN, perceptual VID, or temporary VID)

Sample userinfo JWT payload:
```json
{
  "sub": "63EBC25D699305A26EE740A955852EAB2E6527BFF2F5E9E5562B502DACECD020",
  "address": {
    "street_address": "#991, 47 Street, 6 block",
    "country": "India",
    "locality": "Bengaluru",
    "region": "Bengaluru Urban",
    "postal_code": "14022"
  },
  "gender": "Male",
  "phone_number": "91000395660",
  "name": "Manoj",
  "email": "manoj@mail.com"
}
```

Sample userinfo JWT payload (with bilingual claims):
```json
{
  "sub": "63EBC25D699305A26EE740A955852EAB2E6527BFF2F5E9E5562B502DACECD020",
  "name#en": "Manoj",
  "address#en": { "formatted#en": "#991, 47 Street, 6 block" },
  "phone_number": "91600395660",
  "gender#kn": "ಗಂಡು",
  "name#kn": "ಮನೋಜ್",
  "address#kn": { "formatted#kn": "#991, 47 ಸ್ಟ್ರೀಟ್, 6 ಬ್ಲಾಕ್" },
  "gender#en": "Male",
  "email": "manoj@mail.com"
}
```

---

## Changes from the Java Implementation

The following documents what changed, what is new, and what is confirmed unchanged when moving from the Java-based eSignet to the Go backend (eSignet Thunder v2.0.0+). Each item is verified against the Go source.

### Changed

| Area | Java documentation | Go implementation |
|---|---|---|
| Authorization endpoint path | `/authorize` | `/oauth2/authorize` |
| Authorization endpoint response | HTTP 200 — loads JS application HTML | HTTP 302 — redirects to UI login page with `authId` and `executionId` parameters |
| Token endpoint path | `/oauth/v2/token` | `/oauth2/token` |
| Userinfo endpoint path | `/oidc/userinfo` | `/oauth2/userinfo` |
| JWKS endpoint path | `/.well-known/jwks.json` | `/oauth2/jwks` — also accessible at `/.well-known/jwks.json` via reverse proxy in production |
| PAR endpoint path | `/oauth/par` | `/oauth2/par` |
| Default token signing algorithm | RS256 (typical Java default) | **PS256** (RSASSA-PSS with SHA-256). Supported set: PS256, ES256, ES256K, EdDSA. RS256 is available in the underlying library but excluded from the server's default supported signing algorithms. |
| Userinfo response format | Always a signed JWS wrapped in JWE (nested JWT — sign-then-encrypt) | **JWS only** by default (signed, not encrypted). JWE is opt-in per-client: set `userinfo_response_type: JWE` in the client's `additionalConfig`. Encryption algorithm from the client's `encPublicKey` JWK `alg` field; content encryption is always `A256GCM`. |
| PAR `expires_in` | 10 seconds (in the published example) | Default **3600 seconds** (configurable via `MOSIP_ESIGNET_OAUTH_PAR_EXPIRY_SECONDS`) |
| PAR enforcement | Described as a general option any RP can use | Per-client: set `require_pushed_authorization_requests: true` in `additionalConfig` to require PAR for that client. Other clients may use PAR optionally. |
| DPoP enforcement | Described as a general option | Per-client: set `dpop_bound_access_tokens: true` in `additionalConfig`. Server-wide default is DPoP not required. |
| Supported ACR values | 7 values including `static-code`, `linked-wallet`, `id-token` | 4 values active in the default flow: `generated-code`, `password`, `biometrics`, `knowledge` |
| `phone` claim name | `phone` | `phone_number` (corrected to the OIDC standard claim name) |

### New in the Go Implementation

| Behaviour | Details |
|---|---|
| No refresh tokens | `renew_on_grant: false` by default. RPs must restart the authorization flow when the access token expires. |
| Token revocation disabled | Token revocation is disabled by default. |
| `c_nonce` and `c_nonce_expires_in` in token response | Present in every token response for VCI (Verifiable Credential Issuance). Not needed for standard OIDC flows. |
| DPoP nonce support | When the server returns `use_dpop_nonce`, a `DPoP-Nonce` response header carries the required nonce value. Include it in the `nonce` claim of the next DPoP proof and retry. |
| `dpop_jkt` on PAR | The PAR endpoint accepts `dpop_jkt` (JWK Thumbprint, SHA-256) to bind the PAR request to a specific DPoP public key before token exchange. |
| Optional ID token JWE | The ID token can optionally be encrypted: set `id_token_response_type: JWE` in `additionalConfig`. Default is plain JWS. |
| `cnf.jkt` claim in access token | DPoP-bound access tokens carry the DPoP public key thumbprint in `cnf.jkt` per RFC 9449. |

### Confirmed Unchanged from the Java Implementation

| Behaviour |
|---|
| Only `private_key_jwt` is supported for client authentication at the token endpoint |
| Required `client_assertion` JWT claims: `iss`, `sub`, `aud`, `exp`, `iat`, `jti` |
| Token response shape: `access_token`, `id_token`, `token_type`, `expires_in` |
| `token_type` is `"Bearer"` for standard flows and `"DPoP"` for DPoP-bound flows |
| Authorization code grant type only (`grant_type: authorization_code`) |
| `sub` claim is pairwise pseudonymous (PSUT — Partner Specific User Token) in both the ID token and access token |
| ID token does **not** contain user claims — user claims are available only from the userinfo endpoint |
| PKCE supported; only `S256` is accepted as `code_challenge_method` |
| `prompt=consent` forces a fresh consent screen on every authorization request |
| PAR `client_assertion` uses the same `private_key_jwt` format as the token endpoint |
| DPoP implemented per RFC 9449; error codes `invalid_dpop_proof` and `use_dpop_nonce` both present |
| Always fetch endpoint URLs from `/.well-known/openid-configuration` — never hardcode paths |