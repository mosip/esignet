# `oauth-authorization-server`

`GET /.well-known/oauth-authorization-server` — the OAuth 2.0 Authorization Server Metadata
document. Every field it carries is also carried by
[`openid-configuration.md`](openid-configuration.md); it has no fields of its own.

| Field | Source | How to change |
|---|---|---|
| `issuer` | `issuer` (env `MOSIP_ESIGNET_HOST`) | [`../../configuration.md`](../../configuration.md#3-basic-configuration) |
| `authorization_endpoint`, `token_endpoint`, `jwks_uri`, `introspection_endpoint`, `pushed_authorization_request_endpoint` | fixed path constants prefixed with `server.public_url` | change the base URL to move all of them together |
| `revocation_endpoint` | present only if `oauth.token_revocation.enabled` | fixed `false` in code — currently never present |
| `registration_endpoint` | present only if `oauth.dcr.enabled` | `deployment.yaml`'s `oauth.dcr` block |
| `backchannel_authentication_endpoint` + related fields | present only if `ciba` is in `oauth.allowed_grant_types` | `deployment.yaml`; absent by default (`allowed_grant_types: ["authorization_code"]`) |
| `require_pushed_authorization_requests` | `oauth.par.require_par` | `deployment.yaml` |
| `response_types_supported` | `oauth.allowed_response_types` | `deployment.yaml` (default `["code"]`) |
| `grant_types_supported` | `oauth.allowed_grant_types` | `deployment.yaml` (default `["authorization_code"]`) |
| `token_endpoint_auth_methods_supported` | `oauth.allowed_auth_methods` | `deployment.yaml` (default `["private_key_jwt"]`) |
| `token_endpoint_auth_signing_alg_values_supported`, `dpop_signing_alg_values_supported` | `supported_signing_algorithms` intersected with the keymanager's supported algorithms | `MOSIP_ESIGNET_OAUTH_SUPPORTED_SIGNING_ALGORITHMS` / `deployment.yaml` |
| `code_challenge_methods_supported` | fixed PKCE method set | not configurable |
| `authorization_response_iss_parameter_supported` | always `true` | not configurable |
| `authorization_grant_profiles_supported` | includes the JWT Authorization Grant profile only if `urn:ietf:params:oauth:grant-type:jwt-bearer` is in `oauth.allowed_grant_types` | `deployment.yaml`; absent by default |

Note `token_endpoint_auth_signing_alg_values_supported`/`dpop_signing_alg_values_supported` are not
the same source as `id_token_signing_alg_values_supported` — see
[`openid-configuration.md`](openid-configuration.md).

## Example (abridged)

```json
{
  "issuer": "https://esignet.example.com",
  "token_endpoint": "https://esignet.example.com/oauth2/token",
  "jwks_uri": "https://esignet.example.com/oauth2/jwks",
  "response_types_supported": ["code"],
  "grant_types_supported": ["authorization_code"],
  "token_endpoint_auth_methods_supported": ["private_key_jwt"],
  "token_endpoint_auth_signing_alg_values_supported": ["PS256", "ES256", "ES256K", "EdDSA"],
  "dpop_signing_alg_values_supported": ["PS256", "ES256", "ES256K", "EdDSA"],
  "require_pushed_authorization_requests": false
}
```
