# `.well-known` discovery documents

| Document | Route | Covers |
|---|---|---|
| OpenID Provider Metadata | `GET /.well-known/openid-configuration` | [`well-known/openid-configuration.md`](well-known/openid-configuration.md) — lets OIDC clients auto-discover login/userinfo endpoints, supported scopes/claims, and how identity data is signed. |
| OAuth 2.0 Authorization Server Metadata | `GET /.well-known/oauth-authorization-server` | [`well-known/oauth-authorization-server.md`](well-known/oauth-authorization-server.md) — lets plain OAuth clients auto-discover the token/authorization endpoints and which grant types, auth methods, and security features (PAR, DPoP, PKCE) are supported. |
| JSON Web Key Set | `GET /oauth2/jwks` (aliased at `/.well-known/jwks.json` by the reverse proxy) | [`well-known/jwks-json.md`](well-known/jwks-json.md) — publishes the public keys clients need to verify tokens issued by esignet, and how key rotation is reflected here. |

## 1. Where these are implemented

None of these routes are registered by code in this repository — they come from the embedded
engine module (`github.com/thunder-id/thunderid`, pinned in `esignet-service/go.mod`),
`internal/oauth/oauth2/discovery`. There is no `/.well-known/jwks.json` route in the backend; that
path only exists as an nginx `proxy_pass` alias to `/oauth2/jwks`
(`oidc-ui/nginx/nginx.conf`, `helm/oidc-ui/templates/configmap.yaml`).

## 2. What drives every document

- **`issuer`** (env `MOSIP_ESIGNET_HOST`) → the `issuer` field.
- **`server.public_url`** (env `MOSIP_ESIGNET_BASE_URL`, falls back to `issuer`) → prefixed onto
  every endpoint path to build the full endpoint URLs.

See [`../configuration.md`](../configuration.md#3-basic-configuration).

## 3. CORS

Gated by `allowed_origin_regex` (§ [`../configuration.md`](../configuration.md#3-basic-configuration))
— unset means no `Access-Control-Allow-Origin` is returned for these endpoints.
