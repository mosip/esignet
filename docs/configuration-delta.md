# Configuration delta: esignet `v1.8.0` (Java) → `v2.0.0` (Go)

This page lists **only the configuration differences** between the Java eSignet `v1.8.0` and the Go eSignet `v2.0.0`,
anchored on the Go service's process environment variables (`esignet-service/.env` / `.env.example`).
Java's IDA/mock/Sunbird **authenticator plugin** properties live in the separate `esignet-plugins`
repo (`v1.4.0`, the version pinned for the 1.8.0 release) — see §5.7.

Legend: **Renamed** — same setting, new key. **Changed** — same setting, new default.
**Added** — new in Go. **Removed** — dropped. **Moved** — no longer a backend setting
(now declarative YAML under `<DATA_DIR>` or a per-client DB row).

---

## 1. Configuration model change

Both services get their configuration input the same way: **process environment variables** set at
deploy time. Java maps them onto dotted keys via Spring's
**relaxed binding** (`MOSIP_ESIGNET_HOST` → `mosip.esignet.host`); Go reads them **directly by name**
(`os.Getenv`, `UPPER_SNAKE` keys), applying them over the values already decoded from
`deployment.yaml` — `LoadAppConfig` parses that file first, then `applyDefaults` resolves
settings that go through `envOrConfigOrDefault` and friends as **env var → loaded YAML value →
compiled-in default** — with no Spring-style properties/binding layer. Not every field follows
that chain: `METRICS_PORT` and the pprof settings are environment-only (no YAML fallback), and
several `Flow`/`Observability` fields are hard-coded in `applyDefaults` regardless of `deployment.yaml`.

The one real difference: Java can additionally source these values from a **Spring Cloud Config
Server** (`bootstrap.properties: spring.cloud.config.uri`), serving them centrally instead of as
per-deployment env vars. Go has no config-server equivalent — `deployment.yaml` is loaded locally,
and env vars are the only override on top of it.

---

## 2. Changed defaults

Same meaning in both, different shipped default.

| Setting | Java default | Go env var | Go default |
|---|---|---|---|
| Access/ID-token lifetime | `3600` (two separate properties: `id-token-expire-seconds`, `access-token-expire-seconds`) | `MOSIP_ESIGNET_JWT_VALIDITY_PERIOD` (unified; `MOSIP_ESIGNET_OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS` overrides access-token only) | **`120`** |
| Pushed Authorization Request (PAR) expiry | `60` (`mosip.esignet.par.expire-seconds`) | `MOSIP_ESIGNET_OAUTH_PAR_EXPIRY_SECONDS` | **`3600`** |
| Auth-code lifetime | `300` (cache TTL, not a standalone property) | `MOSIP_ESIGNET_OAUTH_AUTH_CODE_LIFETIME_SECONDS` | **`60`** |
| Cache backend | `redis` | `MOSIP_ESIGNET_CACHE_TYPE` | **`inmemory`** |
| Signing algorithms | `RS256,PS256` — hardcoded literals inside the `discovery.key-values` blob, not an independently configurable property | `MOSIP_ESIGNET_OAUTH_SUPPORTED_SIGNING_ALGORITHMS` | **`PS256,ES256,ES256K,EdDSA`** |
| Encryption algorithms | `RSAXXXXX` — a literal placeholder value in `discovery.key-values` (not a real algorithm id; enc method hardcoded to `A128GCM`) | `MOSIP_ESIGNET_OAUTH_SUPPORTED_ENCRYPTION_ALGORITHMS` | **`RSA-OAEP,RSA-OAEP-256`** |
| DPoP clock skew | `10` (`mosip.esignet.dpop.clock-skew`) | `MOSIP_ESIGNET_DPOP_LEEWAY` | `10` (renamed, value unchanged) |

---

## 3. Added in Go (no Java equivalent)

Go-only settings with nothing comparable on the Java side. `MOSIP_ESIGNET_CACHE_TYPE` (§5.4) and
`KEYMANAGER_*`/`CRYPTOMANAGER_*` (§5.6) are **not** listed here — they replace existing Java
settings (`spring.cache.type`, `mosip.kernel.*`) rather than being net-new.

