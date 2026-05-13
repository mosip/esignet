# AGENTS.md — Guide for AI Agents Working in This Codebase

This file tells AI agents (Claude, Copilot, Cursor, etc.) everything they need to understand the codebase shape, make consistent changes, and avoid common mistakes. Read it before touching any Go source file.

---

## Project at a Glance

| Attribute | Value |
|---|---|
| Language | Go 1.23+ |
| Module | `github.com/mosip/esignet` |
| HTTP router | `github.com/go-chi/chi/v5` |
| Database | PostgreSQL via `github.com/jackc/pgx/v5` |
| Cache | Redis via `github.com/redis/go-redis/v9` |
| Config | Env vars via `github.com/kelseyhightower/envconfig` |
| Logging | `log/slog` (JSON by default) |
| Entry point | `cmd/esignet/main.go` |
| Listen port | `8088` |

---

## Directory Layout and Responsibilities

```
cmd/esignet/main.go          Wires everything: config → logger → db → redis → server.
                             Never put business logic here. Keep it to construction and lifecycle only.

internal/config/config.go    Single config struct. All env vars live here.
                             Add a new field when a new tunable is needed; never use os.Getenv elsewhere.

internal/db/postgres.go      pgx pool wrapper. Exposes Pool for raw queries and Ping for health.
                             Add repository types as separate files under internal/db/ (e.g. user_repo.go).

internal/cache/redis.go      go-redis client wrapper. Exposes Client for raw commands and Ping for health.
                             Add cache helpers as separate files under internal/cache/.

internal/middleware/         Pure http.Handler middleware. Each function must be stateless and composable.
  middleware.go              RequestID, Logger, Recoverer live here.
                             Add new middleware (auth, rate-limit, CORS) as additional functions in this file
                             or as new files in this package.

internal/handler/            HTTP handler functions. One file per resource domain.
  health.go                  GET /health — the only handler currently.
                             New handlers go in new files: e.g. internal/handler/token.go for /token routes.

internal/server/server.go    Owns the chi.Router, middleware stack, and http.Server lifecycle.
                             Route registration happens here. Import new handler packages and add routes
                             inside New().

pkg/logger/logger.go         slog factory. Do not add application logic here.
```

---

## Established Patterns — Follow These Exactly

### 1. Adding a New HTTP Endpoint

1. Create `internal/handler/<domain>.go` with a constructor that accepts its dependencies (logger, db, cache, etc.) as arguments — no globals.
2. The handler file returns `http.HandlerFunc` values, not methods. Keep the `Pinger` interface pattern: define minimal interfaces in the handler package, not in the dependency packages.
3. Register the route in `internal/server/server.go` inside `New()`.
4. If the handler needs a new config value, add it to `internal/config/config.go` first.

**Example — adding `GET /token`:**

```go
// internal/handler/token.go
package handler

import (
    "encoding/json"
    "log/slog"
    "net/http"
)

func TokenHandler(log *slog.Logger) http.HandlerFunc {
    return func(w http.ResponseWriter, r *http.Request) {
        w.Header().Set("Content-Type", "application/json")
        _ = json.NewEncoder(w).Encode(map[string]string{"token": "..."})
    }
}
```

```go
// internal/server/server.go — inside New(), after the /health route
r.Get("/token", handler.TokenHandler(log))
```

### 2. Adding a New Config Field

Always add to the appropriate nested struct in `internal/config/config.go`. Use `envconfig` tags.

```go
type ServerConfig struct {
    // existing fields ...
    MaxHeaderBytes int `envconfig:"MAX_HEADER_BYTES" default:"1048576"`
}
```

Then document the new variable in `.env.example` and in the `README.md` configuration table.

### 3. Adding a Database Repository

Create a new file inside `internal/db/`, not alongside `postgres.go`. Accept `*pgxpool.Pool` (not `*Postgres`) so the function is testable with a pool mock.

```go
// internal/db/user_repo.go
package db

import (
    "context"
    "fmt"

    "github.com/jackc/pgx/v5/pgxpool"
)

type UserRepo struct {
    pool *pgxpool.Pool
}

func NewUserRepo(pool *pgxpool.Pool) *UserRepo {
    return &UserRepo{pool: pool}
}

func (r *UserRepo) FindByID(ctx context.Context, id string) (*User, error) {
    // ...
    return nil, fmt.Errorf("find user: %w", err)
}
```

