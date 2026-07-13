# eSignet (ThunderID engine embedder)

Module: `github.com/mosip/esignet`

A Go service that embeds the ThunderID authorization engine with PostgreSQL-backed OIDC client management, Redis-backed session/flow storage, and pluggable authentication (mock identity system, MOSIP IDA, or SunbirdRC KBI).

## Prerequisites

- Go 1.26+
- Bash (Git Bash on Windows) to run `make.sh`
- OpenSSL (for signing key generation; bundled with Git Bash)
- PostgreSQL 14+ (client management persistence)
- Redis 6.0+ (runtime / session store; requires `KEEPTTL` support)
- Network access to fetch the Thunder backend module (see `go.mod` `replace` directive)

## Repository layout

```text
esignet-service/
  cmd/esignet/main.go               # HTTP entrypoint
  internal/clientmgmt/              # OIDC client management API + sqlc-generated db/ layer
  internal/config/                  # env-based config (app, DB, Redis)
  internal/engine/                  # Thunder engine providers/executors (Actor, Authn, Authz, Consent, Flow, ...)
    mock/                           # Mock authenticator (local/dev; talks to esignet-mock-services)
    mosip/                          # MOSIP IDA authn provider + OTP executor + auditor
    sunbird/                        # SunbirdRC KBI authn provider
    runtimestores/                  # Redis-backed / in-memory flow, session, PAR stores
    shared/                         # Code shared across engine providers
  internal/security/                # JWKS validation, scope middleware, request-time checks
  internal/log/                     # Structured logging helpers
  internal/common/                  # Shared models/utils
  data/
    deployment.yaml                 # engine deployment defaults (env-var expanded)
    flows/, i18n/, layouts/, themes/  # declarative YAML (flows, translations, layout, theme)
  keys/                             # signing.key + signing.crt (local, gitignored)
  sqlc.yaml                         # SQLC codegen config
  make.sh                           # build/run/test entry point (Linux + Git Bash)
  Dockerfile
```

## Build

All build targets generate TLS signing material first (`./make.sh keys`), because the engine needs a PEM key pair for JWT signing.

| Command | Output | Notes |
|---------|--------|--------|
| `./make.sh keys` | `keys/signing.key`, `keys/signing.crt` | One-time (or regenerate locally). Not committed. |
| `./make.sh build` | `out/esignet.exe` (Windows), `out/esignet` (Linux) | Production binary. |
| `./make.sh test` | — | Unit tests with race detector. |
| `./make.sh coverage` | `coverage.out` | Coverage profile + per-package summary. |
| `./make.sh coverage-html` | `coverage.html` | Opens full HTML report. |

```bash
cd esignet-service
./make.sh build
```

If `go build` fails with a missing module, run `go mod download` first.

The checked-in `go.mod` `replace` directive pins a Thunder backend fork until the upstream release is available. Refresh it with `./make.sh update-thunder`, which resolves the latest commit on `THUNDER_BRANCH` (default `feature/thunderid-engine-impr`) of `github.com/thunder-id/thunderid` and points the `replace` directive at `THUNDER_MODULE` (default `github.com/thunder-id/thunderid/backend`).

## Run

### Quick start (development)

```bash
cp .env.example .env          # fill in DATABASE_* / DB_DBUSER_PASSWORD and REDIS_* at minimum
./make.sh run
```

Copy `.env.example` to `.env` to override defaults, or pass overrides on the command line (`./make.sh run PORT=9090`).

### Binary

```bash
./make.sh keys && ./make.sh build

export PORT=8088
export MOSIP_ESIGNET_HOST=http://127.0.0.1:8088
export DATABASE_HOST=localhost
export DATABASE_USERNAME=esignet
export DB_DBUSER_PASSWORD=secret
export DATABASE_NAME=mosip_esignet
export REDIS_HOST=localhost
export AUTHN_PROVIDER=mosip

./out/esignet.exe   # ./out/esignet on Linux
```