| Env var(s) | Purpose |
|---|---|
| `OUTBOUND_HTTP_CLIENT_*`, `OUTBOUND_IDSYSTEM_HTTP_CLIENT_*` | Outbound HTTP timeouts / pool sizes |
| `INBOUND_HTTP_SERVER_*` | Inbound server timeouts / limits |
| `DATABASE_*` pool, `REDIS_*` pool | Connection-pool tuning |
| `MOSIP_ESIGNET_AUTH_FLOW_ID`, `MOSIP_ESIGNET_THEME_ID`, `MOSIP_ESIGNET_LAYOUT_ID` | Default flow / theme / layout ids |
| `MOSIP_ESIGNET_RESOURCE_SERVERS_JSON` | Resource-indicator (RFC 8707) config |
| `MOSIP_ESIGNET_CLIENT_CACHE_TTL_SECS` | Client-detail cache TTL |
| `MOSIP_ESIGNET_CORS_ALLOWED_ORIGIN_REGEX` | CORS origin allow-list |
| `MOSIP_ESIGNET_PPROF_ENABLED`, `MOSIP_ESIGNET_PPROF_PORT`, `METRICS_PORT` | Profiling / metrics endpoints |
| `MOSIP_ESIGNET_MOCK_*` | First-class mock authn provider |

---

## 4. Removed in Go

| Java setting(s) | Reason |
|---|---|
| `spring.kafka.*`, `mosip.esignet.kafka.*`, cache names `linked*` | Linked (QR cross-device) flow + Kafka removed |
| `mosip.esignet.binding.*` | Key-binding subsystem removed |
| `mosip.esignet.security.ignore-csrf-urls`, `ignore-auth-urls`, `server.servlet.path` filters | Not applicable to the Go server |
| `mosip.esignet.discovery.key-values` | Discovery document derived at runtime |
| `mosip.kernel.keymanager.*` health/registration | Kernel keymanager replaced by in-tree keymanager |
| `spring.cloud.config.*`, `spring.profiles.*` | No config server / profiles |

---

## 5. Per-area delta

### 5.1 Service / host

| Java | Go |
|---|---|
| `mosip.esignet.host`, `mosip.esignet.domain.url` (`=https://${mosip.esignet.host}`) | `MOSIP_ESIGNET_HOST` (also used as issuer); `MOSIP_ESIGNET_BASE_URL` optionally overrides the public URL |
| `server.port` | `PORT` |
| `${NAMESPACE:esignet}` | `NAMESPACE` |
| `logging.level.*` | `LOG_LEVEL` |
| — | `DATA_DIR` (root for declarative YAML) |

### 5.2 Token lifetimes

See §2 — access/id-token, PAR and auth-code all changed defaults; access/id are unified under
`MOSIP_ESIGNET_JWT_VALIDITY_PERIOD` (with `MOSIP_ESIGNET_OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS` as an
access-token-only override on top). `MOSIP_ESIGNET_JWT_LEEWAY` and `MOSIP_ESIGNET_DPOP_LEEWAY` are new
leeway knobs.

### 5.3 OAuth / OIDC

| Java | Go |
|---|---|
| hardcoded in `discovery.key-values`/`oauth.key-values` (not a standalone property — see §2) | `MOSIP_ESIGNET_OAUTH_SUPPORTED_SIGNING_ALGORITHMS`, `MOSIP_ESIGNET_OAUTH_SUPPORTED_ENCRYPTION_ALGORITHMS` |
| `spring.security.oauth2.resourceserver.jwt.issuer-uri`/`jwk-set-uri` (Keycloak) | `MOSIP_ESIGNET_SECURITY_ISSUER_URL`, `MOSIP_ESIGNET_SECURITY_JWKS_URL` (generic; disabled by default) |
| — (fixed kernel-keymanager app-id `OIDC_SERVICE`, not configurable) | `MOSIP_ESIGNET_SIGNING_KEY_REF_ID` (configurable, default `RSA_2048`) — **Added**, not a rename |
| — | `MOSIP_ESIGNET_RESOURCE_SERVERS_JSON` (RFC 8707) |

### 5.4 Cache

| Java | Go |
|---|---|
| `spring.cache.type=redis` | `MOSIP_ESIGNET_CACHE_TYPE` (`redis` \| `inmemory`, default `inmemory`) |
| `redis.host/port/password` | `REDIS_HOST`, `REDIS_PORT`, `REDIS_PASSWORD` (+ pool vars) |

### 5.5 Database

