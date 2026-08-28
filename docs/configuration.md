# Configuration

Configuration reference for `esignet-service` (the Go OIDC/OAuth2 provider) and `oidc-ui` (the
login/consent UI). Every property below is verified against the current eSignet-go implementation.

Sub-pages:

| Page | Covers |
|---|---|
| [`configuration/acr.md`](configuration/acr.md) | Which authentication methods (OTP, password, biometrics, wallet, KBI, ...) a client can request, and how a requested method maps to the actual login screen shown to the user. |
| [`configuration/claims.md`](configuration/claims.md) | What personal data (name, email, address, ...) a client is allowed to receive and how OIDC scopes control what's actually returned, plus how that data is delivered (signed vs. encrypted). |
| [`configuration/login-id.md`](configuration/login-id.md) | The identifiers end users can log in with (UIN/VID, mobile, email, national ID) and how each one is validated. |
| [`configuration/well-known.md`](configuration/well-known.md) | The standard OIDC/OAuth discovery endpoints clients use to auto-configure themselves, and what determines the URLs/values they advertise. |
| [`configuration/resource-servers-and-permissions.md`](configuration/resource-servers-and-permissions.md) | Granting clients permission to do things beyond reading profile data — e.g. issuing a Verifiable Credential or making a payment — and controlling which downstream service each permission is authorized for. |

## 1. Where configuration lives

| Plane | Location |
|---|---|
| Environment variables | process environment |
| Service YAML | `esignet-service/data/deployment.yaml` |
| Declarative UI assets | `<DATA_DIR>/{flows,layouts,themes,i18n}/*.yaml` |
| Per-client rows | `client_detail` table (Postgres), via `/client-mgmt/*` |
| UI runtime config | `oidc-ui`'s `window._env_` and `public/theme/config.json` |

`DATA_DIR` (default `./data`) controls where flow/layout/theme/i18n assets are read from, but
**not** where `deployment.yaml` is read from — that path is hardcoded to `./data/deployment.yaml`.

## 2. Precedence and parsing rules

Effective value for most `deployment.yaml` settings: **env var > `deployment.yaml` > compiled-in
default** (`esignet-service/internal/config/app.go`).

Exceptions:

- **Redis pool/timeout tuning** (`REDIS_POOL_SIZE`, `REDIS_MIN_IDLE_CONNS`, etc.) is env-var-only —
  never read from `deployment.yaml`.
- **The Postgres DSN resolves as a whole.** Setting any of `DATABASE_URL`, `DATABASE_HOST`,
  `DATABASE_PORT`, `DATABASE_NAME`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `DB_DBUSER_PASSWORD`
  discards `db.dsn` from `deployment.yaml` entirely rather than merging with it.

Other rules:

- **Zero/negative = "not set"** for most integer settings, at every tier. Explicit exceptions that
  honor an env var of `0` as "no limit": `DB_CONN_MAX_LIFETIME_SECS`, `REDIS_CONN_MAX_LIFETIME_SECS`.
- **`deployment.yaml` is strict** (`KnownFields(true)`) — an unrecognised key is a startup error.
- **`${VAR}` expansion is `os.ExpandEnv`, not a shell** — plain `${VAR}` works; `${VAR:-default}`
  silently expands to `""`. Same rule applies to the flow/layout/theme YAML under `DATA_DIR`.
- An invalid `resource_servers` list or an out-of-range `MOSIP_ESIGNET_OIDC_UI_PORT` aborts startup.
  A `inbound_http_server.write_timeout_secs` that doesn't exceed the combined outbound timeouts
  only logs a warning (§9).

## 3. Basic configuration

