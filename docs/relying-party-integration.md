# Development and Integration with eSignet

This guide provides a step-by-step approach for developers who want to integrate their application as a **Relying Party (RP)** with **eSignet** (Go backend, v2.0.0+).

> **Migrating from the Java-based eSignet?** See [Changes from the Java Implementation](#changes-from-the-java-implementation) at the bottom — several endpoint paths and behaviours have changed.

## Prerequisites

Before integrating your Relying Party application with eSignet, ensure the following are in place:

| Requirement | Description |
|---|---|
| **Client registered with eSignet** | RP must be onboarded and issued a `client_id`. |
| **eSignet well-known endpoint access** | `/.well-known/jwks.json` and `/.well-known/openid-configuration` |
| **Client Authentication Method** | Only **private_key_jwt** is supported for token endpoint client authentication. Access to **RSA/EC Private Key** used by RP to sign the JWT during token request. Note: Should be securely stored and rotated periodically. The private key must remain on the RP **backend** — frontend or native apps must not hold an extractable client private key. Client assertion signing and token exchange must be performed server-side. |
| **Registered Redirect URI** | Must be pre-configured with eSignet (during onboarding). It may be a frontend page, a mobile app deeplink/custom scheme, or a backend endpoint. |
| **Scopes/Claims required by RP** | `openid` is mandatory. Optional scopes: `profile`, `email`, `phone`, or custom scopes if supported. Check `/.well-known/openid-configuration` for supported scopes and claims. |
| **Choose libraries for JWT Creation, Signing & OIDC Integration** | Since **private_key_jwt** authentication requires the RP to generate a signed JWT for token requests, use well-supported cryptographic and OIDC client libraries. Reference: https://openid.net/developers/certified-openid-connect-implementations/ |

> Always fetch the authorize, PAR, token, userinfo endpoint URL dynamically from `.well-known/openid-configuration`. This ensures your integration remains compatible even if environments or endpoints change.

## Step-by-Step Implementation

### Step 1: Redirect User to eSignet Authorization Endpoint

Add a **Sign-in with eSignet** button to your login page that links to the authorize URL. A lightweight JavaScript npm package is available from eSignet to render this button automatically. Install it via npm:

```text
npm install @mosip/sign-in-with-esignet
```

The button may follow specific branding guidelines such as name, logo usage, color scheme, and size. These are typically defined by the Identity Provider. eSignet provides a UI Storybook that demonstrates recommended styles and customization options for relying party implementations: [UI Storybook](https://mosip.github.io/mosip-sdk/?path=/docs/javascript-sign-in-with-esignet--docs)

Additionally, the package provides **PAR** and **DPoP** support. Refer to the Storybook to know more details on how to configure and use the `par_callback` and `dpop_callback` parameters.

**eSignet Authorization Endpoint Specification**: see [`GET /oauth2/authorize`](./esignet-openapi.yaml) in the eSignet OpenAPI specification.

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
- The `redirect_uri` provided must be an absolute, fully qualified URL. The registered URI may use `*` or `**` as standalone path-segment wildcards; wildcards in the scheme, host, or partial path segments are not supported.
- `prompt=consent` forces a consent screen on every auth flow; without it, consent is shown only on first authorization or after the granted consent expires.
- **Per-client PKCE**: when a client is registered with `require_pkce: true` in `additionalConfig`, `code_challenge` and `code_challenge_method=S256` are mandatory in every authorization request, and `code_verifier` must be included in the subsequent token request.

#### PAR Support in Authorization Request

eSignet supports **Pushed Authorization Requests (PAR)** per OAuth 2.0 standards. The RP first submits authorization request parameters directly to eSignet via the **back-channel PAR endpoint**, receives a `request_uri`, and uses it in the authorize URL.

PAR enforcement is **per-client**: when a client is registered with `require_pushed_authorization_requests: true` in its `additionalConfig`, that client must use PAR. For other clients, PAR is optional but mandatory for FAPI 2.0 compliance.

**eSignet PAR Endpoint Specification**: see [`POST /oauth2/par`](./esignet-openapi.yaml) in the eSignet OpenAPI specification.

Not supported: client authentication in PAR request header, JAR (RFC 9101) `request` parameter, non-registered redirect URIs.

After receiving the `request_uri`, redirect the user's browser to:

```text
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
| Payload | `ath` | Base64url-encoded SHA-256 hash of the ASCII access token — **required** when the proof accompanies an access token (i.e. on userinfo requests) |

> **DPoP key reuse requirement**: Generate **one** DPoP key pair before the PAR request. Reuse the same private key for all subsequent proofs (PAR, token, and userinfo). Do not generate a new key pair between requests — a different key at the token endpoint will not match the `dpop_jkt` binding from PAR and will cause token exchange failure. Generate a fresh proof (new `jti` and current `iat`) for each individual request.

Send the DPoP proof in the `DPoP` HTTP header on:
- `POST /oauth2/par` (when using PAR with a DPoP-bound client)
- `POST /oauth2/token` (required for DPoP-bound clients)
- `GET /oauth2/userinfo` (required when using a DPoP-bound access token; send `Authorization: DPoP <token>`)

**DPoP nonce**: if the server returns error code `use_dpop_nonce`, a `DPoP-Nonce` response header will be present. Include its value in the `nonce` claim of the next DPoP proof and retry.

> **Notes:**
>
> - `scope` defines what user attributes the RP can request.
> - `claims` enables the RP to decide which user attributes are optional and which are mandatory.
> - Always generate a fresh `state` and `nonce` on every authorization request to prevent replay attacks and CSRF. Persist both values in the browser session before redirecting. When the authorization response arrives at the callback, compare the returned `state` to the stored value — reject any response where `state` is absent or does not match before proceeding to token exchange. After token exchange, extract the `nonce` claim from the ID Token and compare it to the stored nonce value — reject the authentication result if the `nonce` is absent or does not match.
> - `acr_values` defines authentication method options. The RP must choose based on the required assurance level.
> - The `redirect_uri` provided must be an absolute, fully qualified URL. Registered URIs may use `*` or `**` as standalone path-segment wildcards (e.g. `https://example.com/callback/*`). Wildcards embedded in the scheme, host, or partial path segments are rejected.
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

**eSignet Token Endpoint Specification**: see [`POST /oauth2/token`](./esignet-openapi.yaml) in the eSignet OpenAPI specification.

Only supported client authentication method: **private_key_jwt**

Required `client_assertion` JWT claims:
- `iss` — must be `client_id`
- `sub` — must be `client_id`
- `aud` — token endpoint URL (e.g. `https://esignet.example.org/v1/esignet/oauth2/token`). For FAPI 2.0 strict mode (`client_auth_assertion_audience: strict_audience_check`), use the server's issuer URL from `/.well-known/openid-configuration` instead.
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

Error codes (HTTP 400): `invalid_transaction`, `invalid_assertion`, `invalid_redirect_uri`, `invalid_input`, `unknown_error`, `invalid_request`, `invalid_grant`, `invalid_assertion_type`, `invalid_pkce_code_verifier`, `unsupported_pkce_challenge_method`, `pkce_failed`, `invalid_dpop_proof`, `use_dpop_nonce`

> **Note:** No refresh tokens are issued by default (`renew_on_grant: false`). Token revocation is also disabled by default. RPs must re-initiate the authorization flow when the access token expires.

#### Step 4: Verify and Parse the Access and ID Token

Access tokens generated by eSignet follow [RFC 9068] JSON Web Token (JWT) Profile for OAuth 2.0 Access Tokens.

**eSignet JWKS Endpoint Specification**: see [`GET /oauth2/jwks`](./esignet-openapi.yaml) in the eSignet OpenAPI specification.

Validate JWT signature using public key published on eSignet `/.well-known/jwks.json` (served via the `/oauth2/jwks` endpoint; also accessible at `/.well-known/jwks.json` via reverse proxy in production). Match the public key using the `kid` header claim from the token.

Validate `iss`, `exp`, `iat` in both the tokens.

Validate `aud` in both tokens — note that the expected value differs:
- **ID token** `aud` — must equal the RP's `client_id`
- **Access token** `aud` — must equal the resource server identifier (not the `client_id`)

Additionally validate `auth_time`, `nonce`, `acr`, `at_hash` in the ID token.

Default signing algorithm: **PS256** (RSASSA-PSS with SHA-256). Supported signing algorithms: PS256, ES256, ES256K, EdDSA. Always check the token's `alg` header and reject tokens signed with an algorithm outside this set.

**ID token JWE (opt-in)**: when a client is registered with `id_token_response_type: JWE` in `additionalConfig`, the token endpoint returns the `id_token` as a nested JWT. Process it as follows:
1. Decrypt the outer JWE using the RP's encryption private key (key algorithm from the registered `encPublicKey` JWK `alg` field, e.g. `RSA-OAEP-256`; content encryption is always `A256GCM`).
2. The decrypted payload is a JWS-signed JWT — validate its signature using eSignet's public keys from `/.well-known/jwks.json`.
3. Proceed with standard ID token claim validation on the inner JWT.

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

**eSignet UserInfo Endpoint Specification**: see [`GET /oauth2/userinfo`](./esignet-openapi.yaml) in the eSignet OpenAPI specification.

Authentication: Bearer (access token) or DPoP-bound access token.

- Standard flow: `Authorization: Bearer <access_token>`
- DPoP-bound flow: `Authorization: DPoP <access_token>` with `DPoP: <proof>` header (proof bound to this endpoint URL and `GET` method)

Response (HTTP 200): `application/jwt` — a signed JWS JWT by default; a nested JWT (signed with JWS, then encrypted with JWE) when the client is registered with `userinfo_response_type: JWE`.

Error (HTTP 401): `WWW-Authenticate` response header (RFC 6750 Bearer challenge format), error codes: `invalid_token`, `unknown_error`, `invalid_dpop_proof`, `use_dpop_nonce`.

Supported claims in userinfo JWT:
- `sub` (Partner Specific User Token — PSUT)
- `name`, `address`, `gender`, `birthdate`, `picture`, `email`, `phone_number`, `locale`
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