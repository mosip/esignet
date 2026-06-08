# eSignet (ThunderID engine embedder)

Module: `github.com/mosip/esignet`

A Go service that embeds the ThunderID authorization engine with PostgreSQL-backed OIDC client management, Redis-backed session/flow storage, and MOSIP IDA authentication support.

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
  internal/catalog/                 # users + OAuth clients from YAML
  internal/clientmgmt/              # OIDC client management API
    db/                             # SQLC-generated Postgres layer
    handler.go                      # HTTP handlers
    service.go                      # business logic
    middleware.go                   # Bearer token scope enforcement
    jwks.go                         # JWKS fetcher + cache
  internal/store/                   # Redis-backed runtime.Store
  internal/host/                    # Actor, Authn, Authz, Consent, executors
  internal/config/                  # env-based config (engine, DB, Redis, authn, MOSIP)
  data/repository/resources/        # declarative fixtures (flows, apps, OU, …)
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
cp .env.example .env          # fill in POSTGRES_* and REDIS_* at minimum
./make.sh run
```

Copy `.env.example` to `.env` to override defaults, or pass overrides on the command line (`./make.sh run PORT=9090`).

### Binary

```bash
./make.sh keys && ./make.sh build

export ISSUER_URL=http://127.0.0.1:8080
export POSTGRES_HOST=localhost
export POSTGRES_USER=esignet
export POSTGRES_PASSWORD=secret
export POSTGRES_DB=esignet
export REDIS_HOST=localhost

./out/esignet.exe   # ./out/esignet on Linux
```

Startup log:

```text
time=... level=INFO msg="redis connected"   key_prefix=esignet:
time=... level=INFO msg="client mgmt scope enforcement"  required_scope=client_mgmt  jwks_endpoint=http://127.0.0.1:8080/.well-known/jwks.json
time=... level=INFO msg="server listening"  addr=:8080  issuer=http://127.0.0.1:8080
```

Set `LOG_LEVEL=debug` for verbose tracing.

## Environment variables

### Core engine

| Variable | Default | Purpose |
|----------|---------|---------|
| `LOG_LEVEL` | `info` | `debug` / `info` / `warn` / `error` |
| `PORT` | `8080` | HTTP listen port |
| `ISSUER_URL` | `http://127.0.0.1:<PORT>` | OIDC issuer, JWT `iss`, discovery base |
| `DATA_DIR` | `./data` | Declarative YAML root |
| `SIGNING_KEY_PATH` | `./keys/signing.key` | PEM private key for JWT signing |
| `GATE_CLIENT_SCHEME` | `http` | Gate UI scheme for authorize redirects |
| `GATE_CLIENT_HOSTNAME` | `127.0.0.1` | Gate UI hostname |
| `GATE_CLIENT_PORT` | `<PORT>` | Gate UI port |
| `GATE_CLIENT_LOGIN_PATH` | `/signin` | Login page path |
| `GATE_CLIENT_ERROR_PATH` | `/error` | Error page path |
| `JWT_AUDIENCE` | _(empty)_ | JWT `aud` claim |
| `OAUTH_*` | _(empty)_ | Optional OAuth lifetime/policy overrides |

### PostgreSQL

| Variable | Default | Purpose |
|----------|---------|---------|
| `POSTGRES_URL` | _(empty)_ | Full DSN — takes precedence if set |
| `POSTGRES_HOST` | `localhost` | |
| `POSTGRES_PORT` | `5432` | |
| `POSTGRES_DB` | `esignet` | |
| `POSTGRES_USER` | `postgres` | |
| `POSTGRES_PASSWORD` | _(empty)_ | |
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
| `CLIENT_MGMT_JWKS_ENDPOINT` | `<ISSUER_URL>/.well-known/jwks.json` | JWKS URL for validating Bearer tokens |
| `CLIENT_MGMT_JWKS_CACHE_TTL_SECS` | `300` | JWKS key cache TTL |

### Authentication provider