| Variable | YAML key | Default | Purpose |
|---|---|---|---|
| `NAMESPACE` | `identifier` | `esignet` | Deployment identifier. |
| `PORT` | `port` | `8080` | HTTP listen port. |
| `METRICS_PORT` | _(env-only)_ | `9090` | Prometheus `/metrics` port. |
| `MOSIP_ESIGNET_HOST` | `issuer` | `http://localhost:<PORT>` | OIDC `issuer`; fallback base URL. |
| `MOSIP_ESIGNET_BASE_URL` | `server.public_url` | falls back to `issuer` | Base URL prefixed to every discovery endpoint path. `server.http_only` is derived from its scheme, not independently settable. |
| `DATA_DIR` | `data_dir` | `./data` | Root for `flows/`, `layouts/`, `themes/`, `i18n/`. |
| `MOSIP_ESIGNET_PPROF_ENABLED` | _(env-only)_ | `false` | Enables `net/http/pprof`. |
| `MOSIP_ESIGNET_PPROF_PORT` | _(env-only)_ | `6060` | pprof port; bound to `127.0.0.1` only. |
| `LOG_LEVEL` | _(env-only)_ | `info` | `debug`\|`info`\|`warn`\|`error`. |

Token/session lifetimes:

| Variable | YAML key | Default |
|---|---|---|
| `MOSIP_ESIGNET_JWT_VALIDITY_PERIOD` (alias `MOSIP_ESIGNET_OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS`) | `jwt.validity_period` | `120` |
| `MOSIP_ESIGNET_JWT_LEEWAY` | `jwt.leeway` | `10` |
| `MOSIP_ESIGNET_SIGNING_KEY_REF_ID` | `jwt.preferred_key_id` | `RSA_2048` |
| `MOSIP_ESIGNET_OAUTH_AUTH_CODE_LIFETIME_SECONDS` | `oauth.authorization_code.validity_period` | `60` |
| `MOSIP_ESIGNET_OAUTH_PAR_EXPIRY_SECONDS` | `oauth.par.expires_in` | `3600` |
| `MOSIP_ESIGNET_DPOP_LEEWAY` | `oauth.dpop.leeway` | `10` |
| — | `oauth.dpop.required` | `false` |
| — | `oauth.dpop.iat_window` | `60` |
| — | `oauth.dpop.allowed_algs` | `["ES256","PS256","ES384","ES512","EdDSA","RS256"]` |
| — | `oauth.dpop.max_jti_length` | `256` |
| — (no env override) | `security_config.request_time_leeway_secs` | `300` |

`oauth.refresh_token.renew_on_grant`, `oauth.token_revocation.enabled`, `oauth.logout.enabled`,
`oauth.allow_wildcard_redirect_uri` are accepted `deployment.yaml` keys but are overwritten
unconditionally after parsing (always `false`/`false`/`false`/`true` respectively) — setting them
in the file has no effect.

`MOSIP_ESIGNET_CORS_ALLOWED_ORIGIN_REGEX` ↔ `allowed_origin_regex`, default `""` (CORS disabled).

Captcha:

| Variable | YAML key | Default |
|---|---|---|
| `MOSIP_ESIGNET_CAPTCHA_VALIDATOR_URL` | `captcha_config.validator_url` | `""` — empty skips captcha validation entirely |
| `MOSIP_ESIGNET_CAPTCHA_MODULE_NAME` | `captcha_config.module_name` | `esignet` |
| `MOSIP_ESIGNET_CAPTCHA_TIMEOUT_SECS` | `captcha_config.timeout_secs` | `10` |
| `MOSIP_ESIGNET_CAPTCHA_SITE_PROVIDER` | — | none — expanded into `data/flows/flow-esignet.yaml`, not read by Go config |
| `MOSIP_ESIGNET_CAPTCHA_SITE_KEY` | — | none — same mechanism |

Login gate:

| Variable | YAML key | Default |
|---|---|---|
| `MOSIP_ESIGNET_OIDC_UI_SCHEME` | `gate_client.scheme` | `http` |
| `MOSIP_ESIGNET_OIDC_UI_HOSTNAME` | `gate_client.hostname` | `127.0.0.1` |
| `MOSIP_ESIGNET_OIDC_UI_PORT` | `gate_client.port` | `3000` (1–65535; invalid value aborts startup) |
| `MOSIP_ESIGNET_OIDC_UI_LOGIN_PATH` | `gate_client.login_path` | `/signin` |
| `MOSIP_ESIGNET_OIDC_UI_ERROR_PATH` | `gate_client.error_path` | `/error` |

## 4. OAuth and OpenID

`deployment.yaml`'s `oauth:` block (no env override — edit the file):

```yaml
oauth:
  allowed_subject_types: ["pairwise"]
  allowed_grant_types: ["authorization_code"]
  allowed_response_types: ["code"]
  allowed_auth_methods: ["private_key_jwt"]
  allowed_scopes: ["openid", "profile", "email", "phone"]
  allowed_claims: ["name", "address", "gender", "birthdate", "picture", "email", "phone_number"]
  default_scope_claims_mapping:
    openid: ["sub"]
    profile: ["name", "address", "gender", "birthdate", "picture", "email", "phone_number"]
    email: ["email"]
    phone: ["phone_number"]
  send_server_errors_to_client: false
```

These `allowed_*` lists feed the corresponding `*_supported` discovery fields — see
[`well-known.md`](configuration/well-known.md). A separate top-level `scope_claims` map governs
actual claim release at runtime — see [`configuration/claims.md`](configuration/claims.md).

Signing/encryption algorithm advertisement:

| Variable | YAML key | Default |
|---|---|---|
| `MOSIP_ESIGNET_OAUTH_SUPPORTED_SIGNING_ALGORITHMS` (comma-separated) | `supported_signing_algorithms` | `["PS256","ES256","ES256K","EdDSA"]` |
| `MOSIP_ESIGNET_OAUTH_SUPPORTED_ENCRYPTION_ALGORITHMS` (comma-separated) | `supported_enc_algorithms` | `["RSA-OAEP","RSA-OAEP-256"]` |

`supported_enc_algorithms` only constrains a client's registered encryption-key `alg` at
registration time — see [`well-known.md`](configuration/well-known.md) for why it does not affect
the encryption algorithms discovery advertises.

Bearer-token scope enforcement on `/client-mgmt/*` and `/system-info/*` is configured under the
`security_config:` block in `deployment.yaml`: `issuer_url` (env `MOSIP_ESIGNET_SECURITY_ISSUER_URL`),
`jwks_url` (env `MOSIP_ESIGNET_SECURITY_JWKS_URL`), `jwks_cache_ttl` (default `3000`), and an
`endpoint`/`method`/`scope` `scope_mapping` list — see `deployment.yaml` for the full list, which
includes mappings for the deprecated `/client-mgmt/oidc-client` and `/client-mgmt/oauth-client`
aliases. Enforcement only activates when both `issuer_url` and `jwks_url` are non-empty.

## 5. Cache and runtime store

| Variable | YAML key | Default |
|---|---|---|
| `MOSIP_ESIGNET_CACHE_TYPE` | `runtime_db_type` | `inmemory` — any value other than `redis` is coerced to `inmemory` |
| `MOSIP_ESIGNET_CLIENT_CACHE_TTL_SECS` | `client_cache_ttl_secs` | `3600` |
| — (no env override) | `design_cache_ttl_secs` | `86400` |
| — (no env override) | `flow_cache_ttl_secs` | `86400` |

Redis connection (pool/timeout tuning is env-only, §2):