Startup log:

```text
time=... level=INFO msg="Scope enforcement disabled; set ISSUER_URL and JWKS_URL in security_config to enable"
time=... level=INFO msg="Request time validation enabled"  leeway=5m0s
time=... level=INFO msg="authn provider selected"  provider=mosip
time=... level=INFO msg="redis connected"   key_prefix=esignet:
time=... level=INFO msg="server listening"  addr=:8088  issuer=http://127.0.0.1:8088
```

(If `security_config.issuer_url` and `jwks_url` are both set in `data/deployment.yaml`, the first line is replaced by `msg="Scope enforcement enabled" jwks_endpoint=... issuer=...`.)

Set `LOG_LEVEL=debug` for verbose tracing.

## Environment variables

### Core engine

| Variable | Default | Purpose |
|----------|---------|---------|
| `LOG_LEVEL` | `info` | `debug` / `info` / `warn` / `error` |
| `PORT` | `8088` | HTTP listen port (code default; `./make.sh run`/`build` default `PORT` to `8080` unless overridden) |
| `NAMESPACE` | `esignet` | Service identifier; also used as the Redis cache key prefix |
| `MOSIP_ESIGNET_HOST` | `http://127.0.0.1:<PORT>` | OIDC issuer, JWT `iss`, discovery base |
| `DATA_DIR` | `./data` | Declarative YAML root (`flows/`, `i18n/`, `layouts/`, `themes/`, `keys/`) |
| `CRYPTO_ENCRYPTION_KEY` | _(required)_ | Hex key for `crypto.encryption.key` in `data/deployment.yaml`; the process panics at startup if unset |
| `AUTHN_PROVIDER` | `mock` | `mock` (default; talks to esignet-mock-services), `mosip` (MOSIP IDA), or `sunbird` (SunbirdRC registry KBI). `./make.sh run` overrides the default to `mosip`. |
| `LAYOUT_ID` | `layout-esignet` | Declarative layout id |
| `THEME_ID` | `theme-esignet` | Declarative theme id |
| `AUTH_FLOW_ID` | `flow-esignet` | Declarative authentication flow id |
| `OAUTH_AUTH_CODE_LIFETIME_SECONDS` | `120` | Authorization code lifetime |
| `OAUTH_ACCESS_TOKEN_LIFETIME_SECONDS` | `3600` | Access token lifetime |
| `OAUTH_PAR_EXPIRY_SECONDS` | `3600` | Pushed authorization request expiry |

Signing keys are generated locally by `./make.sh keys` at `keys/signing.key` and `keys/signing.crt`.

### OIDC UI (login gate)

Authorize redirects are sent to the Thunder gate client:

| Variable | Default | Purpose |
|----------|---------|---------|
| `OIDC_UI_SCHEME` | `http` | Gate UI scheme |
| `OIDC_UI_HOSTNAME` | `127.0.0.1` | Gate UI hostname |
| `OIDC_UI_PORT` | `3000` | Gate UI port |
| `OIDC_UI_LOGIN_PATH` | `/signin` | Login page path |
| `OIDC_UI_ERROR_PATH` | `/error` | Error page path |

### PostgreSQL

| Variable | Default | Purpose |
|----------|---------|---------|
| `DATABASE_URL` | _(empty)_ | Full DSN — takes precedence over the individual vars below if set |
| `DATABASE_HOST` | `localhost` | |
| `DATABASE_PORT` | `5455` | |
| `DATABASE_NAME` | `mosip_esignet` | |
| `DATABASE_USERNAME` | `postgres` | |
| `DATABASE_PASSWORD` / `DB_DBUSER_PASSWORD` | _(empty)_ | Either name is accepted; `DATABASE_PASSWORD` takes precedence if both are set |
| `DB_MAX_OPEN_CONNS` | `25` | Max open connections |
| `DB_MAX_IDLE_CONNS` | `5` | Max idle connections |
| `DB_CONN_MAX_LIFETIME_SECS` | `0` (no limit) | Connection lifetime |
| `DB_CONN_MAX_IDLE_TIME_SECS` | `60` | Idle timeout |