Wire it in `main.go`:
```go
userRepo := db.NewUserRepo(postgres.Pool)
```

### 4. Adding a Redis Helper

Same pattern as DB repos — new file in `internal/cache/`, accept `*redis.Client`.

### 5. Adding Middleware

Add a new function to `internal/middleware/middleware.go`. Middleware must be of the form:

```go
func MyMiddleware(...config...) func(http.Handler) http.Handler {
    return func(next http.Handler) http.Handler {
        return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
            // pre-processing
            next.ServeHTTP(w, r)
            // post-processing
        })
    }
}
```

Register it in `internal/server/server.go` with `r.Use(middleware.MyMiddleware(...))`.

---

## Code Style Conventions

This project follows the Go style guide in `.llm/STYLE.md`. Key rules:

- **No `any` unless unavoidable.** Use typed structs for all JSON shapes.
- **Error wrapping.** Always `fmt.Errorf("context: %w", err)`. Never log *and* return an error — let the caller decide.
- **Constructor naming.** Primary constructor in a package is always `New(...)`, not `NewFoo(...)`.
- **Interfaces belong at the consumer.** Define the `Pinger` interface in `internal/handler`, not in `internal/db`. Keep interfaces as small as possible.
- **Context first.** Every function doing I/O takes `ctx context.Context` as its first argument.
- **Receiver naming.** One or two letters, consistent across all methods of a type. Never `this` or `self`.
- **Import groups.** Four groups separated by blank lines: stdlib → external → internal → side-effect.
- **No globals.** Config, logger, db, and cache are always passed explicitly. No `init()` side-effects that register global state.

---

## Dependency Management

Never call `go get` directly to add a dependency. Instead:

1. Add the import to the `.go` source file.
2. Run `go mod tidy`.

This keeps `go.mod` and `go.sum` in sync and removes unused modules automatically.

To upgrade a dependency:
```bash
go get github.com/foo/bar@latest
go mod tidy
```

---

## What Belongs Where — Quick Decision Table

| What you're adding | Where it goes |
|---|---|
| New HTTP route | `internal/handler/<domain>.go` + registered in `internal/server/server.go` |
| New env-var | `internal/config/config.go` + `.env.example` |
| New DB query | `internal/db/<entity>_repo.go` |
| New Redis operation | `internal/cache/<purpose>.go` |
| New middleware | `internal/middleware/middleware.go` |
| Shared utility (no I/O) | `pkg/<name>/<name>.go` |
| New binary entrypoint | `cmd/<name>/main.go` |
| Database migration | `pkg/db/migrations/` (via `make migrate-new`) |

---

## Things to Avoid

- **Do not** store `context.Context` in a struct field.
- **Do not** use `os.Getenv` directly — all env config must flow through `internal/config/config.go`.
- **Do not** call `log.Fatal` or `os.Exit` outside of `main.go`.
- **Do not** add global `var` or `init()` that mutates shared state.
- **Do not** skip the `go mod tidy` step after adding or removing an import.
- **Do not** write new handler logic inside `internal/server/server.go` — that file is for routing and lifecycle only.
- **Do not** add routes that bypass the middleware stack (i.e. outside of the chi router).

---

## Verifying Your Changes

Before considering a change complete:

```bash
go mod tidy          # sync dependencies
go vet ./...         # static analysis
go fmt ./...         # formatting
go test -race ./...  # tests with race detector
make build           # confirm binary compiles
```

For a full lint pass (requires Docker or a local golangci-lint install):
```bash
make lint
```

---

## Health Check Contract

The `GET /health` endpoint is the canonical readiness probe. Any new backing service added to the application **must** implement the `handler.Pinger` interface and be passed into `server.Dependencies` so it appears in the health response. This ensures operators can see the full dependency graph in one call.

```go
// handler.Pinger — the only interface a dependency needs to satisfy
type Pinger interface {
    Ping(ctx context.Context) error
}
```
