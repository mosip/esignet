# eSignet (ThunderID engine embedder)

Module: `github.com/mosip/esignet`

A minimal Go sample that embeds the ThunderID authorization engine (`pkg/thunderidengine`) with declarative YAML under `data/repository/resources/` and in-memory host providers for users, OAuth clients, authn, and permit-all authorization.

## Prerequisites

- Go 1.26+
- OpenSSL (for signing key generation)
- Network access to fetch the Thunder backend module (branch `3037` on [github.com/anushasunkada/thunder](https://github.com/anushasunkada/thunder.git)):

  ```text
  replace github.com/thunder-id/thunderid => github.com/anushasunkada/thunder/backend v0.0.0-20260531173111-fb56c4c3624d
  ```

  To pin a newer commit on branch `3037`: `go get github.com/anushasunkada/thunder/backend@3037` then `go mod tidy`.

## Repository layout

```text
test-app/
  cmd/engine/main.go          # HTTP entrypoint
  internal/catalog/         # users + OAuth clients from YAML
  internal/host/            # Actor, Authn, Authz, Consent providers
  data/repository/resources/  # declarative fixtures (flows, apps, OU, …)
  keys/                     # signing.key + signing.crt (local, gitignored)
  postman/                  # Postman collection + environment
  scripts/oauth-smoke.sh    # end-to-end OAuth smoke test
  Makefile
  Dockerfile                # build context = parent of test-app (see Docker)
```

## Build

All build targets generate TLS signing material first (`make keys`), because the engine needs a PEM key pair for JWT signing.

| Command | Output | Notes |
|---------|--------|--------|
| `make keys` | `keys/signing.key`, `keys/signing.crt` | One-time (or regenerate locally). Not committed. |
| `make build` | `./engine` | Preferred binary build. |
| `go build -o engine ./cmd/engine` | `./engine` | Same as `make build` after `make keys`. |
| `make test` | — | Unit tests + OIDC discovery smoke (`cmd/engine`). |

Example:

```bash
cd /opt/mosip/github/test-app
make build
```

If `go build` fails with a missing module, run `go mod download` (requires access to the fork above).

## Run

### Makefile (development)

```bash
cd /opt/mosip/github/test-app
make run
```

Runs `go run ./cmd/engine` with `THUNDERID_ISSUER=http://127.0.0.1:8080`, `THUNDERID_DATA_DIR=./data`, and `THUNDERID_SIGNING_KEY=./keys/signing.key`.

### Binary

```bash
cd /opt/mosip/github/test-app
make keys && make build

export THUNDERID_ISSUER=http://127.0.0.1:8080
export THUNDERID_DATA_DIR=./data
export THUNDERID_SIGNING_KEY=./keys/signing.key
export PORT=8080

./engine
```

The server logs the listen address and issuer, for example:

```text
ThunderID engine embedder listening on :8080 (issuer=http://127.0.0.1:8080)
```

Use **127.0.0.1** in URLs if `localhost` resolves to IPv6 only and connections fail.

### Environment variables

| Variable | Default | Purpose |
|----------|---------|---------|
| `PORT` | `8080` | HTTP listen port (`:PORT`). |
| `THUNDERID_ISSUER` | `http://127.0.0.1:<PORT>` | OIDC issuer, JWT `iss`, discovery. **Must match the URL clients use** for OAuth to succeed. |
| `THUNDERID_DATA_DIR` | `./data` | Server home; declarative YAML under `data/repository/resources/`. |
| `THUNDERID_SIGNING_KEY` | `./keys/signing.key` | PEM private key; `.crt` with the same basename is loaded automatically. |

### Health check

```bash
curl -s http://127.0.0.1:8080/health
# ok
```

## Docker

The image is built from the `test-app` directory; Thunder is fetched during `go mod download` from the remote module replace (no local checkout required).

**Build**

```bash
cd /opt/mosip/github/test-app
make docker-build
```

Equivalent:

```bash
cd /opt/mosip/github/test-app
docker build -f Dockerfile -t esignet:latest .
```

**Run**

```bash
docker run --rm -p 8080:8080 \
  -e THUNDERID_ISSUER=http://127.0.0.1:8080 \
  esignet:latest
```

On first start, the entrypoint creates `/app/keys/signing.key` and `signing.crt` if they are missing. Declarative data is baked in at `/app/data`.

**Smoke test against the container**

```bash
BASE_URL=http://127.0.0.1:8080 ./scripts/oauth-smoke.sh
```

Each step prints `PASS` or `FAIL` to the console (authorize → flow → callback → token → userinfo), then a short summary (exit code 1 if any step failed).

(Use the host port you mapped, and set `THUNDERID_ISSUER` in `docker run` to that same base URL.)

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

Folder **2** is a linear sequence: PKCE setup → authorize (no redirect follow) → flow resume → credentials → auth callback → token → userinfo. Set `THUNDERID_ISSUER` to match `baseUrl` (e.g. `http://127.0.0.1:8080`) before running OAuth steps.

## Tests

```bash
make test
```

## OAuth

Authorize redirects to the gate client (`http://localhost:8080/signin?...`) with `executionId`, `authId`, and related query parameters. Postman folder 2 parses that redirect without following it (`followRedirects: false` on authorize). The public client uses PKCE; no client secret is required.

End-to-end check (server must be running with `THUNDERID_ISSUER` set):

```bash
BASE_URL=http://127.0.0.1:8080 ./scripts/oauth-smoke.sh
```

Each step prints `PASS` or `FAIL` to the console (authorize → flow → callback → token → userinfo), then a short summary (exit code 1 if any step failed).