| Variable | YAML key | Default |
|---|---|---|
| `REDIS_URL` | `redis.url` | `""` — takes precedence over host/port |
| `REDIS_HOST` | `redis.host` | `localhost` |
| `REDIS_PORT` | `redis.port` | `6379` |
| `REDIS_PASSWORD` | `redis.password` | `""` |
| `REDIS_DB` | `redis.db` | `0` |
| `REDIS_TLS_ENABLED` | `redis.tls` | `false` (auto for `rediss://`) |
| `REDIS_KEY_PREFIX` | `redis.key_prefix` | `esignet:` |
| `REDIS_SENTINEL_MASTER` | `redis.sentinel_master` | `""` |
| `REDIS_SENTINEL_ADDRS` (comma-separated) | `redis.sentinel_addrs` | `""` |
| `REDIS_POOL_SIZE` | _(env-only)_ | `10` |
| `REDIS_MIN_IDLE_CONNS` | _(env-only)_ | `2` |
| `REDIS_CONN_MAX_IDLE_TIME_SECS` | _(env-only)_ | `300` |
| `REDIS_CONN_MAX_LIFETIME_SECS` | _(env-only)_ | `1800`; explicit `0` = no limit |
| `REDIS_DIAL_TIMEOUT_SECS` | _(env-only)_ | `5` |
| `REDIS_READ_TIMEOUT_SECS` | _(env-only)_ | `3` |
| `REDIS_WRITE_TIMEOUT_SECS` | _(env-only)_ | `3` |
| `REDIS_POOL_TIMEOUT_SECS` | _(env-only)_ | `4` |

Connection mode priority: `REDIS_URL` set → DSN mode; else `REDIS_SENTINEL_MASTER` +
`REDIS_SENTINEL_ADDRS` both set → Sentinel; else single-node `REDIS_HOST`/`REDIS_PORT`.

## 6. Database

| Variable | YAML key | Default |
|---|---|---|
| `DATABASE_URL` | `db.dsn` | `""`; `sslmode=disable` appended if not already specified |
| `DATABASE_HOST` | — | `localhost` |
| `DATABASE_PORT` | — | `5455` |
| `DATABASE_NAME` | — | `mosip_esignet` |
| `DATABASE_USERNAME` | — | `postgres` |
| `DATABASE_PASSWORD` / `DB_DBUSER_PASSWORD` | — | `""`; `DATABASE_PASSWORD` takes precedence |
| `DB_MAX_OPEN_CONNS` | `db.pool.max_open_conns` | `25` |
| `DB_MAX_IDLE_CONNS` | `db.pool.max_idle_conns` | `5` |
| `DB_CONN_MAX_LIFETIME_SECS` | `db.pool.conn_max_lifetime_secs` | `1800`; explicit env `0` = no limit |
| `DB_CONN_MAX_IDLE_TIME_SECS` | `db.pool.conn_max_idle_time_secs` | `300` |

## 7. Provider integrations

`MOSIP_ESIGNET_AUTHN_PROVIDER` (YAML `provider`, default `mock`) selects the identity/authentication
backend and, together with it, the audit backend (`mosip` → IDA-backed audit; `mock`/`sunbird` →
no-op). Any other value fails startup.

**`mock`** — no required vars.

| Variable | Default |
|---|---|
| `MOSIP_ESIGNET_MOCK_DOMAIN_URL` | `http://mock-identity-system.mockid` |
| `MOSIP_ESIGNET_MOCK_KYC_AUTH_URL` | `<base>/v1/mock-identity-system/v2/kyc-auth` |
| `MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_URL` | `<base>/v1/mock-identity-system/kyc-exchange` |
| `MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_V3_URL` | `<base>/v1/mock-identity-system/v3/kyc-exchange` |
| `MOSIP_ESIGNET_MOCK_SEND_OTP_URL` | `<base>/v1/mock-identity-system/send-otp` |
| `MOSIP_ESIGNET_MOCK_AUTHENTICATOR_SIGNING_KEYS_URL` | `<base>/v1/mock-identity-system/keys.json` |

**`mosip`** (MOSIP IDA) — **required**: `MOSIP_IDA_CLIENT_SECRET`, `MOSIP_ESIGNET_MISP_KEY`,
`MOSIP_API_INTERNAL_HOST`.

