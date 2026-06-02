# eSignet (ThunderID engine embedder)

Module: `github.com/mosip/esignet`

A minimal Go sample that embeds the ThunderID authorization engine (`pkg/thunderidengine`) with declarative YAML under `data/repository/resources/` and in-memory host providers for users, OAuth clients, authn, and permit-all authorization.

## Prerequisites

- Go 1.26+
- OpenSSL (for signing key generation)
- Network access to fetch the Thunder backend module (see `go.mod` `replace` directive for the pinned fork)

## Repository layout

```text
esignet-service/
  cmd/esignet/main.go         # HTTP entrypoint
  internal/catalog/           # users + OAuth clients from YAML
  internal/host/              # Actor, Authn, Authz, Consent, custom executors
  internal/config/            # env-based configuration (authn, MOSIP)
  data/repository/resources/  # declarative fixtures (flows, apps, OU, …)
  keys/                       # signing.key + signing.crt (local, gitignored)
  postman/                    # Postman collection + environment
  scripts/oauth-smoke.sh      # end-to-end OAuth smoke test
  Makefile
  Dockerfile
```

## Build

All build targets generate TLS signing material first (`make keys`), because the engine needs a PEM key pair for JWT signing.

| Command | Output | Notes |
|---------|--------|--------|
| `make keys` | `keys/signing.key`, `keys/signing.crt` | One-time (or regenerate locally). Not committed. |
| `make build` | `out/esignet.exe` | Preferred binary build. |
| `go build -o out/esignet.exe ./cmd/esignet` | `out/esignet.exe` | Same as `make build` after `make keys`. |
| `make test` | — | Unit tests + OIDC discovery smoke (`cmd/esignet`). |

Example:

```bash
cd esignet-service
make build
```

If `go build` fails with a missing module, run `go mod download` (requires access to the module replace in `go.mod`).

## Run

### Makefile (development)

```bash
cd esignet-service
make run
```

