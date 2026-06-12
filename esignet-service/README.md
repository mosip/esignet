# eSignet (ThunderID engine embedder)

Module: `github.com/mosip/esignet`

A Go service that embeds the ThunderID authorization engine with PostgreSQL-backed OIDC client management, Redis-backed session/flow storage, and MOSIP IDA authentication support.

## Prerequisites

- Go 1.26+
- OpenSSL (for signing key generation)
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
  Makefile
  Dockerfile
```

## Build

All build targets generate TLS signing material first (`make keys`), because the engine needs a PEM key pair for JWT signing.

| Command | Output | Notes |
|---------|--------|--------|
| `make keys` | `keys/signing.key`, `keys/signing.crt` | One-time (or regenerate locally). Not committed. |
| `make build` | `out/esignet.exe` | Production binary. |
| `make test` | — | Unit tests with race detector. |
| `make coverage` | `coverage.out` | Coverage profile + per-package summary. |
| `make coverage-html` | `coverage.html` | Opens full HTML report. |

```bash
cd esignet-service
make build
```

If `go build` fails with a missing module, run `go mod download` first.

## Run

### Quick start (development)

```bash
cp .env.example .env          # fill in POSTGRES_* and REDIS_* at minimum
make run
```

### Binary

```bash
make keys && make build

export ISSUER_URL=http://127.0.0.1:8080
export POSTGRES_HOST=localhost
export POSTGRES_USER=esignet
export POSTGRES_PASSWORD=secret
export POSTGRES_DB=esignet
export REDIS_HOST=localhost

./out/esignet.exe
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
| `AUTHN_PROVIDER` | `catalog` | `catalog` (local YAML users) or `mosip` (MOSIP IDA) |
| `MOSIP_API_BASE_URL` | _(empty)_ | MOSIP API host |
| `MOSIP_LICENSE_KEY` | _(empty)_ | License key used in IDA endpoint paths |
| `MOSIP_P12_PATH` | _(empty)_ | Partner keystore (required for MOSIP auth) |
| `MOSIP_P12_PASSWORD` | _(empty)_ | Partner keystore password |

See `.env.example` for the full list including optional MOSIP URL overrides.

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
go install github.com/sqlc-dev/sqlc/cmd/sqlc@latest
sqlc generate
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
make test           # unit tests with race detector
make coverage       # coverage profile → stdout summary
make coverage-html  # full HTML report → coverage.html
```

Unit tests use `miniredis` for Redis (no running Redis required) and mock queriers for the Postgres layer. The OIDC discovery smoke test in `cmd/esignet` requires `make keys` first (skipped automatically if keys are absent).

## Health check

```bash
curl -s http://127.0.0.1:8080/health
# ok
```

## Docker

```bash
make docker-build

docker run --rm -p 8080:8080 \
  -e ISSUER_URL=http://127.0.0.1:8080 \
  -e POSTGRES_URL=postgres://esignet:secret@host.docker.internal:5432/esignet?sslmode=disable \
  -e REDIS_URL=redis://host.docker.internal:6379/0 \
  esignet:latest
```

On first start the entrypoint generates signing keys if absent. Data is baked in at `/home/mosip/data`.

```bash
make smoke BASE_URL=http://127.0.0.1:8080
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

Folder **3** (MOSIP OTP) requires `AUTHN_PROVIDER=mosip` and `MOSIP_*` vars. Set `individualId` and `otp` in the environment before running.
