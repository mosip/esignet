# eSignet Service

A production-grade Go HTTP service with PostgreSQL and Redis integration, structured for extensibility and operational clarity.

---

## Table of Contents

- [Overview](#overview)
- [Project Structure](#project-structure)
- [Prerequisites](#prerequisites)
- [Quickstart](#quickstart)
- [Configuration](#configuration)
- [API Reference](#api-reference)
- [Development](#development)
- [Docker](#docker)
- [Database Migrations](#database-migrations)

---

## Overview

`esignet-service` is a Go HTTP service running on port **8088**. It provides:

- Structured JSON logging via `log/slog`
- PostgreSQL connection pool via `pgx/v5`
- Redis client via `go-redis/v9`
- Chi router with request-ID injection, access logging, panic recovery, and gzip compression
- A deep health endpoint that concurrently pings all backing services
- Graceful shutdown on `SIGINT`/`SIGTERM`
- Multi-stage Docker build with a minimal Alpine production image

---

## Project Structure

```
.
├── cmd/
│   └── esignet/
│       └── main.go              # Entrypoint — wires config, logger, DB, Redis, server
├── internal/
│   ├── config/
│   │   └── config.go            # Env-var configuration (envconfig)
│   ├── db/
│   │   └── postgres.go          # pgx/v5 connection pool + Ping
│   ├── cache/
│   │   └── redis.go             # go-redis/v9 client + Ping
│   ├── middleware/
│   │   └── middleware.go        # RequestID · Logger · Recoverer
│   ├── handler/
│   │   └── health.go            # GET /health — concurrent dependency checks
│   └── server/
│       └── server.go            # Chi router, route registration, graceful shutdown
├── pkg/
│   └── logger/
│       └── logger.go            # slog JSON/text handler factory
├── Dockerfile                   # Multi-stage: dev → builder → production
├── compose.yaml                 # Full local stack: app + postgres + redis
├── Makefile                     # Developer targets
├── go.mod
└── .env.example                 # All supported environment variables with defaults
```

---

## Prerequisites

| Tool | Version | Purpose |
|---|---|---|
| Go | 1.23+ | Build and run the service |
| Docker + Compose | v2 | Local backing services and container builds |
| Make | any | Convenience targets |

---

## Quickstart

```bash
# 1. Clone and enter the repo
git clone <repo-url>
cd esignet-service

# 2. Copy the example env file
cp .env.example .env

# 3. Pull Go dependencies
go mod tidy

# 4a. Start everything with Docker Compose (app + postgres + redis)
make up

# 4b. OR start only the backing services and run the server locally
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

---

## API Reference

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

Every response includes `X-Request-ID` in the response headers.

---

## Development

### Make Targets

```bash
make dev          # go run ./cmd/esignet  (fastest inner loop)
make build        # compile → bin/esignet
make run          # build + run the binary
make test         # go test -race -cover ./...
make lint         # golangci-lint run
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