| Variable | Default |
|---|---|
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL` | `<host>/mosip-certs/ida-partner.cer` |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL` | `<host>/idauthentication/v1/otp/<license>/` |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL` | `<host>/idauthentication/v1/kyc-auth/delegated/<license>/` |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL` | `<host>/idauthentication/v1/kyc-exchange/delegated/<license>/` |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_GET_CERTIFICATES_URL` | `<host>/idauthentication/v1/internal/getAllCertificates?applicationId=IDA_KYC_EXCHANGE&referenceId=` |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUTH_TOKEN_URL` | `<host>/v1/authmanager/authenticate/clientidsecretkey` |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUDIT_MANAGER_URL` | `<host>/v1/auditmanager/audits` |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_CLIENT_ID` | `mosip-ida-client` |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_APP_ID` | `ida` |
| `MOSIP_ESIGNET_DOMAIN_URL` | falls back to `MOSIP_API_INTERNAL_HOST` |
| `IDA_AUTHENTICATOR_ENV` | `Staging` |

**`sunbird`** (SunbirdRC KBI) — **required**:
`MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_REGISTRY_SEARCH_URL`.

| Variable | Default |
|---|---|
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REGISTRY_GET_URL` | `""` |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_INDIVIDUAL_ID_FIELD` | `policyNumber` |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_KBI_ENTITY_ID_FIELD` | `osid` |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_FIELD_DETAILS` (JSON) | `[{"id":"policyNumber","type":"text","format":""},{"id":"fullName","type":"text","format":""},{"id":"dob","type":"date","format":"dd/mm/yyyy"}]` |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_IDENTITY_OPENID_CLAIMS_MAPPING` (JSON) | `{"name":"fullName","email":"email","phone_number":"mobile","gender":"gender","birthdate":"dob"}` |

## 8. Key manager

Env-only (`esignet-service/internal/keymanager/config.go`) — none of these are read from
`deployment.yaml`.

| Variable | Default |
|---|---|
| `KEYMANAGER_KEYSTORE_TYPE` | `PKCS11` (or `PKCS12`) — selects the backend below |

PKCS#11 (HSM/SoftHSM2 — requires a `CGO_ENABLED=1` build):

| Variable | Default |
|---|---|
| `KEYMANAGER_PKCS11_MODULE_PATH` | `""` — required |
| `KEYMANAGER_PKCS11_TOKEN_LABEL` | `""` — one of this or slot-id required |
| `KEYMANAGER_PKCS11_SLOT_ID` | `""` — takes precedence over token label |
| `KEYMANAGER_PKCS11_PIN` | `""` |

PKCS#12 (file-based; the only backend that works in the default `CGO_ENABLED=0` local build):

| Variable | Default |
|---|---|
| `KEYMANAGER_PKCS12_FILE_PATH` | `""` — required |
| `KEYMANAGER_PKCS12_PASSWORD` | `""` — required |
| `KEYMANAGER_PKCS12_ALLOW_INSECURE_SOFTWARE_KEYSTORE` | `"false"` — must be the literal string `"true"` to opt in |

Applies regardless of backend:

| Variable | Default |
|---|---|
| `KEYMANAGER_CERT_CN` | `www.mosip.io` |
| `KEYMANAGER_CERT_OU` | `mosip-esignet` |
| `KEYMANAGER_CERT_O` | `IIITB` |
| `KEYMANAGER_CERT_L` | `Bangalore` |
| `KEYMANAGER_CERT_ST` | `KA` |
| `KEYMANAGER_CERT_C` | `IN` |
| `KEYMANAGER_ASYMMETRIC_KEY_LENGTH` | `2048` |
| `KEYMANAGER_KEY_CACHE_EXPIRE_MINS` | `1440`; `<=0` disables caching |
| `KEYMANAGER_SYMMETRIC_KEY_VALIDITY_DAYS` | `1825` |
| `KEYMANAGER_SYMMETRIC_KEY_ALLOWED_REF_IDS` (comma-separated) | `CACHE_ENCRYPT` |
| `KEYMANAGER_FOREIGN_DOMAIN_ALLOWED_APP_IDS` (comma-separated) | `PARTNER,IDA` |
| `KEYMANAGER_CERTIFICATE_ALLOWED_REF_IDS` (comma-separated) | `""` — empty allows none |

