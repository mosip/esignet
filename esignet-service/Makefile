-include .env
export

OUT_DIR         := out
BINARY          := $(OUT_DIR)/esignet.exe
CMD             := ./cmd/esignet
KEY_DIR         := keys
SIGNING_KEY     := $(KEY_DIR)/signing.key
SIGNING_CERT    := $(KEY_DIR)/signing.crt
PORT            ?= 8080
ISSUER_URL ?= http://127.0.0.1:$(PORT)
DATA_DIR ?= ./data
SIGNING_KEY_PATH ?= ./$(SIGNING_KEY)
DOCKER_IMAGE    ?= esignet:latest

GOLANGCI_LINT       ?= golangci-lint
GOLANGCI_LINT_VERSION ?= latest
SQLC_VERSION        ?= latest

THUNDER_BRANCH ?= 3037
THUNDER_MODULE := github.com/anushasunkada/thunder/backend

.PHONY: help all keys build run dev test lint lint-install smoke tidy \
        sqlc sqlc-install docker-build docker-run clean distclean update-thunder

.DEFAULT_GOAL := help

##@ Build
all: build ## Alias for build

keys: ## Generate local TLS signing key and certificate
	@mkdir -p $(KEY_DIR)
	@if [ -f "$(SIGNING_KEY)" ] && [ -f "$(SIGNING_CERT)" ]; then \
		echo "keys: $(SIGNING_KEY) and $(SIGNING_CERT) already exist"; \
	else \
		openssl req -x509 -newkey rsa:2048 \
			-keyout "$(SIGNING_KEY)" -out "$(SIGNING_CERT)" \
			-days 3650 -nodes -subj "/CN=esignet"; \
		echo "keys: wrote $(SIGNING_KEY) and $(SIGNING_CERT)"; \
	fi

build: keys ## Compile production binary (out/esignet.exe)
	@mkdir -p $(OUT_DIR)
	CGO_ENABLED=0 go build -trimpath -ldflags="-s -w" -o $(BINARY) $(CMD)

##@ Run
run: keys ## Run with go run (development)
	PORT=$(PORT) \
	ISSUER_URL=$(ISSUER_URL) \
	DATA_DIR=$(DATA_DIR) \
	SIGNING_KEY_PATH=$(SIGNING_KEY_PATH) \
	AUTHN_PROVIDER=$(or $(AUTHN_PROVIDER),catalog) \
	go run $(CMD)

dev: run ## Alias for run

##@ Test
test: keys ## Run unit tests with race detector
	go test -race -cover ./...

coverage: keys ## Run tests and write coverage profile (coverage.out)
	go test -race -coverprofile=coverage.out -covermode=atomic ./...
	@go tool cover -func=coverage.out | tail -1

coverage-html: coverage ## Generate HTML coverage report (coverage.html)
	go tool cover -html=coverage.out -o coverage.html
	@echo "report: coverage.html"

lint: ## Run golangci-lint
	@PATH="$$(go env GOPATH)/bin:$$PATH"; \
	export GOLANGCI_LINT_CACHE="$(CURDIR)/.golangci-lint-cache"; \
	if command -v golangci-lint >/dev/null 2>&1; then \
		golangci-lint run ./...; \
	else \
		echo "golangci-lint not on PATH; running via go run (use make lint-install to install)"; \
		go run github.com/golangci/golangci-lint/v2/cmd/golangci-lint@$(GOLANGCI_LINT_VERSION) run ./...; \
	fi

lint-install: ## Install golangci-lint (GOLANGCI_LINT_VERSION=$(GOLANGCI_LINT_VERSION))
	go install github.com/golangci/golangci-lint/v2/cmd/golangci-lint@$(GOLANGCI_LINT_VERSION)
	@echo "installed: $$(go env GOPATH)/bin/golangci-lint"

smoke: ## End-to-end OAuth smoke test (server must be running)
	BASE_URL=$(or $(BASE_URL),$(ISSUER_URL)) ./scripts/oauth-smoke.sh

##@ Docker
docker-build: ## Build container image (esignet:latest by default)
	docker build -f Dockerfile -t $(DOCKER_IMAGE) .

