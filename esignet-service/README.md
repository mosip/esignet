# eSignet (ThunderID engine embedder)

Module: `github.com/mosip/esignet`

A Go service that embeds the ThunderID authorization engine with PostgreSQL-backed OIDC client management, Redis-backed session/flow storage, and pluggable authentication (MOSIP IDA or SunbirdRC KBI).

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
  internal/clientmgmt/              # OIDC client management API
  internal/config/                  # env-based config (app, DB, Redis)
  internal/host/                    # Actor, Authn, Authz, Consent, executors
    mosip/                          # MOSIP IDA authn provider + OTP executor
    sunbird/                        # SunbirdRC KBI authn provider
  data/
    deployment.yaml                 # engine deployment defaults
    config/resources/               # declarative fixtures (flows, apps, OU, …)
  keys/                             # signing.key + signing.crt (local, gitignored)
  postman/                          # Postman collection + environment
  scripts/oauth-smoke.sh            # end-to-end OAuth smoke test
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

export MOSIP_ESIGNET_HOST=http://127.0.0.1:8080
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
time=... level=INFO msg="redis connected"   key_prefix=esignet:
time=... level=INFO msg="client mgmt scope enforcement"  required_scope=client_mgmt  jwks_endpoint=...
time=... level=INFO msg="authn provider selected"  provider=mosip
time=... level=INFO msg="server listening"  addr=:8080  issuer=http://127.0.0.1:8080
```

Set `LOG_LEVEL=debug` for verbose tracing.

## Environment variables

### Core engine

| Variable | Default | Purpose |
|----------|---------|---------|
| `LOG_LEVEL` | `info` | `debug` / `info` / `warn` / `error` |
| `PORT` | `8088` | HTTP listen port (`make.sh` defaults to `8080`) |
| `MOSIP_ESIGNET_HOST` | `http://127.0.0.1:<PORT>` | OIDC issuer, JWT `iss`, discovery base |
| `DATA_DIR` | `./data` | Declarative YAML root (`data/config/resources/`) |
| `JWT_AUDIENCE` | _(empty)_ | JWT `aud` claim |
| `JWT_PREFERRED_KEY_ID` | _(empty)_ | Preferred signing key id |
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

### Declarative defaults

| Variable | Default | Purpose |
|----------|---------|---------|
| `DEFAULT_THEME_ID` | _(empty)_ | Default theme for flows |
| `DEFAULT_LAYOUT_ID` | _(empty)_ | Default layout for flows |
| `DEFAULT_AUTH_FLOW_ID` | _(empty)_ | Default authentication flow |
| `DEFAULT_REGISTRATION_FLOW_ID` | _(empty)_ | Default registration flow |
| `DEFAULT_RECOVERY_FLOW_ID` | _(empty)_ | Default recovery flow |

### PostgreSQL

| Variable | Default | Purpose |
|----------|---------|---------|
| `POSTGRES_URL` | _(empty)_ | Full DSN — takes precedence if set |
| `DATABASE_HOST` | `localhost` | |
| `DATABASE_PORT` | `5432` | |
| `DATABASE_NAME` | `mosip_esignet` | |
| `DATABASE_USERNAME` | `postgres` | |
| `DB_DBUSER_PASSWORD` | _(empty)_ | |
| `DB_MAX_OPEN_CONNS` | `25` | Max open connections |
| `DB_MAX_IDLE_CONNS` | `5` | Max idle connections |
| `DB_CONN_MAX_LIFETIME_SECS` | `300` | Connection lifetime |
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

| Variable | Default | Purpose |
|----------|---------|---------|
| `CLIENT_MGMT_REQUIRED_SCOPE` | `client_mgmt` | Bearer token scope required on all `/client-mgmt/*` endpoints |
| `CLIENT_MGMT_ISSUER_URL` | _(empty)_ | Expected `iss` claim in Bearer tokens |
| `CLIENT_MGMT_JWKS_ENDPOINT` | _(empty)_ | JWKS URL for validating Bearer tokens |
| `CLIENT_MGMT_JWKS_CACHE_TTL_SECS` | `300` | JWKS key cache TTL |

Set `CLIENT_MGMT_ISSUER_URL` and `CLIENT_MGMT_JWKS_ENDPOINT` to match `MOSIP_ESIGNET_HOST` when validating tokens issued by the local engine.

### Authentication provider

| Variable | Default | Purpose |
|----------|---------|---------|
| `AUTHN_PROVIDER` | _(required)_ | `mosip` (MOSIP IDA) or `sunbird` (SunbirdRC registry KBI). `make.sh run` defaults to `mosip`. |

#### MOSIP IDA