Cryptomanager (env-only,
`esignet-service/internal/keymanager/cryptomanager/config.go`): `CRYPTOMANAGER_DATA_KEY_SPLITTER`
(default `#KEY_SPLITTER#`), `CRYPTOMANAGER_JWT_ENFORCE_2048` (default `true`),
`CRYPTOMANAGER_CALLER_NONCE_ALLOWED_REF_IDS` (comma-separated, default `""`).

`jwt.preferred_key_id` (§3) selects which provisioned reference id (`RSA_2048`,
`EC_SECP256R1_SIGN`, `EC_SECP256K1_SIGN`, `ED25519_SIGN`) signs issued tokens — see
`esignet-service/internal/keymanager/README.md` for key-hierarchy details.

## 9. HTTP tuning

Three independently-tunable blocks, each `env var > deployment.yaml > compiled default`:

- `OUTBOUND_IDSYSTEM_HTTP_CLIENT_*` ↔ `outbound_idsystem_http_client:` — MOSIP IDA / SunbirdRC calls.
- `OUTBOUND_HTTP_CLIENT_*` ↔ `outbound_http_client:` — captcha validation and JWKS fetch (shared pool).
- `INBOUND_HTTP_SERVER_*` ↔ `inbound_http_server:` — the service's own HTTP server.

Suffixes/defaults for both outbound blocks: `TIMEOUT_SECS`=`30`, `DIAL_TIMEOUT_SECS`=`5`,
`DIAL_KEEP_ALIVE_SECS`=`30`, `TLS_HANDSHAKE_TIMEOUT_SECS`=`10`,
`RESPONSE_HEADER_TIMEOUT_SECS`=`10`, `IDLE_CONN_TIMEOUT_SECS`=`90`, `MAX_CONNS_PER_HOST`=`500`,
`MAX_IDLE_CONNS`=`500`, `MAX_IDLE_CONNS_PER_HOST`=`200`.

Inbound: `READ_HEADER_TIMEOUT_SECS`=`10`, `READ_TIMEOUT_SECS`=`30`, `WRITE_TIMEOUT_SECS`=`90`,
`IDLE_TIMEOUT_SECS`=`120`. `write_timeout_secs` must exceed the combined `timeout_secs` of both
outbound clients (stock defaults: 30+30=60 vs. inbound 90) — raising either outbound timeout
without raising this only logs a warning, it isn't enforced.

## 10. eSignet UI configuration

**Build-time** (Vite): `VITE_API_URL` — `oidc-ui/.env` sets `http://localhost:8080` for local dev,
`oidc-ui/.env.production` sets `/v1/esignet`.

**Runtime**, `window._env_` (populated by the container entrypoint from baked-in defaults, overridable
by a live env var of the same name):

| Key | Default (`oidc-ui/public/env-config.js`) |
|---|---|
| `DEFAULT_LANG` | `en` |
| `DEFAULT_WELLKNOWN` | URL-encoded JSON array of `{name, value}` discovery-document links |
| `DEFAULT_THEME` | `""` |
| `DEFAULT_FAVICON` | `favicon.ico` |
| `DEFAULT_TITLE` | `eSignet` |
| `DEFAULT_FONT_URL` | Google Fonts Montserrat URL |
| `DEFAULT_ID_PROVIDER_NAME` | `eSignet` |
| `POLLING_URL` | `<api-base>/actuator/health` |
| `POLLING_INTERVAL` | `10000` (ms) |
| `POLLING_TIMEOUT` | `5000` (ms) |
| `POLLING_ENABLED` | `true` |