docker-run: docker-build ## Run container mapped to PORT (default 8080)
	docker run --rm -p $(PORT):8080 \
		-e ISSUER_URL=$(ISSUER_URL) \
		$(DOCKER_IMAGE)

##@ Maintenance
sqlc: ## Regenerate DB layer from SQL (requires sqlc; run sqlc-install first)
	@PATH="$$(go env GOPATH)/bin:$$PATH"; \
	if command -v sqlc >/dev/null 2>&1; then \
		sqlc generate; \
	else \
		echo "sqlc not on PATH; running via go run (use make sqlc-install to install)"; \
		go run github.com/sqlc-dev/sqlc/cmd/sqlc@$(SQLC_VERSION) generate; \
	fi

sqlc-install: ## Install sqlc (SQLC_VERSION=$(SQLC_VERSION))
	go install github.com/sqlc-dev/sqlc/cmd/sqlc@$(SQLC_VERSION)
	@echo "installed: $$(go env GOPATH)/bin/sqlc"

update-thunder: ## Update thunder replace directive to latest commit on THUNDER_BRANCH (default: 3037)
	@echo "Fetching latest commit on branch '$(THUNDER_BRANCH)'..."
	@SHA=$$(git ls-remote https://github.com/anushasunkada/thunder.git refs/heads/$(THUNDER_BRANCH) | awk '{print $$1}'); \
	if [ -z "$$SHA" ]; then echo "error: branch '$(THUNDER_BRANCH)' not found" && exit 1; fi; \
	VERSION=$$(GOPROXY=direct go list -m -json $(THUNDER_MODULE)@$(THUNDER_BRANCH) | grep '"Version"' | sed 's/.*: "\(.*\)".*/\1/'); \
	if [ -z "$$VERSION" ]; then echo "error: could not resolve version" && exit 1; fi; \
	echo "Resolved version: $$VERSION"; \
	sed -i.bak "s|replace github.com/thunder-id/thunderid => $(THUNDER_MODULE) .*|replace github.com/thunder-id/thunderid => $(THUNDER_MODULE) $$VERSION|" go.mod && rm -f go.mod.bak; \
	echo "go.mod updated"

tidy: ## Run go mod tidy
	go mod tidy

clean: ## Remove build artefacts and coverage output
	rm -f $(BINARY) engine
	rm -rf bin/ $(OUT_DIR)/
	rm -f coverage.out coverage.html
	@echo "cleaned"

distclean: clean ## Also remove generated signing keys under keys/
	rm -f $(SIGNING_KEY) $(SIGNING_CERT)
	@rmdir $(KEY_DIR) 2>/dev/null || true

##@ Help
help: ## Show this help
	@awk 'BEGIN {FS = ":.*##"; printf "\nUsage: make <target>\n"} \
		/^##@/ { printf "\n%s\n", substr($$0, 5) } \
		/^[a-zA-Z0-9_.-]+:.*##/ { printf "  %-18s %s\n", $$1, $$2 }' $(MAKEFILE_LIST)
	@echo ""
	@echo "Environment (override on the command line or in .env):"
	@echo "  PORT=$(PORT)"
	@echo "  ISSUER_URL=$(ISSUER_URL)"
	@echo "  DATA_DIR=$(DATA_DIR)"
	@echo "  SIGNING_KEY_PATH=$(SIGNING_KEY_PATH)"
	@echo "  AUTHN_PROVIDER=$(AUTHN_PROVIDER) (catalog|mosip, default catalog)"
	@echo "  MOSIP_API_BASE_URL (optional, used to derive IDA endpoint URLs)"
	@echo "  MOSIP_LICENSE_KEY (optional, used in IDA endpoint paths)"
	@echo "  MOSIP_IDA_PARTNER_CERT_URL (optional override)"
	@echo "  MOSIP_SEND_OTP_BASE_URL (optional override)"
	@echo "  MOSIP_KYC_AUTH_BASE_URL (optional override)"
	@echo "  MOSIP_KYC_EXCHANGE_BASE_URL (optional override)"
	@echo "  MOSIP_P12_PATH (required for MOSIP auth)"
	@echo "  MOSIP_P12_PASSWORD (required for MOSIP auth)"
	@echo ""