| Variable | Default | Purpose |
|----------|---------|---------|
| `AUTHN_PROVIDER` | `catalog` | `catalog` (local YAML users), `mosip` (MOSIP IDA), or `sunbird` (SunbirdRC registry KBI) |
| `MOSIP_API_BASE_URL` | _(empty)_ | MOSIP API host |
| `MOSIP_LICENSE_KEY` | _(empty)_ | License key used in IDA endpoint paths |
| `MOSIP_P12_PATH` | _(empty)_ | Partner keystore (required for MOSIP auth) |
| `MOSIP_P12_PASSWORD` | _(empty)_ | Partner keystore password |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_REGISTRY_SEARCH_URL` | _(empty)_ | SunbirdRC registry search endpoint (**required** for `sunbird` auth) |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REGISTRY_GET_URL` | _(empty)_ | SunbirdRC registry get/entity endpoint; used to fetch OIDC claims after auth |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_INDIVIDUAL_ID_FIELD` | `policyNumber` | Registry field the entered `individualId` maps to in the search filter |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_KBI_ENTITY_ID_FIELD` | `osid` | Registry field holding the entity id returned by search |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_AUTH_FACTOR_KBI_FIELD_DETAILS` | _(Insurance default)_ | JSON list of KBI fields collected from the user |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_IDENTITY_OPENID_CLAIMS_MAPPING` | _(Insurance default)_ | JSON map of OIDC claim → registry field (empty = raw passthrough) |
| `MOSIP_ESIGNET_AUTHENTICATOR_SUNBIRD_RC_REQUEST_TIMEOUT_SECS` | `10` | HTTP timeout in seconds for registry calls (Go-only; no Java equivalent) |

See `.env.example` for the full list including optional MOSIP URL overrides.

### SunbirdRC (KBI) authentication

With `AUTHN_PROVIDER=sunbird`, the user authenticates by knowledge-based identity: they enter an
`individualId` plus KBI fields (default `fullName` and `dob`), which are POSTed as exact-match filters to the
registry search URL. Authentication succeeds only when the registry returns **exactly one** matching entity;
its entity-id field (default `osid`) becomes the user id. Attributes are then fetched from the registry
get URL (`.../{entityId}`) and mapped to OIDC claims via the claims-mapping config. This reuses the
built-in `BasicAuthExecutor` (no custom executor); the demo flow `decl-sunbird-flow-1` and app
`decl-app-sunbird` exercise it.

The `SUNBIRD_RC` environment variables mirror the Java eSignet Sunbird plugin
(`io.mosip.esignet.plugin.sunbirdrc`) property keys — uppercased, with `.` and `-` replaced by `_`.

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

Unit tests use `miniredis` for Redis (no running Redis required) and mock queriers for the Postgres layer. The OIDC discovery smoke test in `cmd/esignet` requires `./make.sh keys` first (skipped automatically if keys are absent).

## Health check

```bash
curl -s http://127.0.0.1:8080/health
# ok
```

## Docker

```bash
./make.sh docker-build

docker run --rm -p 8080:8080 \
  -e ISSUER_URL=http://127.0.0.1:8080 \
  -e POSTGRES_URL=postgres://esignet:secret@host.docker.internal:5432/esignet?sslmode=disable \
  -e REDIS_URL=redis://host.docker.internal:6379/0 \
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

Folder **2** is a linear sequence: PKCE setup → authorize (no redirect follow) → flow resume → credentials → auth callback → token → userinfo. Start the server with `ISSUER_URL` matching `baseUrl` in the environment (e.g. `http://127.0.0.1:8080`) before running OAuth steps.

Folder **3** (MOSIP OTP) requires `AUTHN_PROVIDER=mosip` and `MOSIP_*` variables (see `.env.example`). Set `individualId` and `otp` in the Postman environment before running.

## OAuth

Authorize redirects to the gate client (`http://127.0.0.1:8080/signin?...` by default) with `executionId`, `authId`, and related query parameters. Postman folder 2 parses that redirect without following it (`followRedirects: false` on authorize). The public client uses PKCE; no client secret is required.

End-to-end check (server must be running with `ISSUER_URL` matching `BASE_URL`):

```bash
./make.sh smoke
# or
BASE_URL=http://127.0.0.1:8080 ./scripts/oauth-smoke.sh
```

Each step prints `PASS` or `FAIL` to the console (authorize → flow → callback → token → userinfo), then a short summary (exit code 1 if any step failed).
