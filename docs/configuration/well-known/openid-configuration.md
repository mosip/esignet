# `openid-configuration`

`GET /.well-known/openid-configuration` — the OpenID Provider Metadata document: the OAuth 2.0
Authorization Server Metadata document (see [`oauth-authorization-server.md`](oauth-authorization-server.md))
plus the OIDC-only fields below.

## OIDC-only fields

| Field | Source | How to change |
|---|---|---|
| `userinfo_endpoint` | fixed path `/oauth2/userinfo`, prefixed with `server.public_url` | not configurable |
| `scopes_supported` | `oauth.allowed_scopes` | `deployment.yaml` |
| `claims_supported` | `oauth.allowed_claims` | `deployment.yaml` |
| `subject_types_supported` | `oauth.allowed_subject_types` | `deployment.yaml` (default `["pairwise"]`) |
| `id_token_signing_alg_values_supported`, `userinfo_signing_alg_values_supported` | algorithms of the currently-provisioned signing keys (keymanager `GetPublicKeys`) | rotate/provision keys — see [`../../configuration.md`](../../configuration.md#8-key-manager); **not** the same source as `dpop_signing_alg_values_supported` |
| `id_token_encryption_*`, `userinfo_encryption_*` | engine's built-in JWE support | not configurable; **not** driven by `supported_enc_algorithms` |
| `claims_parameter_supported` | always `true` | not configurable |
| `acr_values_supported` | keys of the fixed ACR→AMR map | see [`../acr.md`](../acr.md) |
| `end_session_endpoint` | present only if `oauth.logout.enabled` | fixed `false` in code — currently never present |

Fields shared with the OAuth 2.0 document: `issuer`, `authorization_endpoint`, `token_endpoint`,
`jwks_uri`, `introspection_endpoint`, `revocation_endpoint`, `registration_endpoint`,
`pushed_authorization_request_endpoint`, `require_pushed_authorization_requests`,
`response_types_supported`, `grant_types_supported`, `token_endpoint_auth_methods_supported`,
`token_endpoint_auth_signing_alg_values_supported`, `code_challenge_methods_supported`,
`dpop_signing_alg_values_supported` — see [`oauth-authorization-server.md`](oauth-authorization-server.md).

## Example (abridged)

```json
{
  "issuer": "https://esignet.example.com",
  "userinfo_endpoint": "https://esignet.example.com/oauth2/userinfo",
  "jwks_uri": "https://esignet.example.com/oauth2/jwks",
  "scopes_supported": ["openid", "profile", "email", "phone"],
  "claims_supported": ["name", "address", "gender", "birthdate", "picture", "email", "phone_number"],
  "subject_types_supported": ["pairwise"],
  "id_token_signing_alg_values_supported": ["RS256"],
  "acr_values_supported": ["mosip:idp:acr:biometrics", "mosip:idp:acr:generated-code", "mosip:idp:acr:knowledge", "mosip:idp:acr:password"],
  "claims_parameter_supported": true
}
```