### Redis

| Variable | Default | Purpose |
|----------|---------|---------|
| `REDIS_URL` | _(empty)_ | Full DSN (`redis://` or `rediss://`) — takes precedence if set |
| `REDIS_HOST` | `localhost` | |
| `REDIS_PORT` | `6379` | |
| `REDIS_PASSWORD` | _(empty)_ | |
| `REDIS_DB` | `0` | |
| `REDIS_TLS_ENABLED` | `false` | Enable TLS (automatic for `rediss://`) |
| `REDIS_SENTINEL_MASTER` | _(empty)_ | Master name — enables Sentinel mode |
| `REDIS_SENTINEL_ADDRS` | _(empty)_ | Comma-separated sentinel addresses |
| `REDIS_POOL_SIZE` | `10` | Max connections |
| `REDIS_MIN_IDLE_CONNS` | `2` | Warm connections kept ready |
| `REDIS_CONN_MAX_IDLE_TIME_SECS` | `300` | |
| `REDIS_CONN_MAX_LIFETIME_SECS` | `0` | 0 = no limit |
| `REDIS_DIAL_TIMEOUT_SECS` | `5` | |
| `REDIS_READ_TIMEOUT_SECS` | `3` | |
| `REDIS_WRITE_TIMEOUT_SECS` | `3` | |
| `REDIS_KEY_PREFIX` | `esignet:` | Namespace prefix for all keys |

### Client management API

Bearer-token scope enforcement on `/client-mgmt/*` is governed by the `security_config` block in `data/deployment.yaml`, not by dedicated environment variables:

```yaml
security_config:
  issuer_url: ""
  jwks_url: ""
  jwks_cache_ttl: 3000
```

Enforcement is enabled only when both `issuer_url` and `jwks_url` are non-empty (edit `data/deployment.yaml` directly, or template it before deploying — there is currently no `CLIENT_MGMT_*` env-var override). When enabled, requests must carry `Authorization: Bearer <token>` with a valid signature (checked against the JWKS endpoint), a matching `iss`, and an unexpired `exp`. Scope-claim matching against a configurable required scope is not yet wired up in this build; leave `issuer_url`/`jwks_url` empty during local development to run with enforcement disabled.

### Authentication provider

