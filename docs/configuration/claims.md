# Claims

## 1. Two claim planes

1. **Per-client `userClaims`** (API field; stored as `client_detail.claims`) — the claims a client
   may ever receive.
2. **Deployment-wide `scope_claims`** (`deployment.yaml`, top level) — which claims each OIDC
   scope expands to.

A claim is only released if it is in both.

## 2. Per-client claims allow-list

`esignet-service/internal/clientmgmt/validate.go`:

```go
var allowedClaims = map[string]struct{}{
	"name": {}, "given_name": {}, "middle_name": {}, "preferred_username": {},
	"nickname": {}, "gender": {}, "birthdate": {}, "email": {},
	"phone_number": {}, "picture": {}, "address": {},
}
```

`userClaims` must be non-empty and every entry must be one of these eleven names
(`invalid_claim` otherwise).

## 3. Scope → claim mapping

`scope_claims` in `deployment.yaml` (no env override):

```yaml
scope_claims:
  openid:
  profile:
    - name
    - given_name
    - middle_name
    - nickname
    - preferred_username
    - picture
    - gender
    - birthdate
  email:
    - email
    - email_verified
  phone:
    - phone_number
    - phone_number_verified
  address:
    - address
```

Allowed scopes for a client = keys of `scope_claims` + that client's
`additional_config.allowed_authorization_scopes` (see
[`resource-servers-and-permissions.md`](resource-servers-and-permissions.md)). Effective
scope→claims mapping intersects each scope's claim list with that client's `userClaims` (§2).

A separate `oauth.allowed_claims`/`oauth.default_scope_claims_mapping` block in `deployment.yaml`
(see [`../configuration.md`](../configuration.md#4-oauth-and-openid)) feeds discovery's
`claims_supported` only — it does not govern claim release. Keep the two in sync manually.

## 4. Userinfo / ID token delivery

Per-client `additionalConfig` keys:

| Key | Values | Default |
|---|---|---|
| `userinfo_response_type` | `JWS` \| `JWE` | `JWS` |
| `id_token_response_type` | `JWS` \| `JWE` | `JWS` |
| `consent_expire_in_mins` | integer, minimum `10` | unset = never expires |

When `JWE` is selected, `enc` is fixed `A256GCM`; `alg` is taken from the client's registered
encryption JWK (`encPublicKey.alg`).

## 5. Provider-specific claim mapping

SunbirdRC (KBI): `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_IDENTITY_OPENID_CLAIMS_MAPPING` (JSON),
default `{"name":"fullName","email":"email","phone_number":"mobile","gender":"gender","birthdate":"dob"}`
— empty/malformed means no claims released for that provider.