Feature flags, fetched at runtime from `theme/config.json` (boolean map): `background_logo`,
`footer`, `otp_info_icon`, `biometrics_info_icon`, `pin_info_icon`, `username_info_icon`,
`remove_language_indicator_pipe`, `outline_toggle`, `outline_dropdown`.

Styling: `theme/variables.css` (CSS custom properties), `CSS_IMAGE_VARIABLES` (logo/background
image path overrides under `public/images/`).

Captcha rendering values (`provider`/`siteKey`/`theme`/`size`) come from the backend's flow YAML,
sourced from `MOSIP_ESIGNET_CAPTCHA_SITE_PROVIDER`/`_SITE_KEY` (§3) — not a UI env var.

Container config: `oidc-ui/Dockerfile` build ARGs (`defaultLang`, `defaultWellknown`,
`defaultFavicon`, `defaultTitle`, `defaultIdProviderName`, `uiDefaultTheme`, `pollingUrl`,
`pollingInterval`, `pollingTimeout`, `pollingEnabled`, `oidcUIPublicUrl`) seed the runtime defaults
above; `i18n_url_env`/`theme_url_env`/`images_url_env` optionally fetch i18n/theme/image bundle
overrides at container start.

## 11. Declarative UI assets

| Variable | YAML key | Default |
|---|---|---|
| `MOSIP_ESIGNET_AUTH_FLOW_ID` | `auth_flow_id` | `flow-esignet` |
| `MOSIP_ESIGNET_THEME_ID` | `theme_id` | `theme-esignet` |
| `MOSIP_ESIGNET_LAYOUT_ID` | `layout_id` | `layout-esignet` |

Selects which files under `<DATA_DIR>/flows/`, `<DATA_DIR>/themes/`, `<DATA_DIR>/layouts/` are
loaded, cached per `design_cache_ttl_secs`/`flow_cache_ttl_secs` (§5). i18n files:
`<DATA_DIR>/i18n/<lang>.yaml`; shipped languages `ar, en, es, fr, hi, km, kn, si, ta`; `en` is the
fallback. See [`configuration/acr.md`](configuration/acr.md) and
[`configuration/login-id.md`](configuration/login-id.md) for the ACR and login-ID config inside
these files.

## 12. Per-client configuration

Configured per client via `/client-mgmt/client` (`POST`/`PUT`/`PATCH`/`GET`), stored in
`client_detail` (`esignet-service/internal/clientmgmt/validate.go`). The older
`/client-mgmt/oidc-client` and `/client-mgmt/oauth-client` endpoints are deprecated aliases with
narrower per-profile validation — use `/client-mgmt/client` for new integrations.

| Field | Constraint |
|---|---|
| `authContextRefs` | ≥1 required; must be recognised `mosip:idp:acr:*` values — [`configuration/acr.md`](configuration/acr.md) |
| `userClaims` | ≥1 required; must be one of eleven recognised OIDC claim names — [`configuration/claims.md`](configuration/claims.md) |
| `redirectUris` | ≥1, unique, ≤1024 chars; wildcards (`*`/`**`) only as whole path segments |
| `additionalConfig` | allow-listed keys only: `userinfo_response_type`, `id_token_response_type`, `consent_expire_in_mins`, `signup_banner_required`, `forgot_pwd_link_required`, `require_pushed_authorization_requests`, `dpop_bound_access_tokens`, `require_pkce`, `purpose`, `allowed_authorization_scopes` |
| `clientId` / `clientName` / `relyingPartyId` / `logoUri` | ≤50 / ≤256 / ≤50 / ≤1024 chars |
| `grantTypes` | must be exactly `authorization_code` |
| `clientAuthMethods` | must be exactly `private_key_jwt` |