Runs `go run ./cmd/esignet` with `ISSUER_URL`, `DATA_DIR`, `SIGNING_KEY_PATH`, and `AUTHN_PROVIDER` (see [Environment variables](#environment-variables)). Copy `.env.example` to `.env` to override defaults via `make`.

### Binary

```bash
cd esignet-service
make keys && make build

export PORT=8080
export ISSUER_URL=http://127.0.0.1:8080
export DATA_DIR=./data
export SIGNING_KEY_PATH=./keys/signing.key
export AUTHN_PROVIDER=catalog

./out/esignet.exe
```

The server logs startup at info level, for example:

```text
time=... level=INFO msg="server listening" addr=:8080 issuer=http://127.0.0.1:8080
```

Set `LOG_LEVEL=debug` for verbose MOSIP/IDA tracing (endpoint URLs, attribute requests, etc.).

Use **127.0.0.1** in URLs if `localhost` resolves to IPv6 only and connections fail.

### Environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `LOG_LEVEL` | `info` | Log verbosity: `debug`, `info`, `warn`, or `error`. |
| `PORT` | `8080` | HTTP listen port (`:PORT`). |
| `ISSUER_URL` | `http://127.0.0.1:<PORT>` | OIDC issuer, JWT `iss`, discovery. **Must match the URL clients use** for OAuth to succeed. |
| `DATA_DIR` | `./data` | Server home; declarative YAML under `data/repository/resources/`. |
| `SIGNING_KEY_PATH` | `./keys/signing.key` | PEM private key; `.crt` with the same basename is loaded automatically. |
| `AUTHN_PROVIDER` | `catalog` | `catalog` (local YAML users) or `mosip` (MOSIP IDA). |
| `MOSIP_API_BASE_URL` | _(empty)_ | MOSIP API host; used to derive IDA URLs when overrides are unset. |
| `MOSIP_LICENSE_KEY` | _(empty)_ | License key segment in IDA endpoint paths. |
| `MOSIP_P12_PATH` | _(empty)_ | Partner keystore path (required for `mosip` auth). |
| `MOSIP_P12_PASSWORD` | _(empty)_ | Partner keystore password. |

See `.env.example` for optional MOSIP URL overrides (`MOSIP_SEND_OTP_BASE_URL`, etc.).

### Health check

```bash
curl -s http://127.0.0.1:8080/health
# ok
```

## Docker

The image is built from the `esignet-service` directory; Thunder is fetched during `go mod download` from the remote module replace (no local checkout required).

**Build**

```bash
cd esignet-service
make docker-build
```

Equivalent:

```bash
cd esignet-service
docker build -f Dockerfile -t esignet:latest .
```

**Run**

```bash
docker run --rm -p 8080:8080 \
  -e ISSUER_URL=http://127.0.0.1:8080 \
  esignet:latest
```

On first start, the entrypoint creates `/home/mosip/keys/signing.key` and `signing.crt` if they are missing. Declarative data is baked in at `/home/mosip/data`.

**Smoke test against the container**

```bash
BASE_URL=http://127.0.0.1:8080 ./scripts/oauth-smoke.sh
```

Or:

```bash
make smoke BASE_URL=http://127.0.0.1:8080
```

Each step prints `PASS` or `FAIL` to the console (authorize → flow → callback → token → userinfo), then a short summary (exit code 1 if any step failed).

Use the host port you mapped, and set `ISSUER_URL` in `docker run` to that same base URL.

## Demo credentials

| Field | Value |
|-------|-------|
| Username | `decl-user-1` |
| Password | `TempPassword123!` |
| Application | `decl-app-1` |
| OAuth client | `decl-public-client-1` |
| Redirect URI | `https://localhost:3000` |
| OU | `decl-ou-1` |

Users are loaded from `data/repository/resources/users/*.yaml` by the host catalog (not by the engine declarative loader). Apps, flows, themes, and OU resources are loaded by Thunder's declarative adapter from the same tree.

## Verify with curl

**Health and discovery**

```bash
curl -s http://127.0.0.1:8080/health
curl -s http://127.0.0.1:8080/.well-known/openid-configuration | jq .
```

**App-native sign-in**

```bash
curl -s -X POST http://127.0.0.1:8080/flow/execute \
  -H 'Content-Type: application/json' \
  -d '{"applicationId":"decl-app-1","flowType":"AUTHENTICATION"}' | jq .

# Use executionId and challengeToken from the response:
curl -s -X POST http://127.0.0.1:8080/flow/execute \
  -H 'Content-Type: application/json' \
  -d '{"executionId":"<id>","challengeToken":"<token>","action":"action_001","inputs":{"username":"decl-user-1","password":"TempPassword123!"}}' | jq .
```

Expect `flowStatus: COMPLETE` and an `assertion` JWT on the second call.

## Postman

1. Import `postman/embedder-local.environment.json` (environment name: **eSignet (local)**)
2. Import `postman/embedder-positive-flow.json`
3. Run **0 — Shared setup**, then either **1 — App-native flow/execute** or **2 — Full OAuth (PKCE)** in order

Folder **2** is a linear sequence: PKCE setup → authorize (no redirect follow) → flow resume → credentials → auth callback → token → userinfo. Start the server with `ISSUER_URL` matching `baseUrl` in the environment (e.g. `http://127.0.0.1:8080`) before running OAuth steps.

Folder **3** (MOSIP OTP) requires `AUTHN_PROVIDER=mosip` and `MOSIP_*` variables (see `.env.example`). Set `individualId` and `otp` in the Postman environment before running.

## Tests

```bash
make test
```

## OAuth

Authorize redirects to the gate client (`http://localhost:8080/signin?...`) with `executionId`, `authId`, and related query parameters. Postman folder 2 parses that redirect without following it (`followRedirects: false` on authorize). The public client uses PKCE; no client secret is required.

End-to-end check (server must be running with `ISSUER_URL` matching `BASE_URL`):

```bash
make smoke
# or
BASE_URL=http://127.0.0.1:8080 ./scripts/oauth-smoke.sh
```

Each step prints `PASS` or `FAIL` to the console (authorize → flow → callback → token → userinfo), then a short summary (exit code 1 if any step failed).