| Java | Go |
|---|---|
| `mosip.esignet.database.hostname/port/name/username` | `DATABASE_HOST`, `DATABASE_PORT`, `DATABASE_NAME`, `DATABASE_USERNAME` |
| `db.dbuser.password` | `DATABASE_PASSWORD` or `DB_DBUSER_PASSWORD` |
| JDBC URL hard-coded `?currentSchema=esignet` | schema is separate: `KEYMANAGER_DB_SCHEMA=esignet`; `DATABASE_URL` takes precedence and is passed through `ensurePostgresSSLMode` (appends `sslmode=disable` only when the DSN doesn't already specify one) |

DSN resolution order (`resolveDBDSN`): `DATABASE_URL`, if set, wins outright; otherwise, if any
individual `DATABASE_*` / `DB_DBUSER_PASSWORD` var is set, those are assembled into the connection
string; otherwise the `deployment.yaml` DSN is used if non-empty; otherwise compiled-in defaults
(`localhost:5455`, database `mosip_esignet`, user `postgres`) are assembled.

### 5.6 Keymanager / crypto

| Java | Go |
|---|---|
| `mosip.kernel.keymanager.keystore.*` | `KEYMANAGER_KEYSTORE_TYPE`, `KEYMANAGER_PKCS12_*`, `KEYMANAGER_PKCS11_*` |
| certificate DN props | `KEYMANAGER_CERT_CN`, `KEYMANAGER_CERT_OU`, `KEYMANAGER_CERT_O`, `KEYMANAGER_CERT_L`, `KEYMANAGER_CERT_ST`, `KEYMANAGER_CERT_C` |
| key-length / validity / cache | `KEYMANAGER_ASYMMETRIC_KEY_LENGTH`, `KEYMANAGER_SYMMETRIC_KEY_VALIDITY_DAYS`, `KEYMANAGER_KEY_CACHE_EXPIRE_MINS` |
| ref-id allow-lists | `KEYMANAGER_SYMMETRIC_KEY_ALLOWED_REF_IDS`, `KEYMANAGER_CERTIFICATE_ALLOWED_REF_IDS`, `KEYMANAGER_FOREIGN_DOMAIN_ALLOWED_APP_IDS` |
| `mosip.kernel.data-key-splitter`, JWT/nonce/thumbprint | `CRYPTOMANAGER_DATA_KEY_SPLITTER`, `CRYPTOMANAGER_JWT_ENFORCE_2048`, `CRYPTOMANAGER_CALLER_NONCE_ALLOWED_REF_IDS`, `CRYPTOMANAGER_THUMBPRINT_CACHE_EXPIRE_MINS` |

### 5.7 Authn provider

Java's IDA/mock/Sunbird property definitions live in the separate `esignet-plugins` repo (`v1.4.0`,
the version pinned for the 1.8.0 release), not in `esignet-java` — verified there directly.
`mosip.esignet.integration.authenticator` selects the plugin bean; Go's `MOSIP_ESIGNET_AUTHN_PROVIDER`
(`mosip` \| `sunbird` \| `mock`) is the equivalent switch.

**IDA (`mosip-identity-plugin`):**

| Java | Go |
|---|---|
| `mosip.esignet.ida.auth.domain`, `mosip.esignet.ida.otp.domain`, `mosip.esignet.ida.internal.domain`, `mosip.esignet.authmanager.domain`, `mosip.esignet.auditmanager.domain` (each independently `${MOSIP_API_INTERNAL_HOST:default}`) | single `MOSIP_API_INTERNAL_HOST` derives all IDA/authmanager/auditmanager endpoint URLs |
| `mosip.esignet.authenticator.ida.misp-license-key` (`=${mosip.esignet.misp.key}`) | `MOSIP_ESIGNET_MISP_KEY` |
| `mosip.esignet.authenticator.ida.secret-key` (`=${mosip.ida.client.secret}`) | `MOSIP_IDA_CLIENT_SECRET` |
| `mosip.esignet.authenticator.ida-domainUri` (`=${mosip.esignet.domain.url}`) | `MOSIP_ESIGNET_DOMAIN_URL` — this is what that Go var is for (see the trap note in §5.1) |
| `mosip.esignet.authenticator.ida.cert-url`, `mosip.esignet.authenticator.ida.send-otp-url`, `mosip.esignet.authenticator.ida.kyc-auth-url`, `mosip.esignet.authenticator.ida.kyc-exchange-url`, `mosip.esignet.authenticator.ida.get-certificates-url`, `mosip.esignet.authenticator.ida.auth-token-url`, `mosip.esignet.authenticator.ida.audit-manager-url` (individual full-URL overrides) | `MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL`, `MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL`, `MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL`, `MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL`, `MOSIP_ESIGNET_AUTHENTICATOR_IDA_GET_CERTIFICATES_URL`, `MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUTH_TOKEN_URL`, `MOSIP_ESIGNET_AUTHENTICATOR_IDA_AUDIT_MANAGER_URL` |
| `mosip.esignet.authenticator.ida.client-id`, `mosip.esignet.authenticator.ida.app-id` | `MOSIP_ESIGNET_AUTHENTICATOR_IDA_CLIENT_ID`, `MOSIP_ESIGNET_AUTHENTICATOR_IDA_APP_ID` |
| `mosip.esignet.authenticator.ida-env` (`=${IDA_AUTHENTICATOR_ENV:Staging}`) | `IDA_AUTHENTICATOR_ENV` — same env var name, kept as-is, not renamed |

