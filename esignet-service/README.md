# eSignet Service

A production-grade Go HTTP service with PostgreSQL and Redis integration, structured for extensibility and operational clarity.

---

## Table of Contents

- [Overview](#overview)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Go modules: ThunderID dependency](#go-modules-thunderid-dependency)
- [Quickstart](#quickstart)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Development](#development)
- [Docker](#docker)
- [Database Migrations](#database-migrations)

---

## Overview

`esignet-service` is a Go HTTP service running on port **8088** (configurable via `PORT`). It provides:

- Structured JSON logging via `log/slog`
- PostgreSQL connection pool via `pgx/v5`
- Redis client via `go-redis/v9`
- **Two HTTP modes**
  - **Standalone** (default): Chi router with request-ID injection, access logging, panic recovery, gzip compression, and the routes below.
  - **Thunder embed** (optional): If `THUNDER_HOME` is set, the process serves `/ping` and `/health` on a `net/http` mux and registers ThunderID OAuth and flow routes via [`embed.WireThunder`](https://github.com/anushasunkada/thunder/tree/public-package/backend/pkg/embed) from the pinned Thunder module (see [Go modules: ThunderID dependency](#go-modules-thunderid-dependency)).
- A deep health endpoint that concurrently pings all backing services
- Graceful shutdown on `SIGINT`/`SIGTERM` (cleanup runs on all exit paths; `main` delegates to `run()` which returns an exit code instead of calling `os.Exit` before defers)
- Multi-stage Docker build with a minimal Alpine production image

---

## Project Structure

```
.
├── cmd/
│   └── esignet/
│       └── main.go              # Entrypoint — config, logger, DB, Redis, std server or Thunder mux
├── internal/
│   ├── authn/
│   │   └── provider.go          # Authn provider passed to Thunder embed.WireThunder
│   ├── config/
│   │   └── config.go            # Env-var configuration (envconfig), incl. optional Thunder
│   ├── db/
│   │   └── postgres.go          # pgx/v5 connection pool + Ping
│   ├── cache/
│   │   └── redis.go             # go-redis/v9 client + Ping
│   ├── middleware/
│   │   └── middleware.go        # RequestID · Logger · Recoverer (standalone stack)
│   ├── handler/
│   │   └── health.go            # GET /health — concurrent dependency checks
│   ├── server/
│   │   └── server.go            # Chi router, route registration, graceful shutdown
│   └── thunderembed/
│       └── server.go            # Optional ThunderID mux + WireThunder when THUNDER_HOME is set
├── pkg/
│   └── logger/
│       └── logger.go            # slog JSON/text handler factory
├── Dockerfile                   # Multi-stage: dev → builder → production
├── compose.yaml                 # Full local stack: app + postgres + redis
├── Makefile                     # Developer targets
├── go.mod                       # Pins ThunderID; see Thunder dependency section
├── config.yaml.example          # Optional YAML template for local/docs (env vars are canonical)
└── .env.example                 # Supported environment variables with defaults
```

---

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Go | 1.26+ | Build and run the service (matches `go.mod`) |
| Docker + Compose | v2 | Local backing services and container builds |
| Make | any | Convenience targets |

---

## Go modules: ThunderID dependency

The service imports ThunderID as **`github.com/thunder-id/thunderid`** (for example `pkg/embed` in Thunder embed mode). Upstream development often tracks a **fork** that hosts the embed-friendly `backend/` tree on branch **`public-package`**:

- Web UI (for context only — not pasted into `go.mod`): [github.com/anushasunkada/thunder/tree/public-package/backend/pkg](https://github.com/anushasunkada/thunder/tree/public-package/backend/pkg)

### What goes in `go.mod`

Go does **not** support raw `https://github.com/.../tree/...` URLs in `go.mod`. Use a **module path** and **version** (or `replace`):

1. **`require`** — pin the logical module `github.com/thunder-id/thunderid` to a **pseudo-version** resolved from the fork (commit time + short hash).
2. **`replace`** — map that module to the fork’s **`backend`** subdirectory module, which Go fetches as `github.com/anushasunkada/thunder/backend` (repository `thunder`, subdir `backend`, same `module github.com/thunder-id/thunderid` line in `backend/go.mod`).

Example shape (exact versions change when you refresh the pin):

```go
require github.com/thunder-id/thunderid v0.0.0-20260514111244-7975af7f6646

replace github.com/thunder-id/thunderid => github.com/anushasunkada/thunder/backend v0.0.0-20260514111244-7975af7f6646
```

### Move the pin to the latest `public-package` commit

```bash
cd esignet-service
go get github.com/anushasunkada/thunder/backend@public-package
go mod tidy
```

Commit the updated `go.mod` and `go.sum` when you intentionally upgrade Thunder.

### Work against a local Thunder checkout

Temporarily point `replace` at your machine (path must reach the directory that contains Thunder’s `go.mod`, usually `backend/`):

```go
replace github.com/thunder-id/thunderid => /absolute/or/relative/path/to/thunder/backend
```

Remove or swap the `replace` before pushing if CI should use the remote fork instead.

---

## Quickstart

```bash
# 1. Clone and enter the repo
git clone <repo-url>
cd esignet-service

# 2. Copy the example env file
cp .env.example .env

# 3. Pull Go dependencies (downloads the pinned Thunder fork; see [Go modules: ThunderID dependency](#go-modules-thunderid-dependency))
go mod tidy

# 4. Optional: Thunder embed — set THUNDER_HOME in .env to a Thunder deployment directory (see Configuration)

# 5a. Start everything with Docker Compose (app + postgres + redis)
make up

# 5b. OR start only the backing services and run the server locally
make db redis   # starts postgres + redis in Docker
make dev        # go run ./cmd/esignet
```

The service is available at `http://localhost:8088`.

---

## Configuration

All configuration is supplied through environment variables. Copy `.env.example` to `.env` for local development.

### Server

| Variable | Default | Description |
|---|---|---|
| `PORT` | `8088` | HTTP listen port |
| `READ_TIMEOUT` | `15s` | Maximum duration to read the full request |
| `WRITE_TIMEOUT` | `15s` | Maximum duration to write the full response |
| `IDLE_TIMEOUT` | `60s` | Keep-alive timeout for idle connections |
| `SHUTDOWN_TIMEOUT` | `30s` | Graceful shutdown drain window |

### PostgreSQL

| Variable | Default | Description |
|---|---|---|
| `DATABASE_URL` | *(required)* | Full connection string, e.g. `postgres://user:pass@host:5432/db?sslmode=disable` |
| `DB_MAX_CONNS` | `10` | Maximum open connections in the pool |
| `DB_MIN_CONNS` | `2` | Minimum idle connections kept alive |
| `DB_MAX_CONN_LIFETIME` | `1h` | Maximum age of a connection before it is recycled |
| `DB_MAX_CONN_IDLE_TIME` | `30m` | Maximum idle time before a connection is closed |
| `DB_HEALTH_TIMEOUT` | `5s` | Timeout for startup ping and health-check pings |

### Redis

| Variable | Default | Description |
|---|---|---|
| `REDIS_ADDR` | `localhost:6379` | `host:port` of the Redis server |
| `REDIS_PASSWORD` | *(empty)* | Password, if required |
| `REDIS_DB` | `0` | Redis logical database index |
| `REDIS_DIAL_TIMEOUT` | `5s` | Timeout for establishing a new connection |
| `REDIS_READ_TIMEOUT` | `3s` | Per-command read deadline |
| `REDIS_WRITE_TIMEOUT` | `3s` | Per-command write deadline |
| `REDIS_POOL_SIZE` | `10` | Maximum number of socket connections |
| `REDIS_HEALTH_TIMEOUT` | `5s` | Timeout for startup ping and health-check pings |

### Logging

| Variable | Default | Options |
|---|---|---|
| `LOG_LEVEL` | `info` | `debug` · `info` · `warn` · `error` |
| `LOG_FORMAT` | `json` | `json` · `text` |

### Thunder embed (optional)

When **`THUNDER_HOME`** is non-empty after trimming whitespace, the binary uses **`internal/thunderembed`**: a `net/http.ServeMux` with the same `/ping` and `/health` handlers as standalone mode, plus Thunder routes from **`embed.WireThunder`**. Point `THUNDER_HOME` at a Thunder **deployment directory** on disk (the layout Thunder expects for config and assets — see ThunderID / fork docs under `pkg/embed`).

| Variable | Default | Description |
|---|---|---|
| `THUNDER_HOME` | *(empty)* | If set, enables Thunder embed mode; if empty, the standalone Chi stack is used |

---

## API Reference

In **standalone** mode, the routes below are the primary application surface. In **Thunder embed** mode, **`GET /ping`** and **`GET /health`** behave the same; additional OAuth, flow, and related paths are registered by Thunder (`embed.WireThunder`). Consult the ThunderID `pkg/` README on your pinned fork for those surfaces.

### `GET /ping`

Lightweight liveness probe. No database or cache calls are made. Use this for high-frequency load-balancer checks.

**Response** `200 OK`
```
pong
```

---

### `GET /health`

Deep readiness probe. Pings PostgreSQL and Redis concurrently within a 5-second budget.

**Response** `200 OK` — all dependencies healthy
```json
{
  "status": "ok",
  "timestamp": "2026-05-13T10:00:00Z",
  "components": {
    "postgres": { "status": "up" },
    "redis":    { "status": "up" }
  }
}
```

**Response** `503 Service Unavailable` — one or more dependencies unhealthy
```json
{
  "status": "degraded",
  "timestamp": "2026-05-13T10:00:00Z",
  "components": {
    "postgres": { "status": "up" },
    "redis":    { "status": "down", "message": "redis ping: dial tcp: connection refused" }
  }
}
```

In **standalone** mode, responses from the Chi stack include `X-Request-ID` (middleware). In **Thunder embed** mode, `/ping` and `/health` do not go through that Chi middleware; Thunder-registered routes follow Thunder’s own HTTP behavior.

## Development

### Make Targets

```bash
make dev          # go run ./cmd/esignet  (fastest inner loop)
make build        # compile → bin/esignet
make run          # build + run the binary
make test         # go test -race -cover ./...
make lint         # golangci-lint (via `go run …`; needs network on first run)
make format       # go fmt ./...
make tidy         # go mod tidy
make clean        # remove bin/
make help         # list all targets
```

### Backing Services Only

```bash
make db           # start postgres container
make redis        # start redis container
make db-down      # stop postgres container
make redis-down   # stop redis container
```

### Live Reload (Docker)

```bash
make watch        # docker compose watch — rebuilds on file changes
```

---

## Docker

### Build

```bash
# Production image
docker build --target production -t esignet-service .

# Development image (includes full Go toolchain)
docker build --target dev -t esignet-service:dev .
```

### Run with Compose

```bash
make up     # start app + postgres + redis (detached)
make down   # stop and remove containers
make watch  # start with file-watch hot rebuild
```

The compose file sets `DATABASE_URL` and `REDIS_ADDR` to point at the companion containers automatically, overriding whatever is in `.env`.

---

## Database Migrations

Migrations are managed with [Goose](https://github.com/pressly/goose). Migration files live in `pkg/db/migrations/`.

```bash
make migrate        # apply all pending migrations
make migrate-down   # roll back the most recent migration
make migrate-new    # create a new timestamped migration file (prompts for name)
```
