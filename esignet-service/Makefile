-include .env
export

BINARY    := bin/esignet
CMD       := ./cmd/esignet

.PHONY: all build run dev test lint format fix generate \
        up down watch \
        db db-down redis redis-down \
        migrate migrate-down migrate-new \
        tidy clean help

# ── Default ───────────────────────────────────────────────────────────────────
all: build

# ── Build ─────────────────────────────────────────────────────────────────────
build:
	@mkdir -p bin
	CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o $(BINARY) $(CMD)

# ── Run (production binary) ───────────────────────────────────────────────────
run: build
	./$(BINARY)

# ── Dev (go run, no compile step) ────────────────────────────────────────────
dev:
	go run $(CMD)

# ── Test ──────────────────────────────────────────────────────────────────────
test:
	go test -race -cover ./...

# ── Lint ──────────────────────────────────────────────────────────────────────
lint:
	go run github.com/golangci/golangci-lint/v2/cmd/golangci-lint@latest run

# ── Format ───────────────────────────────────────────────────────────────────
format:
	go fmt ./...

# ── Fix ──────────────────────────────────────────────────────────────────────
fix:
	go fix ./...

# ── Generate ─────────────────────────────────────────────────────────────────
generate:
	go generate ./...

# ── Module tidy ──────────────────────────────────────────────────────────────
tidy:
	go mod tidy

# ── Docker Compose (full stack) ───────────────────────────────────────────────
up:
	docker compose up -d

down:
	docker compose down

watch:
	docker compose watch

# ── Postgres only ────────────────────────────────────────────────────────────
db:
	docker compose up -d postgres

db-down:
	docker compose down postgres

# ── Redis only ───────────────────────────────────────────────────────────────
redis:
	docker compose up -d redis

redis-down:
	docker compose down redis

# ── Migrations (goose) ────────────────────────────────────────────────────────
migrate:
	go run github.com/pressly/goose/v3/cmd/goose@latest \
	  -dir pkg/db/migrations postgres $(DATABASE_URL) up

migrate-down:
	go run github.com/pressly/goose/v3/cmd/goose@latest \
	  -dir pkg/db/migrations postgres $(DATABASE_URL) down

migrate-new:
	@read -p "Migration name: " name && \
	go run github.com/pressly/goose/v3/cmd/goose@latest \
	  -dir pkg/db/migrations create $$name sql

# ── Clean ─────────────────────────────────────────────────────────────────────
clean:
	rm -rf bin/

# ── Help ──────────────────────────────────────────────────────────────────────
help:
	@echo ""
	@echo "Usage: make <target>"
	@echo ""
	@echo "  build          Compile production binary → $(BINARY)"
	@echo "  run            Build then run the binary"
	@echo "  dev            Run with go run (no compile step)"
	@echo "  test           Run tests with race detector"
	@echo "  lint           Run golangci-lint"
	@echo "  format         Run go fmt"
	@echo "  tidy           Run go mod tidy"
	@echo "  up             Start full stack (app + postgres + redis)"
	@echo "  down           Stop full stack"
	@echo "  watch          Start with docker compose watch (live reload)"
	@echo "  db             Start postgres only"
	@echo "  redis          Start redis only"
	@echo "  migrate        Run database migrations (goose up)"
	@echo "  migrate-down   Roll back last migration (goose down)"
	@echo "  migrate-new    Create a new migration file"
	@echo "  clean          Remove build artefacts"
	@echo ""