| Variable | Default | Purpose |
|----------|---------|---------|
| `MOSIP_API_INTERNAL_HOST` | _(empty)_ | Internal MOSIP API host; used to derive IDA URLs |
| `MOSIP_ESIGNET_MISP_KEY` | _(empty)_ | MISP license key used in IDA endpoint paths |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_CERT_URL` | `<host>/mosip-certs/ida-partner.cer` | IDA partner certificate URL |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_SEND-OTP-URL` | `<host>/idauthentication/v1/otp/<key>/` | Send OTP endpoint |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC-AUTH-URL` | `<host>/idauthentication/v1/kyc-auth/delegated/<key>/` | KYC auth endpoint |
| `MOSIP_ESIGNET_AUTHENTICATOR_IDA_KYC-EXCHANGE-URL` | `<host>/idauthentication/v1/kyc-exchange/delegated/<key>/` | KYC exchange endpoint |
| `MOSIP_ESIGNET_DOMAIN_URL` | `MOSIP_API_INTERNAL_HOST` | Domain URI sent in IDA requests |
| `IDA_AUTHENTICATOR_ENV` | `Staging` | IDA environment label |
| `MOSIP_P12_PATH` | _(empty)_ | Partner keystore (required for MOSIP auth) |
| `MOSIP_P12_PASSWORD` | _(empty)_ | Partner keystore password |

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
built-in `CredentialsAuthExecutor` (no custom executor); the demo flow `decl-sunbird-flow-1` and app
`decl-app-sunbird` exercise it.

## Client management API

All endpoints require `Authorization: Bearer <token>` where the token carries the scope configured by `CLIENT_MGMT_REQUIRED_SCOPE` (default `client_mgmt`). The token is validated against the JWKS endpoint.

Responses follow the envelope `{"response": {...}}` on success and `{"errors": [{"errorCode": "...", "errorMessage": "..."}]}` on failure.

### Create OIDC client

```http
POST /client-mgmt/oidc-client
```

```json
{
  "client_id": "my-app",
  "client_name": "My Application",
  "rp_id": "rp-001",
  "logo_uri": "https://example.com/logo.png",
  "redirect_uris": ["https://example.com/callback"],
  "claims": ["sub", "email"],
  "acr_values": ["mosip:idp:acr:static-code"],
  "public_key": "{\"kty\":\"RSA\",\"n\":\"...\",\"e\":\"AQAB\"}",
  "grant_types": ["authorization_code"],
  "client_auth_methods": ["private_key_jwt"]
}
```

The `public_key` hash is stored for uniqueness enforcement. `enc_public_key` and `enc_public_key_cert` are optional. Status defaults to `ACTIVE`.

### Update OIDC client

```http
PUT /client-mgmt/oidc-client/{client_id}
```

Mutable fields: `client_name`, `logo_uri`, `redirect_uris`, `claims`, `acr_values`, `grant_types`, `client_auth_methods`, `status`, `additional_config`. `rp_id` and `public_key` are immutable after creation.

### Get OIDC client

```http
GET /client-mgmt/oidc-client/{client_id}
```

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
curl -s http://127.0.0.1:8080/health
# ok
```

## Docker

```bash
./make.sh docker-build

docker run --rm -p 8080:8080 \
  -e MOSIP_ESIGNET_HOST=http://127.0.0.1:8080 \
  -e POSTGRES_URL=postgres://esignet:secret@host.docker.internal:5432/mosip_esignet?sslmode=disable \
  -e REDIS_URL=redis://host.docker.internal:6379/0 \
  -e AUTHN_PROVIDER=mosip \
  esignet:latest
```

On first start the entrypoint generates signing keys if absent. Data is baked in at `/home/mosip/data`.

```bash
./make.sh smoke BASE_URL=http://127.0.0.1:8080
```

## Demo credentials

| Field | Value |
|-------|-------|
| Username | `decl-user-1` |
| Password | `TempPassword123!` |
| Application | `decl-app-1` |
| OAuth client | `decl-public-client-1` |
| Redirect URI | `https://localhost:3000` |

## Postman

1. Import `postman/embedder-local.environment.json`
2. Import `postman/embedder-positive-flow.json`
3. Run **0 — Shared setup**, then **1 — App-native flow** or **2 — Full OAuth (PKCE)** in order

Folder **2** is a linear sequence: PKCE setup → authorize (no redirect follow) → flow resume → credentials → auth callback → token → userinfo. Start the server with `MOSIP_ESIGNET_HOST` matching `baseUrl` in the environment (e.g. `http://127.0.0.1:8080`) before running OAuth steps.

Folder **3** (MOSIP OTP) requires `AUTHN_PROVIDER=mosip` and MOSIP variables (see `.env.example`). Set `individualId` and `otp` in the Postman environment before running.

Folder **4** (Client management) requires PostgreSQL with the `client_detail` table, `CLIENT_MGMT_ISSUER_URL` and `CLIENT_MGMT_JWKS_ENDPOINT` matching `baseUrl`, and `clientMgmtToken` set to a Bearer JWT carrying the `client_mgmt` scope. Run **Create OIDC client** → **Get OIDC client** → **Update OIDC client** in order.

## OAuth

Authorize redirects to the gate client (`http://127.0.0.1:3000/signin?...` by default via `OIDC_UI_*`) with `executionId`, `authId`, and related query parameters. Postman folder 2 parses that redirect without following it (`followRedirects: false` on authorize). The public client uses PKCE; no client secret is required.

End-to-end check (server must be running with `MOSIP_ESIGNET_HOST` matching `BASE_URL`):

```bash
./make.sh smoke
# or
BASE_URL=http://127.0.0.1:8080 ./scripts/oauth-smoke.sh
```

Each step prints `PASS` or `FAIL` to the console (authorize → flow → callback → token → userinfo), then a short summary (exit code 1 if any step failed).