**Sunbird RC (`sunbird-rc-plugin`):**

| Java | Go |
|---|---|
| `mosip.esignet.authenticator.sunbird-rc.auth-factor.kbi.individual-id-field` | `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_INDIVIDUAL_ID_FIELD` |
| `mosip.esignet.authenticator.sunbird-rc.auth-factor.kbi.field-details` | `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_FIELD_DETAILS` |
| `mosip.esignet.authenticator.sunbird-rc.auth-factor.kbi.registry-search-url` | `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_REGISTRY_SEARCH_URL` |
| `mosip.esignet.authenticator.sunbird-rc.kbi.entity-id-field` | `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_KBI_ENTITY_ID_FIELD` |
| `mosip.esignet.authenticator.sunbird-rc.identity-openid-claims-mapping` | `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_IDENTITY_OPENID_CLAIMS_MAPPING` |
| `mosip.esignet.authenticator.sunbird-rc.registry-get-url` | `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REGISTRY_GET_URL` |

**Mock (`mock-plugin`):**

| Java | Go |
|---|---|
| `mosip.esignet.mock.domain.url` | `MOSIP_ESIGNET_MOCK_DOMAIN_URL` |
| `mosip.esignet.mock.authenticator.kyc-auth-url` | `MOSIP_ESIGNET_MOCK_KYC_AUTH_URL` |
| `mosip.esignet.mock.authenticator.kyc-exchange-url`, `mosip.esignet.mock.authenticator.kyc-exchange-v3-url` | `MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_URL`, `MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_V3_URL` |
| `mosip.esignet.mock.authenticator.send-otp` | `MOSIP_ESIGNET_MOCK_SEND_OTP_URL` |

### 5.8 Claims / ACR / login-id

Moved to declarative YAML/DB — no backend env vars. Java `amr_acr_mapping.json`, `mosip.esignet.claims.*`
and login-id properties are replaced by `flows/`, `layouts/`, `themes/`, `i18n/` under `<DATA_DIR>` and
`client_detail` rows.

### 5.9 Client management

| Java | Go |
|---|---|
| Keycloak-specific issuer / admin props | generic `MOSIP_ESIGNET_SECURITY_ISSUER_URL` / `MOSIP_ESIGNET_SECURITY_JWKS_URL` (client-mgmt issuer disabled by default) |
| `mosip.esignet.security.auth.{get,post,put}-urls` (URL → required-scope map) | `security_config.scope_mapping` in `deployment.yaml` (see §6) |

### 5.10 Captcha

Renamed, not removed — same per-request captcha validation, different config surface.

| Java | Go |
|---|---|
| `mosip.esignet.captcha.validator-url` | `MOSIP_ESIGNET_CAPTCHA_VALIDATOR_URL` |
| `mosip.esignet.captcha.module-name` | `MOSIP_ESIGNET_CAPTCHA_MODULE_NAME` |
| `mosip.esignet.captcha.site-key` | `MOSIP_ESIGNET_CAPTCHA_SITE_KEY` |
| — | `MOSIP_ESIGNET_CAPTCHA_SITE_PROVIDER`, `MOSIP_ESIGNET_CAPTCHA_TIMEOUT_SECS` (**Added**) |
| `mosip.esignet.captcha.required` (per-auth-factor list, e.g. `send-otp,pwd,kbi,binding-otp`) | no per-factor list — validation applies uniformly, and is skipped entirely (fails open) when `MOSIP_ESIGNET_CAPTCHA_VALIDATOR_URL` is unset |

### 5.11 UI (oidc-ui)

| Java | Go |
|---|---|
| UI base/scheme/host/port | `MOSIP_ESIGNET_OIDC_UI_SCHEME`, `MOSIP_ESIGNET_OIDC_UI_HOSTNAME`, `MOSIP_ESIGNET_OIDC_UI_PORT` |
| login / error paths | `MOSIP_ESIGNET_OIDC_UI_LOGIN_PATH`, `MOSIP_ESIGNET_OIDC_UI_ERROR_PATH` |
| theme / layout | flow YAML (`MOSIP_ESIGNET_AUTH_FLOW_ID`, `MOSIP_ESIGNET_THEME_ID`, `MOSIP_ESIGNET_LAYOUT_ID`) |

---

## 6. Java settings with no Go env var (deployment.yaml-only)

Some Java settings (e.g. OAuth allow-lists, DPoP policy sub-fields, client-management `scope_mapping`)
have no Go env var equivalent — they live in `esignet-service/data/deployment.yaml` and can be modified
there directly if needed.