`AUTHN_PROVIDER` selects the plugin: `mock` (default in the binary; talks to a running [esignet-mock-services](https://github.com/mosip/esignet-mock-services) instance), `mosip` (MOSIP IDA — `./make.sh run` defaults to this), or `sunbird` (SunbirdRC registry KBI).

#### Mock authentication

Used when `AUTHN_PROVIDER=mock`. The mock provider is a thin HTTP client to a running mock-identity-system instance; it does not validate credentials or store identities itself.

| Variable | Default | Purpose |
|----------|---------|---------|
| `MOSIP_ESIGNET_MOCK_DOMAIN_URL` | `http://mock-identity-system.mockid` | Base URL; endpoint URLs below default from `<url>/v1/mock-identity-system/...` unless overridden |
| `MOSIP_ESIGNET_MOCK_KYC_AUTH_URL` | `<domain>/v1/mock-identity-system/v2/kyc-auth` | KYC auth endpoint |
| `MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_URL` | `<domain>/v1/mock-identity-system/kyc-exchange` | KYC exchange endpoint |
| `MOSIP_ESIGNET_MOCK_KYC_EXCHANGE_V3_URL` | `<domain>/v1/mock-identity-system/v3/kyc-exchange` | KYC exchange (v3) endpoint |
| `MOSIP_ESIGNET_MOCK_SEND_OTP_URL` | `<domain>/v1/mock-identity-system/send-otp` | Send OTP endpoint |

#### MOSIP IDA

| Variable | Default | Purpose |
|----------|---------|---------|
| `MOSIP_API_INTERNAL_HOST` | _(empty)_ | Internal MOSIP API host; used to derive IDA URLs |
| `MOSIP_ESIGNET_MISP_KEY` | _(empty)_ | MISP license key used in IDA endpoint paths |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL` | `<host>/mosip-certs/ida-partner.cer` | IDA partner certificate URL |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND_OTP_URL` | `<host>/idauthentication/v1/otp/<key>/` | Send OTP endpoint |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_AUTH_URL` | `<host>/idauthentication/v1/kyc-auth/delegated/<key>/` | KYC auth endpoint |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC_EXCHANGE_URL` | `<host>/idauthentication/v1/kyc-exchange/delegated/<key>/` | KYC exchange endpoint |
| `MOSIP_ESIGNET_DOMAIN_URL` | `MOSIP_API_INTERNAL_HOST` | Domain URI sent in IDA requests |
| `IDA_AUTHENTICATOR_ENV` | `Staging` | IDA environment label |
| `MOSIP_P12_PATH` | _(empty)_ | Partner keystore (required for MOSIP auth) |
| `MOSIP_P12_PASSWORD` | _(empty)_ | Partner keystore password |

##### Audit publishing (`AUTHN_PROVIDER=mosip` only)

Flow lifecycle events are published to mosip-audit-manager. A token is fetched from authmanager (client id/secret/app id) and sent to the audit endpoint as a `Cookie: Authorization=<token>` header; when the secret is unset, audits are posted without that cookie. For any other `AUTHN_PROVIDER`, events are just logged via the application logger.

| Variable | Default | Purpose |
|----------|---------|---------|
| `MOSIP_ESIGNET_AUDIT_MANAGER_URL` | `<MOSIP_API_INTERNAL_HOST>/v1/auditmanager/audits` | Audit ingestion endpoint. Startup fails if this cannot be resolved (i.e. both this and `MOSIP_API_INTERNAL_HOST` are unset) |
| `MOSIP_ESIGNET_AUTH_TOKEN_URL` | `<MOSIP_API_INTERNAL_HOST>/v1/authmanager/authenticate/clientidsecretkey` | authmanager token endpoint |
| `MOSIP_ESIGNET_IDA_CLIENT_ID` | `mosip-ida-client` | authmanager client id |
| `MOSIP_ESIGNET_IDA_CLIENT_SECRET` | _(empty)_ | authmanager client secret |
| `MOSIP_ESIGNET_IDA_APP_ID` | `ida` | authmanager app id |

#### SunbirdRC KBI

| Variable | Default | Purpose |
|----------|---------|---------|
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_REGISTRY_SEARCH_URL` | _(empty)_ | Registry search endpoint (**required** for `sunbird`) |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REGISTRY_GET_URL` | _(empty)_ | Registry get/entity endpoint for OIDC claims |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_INDIVIDUAL_ID_FIELD` | `policyNumber` | Registry field the entered `individualId` maps to |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_KBI_ENTITY_ID_FIELD` | `osid` | Registry field holding the entity id from search |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_FIELD_DETAILS` | _(Insurance default)_ | JSON list of KBI fields collected from the user |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_IDENTITY_OPENID_CLAIMS_MAPPING` | _(Insurance default)_ | JSON map of OIDC claim → registry field |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REQUEST_TIMEOUT_SECS` | `10` | HTTP timeout for registry calls |

See `.env.example` for copy-paste values including optional MOSIP URL overrides.

### SunbirdRC (KBI) authentication

With `AUTHN_PROVIDER=sunbird`, the user authenticates by knowledge-based identity: they enter an
`individualId` plus KBI fields (default `fullName` and `dob`), which are POSTed as exact-match filters to the
registry search URL. Authentication succeeds only when the registry returns **exactly one** matching entity;
its entity-id field (default `osid`) becomes the user id. Attributes are then fetched from the registry
get URL (`.../{entityId}`) and mapped to OIDC claims via the claims-mapping config. This reuses the
built-in `CredentialsAuthExecutor` (no custom executor).

## Client management API

When scope enforcement is enabled (see the "Client management API" subsection under [Environment variables](#environment-variables) above), endpoints require `Authorization: Bearer <token>`, validated against the JWKS endpoint with a matching `iss` and unexpired `exp`.

Requests and responses use the MOSIP envelope: `{"requestTime": "...", "request": {...}}` in, `{"responseTime": "...", "response": {"clientId", "status"}, "errors": []}` out. Validation failures return HTTP 200 with a populated `errors` array and documented `errorCode` values.

### Current API (`/client-mgmt/client`)

| Method | Path | Purpose |
|--------|------|---------|
| POST | `/client-mgmt/client` | Create client (v3 schema) |
| PUT | `/client-mgmt/client/{client_id}` | Full update; `status` is `active` or `inactive` in the request body |
| PATCH | `/client-mgmt/client/{client_id}` | Partial update; all fields optional; `status` is `ACTIVE` or `INACTIVE`; supports nullable `encPublicKey` for JWE userinfo |
| GET | `/client-mgmt/client/{client_id}` | Fetch client metadata (engine use) |

v3 create requires `clientId`, `clientName`, `clientNameLangMap`, `relyingPartyId`, `logoUri`, `authContextRefs`, `publicKey`, `userClaims`, `grantTypes`, and `clientAuthMethods`. `redirectUris` and `additionalConfig` are optional. `clientNameLangMap` is stored in `client_detail.name` as JSON with `@none` set to `clientName`.

`additionalConfig` supports `userinfo_response_type` (`JWS`/`JWE`), `purpose`, consent/UI flags, PAR, and DPoP settings. The JSON object is validated and persisted verbatim.

### Deprecated aliases (backward compatibility)

| Path | Difference from `/client-mgmt/client` |
|------|---------------------------------------|
| `/client-mgmt/oidc-client` | No `clientNameLangMap` or `additionalConfig`; PUT allows only four `authContextRefs` values |
| `/client-mgmt/oauth-client` | Requires `clientNameLangMap`; no `additionalConfig` |

### Example create (v3)

```http
POST /client-mgmt/client
```

```json
{
  "requestTime": "2024-01-01T00:00:00Z",
  "request": {
    "clientId": "my-app",
    "clientName": "My Application",
    "clientNameLangMap": {"eng": "My Application"},
    "relyingPartyId": "rp-001",
    "logoUri": "https://example.com/logo.png",
    "redirectUris": ["https://example.com/callback"],
    "userClaims": ["name", "email"],
    "authContextRefs": ["mosip:idp:acr:static-code"],
    "publicKey": {"kty": "RSA", "n": "...", "e": "AQAB"},
    "grantTypes": ["authorization_code"],
    "clientAuthMethods": ["private_key_jwt"],
    "additionalConfig": {
      "userinfo_response_type": "JWS",
      "consent_expire_in_mins": 30
    }
  }
}
```

The `public_key` hash is stored for uniqueness enforcement. `enc_public_key` and `enc_public_key_cert` are optional on create; PATCH can set or clear `encPublicKey`. Status defaults to `ACTIVE` on create.

### Update (PUT)

```http
PUT /client-mgmt/client/{client_id}
```

Mutable fields: `clientName`, `clientNameLangMap`, `logoUri`, `redirectUris`, `userClaims`, `authContextRefs`, `grantTypes`, `clientAuthMethods`, `status`, `additionalConfig`. `relyingPartyId` and `publicKey` are immutable after creation.

### Database schema

Run the DDL from `internal/clientmgmt/db/schema.sql` before starting the service. To regenerate the Go DB layer after schema changes:

```bash
./make.sh sqlc-install   # one-time
./make.sh sqlc
```

## Redis key layout

All runtime state is namespaced under `REDIS_KEY_PREFIX` (default `esignet:`):

| Prefix | Entity | Notes |
|--------|--------|-------|
| `esignet:flow:<id>` | Flow context | TTL set at creation; updated with `KEEPTTL` |
| `esignet:authcode:<code>` | Auth code | Deleted on first use by the engine |
| `esignet:authreq:<id>` | Auth request | Short-lived PAR / authorize state |
| `esignet:par:<uri>` | PAR object | RFC 9126 pushed authorization request |
| `esignet:jti:<jti>` | JTI replay guard | Presence = seen; TTL = token lifetime |
| `esignet:attrcache:<id>` | Attribute cache | Extendable TTL via `EXPIREAT` |

## Tests

```bash
./make.sh test           # unit tests with race detector
./make.sh coverage       # coverage profile → stdout summary
./make.sh coverage-html  # full HTML report → coverage.html
```

Unit tests use `miniredis` for Redis (no running Redis required) and mock queriers for the Postgres layer. Run `./make.sh keys` before tests that exercise JWT signing.

## Health check

```bash
curl -s http://127.0.0.1:8088/health
# ok
```

## Docker

```bash
./make.sh docker-build

docker run --rm -p 8088:8088 \
  -e MOSIP_ESIGNET_HOST=http://127.0.0.1:8088 \
  -e CRYPTO_ENCRYPTION_KEY=your-64-char-hex-key \
  -e DATABASE_URL=postgres://esignet:secret@host.docker.internal:5455/mosip_esignet?sslmode=disable \
  -e REDIS_URL=redis://host.docker.internal:6379/0 \
  -e AUTHN_PROVIDER=mosip \
  esignet:latest
```

On first start the entrypoint generates signing keys if absent. Data is baked in at `/home/mosip/data`.

## Demo credentials

| Field | Value |
|-------|-------|
| Username | `decl-user-1` |
| Password | `TempPassword123!` |
| Application | `decl-app-1` |
| Redirect URI | `https://localhost:3000` |

OAuth clients are created on demand (see [Postman](#postman) below) rather than pre-seeded.

## Postman

The collection lives in [`postman-collection/`](../postman-collection/README.md), a sibling of this directory.

1. Import `Go-eSignet.postman_environment.json`, then `Go-eSignet.postman_collection.json`.
2. Select the **Go-eSignet (local)** environment; start the server with `MOSIP_ESIGNET_HOST` matching `baseUrl` (default `http://127.0.0.1:8080`).
3. Run **Client Management → Create client** — its pre-request script generates a fresh RSA key and `clientId` entirely inside Postman, no external tooling needed.
4. Run one of the numbered OAuth flow folders (1 — MOSIP OTP, 2 — MOSIP Credentials, 3 — MOSIP FAPI2) top to bottom.

Folders 1 and 2 require `AUTHN_PROVIDER=mosip` and MOSIP variables (see `.env.example`); folder 3 additionally requires Redis 6.0+ (PAR storage) and a client registered with `require_pushed_authorization_requests`/`dpop_bound_access_tokens`. See the [collection README](../postman-collection/README.md) for the full per-folder breakdown.

## OAuth

Authorize redirects to the gate client (`http://127.0.0.1:3000/signin?...` by default via `OIDC_UI_*`) with `executionId`, `authId`, and related query parameters. The Postman flow folders parse that redirect without following it (`followRedirects: false` on authorize). OAuth clients are confidential and use `private_key_jwt` at the token endpoint (with PKCE on authorize, or PAR + DPoP for the FAPI2 folder).

For an end-to-end check of the full authorization code flow (authorize → flow → callback → token → userinfo) and client management (create → get → update), run the [Postman collection](../postman-collection/README.md).
