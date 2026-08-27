# eSignet-go local setup guide

Step-by-step guide for running eSignet locally via Docker Compose.

---

## Table of Contents

1. [Overview](#overview)
2. [Prerequisites](#prerequisites)
3. [Understanding the Compose Files](#understanding-the-compose-files)
4. [Option A — Full Demo](#option-a--full-demo)
   - [Start services](#1-start-services)
   - [Verify services are healthy](#2-verify-services-are-healthy)
   - [Verify database initialization](#3-verify-database-initialization)
5. [Option B — Developer Mode (run from source)](#option-b--developer-mode-run-from-source)
6. [Running the OIDC Happy Flow](#running-the-oidc-happy-flow)
7. [Mock Relying Party (External)](#mock-relying-party-external)
8. [Troubleshooting](#troubleshooting)
9. [Tearing Down / Resetting](#tearing-down--resetting)
10. [Further Reading](#further-reading)

---

## Overview

This guide brings up a fully functional eSignet environment on your local machine using the compose files in this directory. The full demo runs four services — PostgreSQL, a Mock Identity System, the eSignet OIDC service, and the OIDC login UI — wired together so you can complete an end-to-end OIDC flow without any external dependencies.

---

## Prerequisites

| Requirement | Notes |
|---|---|
| Docker Engine + Compose plugin | [Install Docker](https://docs.docker.com/engine/install/); verify with `docker compose version` |
| Free RAM | 4 GB minimum, 8 GB recommended |
| Free disk | ~2 GB for pulled images |
| Free ports | **5455**, **8082**, **8080**, **3000** — must not be in use before starting |
| `curl` | For health checks from the terminal |
| Browser | For the OIDC UI flow |

---

## Understanding the Compose Files

There are two compose files in this directory. Choose the one that matches your goal.

### `docker-compose.yaml` — Full Demo

Brings up **all four services**. Use this for demos, hackathons, or evaluating eSignet without building from source.

| Service | Image | Host Port | Role |
|---|---|---|---|
| `database` | `postgres:bookworm` | **5455** | PostgreSQL — two databases: `mosip_esignet` and `mosip_mockidentitysystem`, schemas and seed data created automatically via `init.sql` |
| `mock-identity-system` | `mosipdev/mock-identity-system:release-0.14.x` | **8082** | Mock identity backend — simulates a national ID system; supports OTP, PIN, and password auth |
| `esignet` | `mosipdev/esignet:develop-go` | **8080** | eSignet OIDC/OAuth2 service — the core authorization server |
| `esignet-ui` | `mosipdev/oidc-ui:develop-go` | **3000** | OIDC login UI — nginx-served React app; proxies API calls to the `esignet` service |

Startup order enforced by `depends_on`:
`database` (healthy) → `mock-identity-system` → `esignet` → `esignet-ui`

### `dependent-docker-compose.yaml` — Dev Dependencies Only

Brings up **only the infrastructure services** (PostgreSQL + Mock Identity System). Use this when you are running `esignet-service` from source and don't want the containerised eSignet or UI.

| Service | Image | Host Port | Role |
|---|---|---|---|
| `database` | `postgres:bookworm` | **5455** | PostgreSQL (same init.sql as above) |
| `mock-identity-system` | `mosipdev/mock-identity-system:release-0.14.x` | **8082** | Mock identity backend |

---

## Option A — Full Demo

All commands are run from this directory (`docker-compose/`).

### 1. Start services

```bash
docker compose up -d
```

Docker pulls the images (first run takes a few minutes) and starts all four services in dependency order. The PostgreSQL container runs `init.sql` on its first start, creating both databases, schemas, tables, and seed data automatically.

Watch startup progress:

```bash
docker compose logs -f
```

Press `Ctrl+C` to stop following logs; the services keep running.

### 2. Verify services are healthy

```bash
docker compose ps
```

Expected output — all four services should be `running` (the `database` service will show `healthy` once its healthcheck passes):

```
NAME                                  SERVICE               STATUS
docker-compose-database-1             database              running (healthy)
docker-compose-mock-identity-system-1 mock-identity-system  running
docker-compose-esignet-1              esignet               running
docker-compose-esignet-ui-1           esignet-ui            running
```

Verify each service responds:

```bash
# eSignet service health
curl http://localhost:8080/health
# Expected: {"status":"UP"} or similar

# OIDC discovery endpoint (via the UI nginx proxy)
curl http://localhost:3000/.well-known/openid-configuration
# Expected: JSON with issuer, authorization_endpoint, token_endpoint, etc.

# JWKS endpoint
curl http://localhost:3000/.well-known/jwks.json
# Expected: JSON with "keys" array

# Mock Identity System
curl http://localhost:8082/v1/mock-identity-system/actuator/health
# Expected: {"status":"UP"}

# OIDC UI (browser)
# Open http://localhost:3000 — should show eSignet details page
```

If `esignet` takes longer to start (it waits for `mock-identity-system`), wait 30–60 seconds and retry.

### 3. Verify database initialization

Confirm both databases and schemas were created by `init.sql`:

```bash
docker compose exec database psql -U postgres -c "\l"
```

Expected output includes:

```
   Name                   | Owner
--------------------------+----------
 mosip_esignet            | postgres
 mosip_mockidentitysystem | postgres
```

Verify the eSignet schema:

```bash
docker compose exec database psql -U postgres -d mosip_esignet -c "\dt esignet.*"
```

Expected tables: `ca_cert_store`, `client_detail`, `consent_detail`, `consent_history`, `key_alias`, `key_policy_def`, `key_store`, `public_key_registry`

Verify the mock identity schema:

```bash
docker compose exec database psql -U postgres -d mosip_mockidentitysystem -c "\dt mockidentitysystem.*"
```

Expected tables: `ca_cert_store`, `key_alias`, `key_policy_def`, `key_store`, `kyc_auth`, `mock_identity`, `partner_data`, `verified_claim`

---

## Option B — Developer Mode (run from source)

Use `dependent-docker-compose.yaml` to start only PostgreSQL and the Mock Identity System, then run the eSignet service from source.

> **Windows (CMD/PowerShell):** use Git Bash or WSL for the `cp` and `./make.sh` commands below — they are shell scripts and do not run natively in CMD or PowerShell. The `docker compose` commands work in any terminal.

```bash
# 1. Start infrastructure only (all platforms)
docker compose -f dependent-docker-compose.yaml up -d

# 2. Configure the service
cd ../esignet-service

# Linux / macOS / Git Bash
cp .env.example .env

# Windows CMD (if not using Git Bash)
copy .env.example .env

# The .env.example defaults do not match the dependent compose — update these lines:
#   DATABASE_PORT=5455          (compose maps postgres to host port 5455, not 5432)
#   DATABASE_USERNAME=postgres  (compose user is postgres, not esignet)
#   DB_DBUSER_PASSWORD=postgres
#   KEYMANAGER_PKCS12_FILE_PATH=./keystore.pfx  (keymanager auto-provisions this file on first startup; /opt/mosip/test.pfx does not exist locally)

# 3. Run the service (Linux / macOS / Git Bash) — keymanager provisions its own keys on first start
./make.sh run    # starts the service on port 8080
```

See [`esignet-service/README.md`](../esignet-service/README.md) for the full environment-variable reference and build options.

For the UI, run separately:

```bash
# all platforms
cd ../oidc-ui
npm install
npm run dev   # starts on port 3000 (Vite dev server)
```

---

## Running the OIDC Happy Flow

The `init.sql` seed data includes one pre-registered mock identity you can use immediately:

| Field | Value |
|---|---|
| Individual ID | `1774231323` |
| PIN | `545411` |
| OTP | `111111` (the mock OTP channel always accepts this static value) |
| Email | `siddhartha.km@gmail.com` |
| Phone | `+919427357934` |

The hCaptcha key configured in compose (`10000000-ffff-ffff-ffff-000000000001`) is the official hCaptcha test key — it always passes without solving a real CAPTCHA.

### End-to-end flow using the Postman collection

The easiest way to walk through the full OIDC flow is via the included Postman collection:

1. Import both files from [`postman-collection/`](../postman-collection/) into Postman.
2. The collection includes requests for:
   - Registering an OIDC client (`POST /client-mgmt/oidc-client`)
   - Initiating an authorization request (`GET /authorize`)
   - Completing authentication (OTP or PIN via the Mock ID system)
   - Exchanging the authorization code for tokens (`POST /oauth2/token`)
   - Fetching user claims (`GET /oidc/userinfo`)
3. Use the pre-seeded individual ID `1774231323` and PIN `545411` when prompted.

### Manual flow overview

```
1. Register an OIDC client
   POST http://localhost:8080/client-mgmt/oidc-client
   (see Postman collection for the request body)

2. Initiate authorization (browser)
   http://localhost:3000/signin?applicationId=<client_id>&authId=<state>
   The OIDC UI login screen appears.

3. Authenticate
   Enter Individual ID: 1774231323
   Enter PIN: 545411
   Solve CAPTCHA: any input works (test key)

4. Approve consent
   Review the requested claims and click Allow.

5. Receive authorization code
   Redirected to your redirect_uri with ?code=<auth_code>

6. Exchange code for tokens
   POST http://localhost:8080/oauth2/token
   grant_type=authorization_code&code=<auth_code>&...

7. Fetch user info
   GET http://localhost:8080/oidc/userinfo
   Authorization: Bearer <access_token>
```

---

## Mock Relying Party (External)

A full end-to-end demo also involves a Mock Relying Party — a sample web application that acts as the OIDC client, initiates the authorization flow, and receives tokens. Its compose setup lives in a separate MOSIP repository:

[mosip/esignet-mock-services — docker-compose setup](https://github.com/mosip/esignet-mock-services/blob/release-0.14.x/docker-compose/README.md)

Follow that guide to bring up the Mock Relying Party Portal alongside this stack. Until then, use the Postman collection above to exercise the full flow.

---

## Troubleshooting

**Port already in use**

```bash
# Linux/macOS
lsof -i :8080
# Windows
netstat -ano | findstr :8080
```

Stop the conflicting process or change the host port in `docker-compose.yaml` (left side of `ports: - HOST:CONTAINER`).

**`database` never becomes healthy**

The healthcheck runs `pg_isready -U postgres` every 5 seconds, up to 20 retries (100 seconds total). If it still fails:

```bash
docker compose logs database
```

Look for permission errors or disk-full messages.

**`mock-identity-system` fails to start**

It depends on the database being healthy. Common causes:
- The `init.sql` script failed (check `docker compose logs database` for SQL errors).
- Insufficient memory — the Spring Boot container needs at least 512 MB.

```bash
docker compose logs mock-identity-system
```

**`esignet` exits immediately**

```bash
docker compose logs esignet
```

Common causes:
- Database not ready yet — re-run `docker compose up -d` after the database is healthy.
- PKCS12 keystore not found — the container uses a bundled test keystore; if you've mounted a custom one, verify the path and password.

**Images not found / pull errors**

```bash
docker pull mosipdev/esignet:develop-go
docker pull mosipdev/oidc-ui:develop-go
```

Check your internet connection or Docker Hub rate limits.

**OIDC discovery returns a connection error from the UI**

The `esignet-ui` nginx proxies `/v1/esignet/` to `esignet:8080` using the internal Docker network. If the proxy fails, `esignet` may not have started yet. Wait 30 seconds and refresh.

**CAPTCHA blocks login**

The compose file uses hCaptcha test key `10000000-ffff-ffff-ffff-000000000001`, which always passes. If you see a real CAPTCHA challenge, the key has been changed — check `MOSIP_ESIGNET_CAPTCHA_SITE_KEY` in `docker-compose.yaml`.

---

## Tearing Down / Resetting

Run all commands from the `docker-compose/` directory.

### Full demo (docker-compose.yaml)

Stop all services and keep the database volume (data is preserved across restarts):

```bash
docker compose down
```

Stop all services and **delete all data** (full clean reset — `init.sql` will re-run on the next `up`):

```bash
docker compose down -v
```

Remove pulled images as well (frees disk space):

```bash
docker compose down -v --rmi all
```

### Developer mode (dependent-docker-compose.yaml)

```bash
docker compose -f dependent-docker-compose.yaml down
docker compose -f dependent-docker-compose.yaml down -v
docker compose -f dependent-docker-compose.yaml down -v --rmi all
```

---

## Further Reading

| Resource | Location |
|---|---|
| eSignet end-user guide (login flow walkthrough) | [docs.esignet.io/test/end-user-guide](https://docs.esignet.io/test/end-user-guide) |
| Relying party integration (authorize URL, client onboarding) | [docs.esignet.io/integration/relying-party](https://docs.esignet.io/integration/relying-party) |
| Mock Relying Party compose setup | [mosip/esignet-mock-services](https://github.com/mosip/esignet-mock-services/blob/release-0.14.x/docker-compose/README.md) |
| Architecture overview (backend + frontend components, flow engine, data model) | [`docs/architecture.md`](../docs/architecture.md) |
| OpenAPI spec (all REST endpoints with request/response schemas) | [`docs/esignet-openapi.yaml`](../docs/esignet-openapi.yaml) |
| Resource servers and OAuth permission scopes | [`docs/resource-servers-and-permissions.md`](../docs/resource-servers-and-permissions.md) |
| eSignet service — env vars, build, run from source | [`esignet-service/README.md`](../esignet-service/README.md) |
| OIDC UI — build, theming, runtime config | [`oidc-ui/README.md`](../oidc-ui/README.md) |
| Postman collection for API exploration | [`postman-collection/`](../postman-collection/) |
| Kubernetes / production deployment | [`deploy/README.md`](../deploy/README.md) |
| Helm charts | [`helm/`](../helm/) |
| API test harness (conformance suite + godog) | [`api-test/README.md`](../api-test/README.md) |
| Database schema scripts | [`db_scripts/`](../db_scripts/) |
