# `jwks.json`

## 1. Route

Real route: `GET /oauth2/jwks` (from the embedded engine module, see
[`../well-known.md`](../well-known.md)). `/.well-known/jwks.json` is only an nginx alias
(`oidc-ui/nginx/nginx.conf`, `helm/oidc-ui/templates/configmap.yaml`) — a client bypassing the UI's
nginx must use `/oauth2/jwks` directly.

## 2. What keys are published

Built from the currently-active keys registered under esignet's keymanager application id,
`OIDC_SERVICE` (`esignet-service/internal/config/app.go`'s `OIDCServiceAppID`).

| Variable | YAML key | Default | Effect |
|---|---|---|---|
| `MOSIP_ESIGNET_SIGNING_KEY_REF_ID` | `jwt.preferred_key_id` | `RSA_2048` | Reference id used to sign issued tokens; also the default reference id resolved for JWKS. |
| `KEYMANAGER_KEY_CACHE_EXPIRE_MINS` | _(env-only)_ | `1440` | Bounds how quickly a key rotation is reflected here. |

Provisioned reference ids: `RSA_2048`, `EC_SECP256R1_SIGN`, `EC_SECP256K1_SIGN`, `ED25519_SIGN` —
see `esignet-service/internal/keymanager/README.md` for rotation details.

## 3. Related endpoints

`GET /system-info/certificate`, `POST /system-info/uploadCertificate` — fetch/replace a certificate
for a given `(applicationId, referenceId)` outside the automatic startup provisioning flow. These
endpoints may require a bearer scope — see
[`../../configuration.md`](../../configuration.md#4-oauth-and-openid).

## 4. Note

`security_config.jwks_cache_ttl` in `deployment.yaml` is the service's own cache for *fetching an
external* JWKS (bearer-token validation on `/client-mgmt/*`/`/system-info/*`) — it has no effect on
how the service serves its own `/oauth2/jwks` response.
