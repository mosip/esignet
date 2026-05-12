-include .env
export


# [postgres-docker]
.PHONY: db db-down
# [/postgres-docker]

# [postgres]
.PHONY: migrate migrate-down migrate-new
# [/postgres]

# [docker]
.PHONY: up down watch
# [/docker]

# [server]
.PHONY: serve
# [/server]

.PHONY: generate fix lint format test build dev

# [docker]
up:
	docker compose --profile postgres up -d

down:
	docker compose --profile postgres down

watch:
	docker compose watch
# [/docker]

# [postgres-docker]
# Database
db:
	docker compose --profile postgres up -d postgres

db-down:
	docker compose --profile postgres down
# [/postgres-docker]

# [postgres]
# Migrations
migrate:
	go run github.com/pressly/goose/v3/cmd/goose@latest -dir pkg/db/migrations postgres $(DATABASE_URL) up

migrate-down:
	go run github.com/pressly/goose/v3/cmd/goose@latest -dir pkg/db/migrations postgres $(DATABASE_URL) down

migrate-new:
	@read -p "Migration name: " name && \
	go run github.com/pressly/goose/v3/cmd/goose@latest -dir pkg/db/migrations create $$name sql
# [/postgres]

# Go Commands
generate:
	go generate ./...

fix:
	go fix ./...

lint:
	go run github.com/golangci/golangci-lint/v2/cmd/golangci-lint@latest run 

format:
	go fmt ./...

test:
	go test -race ./...

build:
	go build -o bin/template ./cmd/template

# [server]
serve:
	go run ./cmd/template
# [/server]

dev:
	go run ./cmd/template
